package digital.anchorstudio.soundprint

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SoundprintRepeatMode { OFF, ALL, ONE }

data class PlaybackQueueSnapshot(
    val queueIds: List<String> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: SoundprintRepeatMode = SoundprintRepeatMode.OFF
)

/** Persists queue occurrences (including duplicate songs), the selected item and resume position. */
class PlaybackQueueStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("soundprint_playback_queue", Context.MODE_PRIVATE)

    fun read(): PlaybackQueueSnapshot {
        val raw = preferences.getString(KEY_SNAPSHOT, null) ?: return PlaybackQueueSnapshot()
        return runCatching {
            val json = JSONObject(raw)
            val ids = json.optJSONArray("queueIds") ?: JSONArray()
            PlaybackQueueSnapshot(
                queueIds = (0 until ids.length()).mapNotNull { index -> ids.optString(index).takeIf(String::isNotBlank) },
                currentIndex = json.optInt("currentIndex", -1),
                positionMs = json.optLong("positionMs", 0L).coerceAtLeast(0L),
                shuffleEnabled = json.optBoolean("shuffleEnabled", false),
                repeatMode = runCatching { SoundprintRepeatMode.valueOf(json.optString("repeatMode", "OFF")) }.getOrDefault(SoundprintRepeatMode.OFF)
            )
        }.getOrDefault(PlaybackQueueSnapshot())
    }

    fun write(snapshot: PlaybackQueueSnapshot) {
        val json = JSONObject().apply {
            put("queueIds", JSONArray(snapshot.queueIds))
            put("currentIndex", snapshot.currentIndex)
            put("positionMs", snapshot.positionMs.coerceAtLeast(0L))
            put("shuffleEnabled", snapshot.shuffleEnabled)
            put("repeatMode", snapshot.repeatMode.name)
        }
        preferences.edit().putString(KEY_SNAPSHOT, json.toString()).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_SNAPSHOT).apply()
    }

    private companion object { const val KEY_SNAPSHOT = "snapshot_v1" }
}

fun MusicTrack.toSoundprintMediaItem(): MediaItem {
    val artwork = artworkUri?.let { raw -> if (raw.startsWith("/")) Uri.fromFile(File(raw)) else Uri.parse(raw) }
    val metadata = MediaMetadata.Builder()
        .setTitle(title.ifBlank { "Unknown title" })
        .setArtist(artist.ifBlank { "Unknown artist" })
        .setAlbumTitle(album.ifBlank { "Soundprint" })
        .apply { if (artwork != null) setArtworkUri(artwork) }
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(Uri.parse(uri))
        .setMediaMetadata(metadata)
        .build()
}

/**
 * Owns the ExoPlayer and MediaSession for background playback.
 * Media3 handles audio focus, noisy-route changes, media notifications and lock-screen actions.
 */
@UnstableApi
class SoundprintPlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var queueStore: PlaybackQueueStore
    private lateinit var library: MusicLibraryManager
    private val listeningWriteQueue = java.util.ArrayDeque<Triple<String, Long, Long>>()
    private var listeningWriteInFlight = false
    @Volatile private var serviceDestroying = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var activeTrackId: String? = null
    private var activeOccurrenceKey: String? = null
    private var activeMediaIndex = -1
    private var transitionSequence = 0L
    private var recordedOccurrenceKey: String? = null
    private var lastSamplePositionMs = 0L
    private var lastSampleElapsedMs = 0L
    private var pendingListeningMs = 0L
    private var lastPersistElapsedMs = 0L
    private var lastTrackDurationMs = 0L

    private val maintenance = object : Runnable {
        override fun run() {
            if (!::player.isInitialized) return
            sampleListeningTime()
            drainListeningWrites()
            val now = SystemClock.elapsedRealtime()
            if (now - lastPersistElapsedMs >= PERSIST_INTERVAL_MS) persistQueue()
            handler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        queueStore = PlaybackQueueStore(this)
        library = MusicLibraryManager.getInstance(applicationContext)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val nextId = mediaItem?.mediaId?.takeIf(String::isNotBlank)
                val previousId = activeTrackId
                val nextIndex = player.currentMediaItemIndex
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && previousId != null &&
                    (previousId != nextId || activeMediaIndex != nextIndex)
                ) {
                    recordSkip(previousId, lastSamplePositionMs, lastTrackDurationMs)
                }
                flushListeningTime()
                activeTrackId = nextId
                activeMediaIndex = nextIndex
                lastTrackDurationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
                transitionSequence += 1L
                activeOccurrenceKey = if (nextId == null) null else "${transitionSequence}:${nextIndex}:${nextId}"
                recordedOccurrenceKey = null
                lastSamplePositionMs = player.currentPosition.coerceAtLeast(0L)
                lastSampleElapsedMs = SystemClock.elapsedRealtime()
                if (player.isPlaying) recordCurrentPlayIfNeeded()
                persistQueue()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    recordCurrentPlayIfNeeded()
                    lastTrackDurationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
                    lastSamplePositionMs = player.currentPosition.coerceAtLeast(0L)
                    lastSampleElapsedMs = SystemClock.elapsedRealtime()
                } else {
                    flushListeningTime()
                    lastSampleElapsedMs = 0L
                }
                persistQueue()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                lastSamplePositionMs = newPosition.positionMs.coerceAtLeast(0L)
                lastSampleElapsedMs = SystemClock.elapsedRealtime()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) flushListeningTime()
                persistQueue()
            }

            override fun onPlayerError(error: PlaybackException) {
                flushListeningTime()
                persistQueue()
            }
        })
        serviceScope.launch {
            val tracks = withContext(Dispatchers.IO) { library.allSongs() }
            withContext(Dispatchers.Main) { restoreQueue(tracks) }
        }
        handler.postDelayed(maintenance, SAMPLE_INTERVAL_MS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistQueue()
        if (!player.isPlaying) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(maintenance)
        flushListeningTime()
        persistQueue()
        if (::mediaSession.isInitialized) mediaSession.release()
        if (::player.isInitialized) player.release()
        serviceDestroying = true
        drainListeningWrites()
        super.onDestroy()
    }

    private fun restoreQueue(availableLibraryTracks: List<MusicTrack>) {
        val saved = queueStore.read()
        if (saved.queueIds.isEmpty()) return
        val availableTracks = availableLibraryTracks.associateBy { it.id }
        val resolved = saved.queueIds.mapIndexedNotNull { oldIndex, id ->
            availableTracks[id]?.let { oldIndex to it }
        }
        if (resolved.isEmpty()) {
            queueStore.clear()
            return
        }
        val requestedIndex = saved.currentIndex.coerceIn(0, saved.queueIds.lastIndex)
        val restoredIndex = resolved.indexOfFirst { it.first == requestedIndex }
            .takeIf { it >= 0 }
            ?: resolved.indexOfFirst { it.first > requestedIndex }.takeIf { it >= 0 }
            ?: resolved.lastIndex
        player.shuffleModeEnabled = saved.shuffleEnabled
        player.repeatMode = saved.repeatMode.toPlayerRepeatMode()
        player.setMediaItems(resolved.map { it.second.toSoundprintMediaItem() }, restoredIndex, saved.positionMs)
        player.prepare()
        activeTrackId = player.currentMediaItem?.mediaId
        activeMediaIndex = restoredIndex
        activeOccurrenceKey = if (activeTrackId == null) null else "restored:${restoredIndex}:${activeTrackId}"
    }

    private fun persistQueue() {
        if (!::player.isInitialized) return
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        if (ids.isEmpty()) {
            queueStore.clear()
            return
        }
        queueStore.write(
            PlaybackQueueSnapshot(
                queueIds = ids,
                currentIndex = player.currentMediaItemIndex,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode.toSoundprintRepeatMode()
            )
        )
        lastPersistElapsedMs = SystemClock.elapsedRealtime()
    }

    private fun recordCurrentPlayIfNeeded() {
        val id = player.currentMediaItem?.mediaId ?: return
        val occurrence = activeOccurrenceKey ?: "${player.currentMediaItemIndex}:${id}"
        if (recordedOccurrenceKey == occurrence) return
        recordedOccurrenceKey = occurrence
        val duration = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
        serviceScope.launch {
            try {
                library.recordPlay(id, 0L, duration)
            } catch (error: Exception) {
                Log.e("SoundprintPlayback", "Play statistics write failed for track $id.", error)
            }
        }
    }

    private fun recordSkip(id: String, positionMs: Long, durationMs: Long) {
        serviceScope.launch {
            try {
                library.recordSkip(id, positionMs, durationMs)
            } catch (error: Exception) {
                Log.e("SoundprintPlayback", "Skip statistics write failed for track $id.", error)
            }
        }
    }

    private fun sampleListeningTime() {
        if (!player.isPlaying) return
        val now = SystemClock.elapsedRealtime()
        val position = player.currentPosition.coerceAtLeast(0L)
        lastTrackDurationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: lastTrackDurationMs
        val previousPosition = lastSamplePositionMs
        val elapsed = if (lastSampleElapsedMs == 0L) 0L else now - lastSampleElapsedMs
        val delta = position - previousPosition
        if (activeTrackId != null && delta > 0L && elapsed > 0L && delta <= elapsed + 1_500L && delta <= 3_000L) {
            pendingListeningMs += delta
            if (pendingListeningMs >= LISTENING_FLUSH_MS) flushListeningTime()
        }
        lastSamplePositionMs = position
        lastSampleElapsedMs = now
    }

    private fun flushListeningTime() {
        val id = activeTrackId
        val listened = pendingListeningMs
        pendingListeningMs = 0L
        if (id != null && listened > 0L) {
            synchronized(listeningWriteQueue) {
                listeningWriteQueue.addLast(Triple(id, listened, lastTrackDurationMs))
            }
        }
        drainListeningWrites()
    }

    /** All history writes are serialized on Dispatchers.IO; failed batches remain queued for retry. */
    private fun drainListeningWrites() {
        synchronized(listeningWriteQueue) {
            if (listeningWriteInFlight || listeningWriteQueue.isEmpty()) return
            listeningWriteInFlight = true
        }
        serviceScope.launch {
            while (true) {
                val batch = synchronized(listeningWriteQueue) {
                    if (listeningWriteQueue.isEmpty()) null else listeningWriteQueue.first()
                } ?: break
                try {
                    library.recordListeningTime(batch.first, batch.second, batch.third)
                    synchronized(listeningWriteQueue) {
                        if (listeningWriteQueue.firstOrNull() == batch) listeningWriteQueue.removeFirst()
                    }
                } catch (error: Exception) {
                    Log.e("SoundprintPlayback", "Listening statistics write failed; retaining it for retry.", error)
                    break
                }
            }
            val shouldCancel = synchronized(listeningWriteQueue) {
                listeningWriteInFlight = false
                serviceDestroying && listeningWriteQueue.isEmpty()
            }
            if (shouldCancel) serviceScope.cancel()
        }
    }

    private fun SoundprintRepeatMode.toPlayerRepeatMode() = when (this) {
        SoundprintRepeatMode.ALL -> Player.REPEAT_MODE_ALL
        SoundprintRepeatMode.ONE -> Player.REPEAT_MODE_ONE
        SoundprintRepeatMode.OFF -> Player.REPEAT_MODE_OFF
    }

    private fun Int.toSoundprintRepeatMode() = when (this) {
        Player.REPEAT_MODE_ALL -> SoundprintRepeatMode.ALL
        Player.REPEAT_MODE_ONE -> SoundprintRepeatMode.ONE
        else -> SoundprintRepeatMode.OFF
    }

    private companion object {
        const val SAMPLE_INTERVAL_MS = 1_000L
        const val PERSIST_INTERVAL_MS = 5_000L
        const val LISTENING_FLUSH_MS = 10_000L
    }
}

/** Activity-side connection to the service. Screens can share one connection and observe its Player. */
class SoundprintPlaybackConnection(
    context: Context,
    private val onConnected: (MediaController) -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executor { command -> mainHandler.post(command) }
    @Volatile var controller: MediaController? = null
        private set
    @Volatile private var closed = false
    private data class PendingPlay(val tracks: List<MusicTrack>, val startIndex: Int, val shuffle: Boolean)
    @Volatile private var pendingPlay: PendingPlay? = null
    private val future = MediaController.Builder(
        context.applicationContext,
        SessionToken(context.applicationContext, ComponentName(context, SoundprintPlaybackService::class.java))
    ).buildAsync()

    init {
        future.addListener({
            val connected = runCatching { future.get() }.getOrNull() ?: return@addListener
            if (closed) connected.release() else {
                controller = connected
                onConnected(connected)
                val queued = synchronized(this) { pendingPlay.also { pendingPlay = null } }
                if (queued != null && !closed) startPlayback(connected, queued)
            }
        }, executor)
    }

    fun playTracks(tracks: List<MusicTrack>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty() || closed) return
        val request = PendingPlay(tracks.toList(), startIndex.coerceIn(tracks.indices), shuffle)
        val player = controller
        if (player == null) {
            synchronized(this) {
                val connected = controller
                if (connected == null) {
                    // A tap made while MediaController is connecting must not be discarded.
                    pendingPlay = request
                    return
                }
                startPlayback(connected, request)
            }
        } else startPlayback(player, request)
    }

    private fun startPlayback(player: MediaController, request: PendingPlay) {
        if (closed || request.tracks.isEmpty()) return
        runCatching {
            player.shuffleModeEnabled = request.shuffle
            player.setMediaItems(
                request.tracks.map(MusicTrack::toSoundprintMediaItem),
                request.startIndex.coerceIn(request.tracks.indices),
                0L
            )
            player.prepare()
            player.play()
        }
    }

    fun playAt(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.seekTo(index, 0L)
        player.play()
    }

    fun toggle() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() { controller?.seekToNextMediaItem() }

    fun previous() {
        val player = controller ?: return
        if (player.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS) player.seekTo(0L)
        else player.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs.coerceAtLeast(0L)) }

    fun enqueue(tracks: List<MusicTrack>, next: Boolean = false) {
        if (tracks.isEmpty()) return
        val player = controller ?: return
        if (player.mediaItemCount == 0) {
            playTracks(tracks)
            return
        }
        val insertionIndex = if (next) (player.currentMediaItemIndex + 1).coerceAtLeast(0) else player.mediaItemCount
        player.addMediaItems(insertionIndex, tracks.map(MusicTrack::toSoundprintMediaItem))
    }

    fun removeFromQueue(index: Int) {
        val player = controller ?: return
        if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
    }

    fun removeTrackFromQueue(trackId: String) {
        val player = controller ?: return
        for (index in player.mediaItemCount - 1 downTo 0) {
            if (player.getMediaItemAt(index).mediaId == trackId) player.removeMediaItem(index)
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        val player = controller ?: return
        if (from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount && from != to) player.moveMediaItem(from, to)
    }

    fun clearQueue() { controller?.clearMediaItems() }

    fun setShuffle(enabled: Boolean) { controller?.shuffleModeEnabled = enabled }

    fun cycleRepeat() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun close() {
        closed = true
        controller?.release()
        controller = null
        if (!future.isDone) future.cancel(true)
    }

    private companion object { const val PREVIOUS_RESTART_THRESHOLD_MS = 4_000L }
}
