package digital.anchorstudio.cadenceplays

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MusicLibraryDatabaseTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("cadence_music_library.db")
    }

    @After fun tearDown() {
        context.deleteDatabase("cadence_music_library.db")
    }

    private fun track(uri: String, source: String? = null) = MusicTrack(
        id = UUID.nameUUIDFromBytes(uri.toByteArray()).toString(),
        uri = uri, title = "Track", artist = "Artist", album = "Album",
        albumArtist = "Artist", genre = "Electronic", durationMs = 180_000,
        trackNumber = 1, year = 2025, mimeType = "audio/mpeg", sizeBytes = 1024,
        modifiedAt = 100, folder = "Music", artworkUri = null,
        addedAt = 100, isFavorite = false, playCount = 0, lastPlayedAt = null,
        contentHash = "sha256-test", sourceUri = source
    )

    @Test fun upsertPreservesUserStateAndSupportsOverlappingSources() {
        val db = MusicLibraryDatabase(context)
        val original = track("content://music/one", "folder-A")
        db.upsert(original, "scan-A")
        db.setFavorite(original.id, true)
        db.markPlayed(original.id)

        db.upsert(original.copy(title = "Updated title", sourceUri = "folder-B"), "scan-B")
        val updated = db.findById(original.id)!!
        assertEquals("Updated title", updated.title)
        assertTrue(updated.isFavorite)
        assertEquals(1, updated.playCount)

        val sourceLossCount = db.completeSourceScan("folder-A", "scan-A-next", true)
        assertEquals("A still-available overlapping source must not count as missing.", 0, sourceLossCount)
        assertTrue("The second source still makes this track available.", db.findById(original.id)!!.isAvailable)

        db.removeSource("folder-B")
        assertFalse("Removing the last readable source marks the track unavailable.", db.findById(original.id)!!.isAvailable)
        db.close()
    }

    @Test fun backupRestorePreservesTracksPlaylistsAndListeningState() {
        val db = MusicLibraryDatabase(context)
        val item = track("content://music/backup", "folder-backup")
        db.upsert(item, "scan-1")
        db.setFavorite(item.id, true)
        db.markPlayed(item.id)
        val playlistId = db.createPlaylist("Road trip")
        db.addToPlaylist(playlistId, item.id)

        val output = ByteArrayOutputStream()
        db.writeBackup(output)
        db.clearLibrary()
        val restored = db.restoreBackup(ByteArrayInputStream(output.toByteArray()), true)

        assertTrue(restored > 0)
        val copy = db.findById(item.id)!!
        assertEquals("Track", copy.title)
        assertTrue(copy.isFavorite)
        assertEquals(1, copy.playCount)
        assertFalse("Restored file permissions must be revalidated before playback.", copy.isAvailable)
        assertEquals(listOf(item.id), db.playlists().single().trackIds)
        db.close()
    }

    @Test fun incompleteFolderScanNeverMarksUnseenTracksMissing() {
        val db = MusicLibraryDatabase(context)
        val item = track("content://music/interrupted", "folder-interrupted")
        db.upsert(item, "scan-before")

        val unavailableAfterFailure = db.completeSourceScan("folder-interrupted", "scan-failed", false)
        assertEquals(0, unavailableAfterFailure)
        assertTrue(db.findById(item.id)!!.isAvailable)

        val unavailableAfterCompleteScan = db.completeSourceScan("folder-interrupted", "scan-complete", true)
        assertEquals(1, unavailableAfterCompleteScan)
        assertFalse(db.findById(item.id)!!.isAvailable)
        db.close()
    }

    @Test fun mergingBackupDoesNotInvalidateExistingTracksOrTrustNewPermissions() {
        val db = MusicLibraryDatabase(context)
        val existing = track("content://music/existing", "folder-existing")
        val incoming = track("content://music/incoming", "folder-incoming")
        db.upsert(existing, "scan-existing")
        db.upsert(incoming, "scan-incoming")
        val bytes = ByteArrayOutputStream().also { db.writeBackup(it) }.toByteArray()

        db.clearLibrary()
        db.upsert(existing, "scan-existing")
        db.restoreBackup(ByteArrayInputStream(bytes), false)
        assertTrue("A track already available in this install stays available during merge.", db.findById(existing.id)!!.isAvailable)
        assertFalse("A newly restored URI must be revalidated before it is treated as playable.", db.findById(incoming.id)!!.isAvailable)
        db.close()
    }

    @Test fun repositoryFacadeReadsAndMutatesThePersistentLibrary() {
        val repository = MusicLibraryRepository(context)
        val database = MusicLibraryDatabase(context)
        val item = track("content://music/repository", "folder-repository")
        database.upsert(item, "scan-repository")
        database.saveSource("folder-repository", "Music", "scan-repository")

        assertEquals(1, repository.count())
        assertEquals(item.id, repository.tracks().single().id)
        assertTrue(repository.toggleFavorite(item.id))
        assertEquals(1, repository.favorites().size)
        assertEquals("Music", repository.sources().single().displayName)

        val backup = ByteArrayOutputStream()
        repository.writeBackup(backup)
        repository.clearLibrary()
        assertEquals(0, repository.count())
        repository.restoreBackup(ByteArrayInputStream(backup.toByteArray()), true)
        assertEquals(item.id, repository.track(item.id)!!.id)
        database.close()
    }


    @Test fun soundprintFingerprintsPersistAndInvalidateWhenTheSourceChanges() {
        val db = MusicLibraryDatabase(context)
        val item = track("content://music/soundprint-cache")
        db.upsert(item)
        val fingerprint = SoundprintFingerprint(
            trackId = item.id, sourceSize = item.sizeBytes, sourceModifiedAt = item.modifiedAt,
            durationMs = item.durationMs, sampleRate = 22_050, frameCount = 8, featureBands = 24,
            compressedFeatures = byteArrayOf(1, 2, 3, 4), generatedAt = 500L
        )

        db.saveSoundprintFingerprint(fingerprint)

        val cached = db.soundprintFingerprint(item.id, item.sizeBytes, item.modifiedAt)!!
        assertEquals(fingerprint.frameCount, cached.frameCount)
        assertArrayEquals(fingerprint.compressedFeatures, cached.compressedFeatures)
        assertNull(db.soundprintFingerprint(item.id, item.sizeBytes, item.modifiedAt + 1L))

        db.removeTrackRecord(item.id)
        assertNull(db.soundprintFingerprint(item.id, item.sizeBytes, item.modifiedAt))
        db.close()
    }

    @Test fun soundprintFingerprintsAreIncludedInLibraryBackups() {
        val db = MusicLibraryDatabase(context)
        val item = track("content://music/soundprint-backup")
        db.upsert(item)
        val fingerprint = SoundprintFingerprint(
            trackId = item.id, sourceSize = item.sizeBytes, sourceModifiedAt = item.modifiedAt,
            durationMs = item.durationMs, sampleRate = 22_050, frameCount = 8, featureBands = 24,
            compressedFeatures = byteArrayOf(9, 8, 7, 6), generatedAt = 600L
        )
        db.saveSoundprintFingerprint(fingerprint)
        val backup = ByteArrayOutputStream()
        db.writeBackup(backup)
        db.clearLibrary()
        db.restoreBackup(ByteArrayInputStream(backup.toByteArray()), true)

        val restored = db.soundprintFingerprint(item.id, item.sizeBytes, item.modifiedAt)
        assertNotNull(restored)
        assertArrayEquals(fingerprint.compressedFeatures, restored!!.compressedFeatures)
        db.close()
    }

    @Test fun versionOneDatabaseMigratesThroughEverySchemaVersion() {
        val raw = context.openOrCreateDatabase("cadence_music_library.db", Context.MODE_PRIVATE, null)
        raw.execSQL("""CREATE TABLE tracks(
            id TEXT PRIMARY KEY, uri TEXT NOT NULL UNIQUE, title TEXT NOT NULL, artist TEXT NOT NULL,
            album TEXT NOT NULL, album_artist TEXT NOT NULL, genre TEXT NOT NULL, duration_ms INTEGER NOT NULL,
            track_number INTEGER NOT NULL, year INTEGER NOT NULL, mime_type TEXT NOT NULL, size_bytes INTEGER NOT NULL,
            modified_at INTEGER NOT NULL, folder TEXT NOT NULL, artwork_uri TEXT, added_at INTEGER NOT NULL,
            is_favorite INTEGER NOT NULL DEFAULT 0, play_count INTEGER NOT NULL DEFAULT 0, last_played_at INTEGER)""")
        raw.execSQL("CREATE TABLE library_sources(uri TEXT PRIMARY KEY,display_name TEXT NOT NULL,last_scanned_at INTEGER NOT NULL)")
        raw.execSQL("CREATE TABLE playlist_tracks(playlist_id TEXT NOT NULL,track_id TEXT NOT NULL,position INTEGER NOT NULL,PRIMARY KEY(playlist_id,track_id))")
        raw.execSQL("CREATE TABLE playlists(id TEXT PRIMARY KEY,name TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)")
        raw.execSQL("CREATE TABLE soundprint_trash(track_id TEXT PRIMARY KEY,removed_at INTEGER NOT NULL,reason TEXT NOT NULL)")
        raw.execSQL("CREATE TABLE audio_analysis_cache(fingerprint TEXT PRIMARY KEY,profile_json TEXT NOT NULL,source_size INTEGER NOT NULL,source_mime TEXT NOT NULL,created_at INTEGER NOT NULL)")
        raw.execSQL("""INSERT INTO tracks(id,uri,title,artist,album,album_artist,genre,duration_ms,track_number,year,mime_type,size_bytes,modified_at,folder,added_at)
            VALUES('v1','content://legacy/v1','Version one track','Artist','Album','Artist','','1000',1,2020,'audio/mpeg',100,1,'Music',1)""")
        raw.version = 1
        raw.close()

        val migrated = MusicLibraryDatabase(context)
        assertEquals("Version one track", migrated.findById("v1")!!.title)
        assertEquals(5, migrated.readableDatabase.version)
        val sourceColumns = mutableSetOf<String>()
        migrated.readableDatabase.rawQuery("PRAGMA table_info(library_sources)", null).use { cursor ->
            while (cursor.moveToNext()) sourceColumns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        }
        assertTrue(sourceColumns.contains("last_scan_id"))
        migrated.close()
    }

    @Test fun versionTwoDatabaseMigratesWithoutDroppingExistingTracks() {
        val raw = context.openOrCreateDatabase("cadence_music_library.db", Context.MODE_PRIVATE, null)
        raw.execSQL("""CREATE TABLE tracks(
            id TEXT PRIMARY KEY, uri TEXT NOT NULL UNIQUE, title TEXT NOT NULL, artist TEXT NOT NULL,
            album TEXT NOT NULL, album_artist TEXT NOT NULL, genre TEXT NOT NULL, duration_ms INTEGER NOT NULL,
            track_number INTEGER NOT NULL, year INTEGER NOT NULL, mime_type TEXT NOT NULL, size_bytes INTEGER NOT NULL,
            modified_at INTEGER NOT NULL, folder TEXT NOT NULL, artwork_uri TEXT, added_at INTEGER NOT NULL,
            is_favorite INTEGER NOT NULL DEFAULT 0, play_count INTEGER NOT NULL DEFAULT 0, last_played_at INTEGER,
            bitrate INTEGER NOT NULL DEFAULT 0, sample_rate INTEGER NOT NULL DEFAULT 0, channel_count INTEGER NOT NULL DEFAULT 0,
            disc_number INTEGER NOT NULL DEFAULT 0, content_hash TEXT, duplicate_of TEXT, source_uri TEXT,
            is_available INTEGER NOT NULL DEFAULT 1, last_seen_scan_id TEXT)""")
        raw.execSQL("CREATE TABLE library_sources(uri TEXT PRIMARY KEY,display_name TEXT NOT NULL,last_scanned_at INTEGER NOT NULL,last_scan_id TEXT)")
        raw.execSQL("CREATE TABLE playlist_tracks(playlist_id TEXT NOT NULL,track_id TEXT NOT NULL,position INTEGER NOT NULL,PRIMARY KEY(playlist_id,track_id))")
        raw.execSQL("CREATE TABLE playlists(id TEXT PRIMARY KEY,name TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)")
        raw.execSQL("CREATE TABLE soundprint_trash(track_id TEXT PRIMARY KEY,removed_at INTEGER NOT NULL,reason TEXT NOT NULL)")
        raw.execSQL("CREATE TABLE audio_analysis_cache(fingerprint TEXT PRIMARY KEY,profile_json TEXT NOT NULL,source_size INTEGER NOT NULL,source_mime TEXT NOT NULL,created_at INTEGER NOT NULL)")
        raw.execSQL("""INSERT INTO tracks(id,uri,title,artist,album,album_artist,genre,duration_ms,track_number,year,mime_type,size_bytes,modified_at,folder,added_at,source_uri,is_available,last_seen_scan_id)
            VALUES('legacy','content://legacy/1','Legacy track','Artist','Album','Artist','','1000',1,2020,'audio/mpeg',100,1,'Music',1,'legacy-folder',1,'old-scan')""")
        raw.version = 2
        raw.close()

        val migrated = MusicLibraryDatabase(context)
        val item = migrated.findById("legacy")!!
        assertEquals("Legacy track", item.title)
        assertEquals(5, migrated.readableDatabase.version)
        assertTrue(migrated.readableDatabase.rawQuery("SELECT 1 FROM track_sources WHERE track_id='legacy' AND source_uri='legacy-folder'", null).use { it.moveToFirst() })
        migrated.close()
    }
}
