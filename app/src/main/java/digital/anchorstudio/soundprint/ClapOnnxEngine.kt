package digital.anchorstudio.soundprint

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONObject

/**
 * Native CLAP inference. The 18 text embeddings are generated once from the
 * bundled prompt set and cached against the pinned model version. Audio is
 * decoded by Android MediaCodec and passed directly to ONNX Runtime.
 */
class ClapOnnxEngine(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val environment = OrtEnvironment.getEnvironment()
    private var audioSession: OrtSession? = null
    private var textEmbeddings: List<FloatArray> = emptyList()
    private val modelManager = ClapModelManager(app)

    suspend fun prepare(onProgress: (ClapModelInstallProgress) -> Unit = {}) = withContext(Dispatchers.IO) {
        val files = modelManager.installedFiles() ?: modelManager.install(onProgress)
        val cache = File(files.audioModel.parentFile, "mood-text-embeddings.bin")
        textEmbeddings = readEmbeddingCache(cache) ?: createTextEmbeddings(files).also { writeEmbeddingCache(cache, it) }
        audioSession?.close()
        val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
        audioSession = environment.createSession(files.audioModel.absolutePath, options)
    }

    suspend fun analyse(track: MusicTrack): Map<String, Float> {
        check(audioSession != null && textEmbeddings.size == SoundprintMood.entries.size) {
            "The CLAP model has not been prepared."
        }
        val waveform = ClapPcmDecoder(app).decodeCenterWindow(track)
        val features = ClapFeatureExtractor.extract(waveform)
        return withContext(Dispatchers.Default) {
            val session = audioSession ?: error("CLAP audio model is not loaded.")
            val inputName = session.inputInfo.keys.firstOrNull { it.contains("input_features") }
                ?: session.inputInfo.keys.firstOrNull()
                ?: error("CLAP audio model has no input.")
            val inputInfo = session.inputInfo.getValue(inputName).info as TensorInfo
            val shape = audioShape(inputInfo.shape)
            val channelFirst = isChannelFirst(inputInfo.shape)
            val oneCrop = if (channelFirst) toChannelFirst(features) else features
            val cropCount = if (shape.size == 4) shape[1].toInt() else 1
            val modelFeatures = if (cropCount <= 1) oneCrop else FloatArray(oneCrop.size * cropCount).also { target ->
                repeat(cropCount) { crop -> System.arraycopy(oneCrop, 0, target, crop * oneCrop.size, oneCrop.size) }
            }
            val tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(modelFeatures), shape)
            val inputs = linkedMapOf<String, OnnxTensor>(inputName to tensor)
            try {
                session.inputInfo.keys.filter { it != inputName }.forEach { name ->
                    val info = session.inputInfo.getValue(name).info as? TensorInfo
                    val dimensions = info?.shape?.map { if (it <= 0L) 1L else it }?.toLongArray() ?: longArrayOf(1, 1)
                    val elementCount = dimensions.fold(1L) { total, dimension -> total * dimension }.toInt()
                    when {
                        name.contains("is_longer") -> inputs[name] = when (info?.type) {
                            OnnxJavaType.BOOL -> booleanTensor(dimensions, false)
                            OnnxJavaType.INT64 -> OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(elementCount)), dimensions)
                            else -> OnnxTensor.createTensor(environment, FloatBuffer.wrap(FloatArray(elementCount)), dimensions)
                        }
                        name.contains("attention_mask") -> inputs[name] = when (info?.type) {
                            OnnxJavaType.BOOL -> booleanTensor(dimensions, true)
                            OnnxJavaType.INT64 -> OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(elementCount) { 1L }), dimensions)
                            else -> OnnxTensor.createTensor(environment, FloatBuffer.wrap(FloatArray(elementCount) { 1f }), dimensions)
                        }
                    }
                }
                session.run(inputs).use { result ->
                    val outputName = session.outputNames.firstOrNull { it.contains("audio_embeds") }
                        ?: session.outputNames.first()
                    val output = result.get(outputName).get() as OnnxTensor
                    val audioVector = flatten(output.value).also { check(it.size == textEmbeddings.first().size) {
                        "CLAP returned an unexpected audio embedding size."
                    } }
                    val audioNorm = normalize(audioVector)
                    val logits = textEmbeddings.map { dot(audioNorm, it) * 14.285714f }
                    val maxLogit = logits.maxOrNull() ?: error("CLAP returned no mood scores.")
                    val exponentials = logits.map { exp((it - maxLogit).toDouble()).toFloat() }
                    val total = exponentials.sum().coerceAtLeast(1e-12f)
                    val probabilities = exponentials.map { it / total }
                    val highest = probabilities.maxOrNull()?.coerceAtLeast(1e-12f) ?: 1f
                    SoundprintMood.entries.mapIndexed { index, mood ->
                        mood.id to (probabilities[index] / highest).coerceIn(0f, 1f)
                    }.toMap()
                }
            } finally {
                inputs.values.forEach { runCatching { it.close() } }
            }
        }
    }

    private suspend fun createTextEmbeddings(files: ClapModelFiles): List<FloatArray> = withContext(Dispatchers.IO) {
        val tokenizer = ClapRobertaTokenizer(files.vocabulary, files.merges)
        val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
        environment.createSession(files.textModel.absolutePath, options).use { session ->
            SoundprintMood.entries.map { mood ->
                coroutineContext.ensureActive()
                val tokenIds = tokenizer.encode(mood.prompt)
                val ids = OnnxTensor.createTensor(environment, LongBuffer.wrap(tokenIds), longArrayOf(1, tokenIds.size.toLong()))
                val mask = OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(tokenIds.size) { 1L }), longArrayOf(1, tokenIds.size.toLong()))
                val inputs = linkedMapOf<String, OnnxTensor>()
                try {
                    session.inputInfo.keys.forEach { name ->
                        when {
                            name.contains("input_ids") -> inputs[name] = ids
                            name.contains("attention_mask") -> inputs[name] = mask
                            name.contains("token_type_ids") -> inputs[name] = OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(tokenIds.size)), longArrayOf(1, tokenIds.size.toLong()))
                            name.contains("position_ids") -> inputs[name] = OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(tokenIds.size) { it.toLong() + 2L }), longArrayOf(1, tokenIds.size.toLong()))
                            else -> error("Unsupported CLAP text input: $name")
                        }
                    }
                    session.run(inputs).use { result ->
                        val outputName = session.outputNames.firstOrNull { it.contains("text_embeds") }
                            ?: session.outputNames.first()
                        normalize(flatten((result.get(outputName).get() as OnnxTensor).value))
                    }
                } finally {
                    inputs.values.distinct().forEach { runCatching { it.close() } }
                }
            }
        }
    }

    private fun readEmbeddingCache(file: File): List<FloatArray>? = runCatching {
        if (!file.isFile) return null
        java.io.DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readUTF() != CLAP_MODEL_VERSION) return null
            val count = input.readInt()
            if (count != SoundprintMood.entries.size) return null
            val dimension = input.readInt()
            List(count) { FloatArray(dimension) { input.readFloat() } }
        }
    }.getOrNull()

    private fun writeEmbeddingCache(file: File, embeddings: List<FloatArray>) {
        java.io.DataOutputStream(file.outputStream().buffered()).use { output ->
            output.writeUTF(CLAP_MODEL_VERSION)
            output.writeInt(embeddings.size)
            output.writeInt(embeddings.firstOrNull()?.size ?: 0)
            embeddings.forEach { vector -> vector.forEach(output::writeFloat) }
        }
    }

    override fun close() {
        audioSession?.close()
        audioSession = null
    }

    private fun audioShape(modelShape: LongArray): LongArray = when (modelShape.size) {
        4 -> longArrayOf(
            1,
            modelShape[1].takeIf { it > 0L } ?: 1L,
            if (modelShape[2] == 64L) 64L else 1000L,
            if (modelShape[2] == 64L) 1000L else 64L
        )
        3 -> if (modelShape[1] == 64L) longArrayOf(1, 64, 1000) else longArrayOf(1, 1000, 64)
        else -> error("Unsupported CLAP audio input rank: " + modelShape.size)
    }

    private fun isChannelFirst(modelShape: LongArray): Boolean =
        (modelShape.size == 4 && modelShape[2] == 64L) ||
            (modelShape.size == 3 && modelShape[1] == 64L)

    private fun toChannelFirst(features: FloatArray): FloatArray {
        val output = FloatArray(features.size)
        for (frame in 0 until 1000) {
            for (mel in 0 until 64) output[mel * 1000 + frame] = features[frame * 64 + mel]
        }
        return output
    }

    private fun booleanTensor(shape: LongArray, value: Boolean): OnnxTensor {
        val count = shape.fold(1L) { total, dimension -> total * dimension }.toInt()
        val buffer = ByteBuffer.allocateDirect(count).order(ByteOrder.nativeOrder())
        repeat(count) { buffer.put(if (value) 1.toByte() else 0.toByte()) }
        buffer.flip()
        return OnnxTensor.createTensor(environment, buffer, shape, OnnxJavaType.BOOL)
    }

    private fun flatten(value: Any?): FloatArray = when (value) {
        is FloatArray -> value
        is Array<*> -> value.flatMap { flatten(it).asIterable() }.toFloatArray()
        is Number -> floatArrayOf(value.toFloat())
        else -> error("CLAP returned an unsupported tensor value.")
    }

    private fun normalize(values: FloatArray): FloatArray {
        val norm = sqrt(values.sumOf { it.toDouble() * it }.toFloat()).coerceAtLeast(1e-12f)
        return FloatArray(values.size) { values[it] / norm }
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size)
        var total = 0f
        for (i in a.indices) total += a[i] * b[i]
        return total
    }
}

private class ClapRobertaTokenizer(vocabFile: File, mergesFile: File) {
    private val vocab = JSONObject(vocabFile.readText()).let { json ->
        json.keys().asSequence().associateWith { json.getInt(it) }
    }
    private val ranks = HashMap<String, Int>()
    private val pattern = Regex("(?i:'s|'t|'re|'ve|'m|'ll|'d)| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+")
    private val byteEncoder = createByteEncoder()

    init {
        BufferedReader(FileReader(mergesFile)).useLines { lines ->
            lines.drop(1).forEachIndexed { index, line ->
                val pair = line.trim().split(Regex("\\s+"))
                if (pair.size == 2) ranks[pair[0] + " " + pair[1]] = index
            }
        }
    }

    fun encode(text: String): LongArray {
        val tokens = mutableListOf<Long>()
        tokens += 0L
        pattern.findAll(text).forEach { match ->
            val encoded = buildString {
                match.value.toByteArray(Charsets.UTF_8).forEach { byte ->
                    append(byteEncoder[byte.toInt() and 0xff])
                }
            }
            bpe(encoded).forEach { piece -> tokens += (vocab[piece] ?: 3).toLong() }
        }
        tokens += 2L
        return tokens.take(512).toLongArray()
    }

    private fun bpe(token: String): List<String> {
        val parts = token.map { it.toString() }.toMutableList()
        while (parts.size > 1) {
            var bestPair: String? = null
            var bestRank = Int.MAX_VALUE
            for (index in 0 until parts.lastIndex) {
                val pair = parts[index] + " " + parts[index + 1]
                val rank = ranks[pair] ?: continue
                if (rank < bestRank) { bestRank = rank; bestPair = pair }
            }
            val selectedPair = bestPair ?: break
            val merged = mutableListOf<String>()
            var index = 0
            while (index < parts.size) {
                if (index < parts.lastIndex && parts[index] + " " + parts[index + 1] == selectedPair) {
                    merged += parts[index] + parts[index + 1]
                    index += 2
                } else {
                    merged += parts[index]
                    index++
                }
            }
            parts.clear()
            parts.addAll(merged)
        }
        return parts
    }

    private fun createByteEncoder(): CharArray {
        val bytes = mutableListOf<Int>()
        val chars = mutableListOf<Int>()
        (33..126).forEach { bytes += it; chars += it }
        (161..172).forEach { bytes += it; chars += it }
        (174..255).forEach { bytes += it; chars += it }
        var extra = 0
        for (byte in 0..255) {
            if (byte !in bytes) { bytes += byte; chars += 256 + extra; extra++ }
        }
        val result = CharArray(256)
        bytes.forEachIndexed { index, byte -> result[byte] = chars[index].toChar() }
        return result
    }
}

private object ClapFeatureExtractor {
    private const val SAMPLE_RATE = 48_000
    private const val FFT_SIZE = 1024
    private const val HOP = 480
    private const val MEL_BANDS = 64
    private const val FRAMES = 1000

    fun extract(input: FloatArray): FloatArray {
        require(input.isNotEmpty()) { "The selected track has no decodable audio." }
        val samples = FloatArray(SAMPLE_RATE * 10)
        if (input.size >= samples.size) {
            val start = ((input.size - samples.size) / 2).coerceAtLeast(0)
            input.copyInto(samples, 0, start, start + samples.size)
        } else {
            for (index in samples.indices) samples[index] = input[index % input.size]
        }
        val filters = melFilters()
        val window = DoubleArray(FFT_SIZE) { 0.5 - 0.5 * cos(2.0 * PI * it / FFT_SIZE) }
        val real = DoubleArray(FFT_SIZE)
        val imaginary = DoubleArray(FFT_SIZE)
        val output = FloatArray(FRAMES * MEL_BANDS)
        for (frame in 0 until FRAMES) {
            val start = frame * HOP - FFT_SIZE / 2
            for (i in 0 until FFT_SIZE) {
                val rawIndex = start + i
                val sampleIndex = when {
                    rawIndex < 0 -> (-rawIndex).coerceAtMost(samples.lastIndex)
                    rawIndex >= samples.size -> (2 * samples.size - rawIndex - 2).coerceIn(0, samples.lastIndex)
                    else -> rawIndex
                }
                real[i] = samples[sampleIndex].toDouble() * window[i]
                imaginary[i] = 0.0
            }
            fft(real, imaginary)
            val power = DoubleArray(FFT_SIZE / 2 + 1) { bin -> real[bin] * real[bin] + imaginary[bin] * imaginary[bin] }
            for (mel in 0 until MEL_BANDS) {
                var energy = 0.0
                for (bin in power.indices) energy += power[bin] * filters[mel][bin]
                output[frame * MEL_BANDS + mel] = (10.0 * kotlin.math.log10(max(1e-10, energy))).toFloat()
            }
        }
        return output
    }

    private fun melFilters(): Array<DoubleArray> {
        fun hzToMel(hz: Double): Double = if (hz < 1000.0) hz / (200.0 / 3.0) else 15.0 + ln(hz / 1000.0) / (ln(6.4) / 27.0)
        fun melToHz(mel: Double): Double = if (mel < 15.0) mel * (200.0 / 3.0) else 1000.0 * exp((ln(6.4) / 27.0) * (mel - 15.0))
        val minMel = hzToMel(50.0)
        val maxMel = hzToMel(14_000.0)
        val points = DoubleArray(MEL_BANDS + 2) { index -> melToHz(minMel + (maxMel - minMel) * index / (MEL_BANDS + 1)) }
        return Array(MEL_BANDS) { band ->
            val left = points[band]
            val center = points[band + 1]
            val right = points[band + 2]
            DoubleArray(FFT_SIZE / 2 + 1) { bin ->
                val hz = bin.toDouble() * SAMPLE_RATE / FFT_SIZE
                val lower = (hz - left) / max(1e-12, center - left)
                val upper = (right - hz) / max(1e-12, right - center)
                max(0.0, min(lower, upper)) * (2.0 / max(1e-12, right - left))
            }
        }
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = ti
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wr = cos(angle); val wi = sin(angle)
            var start = 0
            while (start < n) {
                var ur = 1.0; var ui = 0.0
                for (offset in 0 until length / 2) {
                    val even = start + offset; val odd = even + length / 2
                    val vr = real[odd] * ur - imaginary[odd] * ui
                    val vi = real[odd] * ui + imaginary[odd] * ur
                    real[odd] = real[even] - vr; imaginary[odd] = imaginary[even] - vi
                    real[even] += vr; imaginary[even] += vi
                    val next = ur * wr - ui * wi
                    ui = ur * wi + ui * wr; ur = next
                }
                start += length
            }
            length *= 2
        }
    }
}

private class ClapPcmDecoder(private val context: Context) {
    suspend fun decodeCenterWindow(track: MusicTrack): FloatArray = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        val samples = FloatAccumulator()
        var sampleRate = track.sampleRate.takeIf { it > 0 } ?: 44_100
        var channels = track.channelCount.takeIf { it > 0 } ?: 2
        val startUs = ((track.durationMs / 2L - 5_000L).coerceAtLeast(0L)) * 1000L
        val endUs = startUs + 10_000_000L
        try {
            extractor.setDataSource(context, Uri.parse(track.uri), null)
            var format: MediaFormat? = null
            var audioTrack = -1
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                if (candidate.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                    format = candidate; audioTrack = index; break
                }
            }
            val audioFormat = format ?: error("Android could not find an audio stream in this file.")
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: error("Audio decoder format is unavailable.")
            if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            extractor.selectTrack(audioTrack)
            if (startUs > 0L) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(audioFormat, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var outputRate = sampleRate
            var outputChannels = channels
            while (!outputEnded) {
                coroutineContext.ensureActive()
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inputIndex) ?: error("Audio decoder input is unavailable.")
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        val time = extractor.sampleTime
                        if (size < 0 || time >= endUs) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, max(0L, time), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, max(0L, time), 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = decoder.outputFormat
                        if (out.containsKey(MediaFormat.KEY_SAMPLE_RATE)) outputRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (out.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) outputChannels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = out.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        try {
                            val buffer = decoder.getOutputBuffer(outputIndex)
                            if (buffer != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                appendWindow(buffer, info, outputChannels, encoding, outputRate, startUs, endUs, samples)
                            }
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || samples.size >= outputRate * 10) outputEnded = true
                        } finally {
                            decoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }
            require(samples.size >= outputRate / 2) { "Android could not decode enough audio from this track." }
            val decoded = samples.toArray()
            resample(decoded, outputRate, 48_000)
        } finally {
            runCatching { decoder?.stop() }; runCatching { decoder?.release() }; runCatching { extractor.release() }
        }
    }

    private fun appendWindow(
        source: ByteBuffer, info: MediaCodec.BufferInfo, channels: Int, encoding: Int, rate: Int,
        startUs: Long, endUs: Long, output: FloatAccumulator
    ) {
        val bytes = when (encoding) { AudioFormat.ENCODING_PCM_FLOAT -> 4; AudioFormat.ENCODING_PCM_8BIT -> 1; else -> 2 }
        val frameBytes = bytes * channels.coerceAtLeast(1)
        val frames = info.size / frameBytes
        val firstFrame = max(0, (((startUs - info.presentationTimeUs).coerceAtLeast(0L) * rate) / 1_000_000L).toInt())
        val lastFrame = min(frames, (((endUs - info.presentationTimeUs).coerceAtLeast(0L) * rate) / 1_000_000L).toInt())
        if (lastFrame <= firstFrame) return
        val data = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        data.position(info.offset + firstFrame * frameBytes)
        data.limit(info.offset + lastFrame * frameBytes)
        while (data.remaining() >= frameBytes) {
            var sum = 0.0
            repeat(channels.coerceAtLeast(1)) {
                val value = when (encoding) {
                    AudioFormat.ENCODING_PCM_FLOAT -> data.float.toDouble()
                    AudioFormat.ENCODING_PCM_8BIT -> ((data.get().toInt() and 0xff) - 128) / 128.0
                    else -> data.short.toDouble() / 32768.0
                }
                sum += value.coerceIn(-1.0, 1.0)
            }
            output.add((sum / channels.coerceAtLeast(1)).toFloat())
        }
    }

    private fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to) return input
        val count = (input.size.toLong() * to / from).toInt().coerceAtLeast(1)
        return FloatArray(count) { index ->
            val position = index.toDouble() * from / to
            val left = position.toInt().coerceIn(0, input.lastIndex)
            val right = min(input.lastIndex, left + 1)
            val mix = (position - left).toFloat()
            input[left] * (1f - mix) + input[right] * mix
        }
    }

    private class FloatAccumulator {
        private var data = FloatArray(65_536)
        var size = 0
            private set
        fun add(value: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = value
        }
        fun toArray() = data.copyOf(size)
    }
}
