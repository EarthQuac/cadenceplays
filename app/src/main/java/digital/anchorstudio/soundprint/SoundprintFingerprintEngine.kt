package digital.anchorstudio.soundprint

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

private const val SOUNDPRINT_VERSION = 1
private const val FEATURE_BANDS = 24
private const val FFT_SIZE = 4096

data class SoundprintFingerprint(
    val trackId: String,
    val sourceSize: Long,
    val sourceModifiedAt: Long,
    val durationMs: Long,
    val sampleRate: Int,
    val frameCount: Int,
    val featureBands: Int,
    val compressedFeatures: ByteArray,
    val generatedAt: Long = System.currentTimeMillis(),
    val version: Int = SOUNDPRINT_VERSION
)

enum class SoundprintMatchKind { EXACT_AUDIO, ACOUSTIC, METADATA }

data class SoundprintMatch(
    val first: MusicTrack,
    val second: MusicTrack,
    val kind: SoundprintMatchKind,
    val confidence: Int,
    val reasons: List<String>,
    val versionWarning: Boolean
)

data class SoundprintDuplicateGroup(
    val id: String,
    val tracks: List<MusicTrack>,
    val matches: List<SoundprintMatch>,
    val containsExactAudio: Boolean
)

data class SoundprintScanProgress(
    val completed: Int,
    val total: Int,
    val currentTitle: String,
    val fingerprintsReused: Int,
    val failed: Int
) {
    val percent: Int get() = if (total == 0) 100 else (completed * 100 / total).coerceIn(0, 100)
}

data class SoundprintScanResult(
    val groups: List<SoundprintDuplicateGroup>,
    val tracksScanned: Int,
    val fingerprintsReused: Int,
    val failed: Int,
    val completedAt: Long = System.currentTimeMillis()
) {
    val matchCount: Int get() = groups.sumOf { it.matches.size }
}

fun formatSoundprintDuration(seconds: Double): String {
    if (!seconds.isFinite() || seconds <= 0.0) return "Duration unknown"
    val whole = seconds.toInt()
    return "${whole / 60}:${(whole % 60).toString().padStart(2, '0')}"
}

object SoundprintText {
    private val versionTerms = Regex("\\b(remix|live|acoustic|instrumental|extended|radio edit|edit|demo|remaster(?:ed)?|version)\\b", RegexOption.IGNORE_CASE)
    fun normalize(value: String?): String = (value ?: "")
        .normalizeCompat()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\.(mp3|m4a|aac|flac|wav|ogg|opus|wma|aiff?)$"), "")
        .replace(Regex("\\b(official audio|official video|lyrics?|audio|video|explicit|clean|copy|duplicate)\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun hasVersionLabel(value: String?): Boolean = versionTerms.containsMatchIn(value.orEmpty())
    private fun String.normalizeCompat(): String = try { java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFKC) } catch (_: Exception) { this }
}

/** Decodes local content URIs and fingerprints actual PCM audio, not filenames or sampled file bytes. */
class SoundprintFingerprintEngine(private val context: Context) {
    suspend fun create(track: MusicTrack, onProgress: (Int) -> Unit = {}): SoundprintFingerprint =
        withContext(Dispatchers.IO) {
            val decoded = decodePcm(track, onProgress)
            val mono = resampleMono(decoded.samples, decoded.sampleRate, 22_050)
            val features = extractFeatures(mono, 22_050)
            require(features.isNotEmpty()) { "Not enough decodable audio to create a Soundprint fingerprint." }
            val compressed = ByteArrayOutputStream().use { output ->
                DeflaterOutputStream(output).use { it.write(features) }
                output.toByteArray()
            }
            SoundprintFingerprint(
                trackId = track.id,
                sourceSize = track.sizeBytes,
                sourceModifiedAt = track.modifiedAt,
                durationMs = if (decoded.sampleRate > 0) decoded.samples.size.toLong() * 1000L / decoded.sampleRate else track.durationMs,
                sampleRate = 22_050,
                frameCount = features.size / FEATURE_BANDS,
                featureBands = FEATURE_BANDS,
                compressedFeatures = compressed
            )
        }

    private data class DecodedAudio(val samples: FloatArray, val sampleRate: Int)

    private suspend fun decodePcm(track: MusicTrack, onProgress: (Int) -> Unit): DecodedAudio {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        val samples = FloatSamples()
        var sourceRate = track.sampleRate.takeIf { it > 0 } ?: 44_100
        var channels = track.channelCount.takeIf { it > 0 } ?: 2
        var durationUs = track.durationMs * 1000L
        try {
            extractor.setDataSource(context, Uri.parse(track.uri), null)
            var audioFormat: MediaFormat? = null
            var audioTrackIndex = -1
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                if (candidate.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                    audioFormat = candidate
                    audioTrackIndex = index
                    break
                }
            }
            val format = audioFormat ?: error("Android could not find a decodable audio stream.")
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Audio format is missing its decoder type.")
            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (format.containsKey(MediaFormat.KEY_DURATION)) durationUs = format.getLong(MediaFormat.KEY_DURATION)
            require(sourceRate > 0 && channels > 0) { "Audio stream has invalid sample properties." }

            extractor.selectTrack(audioTrackIndex)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var outputEncoding = AudioFormat.ENCODING_PCM_16BIT
            var outputChannels = channels
            var outputRate = sourceRate
            var lastProgress = -1

            while (!outputEnded) {
                coroutineContext.ensureActive()
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex) ?: error("Audio decoder input buffer is unavailable.")
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            val presentationTime = extractor.sampleTime.coerceAtLeast(0L)
                            decoder.queueInputBuffer(inputIndex, 0, size, presentationTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = decoder.outputFormat
                        if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) outputRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) outputChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) outputEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        try {
                            if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                val output = decoder.getOutputBuffer(outputIndex)
                                if (output != null) appendPcm(output, info.offset, info.size, outputChannels, outputEncoding, samples)
                                if (durationUs > 0L && info.presentationTimeUs >= 0L) {
                                    val progress = ((info.presentationTimeUs * 100L) / durationUs).toInt().coerceIn(0, 99)
                                    if (progress != lastProgress) { lastProgress = progress; onProgress(progress) }
                                }
                            }
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEnded = true
                        } finally {
                            decoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }
            onProgress(100)
            require(samples.size >= sourceRate / 2) { "The audio decoder returned too little audio to analyse." }
            return DecodedAudio(samples.toArray(), outputRate.takeIf { it > 0 } ?: sourceRate)
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun appendPcm(buffer: java.nio.ByteBuffer, offset: Int, size: Int, channels: Int, encoding: Int, target: FloatSamples) {
        val data = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        data.position(offset)
        data.limit(offset + size)
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            AudioFormat.ENCODING_PCM_8BIT -> 1
            else -> 2
        }
        val frameBytes = bytesPerSample * channels.coerceAtLeast(1)
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
            target.add((sum / channels.coerceAtLeast(1)).toFloat())
        }
    }

    private fun resampleMono(input: FloatArray, inputRate: Int, outputRate: Int): FloatArray {
        if (inputRate == outputRate) return input
        val outputSize = (input.size.toLong() * outputRate / inputRate).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val output = FloatArray(outputSize)
        val ratio = inputRate.toDouble() / outputRate
        for (index in output.indices) {
            val position = index * ratio
            val left = position.toInt().coerceIn(0, input.lastIndex)
            val right = (left + 1).coerceAtMost(input.lastIndex)
            val fraction = (position - left).toFloat()
            output[index] = input[left] * (1f - fraction) + input[right] * fraction
        }
        return output
    }

    private fun extractFeatures(samples: FloatArray, sampleRate: Int): ByteArray {
        if (samples.size < FFT_SIZE) return ByteArray(0)
        val hop = sampleRate / 2
        val window = DoubleArray(FFT_SIZE) { index -> 0.5 - 0.5 * cos(2.0 * PI * index / (FFT_SIZE - 1)) }
        val real = DoubleArray(FFT_SIZE)
        val imaginary = DoubleArray(FFT_SIZE)
        val result = ByteArray(((samples.size - FFT_SIZE) / hop + 1) * FEATURE_BANDS)
        val bandEdges = IntArray(FEATURE_BANDS + 1) { band ->
            val frequency = 70.0 * (9_500.0 / 70.0).pow(band.toDouble() / FEATURE_BANDS)
            (frequency * FFT_SIZE / sampleRate).toInt().coerceIn(1, FFT_SIZE / 2 - 1)
        }
        var cursor = 0
        var start = 0
        while (start + FFT_SIZE <= samples.size) {
            for (index in 0 until FFT_SIZE) {
                real[index] = samples[start + index] * window[index]
                imaginary[index] = 0.0
            }
            fft(real, imaginary)
            val energies = DoubleArray(FEATURE_BANDS)
            for (band in 0 until FEATURE_BANDS) {
                val low = bandEdges[band]
                val high = max(low + 1, bandEdges[band + 1])
                var energy = 0.0
                for (bin in low until high) energy += real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
                energies[band] = ln(energy / max(1, high - low) + 1e-12)
            }
            val mean = energies.average()
            val deviation = sqrt(energies.sumOf { (it - mean) * (it - mean) } / FEATURE_BANDS).coerceAtLeast(1e-6)
            for (band in 0 until FEATURE_BANDS) {
                val normalized = ((energies[band] - mean) / deviation * 32.0 + 128.0).toInt().coerceIn(0, 255)
                result[cursor++] = normalized.toByte()
            }
            start += hop
        }
        return if (cursor == result.size) result else result.copyOf(cursor)
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
            val wLengthReal = cos(angle)
            val wLengthImaginary = kotlin.math.sin(angle)
            var start = 0
            while (start < n) {
                var wReal = 1.0
                var wImaginary = 0.0
                for (offset in 0 until length / 2) {
                    val even = start + offset
                    val odd = even + length / 2
                    val oddReal = real[odd] * wReal - imaginary[odd] * wImaginary
                    val oddImaginary = real[odd] * wImaginary + imaginary[odd] * wReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextReal = wReal * wLengthReal - wImaginary * wLengthImaginary
                    wImaginary = wReal * wLengthImaginary + wImaginary * wLengthReal
                    wReal = nextReal
                }
                start += length
            }
            length = length shl 1
        }
    }

    private class FloatSamples {
        private var values = FloatArray(65_536)
        var size = 0
            private set
        fun add(value: Float) {
            if (size == values.size) values = values.copyOf(values.size * 2)
            values[size++] = value
        }
        fun toArray() = values.copyOf(size)
    }
}

/** Compares compressed spectral-frame sequences, tolerating small codec and encoder differences. */
object SoundprintFingerprintComparator {
    fun compare(first: SoundprintFingerprint, second: SoundprintFingerprint): Double? {
        if (first.version != SOUNDPRINT_VERSION || second.version != SOUNDPRINT_VERSION) return null
        if (first.featureBands != second.featureBands || first.frameCount < 8 || second.frameCount < 8) return null
        val durationRatio = min(first.durationMs, second.durationMs).toDouble() / max(first.durationMs, second.durationMs).coerceAtLeast(1L)
        if (durationRatio < 0.94) return null
        val a = decompress(first)
        val b = decompress(second)
        val bands = first.featureBands
        if (a.size != first.frameCount * bands || b.size != second.frameCount * bands) return null

        val maxOffset = min(4, min(first.frameCount, second.frameCount) / 12)
        var best = 0.0
        for (offset in -maxOffset..maxOffset) {
            val startA = max(0, -offset)
            val startB = max(0, offset)
            val count = min(first.frameCount - startA, second.frameCount - startB)
            if (count < 8) continue
            var total = 0.0
            var compared = 0
            val stride = max(1, count / 180)
            var frame = 0
            while (frame < count) {
                total += cosineFrame(a, (startA + frame) * bands, b, (startB + frame) * bands, bands)
                compared++
                frame += stride
            }
            if (compared > 0) best = max(best, total / compared)
        }
        return best.takeIf { it > 0.0 }
    }

    private fun cosineFrame(a: ByteArray, offsetA: Int, b: ByteArray, offsetB: Int, bands: Int): Double {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (index in 0 until bands) {
            val av = (a[offsetA + index].toInt() and 0xff) - 128
            val bv = (b[offsetB + index].toInt() and 0xff) - 128
            dot += av.toDouble() * bv
            normA += av.toDouble() * av
            normB += bv.toDouble() * bv
        }
        if (normA <= 1e-9 || normB <= 1e-9) return 0.0
        return (dot / sqrt(normA * normB)).coerceIn(-1.0, 1.0)
    }

    private fun decompress(fingerprint: SoundprintFingerprint): ByteArray =
        InflaterInputStream(ByteArrayInputStream(fingerprint.compressedFeatures)).use { it.readBytes() }
}

/** Generates/reuses fingerprints, builds candidate pairs, and groups duplicate matches locally. */
class SoundprintScanner(context: Context, private val library: MusicLibraryManager) {
    private val appContext = context.applicationContext
    private val engine = SoundprintFingerprintEngine(appContext)
    private val preferences = appContext.getSharedPreferences("soundprint_soundprint", Context.MODE_PRIVATE)

    suspend fun loadSavedResults(): SoundprintScanResult? = withContext(Dispatchers.IO) {
        val raw = preferences.getString("last_scan", null) ?: return@withContext null
        runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray("matches") ?: JSONArray()
            val matches = (0 until array.length()).mapNotNull { index ->
                val item = array.getJSONObject(index)
                val first = library.song(item.getString("firstId")) ?: return@mapNotNull null
                val second = library.song(item.getString("secondId")) ?: return@mapNotNull null
                val reasonsArray = item.optJSONArray("reasons") ?: JSONArray()
                val reasons = (0 until reasonsArray.length()).map { reasonsArray.getString(it) }
                SoundprintMatch(first, second, SoundprintMatchKind.valueOf(item.getString("kind")),
                    item.optInt("confidence"), reasons, item.optBoolean("versionWarning"))
            }
            SoundprintScanResult(buildGroups(matches), root.optInt("tracksScanned"),
                root.optInt("fingerprintsReused"), root.optInt("failed"), root.optLong("completedAt"))
        }.getOrNull()
    }

    fun saveResult(result: SoundprintScanResult) {
        val matches = result.groups.flatMap { it.matches }
        val root = JSONObject().put("tracksScanned", result.tracksScanned)
            .put("fingerprintsReused", result.fingerprintsReused).put("failed", result.failed)
            .put("completedAt", result.completedAt)
            .put("matches", JSONArray().apply {
                matches.forEach { match ->
                    put(JSONObject().put("firstId", match.first.id).put("secondId", match.second.id)
                        .put("kind", match.kind.name).put("confidence", match.confidence)
                        .put("versionWarning", match.versionWarning)
                        .put("reasons", JSONArray(match.reasons)))
                }
            })
        preferences.edit().putString("last_scan", root.toString()).apply()
    }

    fun withoutTracks(result: SoundprintScanResult, removedIds: Set<String>): SoundprintScanResult {
        val remaining = result.groups.flatMap { it.matches }
            .filterNot { it.first.id in removedIds || it.second.id in removedIds }
        return result.copy(groups = buildGroups(remaining))
    }

    suspend fun scan(onProgress: (SoundprintScanProgress) -> Unit = {}): SoundprintScanResult {
        val tracks = withContext(Dispatchers.IO) { library.allSongs() }
        val fingerprints = linkedMapOf<String, SoundprintFingerprint>()
        var reused = 0
        var failed = 0
        var completed = 0
        for (track in tracks) {
            coroutineContext.ensureActive()
            try {
                val cached = withContext(Dispatchers.IO) {
                    library.soundprintFingerprint(track.id, track.sizeBytes, track.modifiedAt)
                }
                val fingerprint = cached ?: engine.create(track)
                if (cached != null) reused++ else withContext(Dispatchers.IO) {
                    library.saveSoundprintFingerprint(fingerprint)
                }
                fingerprints[track.id] = fingerprint
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                failed++
            } finally {
                completed++
                onProgress(SoundprintScanProgress(completed, tracks.size, track.title, reused, failed))
            }
        }

        val candidates = linkedMapOf<String, Pair<MusicTrack, MusicTrack>>()
        fun add(first: MusicTrack, second: MusicTrack) {
            if (first.id == second.id) return
            val pair = if (first.id < second.id) first to second else second to first
            candidates["${pair.first.id}:${pair.second.id}"] = pair
        }
        val titleGroups = tracks.groupBy { SoundprintText.normalize(it.title) }.filterKeys { it.isNotBlank() }
        titleGroups.values.forEach { group -> for (i in group.indices) for (j in i + 1 until group.size) add(group[i], group[j]) }

        val byDuration = tracks.filter { it.durationMs > 0 }.sortedBy { it.durationMs }
        for (i in byDuration.indices) {
            val first = byDuration[i]
            var j = i + 1
            val tolerance = max(3_000L, (first.durationMs * 0.025).toLong())
            while (j < byDuration.size && byDuration[j].durationMs - first.durationMs <= tolerance) {
                add(first, byDuration[j])
                j++
            }
        }
        val hashGroups = tracks.filter { !it.contentHash.isNullOrBlank() }.groupBy { it.contentHash }
        hashGroups.values.forEach { group -> for (i in group.indices) for (j in i + 1 until group.size) add(group[i], group[j]) }

        val matches = mutableListOf<SoundprintMatch>()
        for ((first, second) in candidates.values) {
            coroutineContext.ensureActive()
            val versionWarning = SoundprintText.hasVersionLabel(first.title) || SoundprintText.hasVersionLabel(second.title)
            if (!first.contentHash.isNullOrBlank() && first.contentHash == second.contentHash) {
                matches += SoundprintMatch(first, second, SoundprintMatchKind.EXACT_AUDIO, 100,
                    listOf("Identical SHA-256 hash of the complete audio file"), versionWarning)
                continue
            }
            val firstFingerprint = fingerprints[first.id]
            val secondFingerprint = fingerprints[second.id]
            if (firstFingerprint != null && secondFingerprint != null) {
                val similarity = SoundprintFingerprintComparator.compare(firstFingerprint, secondFingerprint)
                if (similarity != null && similarity >= 0.86) {
                    val confidence = (similarity * 100).toInt().coerceIn(86, 99)
                    val exactDuration = abs(first.durationMs - second.durationMs) <= 1_500L
                    val kind = if (similarity >= 0.94 && exactDuration && !versionWarning) SoundprintMatchKind.EXACT_AUDIO else SoundprintMatchKind.ACOUSTIC
                    matches += SoundprintMatch(first, second, kind, confidence,
                        listOf("Local PCM spectral fingerprint similarity: ${confidence}%", "Compared ${min(firstFingerprint.frameCount, secondFingerprint.frameCount)} audio sections"),
                        versionWarning || abs(first.durationMs - second.durationMs) > 3_000L)
                    continue
                }
            }
            val titleA = SoundprintText.normalize(first.title)
            val titleB = SoundprintText.normalize(second.title)
            if (titleA.isNotBlank() && titleA == titleB) {
                val artistA = SoundprintText.normalize(first.artist)
                val artistB = SoundprintText.normalize(second.artist)
                val durationDifference = abs(first.durationMs - second.durationMs)
                if (artistA.isNotBlank() && artistA == artistB && durationDifference <= 3_000L) {
                    matches += SoundprintMatch(first, second, SoundprintMatchKind.METADATA, 72,
                        listOf("Matching normalized title", "Matching artist", "Similar duration; audio could not confirm a match"),
                        versionWarning || durationDifference > 1_500L)
                }
            }
        }
        matches.sortWith(compareByDescending<SoundprintMatch> { it.kind == SoundprintMatchKind.EXACT_AUDIO }
            .thenByDescending { it.confidence })
        val result = SoundprintScanResult(buildGroups(matches), tracks.size - failed, reused, failed)
        saveResult(result)
        return result
    }

    private fun buildGroups(matches: List<SoundprintMatch>): List<SoundprintDuplicateGroup> {
        val adjacency = linkedMapOf<String, MutableSet<String>>()
        val trackById = linkedMapOf<String, MusicTrack>()
        matches.forEach { match ->
            trackById[match.first.id] = match.first
            trackById[match.second.id] = match.second
            adjacency.getOrPut(match.first.id) { linkedSetOf() }.add(match.second.id)
            adjacency.getOrPut(match.second.id) { linkedSetOf() }.add(match.first.id)
        }
        val visited = mutableSetOf<String>()
        val groups = mutableListOf<SoundprintDuplicateGroup>()
        adjacency.keys.forEach { start ->
            if (!visited.add(start)) return@forEach
            val stack = ArrayDeque<String>()
            val members = mutableListOf<String>()
            stack.add(start)
            while (stack.isNotEmpty()) {
                val id = stack.removeLast()
                members += id
                adjacency[id].orEmpty().forEach { neighbor -> if (visited.add(neighbor)) stack.add(neighbor) }
            }
            val memberSet = members.toSet()
            val groupMatches = matches.filter { it.first.id in memberSet && it.second.id in memberSet }
            groups += SoundprintDuplicateGroup(
                id = members.sorted().joinToString(":"),
                tracks = members.mapNotNull { trackById[it] }.sortedBy { it.title.lowercase(Locale.ROOT) },
                matches = groupMatches,
                containsExactAudio = groupMatches.any { it.kind == SoundprintMatchKind.EXACT_AUDIO }
            )
        }
        return groups.sortedWith(compareByDescending<SoundprintDuplicateGroup> { it.containsExactAudio }
            .thenByDescending { it.matches.maxOfOrNull(SoundprintMatch::confidence) ?: 0 })
    }
}


object SoundprintCleanupPlanner {
    /** Only fully verified identical-audio groups are eligible; alternate versions and fuzzy matches stay for manual review. */
    fun tracksToRemove(result: SoundprintScanResult): List<MusicTrack> {
        val removals = mutableListOf<MusicTrack>()
        result.groups.filter { group ->
            group.matches.isNotEmpty() && group.matches.all {
                it.kind == SoundprintMatchKind.EXACT_AUDIO && !it.versionWarning
            }
        }.forEach { group ->
            val ranked = group.tracks.sortedWith(
                compareByDescending<MusicTrack> { quality(it) }
                    .thenBy { it.addedAt }
                    .thenBy { it.id }
            )
            removals += ranked.drop(1)
        }
        return removals.distinctBy { it.id }
    }

    private fun quality(track: MusicTrack): Int =
        (if (track.title.isNotBlank() && track.title != "Unknown title") 8 else 0) +
        (if (track.artist.isNotBlank() && track.artist != "Unknown artist") 6 else 0) +
        (if (track.album.isNotBlank() && track.album != "Unknown album") 4 else 0) +
        (if (track.durationMs > 0) 3 else 0) +
        (track.bitrate / 32_000).coerceIn(0, 20) +
        (if (track.sampleRate > 0) 2 else 0) +
        (if (track.channelCount > 0) 1 else 0) +
        (if (!track.artworkUri.isNullOrBlank()) 2 else 0) +
        (if (track.isAvailable) 3 else -100) +
        (track.sizeBytes / (1024L * 1024L)).coerceAtMost(10L).toInt()
}
