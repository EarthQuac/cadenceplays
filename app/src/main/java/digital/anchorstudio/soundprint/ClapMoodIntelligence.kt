package digital.anchorstudio.soundprint

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCancellableCoroutine
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackStateUpdatedListener
import com.google.android.play.core.assetpacks.AssetPackStatus

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
 * Loads the optional CLAP model pack from Google Play Asset Delivery.
 *
 * Production builds use the Play-managed on-demand pack directly. The model
 * files are never copied into filesDir. Debug APKs keep a cache-only fallback
 * so local/Appetize previews can still exercise CLAP without a Play install.
 */
class ClapModelManager(context: Context) {
    private val app = context.applicationContext
    private val debugModelDir = File(app.codeCacheDir, "clap/$CLAP_MODEL_VERSION")
    private val preferences = app.getSharedPreferences("soundprint_clap_models", Context.MODE_PRIVATE)

    private val assetPackManager by lazy { AssetPackManagerFactory.getInstance(app) }

    fun isInstalled(): Boolean = packFiles() != null ||
        (BuildConfig.DEBUG && debugFiles() != null) ||
        (!BuildConfig.DEBUG && legacyFiles() != null)

    fun installedFiles(): ClapModelFiles? {
        val files = packFiles()
            ?: if (BuildConfig.DEBUG) debugFiles() else legacyFiles()
        if (files != null) verifyIfNeeded(files)
        return files
    }

    suspend fun install(onProgress: (ClapModelInstallProgress) -> Unit = {}): ClapModelFiles =
        withContext(Dispatchers.IO) {
            packFiles()?.let { files ->
                verifyIfNeeded(files)
                return@withContext files
            }

            if (BuildConfig.DEBUG) {
                debugFiles()?.let { return@withContext it }
                return@withContext installDebugAssets(onProgress)
            }

            // Preserve an existing user's already-downloaded model. New
            // installs have no legacy copy and use the Play on-demand pack.
            legacyFiles()?.let { files ->
                verifyIfNeeded(files)
                return@withContext files
            }

            fetchPlayAssetPack(onProgress).also(::verifyIfNeeded)
        }

    /**
     * Production files are owned and updated by Google Play. Do not modify,
     * relocate, or duplicate them in app-private storage.
     */
    fun removeInstalledModel() {
        if (BuildConfig.DEBUG) {
            debugModelDir.deleteRecursively()
        } else {
            runCatching { assetPackManager.removePack(PACK_NAME) }
            File(app.filesDir, "models/clap").deleteRecursively()
        }
        preferences.edit().remove("verified_model_version").apply()
    }

    private fun packFiles(): ClapModelFiles? = runCatching {
        val location = assetPackManager.getPackLocation(PACK_NAME) ?: return null
        filesAt(File(location.assetsPath()))
    }.getOrNull()

    private fun debugFiles(): ClapModelFiles? = runCatching {
        if (!BuildConfig.DEBUG || !File(debugModelDir, ".verified").isFile) return null
        filesAt(debugModelDir)
    }.getOrNull()

    private fun legacyFiles(): ClapModelFiles? = runCatching {
        val root = File(app.filesDir, "models/clap/$CLAP_MODEL_VERSION")
        if (!File(root, ".verified").isFile) return null
        ClapModelFiles(
            audioModel = File(root, "audio_model_quantized.onnx").takeIf { it.isFile && it.length() >= AUDIO_MODEL_BYTES }
                ?: return null,
            textModel = File(root, "text_model_quantized.onnx").takeIf { it.isFile && it.length() >= TEXT_MODEL_BYTES }
                ?: return null,
            vocabulary = File(root, "vocab.json").takeIf { it.isFile && it.length() > 0L }
                ?: return null,
            merges = File(root, "merges.txt").takeIf { it.isFile && it.length() > 0L }
                ?: return null
        )
    }.getOrNull()

    private fun filesAt(root: File): ClapModelFiles {
        val files = ClapModelFiles(
            audioModel = File(root, "clap/onnx/audio_model_quantized.onnx").takeIf { it.isFile && it.length() >= AUDIO_MODEL_BYTES }
                ?: error("The CLAP audio model is not available yet."),
            textModel = File(root, "clap/onnx/text_model_quantized.onnx").takeIf { it.isFile && it.length() >= TEXT_MODEL_BYTES }
                ?: error("The CLAP text model is not available yet."),
            vocabulary = File(root, "clap/vocab.json").takeIf { it.isFile && it.length() > 0L }
                ?: error("The CLAP vocabulary is not available yet."),
            merges = File(root, "clap/merges.txt").takeIf { it.isFile && it.length() > 0L }
                ?: error("The CLAP tokenizer data is not available yet.")
        )
        return files
    }

    private suspend fun installDebugAssets(
        onProgress: (ClapModelInstallProgress) -> Unit
    ): ClapModelFiles {
        debugModelDir.mkdirs()
        val assets = listOf(
            Triple("clap/onnx/audio_model_quantized.onnx", AUDIO_MODEL_BYTES, CLAP_AUDIO_MODEL_SHA256),
            Triple("clap/onnx/text_model_quantized.onnx", TEXT_MODEL_BYTES, CLAP_TEXT_MODEL_SHA256),
            Triple("clap/vocab.json", 1L, ""),
            Triple("clap/merges.txt", 1L, "")
        )
        val total = assets.sumOf { it.second }
        var completed = 0L
        assets.forEach { (assetPath, expectedBytes, expectedSha) ->
            val target = File(debugModelDir, assetPath)
            val valid = target.isFile && target.length() >= expectedBytes &&
                (expectedSha.isEmpty() || sha256(target) == expectedSha)
            if (!valid) {
                target.parentFile?.mkdirs()
                val temporary = File(target.parentFile, target.name + ".copy")
                temporary.delete()
                app.assets.open(assetPath).use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        var copied = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            onProgress(ClapModelInstallProgress(
                                target.name, completed + copied, total,
                                (((completed + copied) * 100L) / total).toInt().coerceIn(0, 99)
                            ))
                        }
                        output.fd.sync()
                    }
                }
                check(temporary.length() >= expectedBytes) { "The bundled CLAP asset is incomplete: $assetPath" }
                if (expectedSha.isNotEmpty()) {
                    check(sha256(temporary) == expectedSha) { "The bundled CLAP asset failed its integrity check." }
                }
                check(temporary.renameTo(target)) { "Could not prepare $assetPath." }
            }
            completed += target.length()
            onProgress(ClapModelInstallProgress(
                target.name, completed, total,
                ((completed * 100L) / total).toInt().coerceIn(0, 100)
            ))
        }
        File(debugModelDir, ".verified").writeText(CLAP_MODEL_VERSION)
        return filesAt(debugModelDir)
    }

    private suspend fun fetchPlayAssetPack(
        onProgress: (ClapModelInstallProgress) -> Unit
    ): ClapModelFiles = suspendCancellableCoroutine { continuation ->
        val manager = assetPackManager
        lateinit var listener: AssetPackStateUpdatedListener

        fun finishWithError(message: String) {
            manager.unregisterListener(listener)
            if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message))
        }

        fun handleState(state: com.google.android.play.core.assetpacks.AssetPackState) {
            if (state.name() != PACK_NAME || !continuation.isActive) return
            val total = state.totalBytesToDownload()
            val downloaded = state.bytesDownloaded()
            val percent = if (total > 0L) {
                ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
            } else state.transferProgressPercentage()
            onProgress(ClapModelInstallProgress("CLAP models", downloaded, total, percent))

            when (state.status()) {
                AssetPackStatus.COMPLETED -> {
                    val files = runCatching {
                        val location = manager.getPackLocation(PACK_NAME)
                            ?: error("Google Play completed the CLAP download but its files are not available.")
                        filesAt(File(location.assetsPath()))
                    }.getOrElse {
                        finishWithError(it.message ?: "The downloaded CLAP model could not be opened.")
                        return
                    }
                    manager.unregisterListener(listener)
                    if (continuation.isActive) continuation.resume(files)
                }
                AssetPackStatus.FAILED -> finishWithError(
                    "Google Play could not download the offline music model (error ${state.errorCode()}). Check your connection and try again."
                )
                AssetPackStatus.CANCELED -> finishWithError("The offline music model download was cancelled.")
                AssetPackStatus.WAITING_FOR_WIFI -> finishWithError(
                    "Google Play is waiting for Wi-Fi before downloading the offline music model. Connect to Wi-Fi, then try again."
                )
                AssetPackStatus.REQUIRES_USER_CONFIRMATION -> finishWithError(
                    "Google Play needs to confirm this installation before it can deliver the offline music model. Install Soundprint from Google Play, then try again."
                )
            }
        }

        listener = AssetPackStateUpdatedListener { state -> handleState(state) }
        manager.registerListener(listener)
        continuation.invokeOnCancellation { manager.unregisterListener(listener) }

        manager.fetch(listOf(PACK_NAME))
            .addOnSuccessListener { states ->
                states.packStates()[PACK_NAME]?.let(::handleState)
            }
            .addOnFailureListener { error ->
                finishWithError(error.message ?: "Google Play could not start the offline music model download.")
            }
    }

    private fun verifyIfNeeded(files: ClapModelFiles) {
        if (preferences.getString("verified_model_version", null) != CLAP_MODEL_VERSION) {
            check(sha256(files.audioModel) == CLAP_AUDIO_MODEL_SHA256) {
                "The CLAP audio model failed its integrity check."
            }
            check(sha256(files.textModel) == CLAP_TEXT_MODEL_SHA256) {
                "The CLAP text model failed its integrity check."
            }
            preferences.edit().putString("verified_model_version", CLAP_MODEL_VERSION).apply()
        }

        val legacyRoot = File(app.filesDir, "models/clap/$CLAP_MODEL_VERSION").absoluteFile.normalize()
        val audioPath = files.audioModel.absoluteFile.normalize().path
        if (!BuildConfig.DEBUG && !audioPath.startsWith(legacyRoot.path + File.separator)) {
            File(app.filesDir, "models/clap").deleteRecursively()
        }
    }

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
        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private companion object {
        const val PACK_NAME = "clapmodels"
        const val AUDIO_MODEL_BYTES = 78_155_433L
        const val TEXT_MODEL_BYTES = 126_000_000L
    }
}
