package digital.anchorstudio.soundprint

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo

/** Foreground-service notification for the durable WorkManager music importer. */
class SoundprintImportNotifications(context: Context) {
    private val app = context.applicationContext
    private val manager = NotificationManagerCompat.from(app)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = app.getSystemService(NotificationManager::class.java)
            val progressChannel = NotificationChannel(
                CHANNEL_ID,
                "Music import progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quiet progress while Soundprint adds music to your library"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            val resultChannel = NotificationChannel(
                RESULT_CHANNEL_ID,
                "Music import updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Completion and error updates when Soundprint finishes importing music"
            }
            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(resultChannel)
        }
    }

    fun foregroundInfo(label: String, total: Int, completed: Int, indeterminate: Boolean): ForegroundInfo {
        val notification = ongoingNotification(
            label,
            if (total > 0) "Preparing · $total left" else "Getting things ready",
            total,
            completed,
            indeterminate
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                SoundprintMusicImportQueue.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(SoundprintMusicImportQueue.NOTIFICATION_ID, notification)
        }
    }

    fun update(progress: LibraryScanProgress) {
        val total = progress.total
        val completed = (progress.visited).coerceAtLeast(0)
        val finished = (progress.imported + progress.updated).coerceAtLeast(0)
        val left = (total - progress.visited).coerceAtLeast(0)
        val percent = if (total > 0) (progress.visited * 100 / total).coerceIn(0, 100) else 0
        val title = progress.currentTitle.ifBlank { progress.sourceName.ifBlank { "Your music" } }
        val summary = "$percent% · ✓ $finished added or updated · $left left" +
            if (progress.skipped > 0) " · ${progress.skipped} skipped" else ""
        post(ongoingNotification(title, summary, total, completed, total <= 0))
    }

    fun complete(result: LibraryScanResult) {
        val done = result.imported + result.updated
        val message = "✓ $done ${if (done == 1) "song" else "songs"} added"
        post(
            NotificationCompat.Builder(app, RESULT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setColor(Color.rgb(252, 158, 71))
                .setContentTitle("Music ready")
                .setContentText(message)
                .setSubText(if (result.skipped > 0) "${result.skipped} files skipped" else "Your library is up to date")
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build(),
            COMPLETION_NOTIFICATION_ID
        )
    }

    fun failed(message: String) {
        post(
            NotificationCompat.Builder(app, RESULT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setColor(Color.rgb(252, 158, 71))
                .setContentTitle("Music import stopped")
                .setContentText(message.ifBlank { "Soundprint couldn't finish importing your music." })
                .setAutoCancel(true)
                .setOngoing(false)
                .build(),
            COMPLETION_NOTIFICATION_ID
        )
    }

    fun dismiss() {
        runCatching { manager.cancel(SoundprintMusicImportQueue.NOTIFICATION_ID) }
    }

    private fun ongoingNotification(
        title: String,
        summary: String,
        total: Int,
        completed: Int,
        indeterminate: Boolean
    ): Notification =
        NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setColor(Color.rgb(252, 158, 71))
            .setContentTitle("Adding music")
            .setContentText(title)
            .setSubText(summary)
            .setProgress(total.coerceAtLeast(0), completed.coerceAtLeast(0), indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

    private fun post(notification: Notification, id: Int = SoundprintMusicImportQueue.NOTIFICATION_ID) {
        runCatching {
            if (!manager.areNotificationsEnabled()) {
                Log.w(TAG, "Notifications are disabled by the user; notification $id was not posted")
                return
            }
            manager.notify(id, notification)
        }.onFailure { error ->
            Log.e(TAG, "Unable to post notification $id", error)
        }
    }

    private companion object {
        const val TAG = "SoundprintNotifications"
        const val CHANNEL_ID = "soundprint_music_import"
        const val RESULT_CHANNEL_ID = "soundprint_music_import_updates"
        const val COMPLETION_NOTIFICATION_ID = 4108
    }
}
