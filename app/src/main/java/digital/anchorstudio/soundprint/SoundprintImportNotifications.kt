package digital.anchorstudio.soundprint

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Quiet, persistent import progress styled with Soundprint's warm amber accent. */
class SoundprintImportNotifications(context: Context) {
    private val app = context.applicationContext
    private val manager = NotificationManagerCompat.from(app)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Music import", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while Soundprint adds music to your library"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            app.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun begin(total: Int, label: String = "Preparing your music") {
        post(NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setColor(Color.rgb(252, 158, 71))
            .setContentTitle("Adding music")
            .setContentText(label)
            .setSubText(if (total > 0) "0 done · $total left" else "Getting things ready")
            .setProgress(total.coerceAtLeast(0), 0, total <= 0)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS).build())
    }

    fun update(progress: LibraryScanProgress) {
        val total = progress.total
        val completed = (progress.imported + progress.updated).coerceAtLeast(0)
        val left = (total - progress.visited).coerceAtLeast(0)
        val title = progress.currentTitle.ifBlank { progress.sourceName.ifBlank { "Your music" } }
        val progressLabel = if (total > 0) "✓ $completed done · $left left" else "✓ $completed done · scanning…"
        val sub = progressLabel + if (progress.skipped > 0) " · ${progress.skipped} skipped" else ""
        post(NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setColor(Color.rgb(252, 158, 71))
            .setContentTitle("Adding music")
            .setContentText(title)
            .setSubText(sub)
            .setProgress(total.coerceAtLeast(0), progress.visited.coerceAtLeast(0), total <= 0)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS).build())
    }

    fun complete(result: LibraryScanResult) {
        val done = result.imported + result.updated
        val message = "✓ $done ${if (done == 1) "song" else "songs"} added"
        post(NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setColor(Color.rgb(252, 158, 71))
            .setContentTitle("Music ready")
            .setContentText(message)
            .setSubText(if (result.skipped > 0) "${result.skipped} files skipped" else "Your library is up to date")
            .setProgress(0, 0, false).setOngoing(false).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS).build())
    }

    fun failed(message: String) {
        post(NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setColor(Color.rgb(252, 158, 71))
            .setContentTitle("Music import stopped")
            .setContentText(message.ifBlank { "Soundprint couldn't finish importing your music." })
            .setAutoCancel(true).setOngoing(false).build())
    }

    fun dismiss() { runCatching { manager.cancel(NOTIFICATION_ID) } }
    private fun post(notification: android.app.Notification) { runCatching { manager.notify(NOTIFICATION_ID, notification) } }

    private companion object {
        const val CHANNEL_ID = "soundprint_music_import"
        const val NOTIFICATION_ID = 4107
    }
}
