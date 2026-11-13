package digital.anchorstudio.cadenceplays

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

const val CLAP_MODEL_VERSION = "larger-clap-music-speech-56bcfdb-v1"
private const val CLAP_MODEL_BASE = "https://huggingface.co/Xenova/larger_clap_music_and_speech/resolve/56bcfdb/"
private const val CLAP_AUDIO_MODEL_SHA256 = "021dd4fb962b4ed20cc3a6730b09e0ccf9c49931047032118a3b64828513a6"
private const val CLAP_TEXT_MODEL_SHA256 = "8f9f29c5f6adee917553d4b3a70729c731c0d18b88efca0ae67c1a1fc278f3b6"

enum class CadenceMood(val id: String, val title: String, val prompt: String) {
    WORKOUT("workout","Workout","high-energy workout music with a driving beat, powerful drums and intense rhythm"),
    UPBEAT("upbeat","Upbeat","bright upbeat lively music with an energetic and cheerful sound"),
    CHILL("chill","Chill","relaxed mellow chill music with a soft groove and gentle energy"),
    FOCUS("focus","Focus","steady unobtrusive instrumental music suitable for concentration and studying"),
    SLEEP("sleep","Sleep","very soft slow calming music suitable for falling asleep"),
    DRIVING("driving","Driving","engaging road trip music with a steady pulse and balanced energy"),
    PARTY("party","Party","dance floor party music with a strong beat and exciting rhythm"),
    HAPPY("happy","Happy","joyful positive cheerful music with a bright uplifting sound"),
    SAD("sad","Sad","sad emotional music with a gentle reflective and sorrowful feeling"),
    ROMANTIC("romantic","Romantic","warm intimate romantic love music with a tender expressive sound"),
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
    val primaryMood: CadenceMood?
        get() = CadenceMood.entries.maxByOrNull { scores[it.id] ?: 0f }
    fun score(mood: CadenceMood): Float = scores[mood.id] ?: 0f
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
 * Downloads the pinned, quantized CLAP encoders into app-private storage.
 * Once installed, inference can run without a network connection. Model files
 * are deliberately not packed into the APK so the APK remains installable.
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
                Asset("onnx/audio_model_quantized.onnx","audio_model_quantized.onnx",78_200_000L,CLAP_AUDIO_MODEL_SHA256),
                Asset("onnx/text_model_quantized.onnx","text_model_quantized.onnx",127_000_000L,CLAP_TEXT_MODEL_SHA256),
                Asset("vocab.json","vocab.json",null,null),
                Asset("merges.txt","merges.txt",null,null)
            )
            val total = assets.sumOf { asset ->
                val target = File(modelDir,asset.name)
                if (target.isFile && target.length() > 0L && (asset.expectedBytes == null || target.length() == asset.expectedBytes)) 0L
                else asset.expectedBytes ?: 0L
            }.coerceAtLeast(1L)
            var completed = 0L
            assets.forEach { asset ->
                coroutineContext.ensureActive()
                val target = File(modelDir,asset.name)
                val alreadyValid = target.isFile && target.length() > 0L &&
                    (asset.expectedBytes == null || target.length() == asset.expectedBytes) &&
                    (asset.sha256 == null || sha256(target) == asset.sha256)
                if (alreadyValid) {
                    completed += asset.expectedBytes ?: target.length()
                } else {
                    target.delete()
                    val temp = File(modelDir,asset.name + ".download")
                    temp.parentFile?.mkdirs()
                    download(asset,temp,completed,total,onProgress)
                    if (asset.expectedBytes != null && temp.length() != asset.expectedBytes) {
                        temp.delete()
                        error("The downloaded ${asset.name} has an unexpected size.")
                    }
                    if (asset.sha256 != null && sha256(temp) != asset.sha256) {
                        temp.delete()
                        error("The downloaded CLAP model failed its SHA-256 integrity check.")
                    }
                    check(temp.renameTo(target)) { "Could not save ${asset.name} to app storage." }
                    completed += asset.expectedBytes ?: target.length()
                }
                onProgress(ClapModelInstallProgress(asset.name,completed,total,
                    ((completed * 100L) / total).toInt().coerceIn(0,100)))
            }
            File(modelDir,".verified").writeText(CLAP_MODEL_VERSION)
            files()
        }

    fun removeInstalledModel() {
        modelDir.deleteRecursively()
    }

    private data class Asset(val remotePath:String,val name:String,val expectedBytes:Long?,val sha256:String?)
    private fun filesExist() = listOf("audio_model_quantized.onnx","text_model_quantized.onnx","vocab.json","merges.txt")
        .all { File(modelDir,it).isFile && File(modelDir,it).length() > 0L }
    private fun files() = ClapModelFiles(
        File(modelDir,"audio_model_quantized.onnx"),File(modelDir,"text_model_quantized.onnx"),
        File(modelDir,"vocab.json"),File(modelDir,"merges.txt")
    )

    private suspend fun download(
        asset:Asset,target:File,completedBefore:Long,total:Long,
        onProgress:(ClapModelInstallProgress)->Unit
    ) {
        val connection = (URL(CLAP_MODEL_BASE + asset.remotePath).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent","Cadence-Android")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "CLAP download failed (HTTP ${connection.responseCode})." }
            val stream = BufferedInputStream(connection.inputStream)
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(256 * 1024)
                var assetBytes = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    output.write(buffer,0,count)
                    assetBytes += count
                    val overall = completedBefore + assetBytes
                    onProgress(ClapModelInstallProgress(asset.name,overall,total,
                        ((overall * 100L) / total).toInt().coerceIn(0,99)))
                }
                output.fd.sync()
            }
            stream.close()
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(1024*1024).use { input ->
            val buffer=ByteArray(1024*1024)
            while(true) { val count=input.read(buffer);if(count<0) break;digest.update(buffer,0,count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
