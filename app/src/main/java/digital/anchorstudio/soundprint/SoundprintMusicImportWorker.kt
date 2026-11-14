package digital.anchorstudio.soundprint

import android.app.Notification
import android.content.Context
import android.net.Uri
import android.os.Build
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Durable import queue. SAF grants are persisted before work is enqueued and
 * the request itself is stored on disk (WorkManager Data has a small size cap).
 */
object SoundprintMusicImportQueue {
    private const val UNIQUE_WORK = "soundprint_music_import_queue"
    private const val INPUT_REQUEST_ID = "request_id"

    fun enqueueFolder(context: Context, treeUri: Uri): UUID =
        enqueue(context, "folder", listOf(treeUri))

    fun enqueueFiles(context: Context, uris: List<Uri>): UUID {
        require(uris.isNotEmpty()) { "Choose at least one audio file." }
        return enqueue(context, "files", uris.distinctBy(Uri::toString))
    }

    private fun enqueue(context: Context, type: String, uris: List<Uri>): UUID {
        val app = context.applicationContext
        uris.forEach { uri ->
            app.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val id = UUID.randomUUID()
        val directory = File(app.filesDir, "music-import-requests").apply { mkdirs() }
        val destination = File(directory, "$id.json")
        val temporary = File(directory, "$id.json.tmp")
        val payload = JSONObject()
            .put("type", type)
            .put("uris", JSONArray().apply { uris.forEach { put(it.toString()) } })
        temporary.writeText(payload.toString(), Charsets.UTF_8)
        check(temporary.renameTo(destination)) { "Could not save the music import request." }

        val request = OneTimeWorkRequestBuilder<SoundprintMusicImportWorker>()
            .setInputData(workDataOf(INPUT_REQUEST_ID to id.toString()))
            .addTag(UNIQUE_WORK)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
        return id
    }

    internal fun requestFile(context: Context, id: String): File =
        File(File(context.applicationContext.filesDir, "music-import-requests"), "$id.json")

    internal const val NOTIFICATION_ID = 4107
    internal const val INPUT_ID = INPUT_REQUEST_ID
}

class SoundprintMusicImportWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val requestId = inputData.getString(SoundprintMusicImportQueue.INPUT_ID)
            ?: return Result.failure()
        val requestFile = SoundprintMusicImportQueue.requestFile(applicationContext, requestId)
        val notifications = SoundprintImportNotifications(applicationContext)

        return try {
            if (!requestFile.isFile) {
                throw IllegalStateException("The saved import request is missing. Please select the music again.")
            }
            val payload = JSONObject(requestFile.readText(Charsets.UTF_8))
            val type = payload.getString("type")
            val urisJson = payload.getJSONArray("uris")
            val uris = buildList {
                for (index in 0 until urisJson.length()) add(Uri.parse(urisJson.getString(index)))
            }
            require(uris.isNotEmpty()) { "The import request contains no files." }

            setForeground(notifications.foregroundInfo(
                if (type == "folder") "Scanning selected folder" else "Preparing selected audio",
                0,
                0,
                true
            ))

            val repository = MusicLibraryRepository(applicationContext)
            val result = when (type) {
                "folder" -> repository.importTree(uris.single()) { progress ->
                    notifications.update(progress)
                }
                "files" -> repository.importFiles(uris) { progress ->
                    notifications.update(progress)
                }
                else -> throw IllegalArgumentException("Unknown music import type.")
            }
            notifications.complete(result)
            Result.success(
                Data.Builder()
                    .putInt("imported", result.imported)
                    .putInt("updated", result.updated)
                    .putInt("skipped", result.skipped)
                    .putInt("errors", result.errors.size)
                    .build()
            )
        } catch (cancelled: CancellationException) {
            // WorkManager owns cancellation and will remove its foreground notification.
            notifications.dismiss()
            throw cancelled
        } catch (error: Exception) {
            notifications.failed(error.message ?: "Soundprint couldn't finish importing your music.")
            Result.failure(workDataOf("error" to (error.message ?: "Music import failed.")))
        } finally {
            requestFile.delete()
        }
    }
}
