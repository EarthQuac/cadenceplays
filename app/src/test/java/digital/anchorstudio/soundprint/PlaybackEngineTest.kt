package digital.anchorstudio.soundprint

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackEngineTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = org.robolectric.RuntimeEnvironment.getApplication()
        MusicLibraryDatabase.getInstance(context).clearLibrary()
        context.deleteDatabase("soundprint_library_management.db")
        context.getSharedPreferences("soundprint_playback_queue", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() {
        MusicLibraryDatabase.getInstance(context).clearLibrary()
        context.deleteDatabase("soundprint_library_management.db")
        context.getSharedPreferences("soundprint_playback_queue", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun track(uri: String) = MusicTrack(
        id = UUID.nameUUIDFromBytes(uri.toByteArray()).toString(),
        uri = uri, title = "Track title", artist = "Track artist", album = "Album",
        albumArtist = "Track artist", genre = "Electronic", durationMs = 180_000L,
        trackNumber = 1, year = 2025, mimeType = "audio/mpeg", sizeBytes = 1024L,
        modifiedAt = 100L, folder = "Music", artworkUri = null, addedAt = 100L,
        isFavorite = false, playCount = 0, lastPlayedAt = null, contentHash = "hash"
    )

    @Test fun queueSnapshotRoundTripsOrderDuplicatesResumePositionAndModes() {
        val store = PlaybackQueueStore(context)
        val expected = PlaybackQueueSnapshot(
            queueIds = listOf("track-a", "track-b", "track-a", "track-c"),
            currentIndex = 2,
            positionMs = 48_321L,
            shuffleEnabled = true,
            repeatMode = SoundprintRepeatMode.ONE
        )

        store.write(expected)

        assertEquals(expected, store.read())
    }

    @Test fun clearingQueueSnapshotRemovesOldResumeState() {
        val store = PlaybackQueueStore(context)
        store.write(PlaybackQueueSnapshot(listOf("track-a"), 0, 12_000L, true, SoundprintRepeatMode.ALL))

        store.clear()

        assertEquals(PlaybackQueueSnapshot(), store.read())
    }

    @Test fun repeatModeAndShuffleStateArePersistedIndependently() {
        val store = PlaybackQueueStore(context)
        store.write(PlaybackQueueSnapshot(listOf("a", "b"), 1, 0L, false, SoundprintRepeatMode.OFF))
        assertFalse(store.read().shuffleEnabled)
        assertEquals(SoundprintRepeatMode.OFF, store.read().repeatMode)

        store.write(store.read().copy(shuffleEnabled = true, repeatMode = SoundprintRepeatMode.ALL))

        assertTrue(store.read().shuffleEnabled)
        assertEquals(SoundprintRepeatMode.ALL, store.read().repeatMode)
    }

    @Test fun mediaItemKeepsTheLocalContentUriAndSoundprintMetadata() {
        val song = track("content://music/provider/song-1")

        val item = song.toSoundprintMediaItem()

        assertEquals(song.id, item.mediaId)
        assertEquals(song.uri, item.localConfiguration?.uri.toString())
        assertEquals(song.title, item.mediaMetadata.title.toString())
        assertEquals(song.artist, item.mediaMetadata.artist.toString())
        assertEquals(song.album, item.mediaMetadata.albumTitle.toString())
    }

    @Test fun playbackEventsFeedPlaySkipAndListeningStatistics() {
        val song = track("content://music/provider/stats")
        // The production manager and repository share the process-wide database instance.
        MusicLibraryDatabase.getInstance(context).upsert(song)
        val manager = MusicLibraryManager(context)

        manager.recordPlay(song.id, 0L, song.durationMs)
        manager.recordSkip(song.id, 22_000L, song.durationMs)
        manager.recordListeningTime(song.id, 12_500L, song.durationMs)

        val stats = manager.listeningStats()[song.id]
        assertNotNull(stats)
        assertEquals(1, stats!!.plays)
        assertEquals(1, stats.skips)
        assertEquals(12_500L, stats.totalListenedMs)
    }
}
