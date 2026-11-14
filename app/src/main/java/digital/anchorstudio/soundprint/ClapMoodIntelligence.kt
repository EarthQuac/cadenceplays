package digital.anchorstudio.soundprint

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

const val CLAP_MODEL_VERSION = "larger-clap-music-speech-56bcfdb-v1"
private const val CLAP_AUDIO_MODEL_SHA256 = "021dd4fb962b4ed20cc3a6730b09e0ccf9f9c49931047032118a3b64828513a6"
private const val CLAP_TEXT_MODEL_SHA256 = "8f9f29c5f6adee917553d4b3a70729c731c0d18b88efca0ae67c1a1fc278f3b6"

enum class SoundprintMood(val id: String, val title: String, val prompt: String) {
    WORKOUT("workout","Workout","high-energy workout music with a driving beat, powerful drums and intense rhythm"),
    DRIVING("driving","Driving","engaging road trip music with a steady pulse and balanced energy"),
    CHILL("chill","Chill","relaxed mellow chill music with a soft groove and gentle energy"),
    FOCUS("focus","Focus","steady unobtrusive instrumental music suitable for concentration and studying"),
    PARTY("party","Party","dance floor party music with a strong beat and exciting rhythm"),
    UPBEAT("upbeat","Upbeat","bright upbeat lively music with an energetic and cheerful sound"),
    HAPPY("happy","Happy","joyful positive cheerful music with a bright uplifting sound"),
    SAD("sad","Sad","sad emotional music with a gentle reflective and sorrowful feeling"),
    ROMANTIC("romantic","Romantic","warm intimate romantic love music with a tender expressive sound"),
    SLEEP("sleep","Sleep","very soft slow calming music suitable for falling asleep"),
    LATE_NIGHT("late-night","Late Night","moody atmospheric late night music with a deep mellow sound"),
    ANGRY("angry","Angry","aggressive hard-hitting angry music with forceful drums and distorted intensity"),
    DREAMY("dreamy","Dreamy","dreamy hazy floating atmospheric music with spacious textures"),
    PEACEFUL("peaceful","Peaceful","peaceful soothing gentle music that feels calm and serene"),
    MELANCHOLY("melancholy","Melancholy","bittersweet wistful melancholy music with a reflective emotional tone"),
    CONFIDENT("confident","Confident","bold self-assured confident music with a strong commanding presence"),
    NOSTALGIC("nostalgic","Nostalgic","nostalgic sentimental music that evokes memories and the feeling of looking back"),
    INTENSE("intense","Intense","intense powerful high-impact music with dramatic energy and force")
}

data class ClapMoodProfile(
    val trackId: String,
    val sourceSize: Long,
    val sourceModifiedAt: Long,
    val modelVersion: String,
    val analyzedAt: Long,
    val scores: Map<String, Float>
) {
    val primaryMood: SoundprintMood?
        get() = SoundprintMood.entries.maxByOrNull { scores[it.id] ?: 0f }
    fun score(mood: SoundprintMood): Float = scores[mood.id] ?: 0f
}

data class ClapModelInstallProgress(
    val currentAsset: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val percent: Int
)

data class ClapModelFiles(
    val audioModel: File,
    val textModel: File,
    val vocabulary: File,
    val merges: File
)

/**
 * Installs the model weights that are already bundled inside the APK.
 * No network connection or in-app model download is required.
 */
class ClapModelManager(context: Context) {
    private val app = context.applicationContext
    private val modelDir = File(app.filesDir, "models/clap/$CLAP_MODEL_VERSION")

    fun isInstalled(): Boolean = filesExist() && File(modelDir, ".verified").isFile

    fun installedFiles(): ClapModelFiles? {
        if (!isInstalled()) return null
        return files()
    }

    suspend fun install(onProgress: (ClapModelInstallProgress) -> Unit = {}): ClapModelFiles =
        withContext(Dispatchers.IO) {
            modelDir.mkdirs()
            val assets = listOf(
                BundledAsset("clap/onnx/audio_model_quantized.onnx", "audio_model_quantized.onnx", 78_155_433L, CLAP_AUDIO_MODEL_SHA256),
                BundledAsset("clap/onnx/text_model_quantized.onnx", "text_model_quantized.onnx", 126_000_000L, CLAP_TEXT_MODEL_SHA256),
                BundledAsset("clap/vocab.json", "vocab.json", 0L, null),
                BundledAsset("clap/merges.txt", "merges.txt", 0L, null)
            )
            val total = assets.sumOf { it.expectedBytes }.coerceAtLeast(1L)
            var completed = 0L

            assets.forEach { asset ->
                coroutineContext.ensureActive()
                val target = File(modelDir, asset.name)
                val valid = target.isFile &&
                    (asset.expectedBytes == 0L || target.length() >= asset.expectedBytes) &&
                    (asset.sha256 == null || sha256(target) == asset.sha256)

                if (!valid) {
                    target.delete()
                    val temporary = File(modelDir, asset.name + ".copy")
                    temporary.delete()
                    temporary.parentFile?.mkdirs()
                    app.assets.open(asset.assetPath).use { input ->
                        FileOutputStream(temporary).use { output ->
                            val buffer = ByteArray(1024 * 1024)
                            var copied = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                copied += count
                                val overall = completed + copied
                                onProgress(ClapModelInstallProgress(
                                    asset.name, overall, total,
                                    ((overall * 100L) / total).toInt().coerceIn(0, 99)
                                ))
                            }
                            output.fd.sync()
                        }
                    }
                    check(temporary.isFile && temporary.length() > 0L) {
                        "A bundled music feature file is missing: ${asset.name}"
                    }
                    if (asset.expectedBytes > 0L) {
                        check(temporary.length() >= asset.expectedBytes) {
                            "A bundled music feature file is incomplete: ${asset.name}"
                        }
                    }
                    if (asset.sha256 != null) {
                        check(sha256(temporary) == asset.sha256) {
                            "A bundled music feature file failed its integrity check."
                        }
                    }
                    check(temporary.renameTo(target)) { "Could not prepare ${asset.name}." }
                }

                completed += if (asset.expectedBytes > 0L) asset.expectedBytes else target.length()
                onProgress(ClapModelInstallProgress(
                    asset.name, completed, total,
                    ((completed * 100L) / total).toInt().coerceIn(0, 100)
                ))
            }

            File(modelDir, "MODEL-NOTICE.txt").writeText(
                "Soundprint includes Xenova/larger_clap_music_and_speech quantized ONNX weights. " +
                    "Model license: Apache License 2.0. Source: https://huggingface.co/Xenova/larger_clap_music_and_speech " +
                    "Pinned revision: 56bcfdb. Inference is performed locally on this device."
            )
            File(modelDir, ".verified").writeText(CLAP_MODEL_VERSION)
            files()
        }

    fun removeInstalledModel() {
        modelDir.deleteRecursively()
    }

    private data class BundledAsset(
        val assetPath: String,
        val name: String,
        val expectedBytes: Long,
        val sha256: String?
    )

    private fun filesExist() = listOf(
        "audio_model_quantized.onnx", "text_model_quantized.onnx", "vocab.json", "merges.txt"
    ).all { File(modelDir, it).isFile && File(modelDir, it).length() > 0L }

    private fun files() = ClapModelFiles(
        File(modelDir, "audio_model_quantized.onnx"),
        File(modelDir, "text_model_quantized.onnx"),
        File(modelDir, "vocab.json"),
        File(modelDir, "merges.txt")
    )

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(1024 * 1024).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
