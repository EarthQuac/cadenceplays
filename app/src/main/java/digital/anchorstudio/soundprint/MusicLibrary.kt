package digital.anchorstudio.soundprint

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

data class MusicTrack(
    val id: String, val uri: String, val title: String, val artist: String,
    val album: String, val albumArtist: String, val genre: String, val durationMs: Long,
    val trackNumber: Int, val year: Int, val mimeType: String, val sizeBytes: Long,
    val modifiedAt: Long, val folder: String, val artworkUri: String?, val addedAt: Long,
    val isFavorite: Boolean, val playCount: Int, val lastPlayedAt: Long?,
    val bitrate: Int = 0, val sampleRate: Int = 0, val channelCount: Int = 0,
    val discNumber: Int = 0, val contentHash: String? = null, val duplicateOf: String? = null,
    val sourceUri: String? = null, val isAvailable: Boolean = true
)
data class MusicPlaylist(val id: String, val name: String, val trackIds: List<String>)
data class MusicLibrarySource(val uri: String, val displayName: String, val lastScannedAt: Long)
data class LibraryScanProgress(val sourceName: String, val visited: Int, val imported: Int, val skipped: Int, val errors: Int)
data class LibraryScanResult(val visited: Int, val imported: Int, val updated: Int, val skipped: Int, val unavailable: Int, val errors: List<LibraryScanError>)
data class LibraryScanError(val uri: String?, val message: String)
data class MusicAlbum(val name: String, val artist: String, val trackCount: Int, val artworkUri: String?, val totalDurationMs: Long)
data class MusicArtist(val name: String, val trackCount: Int, val albumCount: Int)
enum class TrackSort { TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION, YEAR, PLAY_COUNT, LAST_PLAYED }

/** SQLite is the source of truth. Schema upgrades are additive and preserve existing user data. */
class MusicLibraryDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "soundprint_music_library.db", null, 5) {

    override fun onCreate(db: SQLiteDatabase) = createSchema(db)
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) migrateToV2(db)
        if (oldVersion < 3) migrateToV3(db)
        if (oldVersion < 4) migrateToV4(db)
        if (oldVersion < 5) createSoundprintSchema(db)
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE tracks(
            id TEXT PRIMARY KEY, uri TEXT NOT NULL UNIQUE, title TEXT NOT NULL, artist TEXT NOT NULL,
            album TEXT NOT NULL, album_artist TEXT NOT NULL, genre TEXT NOT NULL, duration_ms INTEGER NOT NULL,
            track_number INTEGER NOT NULL, year INTEGER NOT NULL, mime_type TEXT NOT NULL, size_bytes INTEGER NOT NULL,
            modified_at INTEGER NOT NULL, folder TEXT NOT NULL, artwork_uri TEXT, added_at INTEGER NOT NULL,
            is_favorite INTEGER NOT NULL DEFAULT 0, play_count INTEGER NOT NULL DEFAULT 0, last_played_at INTEGER,
            bitrate INTEGER NOT NULL DEFAULT 0, sample_rate INTEGER NOT NULL DEFAULT 0,
            channel_count INTEGER NOT NULL DEFAULT 0, disc_number INTEGER NOT NULL DEFAULT 0,
            content_hash TEXT, duplicate_of TEXT, source_uri TEXT, is_available INTEGER NOT NULL DEFAULT 1,
            last_seen_scan_id TEXT)""")
        createIndexes(db)
        db.execSQL("CREATE TABLE playlists(id TEXT PRIMARY KEY, name TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE playlist_tracks(playlist_id TEXT NOT NULL, track_id TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlist_id,track_id))")
        db.execSQL("CREATE INDEX playlist_tracks_order_idx ON playlist_tracks(playlist_id,position)")
        db.execSQL("CREATE TABLE soundprint_trash(track_id TEXT PRIMARY KEY, removed_at INTEGER NOT NULL, reason TEXT NOT NULL)")
        db.execSQL("CREATE TABLE audio_analysis_cache(fingerprint TEXT PRIMARY KEY, profile_json TEXT NOT NULL, source_size INTEGER NOT NULL, source_mime TEXT NOT NULL, created_at INTEGER NOT NULL)")
        createSoundprintSchema(db)
        db.execSQL("CREATE TABLE library_sources(uri TEXT PRIMARY KEY, display_name TEXT NOT NULL, last_scanned_at INTEGER NOT NULL, last_scan_id TEXT)")
        db.execSQL("CREATE TABLE library_scan_errors(id INTEGER PRIMARY KEY AUTOINCREMENT, source_uri TEXT, file_uri TEXT, message TEXT NOT NULL, occurred_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX library_scan_errors_time_idx ON library_scan_errors(occurred_at)")
        createSourceMapSchema(db)
        createFileSourceSchema(db)
    }

    private fun createSoundprintSchema(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS soundprint_fingerprints(
            track_id TEXT PRIMARY KEY, source_size INTEGER NOT NULL, source_modified_at INTEGER NOT NULL,
            duration_ms INTEGER NOT NULL, sample_rate INTEGER NOT NULL, frame_count INTEGER NOT NULL,
            feature_bands INTEGER NOT NULL, compressed_features BLOB NOT NULL, generated_at INTEGER NOT NULL,
            fingerprint_version INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS soundprint_fingerprint_version_idx ON soundprint_fingerprints(fingerprint_version)")
    }

    private fun createSourceMapSchema(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS track_sources(
            track_id TEXT NOT NULL, source_uri TEXT NOT NULL, file_uri TEXT NOT NULL,
            last_seen_scan_id TEXT, is_available INTEGER NOT NULL DEFAULT 1,
            PRIMARY KEY(track_id,source_uri))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS track_sources_source_idx ON track_sources(source_uri,is_available,last_seen_scan_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS track_sources_file_idx ON track_sources(file_uri)")
    }
    private fun migrateToV3(db:SQLiteDatabase) {
        createSourceMapSchema(db)
        db.execSQL("""INSERT OR IGNORE INTO track_sources(track_id,source_uri,file_uri,last_seen_scan_id,is_available)
            SELECT id,source_uri,uri,last_seen_scan_id,is_available FROM tracks WHERE source_uri IS NOT NULL AND source_uri <> ''""")
    }
    private fun createFileSourceSchema(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS library_file_sources(uri TEXT PRIMARY KEY,display_name TEXT NOT NULL,last_scanned_at INTEGER NOT NULL)")
    }
    private fun migrateToV4(db:SQLiteDatabase) { createFileSourceSchema(db) }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS tracks_title_idx ON tracks(title COLLATE NOCASE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS tracks_artist_idx ON tracks(artist COLLATE NOCASE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS tracks_album_idx ON tracks(album COLLATE NOCASE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS tracks_hash_idx ON tracks(content_hash)")
        db.execSQL("CREATE INDEX IF NOT EXISTS tracks_source_idx ON tracks(source_uri,is_available)")
        db.execSQL("CREATE INDEX IF NOT EXISTS tracks_recent_idx ON tracks(last_played_at DESC)")
    }

    private fun migrateToV2(db: SQLiteDatabase) {
        val columns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(tracks)", null).use { c ->
            while (c.moveToNext()) columns += c.getString(c.getColumnIndexOrThrow("name"))
        }
        listOf(
            "bitrate INTEGER NOT NULL DEFAULT 0", "sample_rate INTEGER NOT NULL DEFAULT 0",
            "channel_count INTEGER NOT NULL DEFAULT 0", "disc_number INTEGER NOT NULL DEFAULT 0",
            "content_hash TEXT", "duplicate_of TEXT", "source_uri TEXT",
            "is_available INTEGER NOT NULL DEFAULT 1", "last_seen_scan_id TEXT"
        ).forEach { definition ->
            val name = definition.substringBefore(' ')
            if (name !in columns) db.execSQL("ALTER TABLE tracks ADD COLUMN $definition")
        }
        val sourceColumns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(library_sources)", null).use { c ->
            while (c.moveToNext()) sourceColumns += c.getString(c.getColumnIndexOrThrow("name"))
        }
        if ("last_scan_id" !in sourceColumns) db.execSQL("ALTER TABLE library_sources ADD COLUMN last_scan_id TEXT")
        db.execSQL("CREATE TABLE IF NOT EXISTS library_scan_errors(id INTEGER PRIMARY KEY AUTOINCREMENT, source_uri TEXT, file_uri TEXT, message TEXT NOT NULL, occurred_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS playlist_tracks_order_idx ON playlist_tracks(playlist_id,position)")
        db.execSQL("CREATE INDEX IF NOT EXISTS library_scan_errors_time_idx ON library_scan_errors(occurred_at)")
        createIndexes(db)
    }

    /** Updating an existing row never replaces it, so favourites, history and playlist references survive. */
    fun upsert(track: MusicTrack, scanId: String? = null): Boolean {
        val values = trackValues(track).apply {
            put("is_available", if (track.isAvailable) 1 else 0)
            put("last_seen_scan_id", scanId)
        }
        val inserted = findByUri(track.uri) == null
        if (inserted) writableDatabase.insertOrThrow("tracks", null, values)
        else {
            values.remove("id"); values.remove("added_at"); values.remove("is_favorite")
            values.remove("play_count"); values.remove("last_played_at"); values.remove("source_uri")
            writableDatabase.update("tracks", values, "uri=?", arrayOf(track.uri))
        }
        if (!track.sourceUri.isNullOrBlank()) recordTrackSource(track.id,track.sourceUri,track.uri,scanId,true)
        return inserted
    }
    fun recordTrackSource(trackId:String,sourceUri:String,fileUri:String,scanId:String?,available:Boolean) {
        writableDatabase.insertWithOnConflict("track_sources",null,ContentValues().apply {
            put("track_id",trackId);put("source_uri",sourceUri);put("file_uri",fileUri)
            put("last_seen_scan_id",scanId);put("is_available",if(available) 1 else 0)
        },SQLiteDatabase.CONFLICT_REPLACE)
        writableDatabase.execSQL("UPDATE tracks SET is_available=1 WHERE id=?",arrayOf(trackId))
    }

    private fun trackValues(t: MusicTrack) = ContentValues().apply {
        put("id",t.id); put("uri",t.uri); put("title",t.title); put("artist",t.artist)
        put("album",t.album); put("album_artist",t.albumArtist); put("genre",t.genre)
        put("duration_ms",t.durationMs); put("track_number",t.trackNumber); put("year",t.year)
        put("mime_type",t.mimeType); put("size_bytes",t.sizeBytes); put("modified_at",t.modifiedAt)
        put("folder",t.folder); put("artwork_uri",t.artworkUri); put("added_at",t.addedAt)
        put("is_favorite",if(t.isFavorite) 1 else 0); put("play_count",t.playCount); put("last_played_at",t.lastPlayedAt)
        put("bitrate",t.bitrate); put("sample_rate",t.sampleRate); put("channel_count",t.channelCount)
        put("disc_number",t.discNumber); put("content_hash",t.contentHash); put("duplicate_of",t.duplicateOf)
        put("source_uri",t.sourceUri); put("is_available",if(t.isAvailable) 1 else 0)
    }

    fun findByUri(uri:String):MusicTrack? =
        readableDatabase.rawQuery("SELECT * FROM tracks WHERE uri=?",arrayOf(uri)).use { if(it.moveToFirst()) it.toMusicTrack() else null }
    fun findById(id:String):MusicTrack? =
        readableDatabase.rawQuery("SELECT * FROM tracks WHERE id=?",arrayOf(id)).use { if(it.moveToFirst()) it.toMusicTrack() else null }
    fun findCanonicalByHash(hash:String, excludingUri:String):String? =
        readableDatabase.rawQuery("SELECT id FROM tracks WHERE content_hash=? AND uri<>? AND is_available=1 ORDER BY added_at LIMIT 1",arrayOf(hash,excludingUri))
            .use { if(it.moveToFirst()) it.getString(0) else null }

    fun tracks(query:String="", sort:TrackSort=TrackSort.TITLE, ascending:Boolean=true, favoritesOnly:Boolean=false, availableOnly:Boolean=true):List<MusicTrack> {
        val order = when(sort) {
            TrackSort.TITLE -> "title COLLATE NOCASE"
            TrackSort.ARTIST -> "artist COLLATE NOCASE,title COLLATE NOCASE"
            TrackSort.ALBUM -> "album COLLATE NOCASE,track_number"
            TrackSort.DATE_ADDED -> "added_at"
            TrackSort.DURATION -> "duration_ms"
            TrackSort.YEAR -> "year"
            TrackSort.PLAY_COUNT -> "play_count"
            TrackSort.LAST_PLAYED -> "last_played_at"
        }
        val clauses=mutableListOf("id NOT IN(SELECT track_id FROM soundprint_trash)")
        val args=mutableListOf<String>()
        if(availableOnly) clauses += "is_available=1"
        if(favoritesOnly) clauses += "is_favorite=1"
        if(query.isNotBlank()) {
            clauses += "(title LIKE ? OR artist LIKE ? OR album LIKE ? OR album_artist LIKE ? OR genre LIKE ? OR folder LIKE ?)"
            repeat(6) { args += "%${query.trim()}%" }
        }
        val direction=if(ascending) "ASC" else "DESC"
        return readableDatabase.rawQuery("SELECT * FROM tracks WHERE ${clauses.joinToString(" AND ")} ORDER BY $order $direction",args.toTypedArray())
            .use { c -> buildList { while(c.moveToNext()) add(c.toMusicTrack()) } }
    }

    fun count(availableOnly:Boolean=true):Int {
        val availability=if(availableOnly) " AND is_available=1" else ""
        return readableDatabase.rawQuery("SELECT COUNT(*) FROM tracks WHERE id NOT IN(SELECT track_id FROM soundprint_trash)$availability",null)
            .use { if(it.moveToFirst()) it.getInt(0) else 0 }
    }
    fun updateTrack(track:MusicTrack) {
        val values=trackValues(track).apply {
            remove("id"); remove("uri"); remove("added_at"); remove("is_favorite")
            remove("play_count"); remove("last_played_at")
        }
        writableDatabase.update("tracks",values,"id=?",arrayOf(track.id))
    }
    fun resetPlayStats(id:String) {
        writableDatabase.execSQL("UPDATE tracks SET play_count=0,last_played_at=NULL WHERE id=?",arrayOf(id))
    }
    fun toggleFavorite(id:String):Boolean {
        writableDatabase.execSQL("UPDATE tracks SET is_favorite=CASE is_favorite WHEN 1 THEN 0 ELSE 1 END WHERE id=?",arrayOf(id))
        return readableDatabase.rawQuery("SELECT is_favorite FROM tracks WHERE id=?",arrayOf(id)).use { it.moveToFirst() && it.getInt(0)==1 }
    }
    fun setFavorite(id:String,favorite:Boolean) {
        writableDatabase.execSQL("UPDATE tracks SET is_favorite=? WHERE id=?",arrayOf(if(favorite) 1 else 0,id))
    }
    fun markPlayed(id:String) {
        writableDatabase.execSQL("UPDATE tracks SET play_count=play_count+1,last_played_at=? WHERE id=?",arrayOf(System.currentTimeMillis(),id))
    }
    fun recentlyPlayed(limit:Int=50):List<MusicTrack> =
        readableDatabase.rawQuery("SELECT * FROM tracks WHERE last_played_at IS NOT NULL AND is_available=1 AND id NOT IN(SELECT track_id FROM soundprint_trash) ORDER BY last_played_at DESC LIMIT ?",arrayOf(limit.coerceAtLeast(1).toString()))
            .use { c -> buildList { while(c.moveToNext()) add(c.toMusicTrack()) } }

    fun albums():List<MusicAlbum> =
        readableDatabase.rawQuery("""SELECT album,COALESCE(NULLIF(album_artist,''),artist),COUNT(*),
            (SELECT artwork_uri FROM tracks t2 WHERE t2.album=t.album AND t2.artwork_uri IS NOT NULL LIMIT 1),
            SUM(duration_ms) FROM tracks t WHERE is_available=1 AND id NOT IN(SELECT track_id FROM soundprint_trash)
            GROUP BY album,COALESCE(NULLIF(album_artist,''),artist) ORDER BY album COLLATE NOCASE""",null)
            .use { c -> buildList { while(c.moveToNext()) add(MusicAlbum(c.getString(0),c.getString(1),c.getInt(2),c.getString(3),c.getLong(4))) } }
    fun artists():List<MusicArtist> =
        readableDatabase.rawQuery("""SELECT artist,COUNT(*),COUNT(DISTINCT album) FROM tracks
            WHERE is_available=1 AND id NOT IN(SELECT track_id FROM soundprint_trash)
            GROUP BY artist COLLATE NOCASE ORDER BY artist COLLATE NOCASE""",null)
            .use { c -> buildList { while(c.moveToNext()) add(MusicArtist(c.getString(0),c.getInt(1),c.getInt(2))) } }

    fun createPlaylist(name:String):String {
        require(name.isNotBlank()) { "Playlist name cannot be empty." }
        val id=UUID.randomUUID().toString(); val now=System.currentTimeMillis()
        writableDatabase.insertOrThrow("playlists",null,ContentValues().apply { put("id",id);put("name",name.trim());put("created_at",now);put("updated_at",now) })
        return id
    }
    fun addToPlaylist(playlistId:String,trackId:String) {
        require(findById(trackId)!=null) { "Track does not exist." }
        val db=writableDatabase; db.beginTransaction()
        try {
            val pos=db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM playlist_tracks WHERE playlist_id=?",arrayOf(playlistId)).use { if(it.moveToFirst()) it.getInt(0) else 0 }
            db.insertWithOnConflict("playlist_tracks",null,ContentValues().apply { put("playlist_id",playlistId);put("track_id",trackId);put("position",pos) },SQLiteDatabase.CONFLICT_IGNORE)
            db.execSQL("UPDATE playlists SET updated_at=? WHERE id=?",arrayOf(System.currentTimeMillis(),playlistId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun playlists():List<MusicPlaylist> =
        readableDatabase.rawQuery("SELECT id,name FROM playlists ORDER BY name COLLATE NOCASE",null).use { c ->
            buildList { while(c.moveToNext()) {
                val id=c.getString(0)
                val ids=readableDatabase.rawQuery("SELECT track_id FROM playlist_tracks WHERE playlist_id=? ORDER BY position",arrayOf(id))
                    .use { q -> buildList { while(q.moveToNext()) add(q.getString(0)) } }
                add(MusicPlaylist(id,c.getString(1),ids))
            } }
        }

    fun saveSource(uri:String,name:String,scanId:String?=null) {
        writableDatabase.insertWithOnConflict("library_sources",null,ContentValues().apply {
            put("uri",uri);put("display_name",name);put("last_scanned_at",System.currentTimeMillis());put("last_scan_id",scanId)
        },SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun sources():List<MusicLibrarySource> =
        readableDatabase.rawQuery("SELECT uri,display_name,last_scanned_at FROM library_sources ORDER BY display_name COLLATE NOCASE",null)
            .use { c -> buildList { while(c.moveToNext()) add(MusicLibrarySource(c.getString(0),c.getString(1),c.getLong(2))) } }
    fun markSourceMissing(sourceUri:String) {
        writableDatabase.execSQL("UPDATE track_sources SET is_available=0 WHERE source_uri=?",arrayOf(sourceUri))
        refreshAvailabilityForSource(sourceUri)
    }
    fun completeSourceScan(sourceUri:String,scanId:String,traversalSucceeded:Boolean):Int =
        if(traversalSucceeded) markUnseenUnavailable(sourceUri,scanId) else 0

    fun markUnseenUnavailable(sourceUri:String,scanId:String):Int {
        val previouslyAvailable=mutableListOf<String>()
        readableDatabase.rawQuery("SELECT DISTINCT track_id FROM track_sources WHERE source_uri=? AND is_available=1",arrayOf(sourceUri)).use { c ->
            while(c.moveToNext()) previouslyAvailable+=c.getString(0)
        }
        writableDatabase.execSQL("UPDATE track_sources SET is_available=0 WHERE source_uri=? AND (last_seen_scan_id IS NULL OR last_seen_scan_id<>?)",arrayOf(sourceUri,scanId))
        refreshAvailabilityForSource(sourceUri)
        return previouslyAvailable.count { id ->
            readableDatabase.rawQuery("SELECT is_available FROM tracks WHERE id=?",arrayOf(id)).use { it.moveToFirst() && it.getInt(0)==0 }
        }
    }
    private fun refreshAvailabilityForSource(sourceUri:String) {
        writableDatabase.execSQL("""UPDATE tracks SET is_available=CASE
            WHEN EXISTS(SELECT 1 FROM track_sources ts WHERE ts.track_id=tracks.id AND ts.is_available=1) THEN 1
            WHEN EXISTS(SELECT 1 FROM track_sources ts WHERE ts.track_id=tracks.id) THEN 0
            ELSE is_available END WHERE id IN(SELECT track_id FROM track_sources WHERE source_uri=?)""",arrayOf(sourceUri))
    }
    fun removeSource(sourceUri:String):List<String> {
        val affected=mutableListOf<String>();val db=writableDatabase;db.beginTransaction()
        try {
            db.rawQuery("SELECT DISTINCT track_id FROM track_sources WHERE source_uri=?",arrayOf(sourceUri)).use { c -> while(c.moveToNext()) affected+=c.getString(0) }
            db.delete("track_sources","source_uri=?",arrayOf(sourceUri));db.delete("library_sources","uri=?",arrayOf(sourceUri));db.delete("library_file_sources","uri=?",arrayOf(sourceUri))
            affected.forEach { id ->
                val hasAvailable=db.rawQuery("SELECT 1 FROM track_sources WHERE track_id=? AND is_available=1 LIMIT 1",arrayOf(id)).use { it.moveToFirst() }
                if(hasAvailable) db.execSQL("UPDATE tracks SET is_available=1 WHERE id=?",arrayOf(id))
                else db.execSQL("UPDATE tracks SET is_available=0 WHERE id=?",arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return affected
    }
    fun saveFileSource(uri:String,name:String) {
        writableDatabase.insertWithOnConflict("library_file_sources",null,ContentValues().apply {
            put("uri",uri);put("display_name",name);put("last_scanned_at",System.currentTimeMillis())
        },SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun fileSources():List<Pair<String,String>> =
        readableDatabase.rawQuery("SELECT uri,display_name FROM library_file_sources ORDER BY display_name COLLATE NOCASE",null)
            .use { c -> buildList { while(c.moveToNext()) add(c.getString(0) to c.getString(1)) } }

    fun recordScanError(sourceUri:String?,fileUri:String?,message:String) {
        writableDatabase.insert("library_scan_errors",null,ContentValues().apply {
            put("source_uri",sourceUri);put("file_uri",fileUri);put("message",message.take(1000));put("occurred_at",System.currentTimeMillis())
        })
    }
    fun scanErrors(limit:Int=100):List<LibraryScanError> =
        readableDatabase.rawQuery("SELECT file_uri,message FROM library_scan_errors ORDER BY occurred_at DESC LIMIT ?",arrayOf(limit.coerceIn(1,1000).toString()))
            .use { c -> buildList { while(c.moveToNext()) add(LibraryScanError(c.getString(0),c.getString(1))) } }
    fun clearLibrary() {
        val db=writableDatabase; db.beginTransaction()
        try {
            db.delete("tracks",null,null);db.delete("playlist_tracks",null,null);db.delete("playlists",null,null)
            db.delete("soundprint_trash",null,null);db.delete("soundprint_fingerprints",null,null);db.delete("library_sources",null,null);db.delete("library_file_sources",null,null);db.delete("track_sources",null,null);db.delete("library_scan_errors",null,null);db.delete("audio_analysis_cache",null,null);db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun removeTrackRecord(id:String) {
        val db=writableDatabase;db.beginTransaction()
        try { db.delete("playlist_tracks","track_id=?",arrayOf(id));db.delete("soundprint_trash","track_id=?",arrayOf(id));db.delete("soundprint_fingerprints","track_id=?",arrayOf(id));db.delete("audio_analysis_cache","fingerprint LIKE ?",arrayOf("clap:"+id+":%"));db.delete("tracks","id=?",arrayOf(id));db.setTransactionSuccessful() }
        finally { db.endTransaction() }
    }
    fun moveToTrash(id:String,reason:String="user") {
        writableDatabase.insertWithOnConflict("soundprint_trash",null,ContentValues().apply { put("track_id",id);put("removed_at",System.currentTimeMillis());put("reason",reason) },SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun restoreFromTrash(id:String) { writableDatabase.delete("soundprint_trash","track_id=?",arrayOf(id)) }
    fun pruneTrash(now:Long=System.currentTimeMillis()):Int =
        writableDatabase.delete("soundprint_trash","removed_at < ?",arrayOf((now-30L*86_400_000L).toString()))
    fun saveTwinSenseFingerprint(fingerprint:TwinSenseFingerprint) {
        writableDatabase.insertWithOnConflict("soundprint_fingerprints",null,ContentValues().apply {
            put("track_id",fingerprint.trackId);put("source_size",fingerprint.sourceSize)
            put("source_modified_at",fingerprint.sourceModifiedAt);put("duration_ms",fingerprint.durationMs)
            put("sample_rate",fingerprint.sampleRate);put("frame_count",fingerprint.frameCount)
            put("feature_bands",fingerprint.featureBands);put("compressed_features",fingerprint.compressedFeatures)
            put("generated_at",fingerprint.generatedAt);put("fingerprint_version",fingerprint.version)
        },SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun twinSenseFingerprint(trackId:String,sourceSize:Long,sourceModifiedAt:Long):TwinSenseFingerprint? =
        readableDatabase.rawQuery("SELECT * FROM soundprint_fingerprints WHERE track_id=? AND source_size=? AND source_modified_at=? AND fingerprint_version=?",
            arrayOf(trackId,sourceSize.toString(),sourceModifiedAt.toString(),"1")).use { c ->
            if(!c.moveToFirst()) null else TwinSenseFingerprint(
                trackId=c.getString(c.getColumnIndexOrThrow("track_id")),
                sourceSize=c.getLong(c.getColumnIndexOrThrow("source_size")),
                sourceModifiedAt=c.getLong(c.getColumnIndexOrThrow("source_modified_at")),
                durationMs=c.getLong(c.getColumnIndexOrThrow("duration_ms")),
                sampleRate=c.getInt(c.getColumnIndexOrThrow("sample_rate")),
                frameCount=c.getInt(c.getColumnIndexOrThrow("frame_count")),
                featureBands=c.getInt(c.getColumnIndexOrThrow("feature_bands")),
                compressedFeatures=c.getBlob(c.getColumnIndexOrThrow("compressed_features")),
                generatedAt=c.getLong(c.getColumnIndexOrThrow("generated_at")),
                version=c.getInt(c.getColumnIndexOrThrow("fingerprint_version"))
            )
        }
    fun deleteTwinSenseFingerprint(trackId:String) { writableDatabase.delete("soundprint_fingerprints","track_id=?",arrayOf(trackId)) }

    fun saveAnalysis(fingerprint:String,profileJson:String,sourceSize:Long,sourceMime:String) {
        writableDatabase.insertWithOnConflict("audio_analysis_cache",null,ContentValues().apply {
            put("fingerprint",fingerprint);put("profile_json",profileJson);put("source_size",sourceSize);put("source_mime",sourceMime);put("created_at",System.currentTimeMillis())
        },SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun analysis(fingerprint:String):String? =
        readableDatabase.rawQuery("SELECT profile_json FROM audio_analysis_cache WHERE fingerprint=?",arrayOf(fingerprint)).use { if(it.moveToFirst()) it.getString(0) else null }
    fun deleteAnalysisForTrack(trackId:String) {
        writableDatabase.delete("audio_analysis_cache","fingerprint LIKE ?",arrayOf("clap:"+trackId+":%"))
    }

    private val backupTables=listOf("tracks","library_sources","library_file_sources","track_sources","playlists","playlist_tracks","soundprint_trash","soundprint_fingerprints","audio_analysis_cache","library_scan_errors")

    fun writeBackup(output:OutputStream) {
        val root=JSONObject().put("format","soundprint-native-library").put("formatVersion",1).put("exportedAt",System.currentTimeMillis())
        val tables=JSONObject()
        backupTables.forEach { table ->
            val rows=JSONArray()
            readableDatabase.rawQuery("SELECT * FROM $table",null).use { cursor ->
                while(cursor.moveToNext()) {
                    val row=JSONObject()
                    cursor.columnNames.forEachIndexed { index,name ->
                        val value:Any?=when(cursor.getType(index)) {
                            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                            Cursor.FIELD_TYPE_BLOB -> android.util.Base64.encodeToString(cursor.getBlob(index),android.util.Base64.NO_WRAP)
                            else -> cursor.getString(index)
                        }
                        row.put(name,value ?: JSONObject.NULL)
                    }
                    rows.put(row)
                }
            }
            tables.put(table,rows)
        }
        root.put("tables",tables)
        output.write(root.toString().toByteArray(Charsets.UTF_8));output.flush()
    }

    fun restoreBackup(input:InputStream,replaceExisting:Boolean):Int {
        val root=JSONObject(input.readBytes().toString(Charsets.UTF_8))
        require(root.optString("format")=="soundprint-native-library") { "This is not a Soundprint library backup." }
        require(root.optInt("formatVersion",0) in 1..1) { "This backup version is not supported." }
        val tables=root.optJSONObject("tables") ?: throw IllegalArgumentException("Backup is missing its data tables.")
        val db=writableDatabase;var restored=0;db.beginTransaction()
        try {
            if(replaceExisting) backupTables.asReversed().forEach { db.delete(it,null,null) }
            val order=listOf("tracks","library_sources","library_file_sources","track_sources","playlists","playlist_tracks","soundprint_trash","soundprint_fingerprints","audio_analysis_cache","library_scan_errors")
            order.forEach { table ->
                val rows=tables.optJSONArray(table) ?: JSONArray()
                for(index in 0 until rows.length()) {
                    val row=rows.getJSONObject(index);val values=ContentValues()
                    row.keys().forEach { key ->
                        val value=row.opt(key)
                        when(value) {
                            null,JSONObject.NULL -> values.putNull(key)
                            is Boolean -> values.put(key,if(value) 1 else 0)
                            is Int -> values.put(key,value)
                            is Long -> values.put(key,value)
                            is Double -> values.put(key,value)
                            is String -> if(table=="soundprint_fingerprints" && key=="compressed_features") {
                                values.put(key,android.util.Base64.decode(value,android.util.Base64.DEFAULT))
                            } else values.put(key,value)
                            else -> values.put(key,value.toString())
                        }
                    }
                    if(table=="tracks") {
                        val exists=readableDatabase.rawQuery("SELECT 1 FROM tracks WHERE id=? LIMIT 1",arrayOf(row.optString("id"))).use { it.moveToFirst() }
                        if(replaceExisting || !exists) values.put("is_available",0)
                    }
                    if(table=="track_sources") values.put("is_available",0)
                    db.insertWithOnConflict(table,null,values,SQLiteDatabase.CONFLICT_REPLACE)
                    restored++
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return restored
    }

    private fun Cursor.toMusicTrack():MusicTrack {
        fun s(name:String)=getString(getColumnIndexOrThrow(name))
        fun n(name:String)=getLong(getColumnIndexOrThrow(name))
        val last=getColumnIndexOrThrow("last_played_at")
        return MusicTrack(
            id=s("id"),uri=s("uri"),title=s("title"),artist=s("artist"),album=s("album"),albumArtist=s("album_artist"),
            genre=s("genre"),durationMs=n("duration_ms"),trackNumber=n("track_number").toInt(),year=n("year").toInt(),
            mimeType=s("mime_type"),sizeBytes=n("size_bytes"),modifiedAt=n("modified_at"),folder=s("folder"),
            artworkUri=s("artwork_uri"),addedAt=n("added_at"),isFavorite=n("is_favorite")==1L,playCount=n("play_count").toInt(),
            lastPlayedAt=if(isNull(last)) null else getLong(last),bitrate=n("bitrate").toInt(),sampleRate=n("sample_rate").toInt(),
            channelCount=n("channel_count").toInt(),discNumber=n("disc_number").toInt(),contentHash=s("content_hash"),
            duplicateOf=s("duplicate_of"),sourceUri=s("source_uri"),isAvailable=n("is_available")==1L
        )
    }
}

class MusicLibraryRepository(context:Context) {
    private val app=context.applicationContext
    private val db=MusicLibraryDatabase(app)
    private val artworkDir=java.io.File(app.filesDir,"music-artwork")

    suspend fun importTree(treeUri:Uri,onProgress:(LibraryScanProgress)->Unit={}):LibraryScanResult = withContext(Dispatchers.IO) {
        runCatching { app.contentResolver.takePersistableUriPermission(treeUri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val root=DocumentFile.fromTreeUri(app,treeUri) ?: throw IllegalArgumentException("Android could not open the selected folder.")
        if(!hasPersistedReadPermission(treeUri)) throw SecurityException("Folder permission is not persisted. Re-select the folder to grant access.")
        if(!root.canRead()) throw SecurityException("Soundprint cannot read this folder. Re-select it to grant access.")
        scanRoot(root,treeUri.toString(),onProgress)
    }

    suspend fun importFiles(fileUris:List<Uri>,onProgress:(LibraryScanProgress)->Unit={}):LibraryScanResult = withContext(Dispatchers.IO) {
        val errors=mutableListOf<LibraryScanError>();var visited=0;var imported=0;var updated=0;var skipped=0
        fileUris.distinctBy { it.toString() }.forEach { uri ->
            coroutineContext.ensureActive();visited++
            try {
                runCatching { app.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                if(!hasPersistedReadPermission(uri)) throw SecurityException("Android did not grant persistent access to this audio file.")
                val file=DocumentFile.fromSingleUri(app,uri)
                if(file==null || !file.isFile || !isSupported(file)) skipped++
                else {
                    val track=readMetadata(file,uri.toString())
                    val canonical=track.contentHash?.let { db.findCanonicalByHash(it,track.uri) }
                    if(db.upsert(track.copy(duplicateOf=canonical))) imported++ else updated++
                    db.saveFileSource(uri.toString(),file.name ?: "Audio file")
                }
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) {
                skipped++;val item=LibraryScanError(uri.toString(),error.message ?: "Could not import this file.")
                errors+=item;db.recordScanError(null,item.uri,item.message)
            }
            onProgress(LibraryScanProgress("Selected audio files",visited,imported,skipped,errors.size))
        }
        LibraryScanResult(visited,imported,updated,skipped,0,errors)
    }

    suspend fun rescanSavedFolders(onProgress:(LibraryScanProgress)->Unit={}):LibraryScanResult = withContext(Dispatchers.IO) {
        val total=MutableScanResult()
        db.sources().forEach { source ->
            coroutineContext.ensureActive()
            try {
                val sourceUri=Uri.parse(source.uri)
                val root=if(hasPersistedReadPermission(sourceUri)) DocumentFile.fromTreeUri(app,sourceUri) else null
                if(root==null || !root.canRead()) {
                    db.markSourceMissing(source.uri);total.unavailable++
                    val message="Folder is unavailable or its permission was revoked."
                    total.errors+=LibraryScanError(source.uri,message);db.recordScanError(source.uri,null,message)
                } else total.add(scanRoot(root,source.uri,onProgress))
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) {
                val message=error.message ?: "Folder scan failed."
                total.errors+=LibraryScanError(source.uri,message);db.recordScanError(source.uri,null,message)
            }
        }
        total.result()
    }

    suspend fun rescanSavedFiles(onProgress:(LibraryScanProgress)->Unit={}):LibraryScanResult = withContext(Dispatchers.IO) {
        val errors=mutableListOf<LibraryScanError>();var visited=0;var imported=0;var updated=0;var unavailable=0
        db.fileSources().forEach { (rawUri,name) ->
            coroutineContext.ensureActive()
            val uri=Uri.parse(rawUri)
            if(!hasPersistedReadPermission(uri)) {
                db.markSourceMissing(rawUri);unavailable++
                val message="Permission for this audio file is no longer available."
                errors+=LibraryScanError(rawUri,message);db.recordScanError(rawUri,rawUri,message)
            } else try {
                val file=DocumentFile.fromSingleUri(app,uri)
                if(file==null || !file.exists() || !file.canRead()) { db.markSourceMissing(rawUri);unavailable++ }
                else {
                    val track=readMetadata(file,rawUri)
                    val canonical=track.contentHash?.let { db.findCanonicalByHash(it,track.uri) }
                    if(db.upsert(track.copy(duplicateOf=canonical))) imported++ else updated++
                }
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) {
                val message=error.message ?: "Could not rescan this audio file."
                errors+=LibraryScanError(rawUri,message);db.recordScanError(rawUri,rawUri,message)
            }
            visited++;onProgress(LibraryScanProgress(name,visited,imported,0,errors.size))
        }
        LibraryScanResult(visited,imported,updated,0,unavailable,errors)
    }

    suspend fun rescanAllSavedSources(onProgress:(LibraryScanProgress)->Unit={}):LibraryScanResult {
        val folders=rescanSavedFolders(onProgress);val files=rescanSavedFiles(onProgress)
        return LibraryScanResult(folders.visited+files.visited,folders.imported+files.imported,
            folders.updated+files.updated,folders.skipped+files.skipped,folders.unavailable+files.unavailable,
            folders.errors+files.errors)
    }

    private fun hasPersistedReadPermission(uri:Uri):Boolean =
        app.contentResolver.persistedUriPermissions.any { it.uri==uri && it.isReadPermission }

    private suspend fun scanRoot(root:DocumentFile,sourceUri:String,onProgress:(LibraryScanProgress)->Unit):LibraryScanResult {
        val scanId=UUID.randomUUID().toString();val errors=mutableListOf<LibraryScanError>()
        var visited=0;var imported=0;var updated=0;var skipped=0
        var traversalFailed=false
        val stack=ArrayDeque<DocumentFile>();stack.add(root)
        while(stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val dir=stack.removeLast()
            val children=try { dir.listFiles().toList() }
            catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) {
                val message=error.message ?: "Cannot read this folder."
                errors+=LibraryScanError(dir.uri.toString(),message);db.recordScanError(sourceUri,dir.uri.toString(),message)
                traversalFailed=true
                continue
            }
            children.forEach { child ->
                coroutineContext.ensureActive()
                if(child.isDirectory) stack.add(child)
                else if(child.isFile) {
                    visited++
                    try {
                        if(!isSupported(child)) skipped++
                        else {
                            val track=readMetadata(child,sourceUri)
                            val canonical=track.contentHash?.let { db.findCanonicalByHash(it,track.uri) }
                            if(db.upsert(track.copy(duplicateOf=canonical),scanId)) imported++ else updated++
                        }
                    } catch(cancel:CancellationException) { throw cancel }
                    catch(error:Exception) {
                        skipped++;val message=error.message ?: "Could not read audio metadata."
                        errors+=LibraryScanError(child.uri.toString(),message);db.recordScanError(sourceUri,child.uri.toString(),message)
                        db.findByUri(child.uri.toString())?.let { existing -> db.recordTrackSource(existing.id,sourceUri,existing.uri,scanId,true) }
                    }
                    if(visited%20==0) onProgress(LibraryScanProgress(root.name ?: "Music folder",visited,imported,skipped,errors.size))
                }
            }
        }
        db.saveSource(sourceUri,root.name ?: "Music folder",scanId)
        val unavailable=db.completeSourceScan(sourceUri,scanId,!traversalFailed && errors.isEmpty())
        onProgress(LibraryScanProgress(root.name ?: "Music folder",visited,imported,skipped,errors.size))
        return LibraryScanResult(visited,imported,updated,skipped,unavailable,errors)
    }

    private fun isSupported(file:DocumentFile):Boolean {
        val mime=(file.type ?: app.contentResolver.getType(file.uri)).orEmpty().lowercase(Locale.ROOT)
        val ext=file.name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        return mime.startsWith("audio/") || ext in AUDIO_EXTENSIONS
    }

    private suspend fun readMetadata(file:DocumentFile,sourceUri:String?):MusicTrack {
        val uri=file.uri;val retriever=MediaMetadataRetriever()
        val old=db.findByUri(uri.toString())
        var title=old?.title ?: file.name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Unknown title"
        var artist=old?.artist ?: "Unknown artist";var album=old?.album ?: "Unknown album"
        var albumArtist=old?.albumArtist ?: "";var genre=old?.genre ?: ""
        var duration=old?.durationMs ?: 0L;var trackNumber=old?.trackNumber ?: 0
        var discNumber=old?.discNumber ?: 0;var year=old?.year ?: 0;var bitrate=old?.bitrate ?: 0
        var embeddedArt:ByteArray?=null;var sampleRate=0;var channels=0
        try {
            retriever.setDataSource(app,uri)
            fun value(key:Int)=retriever.extractMetadata(key)
            title=value(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: title
            artist=value(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: artist
            album=value(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() } ?: album
            albumArtist=value(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.takeIf { it.isNotBlank() } ?: albumArtist
            genre=value(MediaMetadataRetriever.METADATA_KEY_GENRE)?.takeIf { it.isNotBlank() } ?: genre
            duration=value(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: duration
            val trackTag=value(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER).orEmpty()
            trackNumber=trackTag.substringBefore('/').toIntOrNull() ?: trackNumber
            discNumber=value(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.toIntOrNull()
                ?: trackTag.substringAfter('/',"").toIntOrNull() ?: discNumber
            year=value(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull() ?: year
            bitrate=value(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            embeddedArt=retriever.embeddedPicture
        } catch(_:Exception) {
            // Preserve filename fallbacks; one malformed file must not abort a folder scan.
        } finally { runCatching { retriever.release() } }

        val extractor=MediaExtractor()
        try {
            extractor.setDataSource(app,uri,null)
            for(index in 0 until extractor.trackCount) {
                val format=extractor.getTrackFormat(index)
                if(format.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                    if(format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if(format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if(format.containsKey(MediaFormat.KEY_BIT_RATE) && bitrate==0) bitrate=format.getInteger(MediaFormat.KEY_BIT_RATE)
                    break
                }
            }
        } catch(_:Exception) {
            // MediaExtractor is not supported by every provider/codec; basic metadata remains usable.
        } finally { runCatching { extractor.release() } }

        val mime=file.type ?: app.contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.name?.substringAfterLast('.')?.lowercase(Locale.ROOT).orEmpty())
            ?: "application/octet-stream"
        val hash=if(old!=null && old.sizeBytes==file.length().coerceAtLeast(0) && old.modifiedAt==file.lastModified().coerceAtLeast(0)) old.contentHash else hashContent(uri)
        val artwork=embeddedArt?.let { cacheArtwork(it,uri.toString()) } ?: old?.artworkUri
        return MusicTrack(
            id=old?.id ?: UUID.nameUUIDFromBytes(uri.toString().toByteArray()).toString(),
            uri=uri.toString(),title=title.trim().ifBlank { "Unknown title" },artist=artist.trim().ifBlank { "Unknown artist" },
            album=album.trim().ifBlank { "Unknown album" },albumArtist=albumArtist.trim(),genre=genre.trim(),
            durationMs=duration.coerceAtLeast(0),trackNumber=trackNumber.coerceAtLeast(0),year=year.coerceAtLeast(0),
            mimeType=mime,sizeBytes=file.length().coerceAtLeast(0),modifiedAt=file.lastModified().coerceAtLeast(0),
            folder=file.parentFile?.name.orEmpty(),artworkUri=artwork,addedAt=old?.addedAt ?: System.currentTimeMillis(),
            isFavorite=old?.isFavorite ?: false,playCount=old?.playCount ?: 0,lastPlayedAt=old?.lastPlayedAt,
            bitrate=bitrate.coerceAtLeast(0),sampleRate=sampleRate.coerceAtLeast(0),channelCount=channels.coerceAtLeast(0),
            discNumber=discNumber.coerceAtLeast(0),contentHash=hash,sourceUri=sourceUri ?: old?.sourceUri,isAvailable=true
        )
    }

    private suspend fun hashContent(uri:Uri):String? {
        val digest=MessageDigest.getInstance("SHA-256")
        return try {
            val stream:InputStream=app.contentResolver.openInputStream(uri) ?: return null
            stream.use { input ->
                val buffer=ByteArray(64*1024)
                while(true) { coroutineContext.ensureActive();val read=input.read(buffer);if(read<0) break;digest.update(buffer,0,read) }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { null }
    }

    private fun cacheArtwork(bytes:ByteArray,uri:String):String? = runCatching {
        if(!artworkDir.exists()) artworkDir.mkdirs()
        val key=MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
        val target=java.io.File(artworkDir,"${key}.jpg")
        if(!target.exists()) target.writeBytes(bytes)
        target.absolutePath
    }.getOrNull()

    fun tracks(query:String="",sort:TrackSort=TrackSort.TITLE,ascending:Boolean=true)=db.tracks(query,sort,ascending)
    fun favorites()=db.tracks(favoritesOnly=true)
    fun count()=db.count()
    fun playlists()=db.playlists()
    fun albums()=db.albums()
    fun artists()=db.artists()
    fun recentlyPlayed(limit:Int=50)=db.recentlyPlayed(limit)
    fun track(id:String)=db.findById(id)
    fun updateTrack(track:MusicTrack)=db.updateTrack(track)
    fun resetPlayStats(id:String)=db.resetPlayStats(id)
    fun toggleFavorite(id:String)=db.toggleFavorite(id)
    fun setFavorite(id:String,favorite:Boolean)=db.setFavorite(id,favorite)
    fun markPlayed(id:String)=db.markPlayed(id)
    fun createPlaylist(name:String)=db.createPlaylist(name)
    fun addToPlaylist(playlistId:String,trackId:String)=db.addToPlaylist(playlistId,trackId)
    fun moveToTwinSenseTrash(id:String)=db.moveToTrash(id,"soundprint")
    fun restoreFromTwinSenseTrash(id:String)=db.restoreFromTrash(id)
    fun pruneExpiredTrash()=db.pruneTrash()
    fun sources()=db.sources()
    fun fileSources()=db.fileSources()
    fun allSources():List<MusicLibrarySource> = sources() + fileSources().map { MusicLibrarySource(it.first,it.second,0L) }
    fun removeSource(sourceUri:String):Boolean {
        val uri=Uri.parse(sourceUri)
        val released=runCatching {
            val permission=app.contentResolver.persistedUriPermissions.firstOrNull { it.uri==uri && it.isReadPermission }
            if(permission!=null) app.contentResolver.releasePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        }.getOrDefault(false)
        db.removeSource(sourceUri)
        return released
    }
    fun scanErrors(limit:Int=100)=db.scanErrors(limit)
    fun clearLibrary() {
        (db.sources().map { it.uri } + db.fileSources().map { it.first }).distinct().forEach { raw ->
            val uri=Uri.parse(raw)
            runCatching {
                val permission=app.contentResolver.persistedUriPermissions.firstOrNull { it.uri==uri && it.isReadPermission }
                if(permission!=null) app.contentResolver.releasePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        db.clearLibrary()
    }
    fun writeBackup(output:OutputStream)=db.writeBackup(output)
    fun restoreBackup(input:InputStream,replaceExisting:Boolean=false)=db.restoreBackup(input,replaceExisting)

    fun removeTrackRecord(id:String)=db.removeTrackRecord(id)
    fun saveAnalysis(fingerprint:String,profileJson:String,size:Long,mime:String)=db.saveAnalysis(fingerprint,profileJson,size,mime)
    fun saveTwinSenseFingerprint(fingerprint:TwinSenseFingerprint)=db.saveTwinSenseFingerprint(fingerprint)
    fun twinSenseFingerprint(trackId:String,size:Long,modifiedAt:Long)=db.twinSenseFingerprint(trackId,size,modifiedAt)
    fun deleteTwinSenseFingerprint(trackId:String)=db.deleteTwinSenseFingerprint(trackId)
    fun cachedAnalysis(fingerprint:String)=db.analysis(fingerprint)
    fun saveClapMoodProfile(track:MusicTrack,modelVersion:String,scores:Map<String,Float>) {
        require(scores.isNotEmpty() && scores.values.all { it.isFinite() && it in 0f..1f }) { "CLAP returned invalid mood scores." }
        val contentHash=track.contentHash.orEmpty()
        val payload=JSONObject().put("trackId",track.id).put("sourceSize",track.sizeBytes)
            .put("sourceModifiedAt",track.modifiedAt).put("contentHash",contentHash).put("modelVersion",modelVersion)
            .put("analyzedAt",System.currentTimeMillis()).put("scores",JSONObject().apply { scores.forEach { (key,value) -> put(key,value.toDouble()) } })
        db.saveAnalysis("clap:${track.id}:${track.sizeBytes}:${track.modifiedAt}:$contentHash:${track.mimeType}:$modelVersion",payload.toString(),track.sizeBytes,track.mimeType)
    }
    fun clapMoodProfile(track:MusicTrack,modelVersion:String):ClapMoodProfile? {
        val key="clap:${track.id}:${track.sizeBytes}:${track.modifiedAt}:${track.contentHash.orEmpty()}:${track.mimeType}:$modelVersion"
        val raw=db.analysis(key) ?: return null
        return runCatching {
            val root=JSONObject(raw);val scores=root.getJSONObject("scores")
            ClapMoodProfile(track.id,root.getLong("sourceSize"),root.getLong("sourceModifiedAt"),
                root.getString("modelVersion"),root.getLong("analyzedAt"),
                scores.keys().asSequence().associateWith { scores.getDouble(it).toFloat() })
        }.getOrNull()
    }
    fun clearClapMoodProfile(trackId:String) {
        db.deleteAnalysisForTrack(trackId)
    }

    private class MutableScanResult {
        var visited=0;var imported=0;var updated=0;var skipped=0;var unavailable=0
        val errors=mutableListOf<LibraryScanError>()
        fun add(r:LibraryScanResult) { visited+=r.visited;imported+=r.imported;updated+=r.updated;skipped+=r.skipped;unavailable+=r.unavailable;errors+=r.errors }
        fun result()=LibraryScanResult(visited,imported,updated,skipped,unavailable,errors)
    }
    companion object {
        private val AUDIO_EXTENSIONS=setOf("mp3","m4a","aac","flac","wav","ogg","opus","wma","aiff","aif","alac","3gp","amr")
    }
}
