package digital.anchorstudio.cadenceplays

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/** Android-native offline library. Original audio stays in its selected document tree. */
data class MusicTrack(
    val id: String, val uri: String, val title: String, val artist: String,
    val album: String, val albumArtist: String, val genre: String,
    val durationMs: Long, val trackNumber: Int, val year: Int,
    val mimeType: String, val sizeBytes: Long, val modifiedAt: Long,
    val folder: String, val artworkUri: String?, val addedAt: Long,
    val isFavorite: Boolean, val playCount: Int, val lastPlayedAt: Long?
)

data class MusicPlaylist(val id: String, val name: String, val trackIds: List<String>)

class MusicLibraryDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "cadence_music_library.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE tracks(
            id TEXT PRIMARY KEY, uri TEXT NOT NULL UNIQUE, title TEXT NOT NULL, artist TEXT NOT NULL,
            album TEXT NOT NULL, album_artist TEXT NOT NULL, genre TEXT NOT NULL, duration_ms INTEGER NOT NULL,
            track_number INTEGER NOT NULL, year INTEGER NOT NULL, mime_type TEXT NOT NULL, size_bytes INTEGER NOT NULL,
            modified_at INTEGER NOT NULL, folder TEXT NOT NULL, artwork_uri TEXT, added_at INTEGER NOT NULL,
            is_favorite INTEGER NOT NULL DEFAULT 0, play_count INTEGER NOT NULL DEFAULT 0, last_played_at INTEGER)""")
        db.execSQL("CREATE INDEX tracks_title_idx ON tracks(title COLLATE NOCASE)")
        db.execSQL("CREATE INDEX tracks_artist_idx ON tracks(artist COLLATE NOCASE)")
        db.execSQL("CREATE INDEX tracks_album_idx ON tracks(album COLLATE NOCASE)")
        db.execSQL("CREATE TABLE playlists(id TEXT PRIMARY KEY, name TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE playlist_tracks(playlist_id TEXT NOT NULL, track_id TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlist_id,track_id))")
        db.execSQL("CREATE TABLE soundprint_trash(track_id TEXT PRIMARY KEY, removed_at INTEGER NOT NULL, reason TEXT NOT NULL)")
        db.execSQL("CREATE TABLE audio_analysis_cache(fingerprint TEXT PRIMARY KEY, profile_json TEXT NOT NULL, source_size INTEGER NOT NULL, source_mime TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE library_sources(uri TEXT PRIMARY KEY, display_name TEXT NOT NULL, last_scanned_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema changes must use additive migrations; never drop a user's library.
    }

    fun upsert(t: MusicTrack) {
        val v = ContentValues().apply {
            put("id",t.id); put("uri",t.uri); put("title",t.title); put("artist",t.artist)
            put("album",t.album); put("album_artist",t.albumArtist); put("genre",t.genre)
            put("duration_ms",t.durationMs); put("track_number",t.trackNumber); put("year",t.year)
            put("mime_type",t.mimeType); put("size_bytes",t.sizeBytes); put("modified_at",t.modifiedAt)
            put("folder",t.folder); put("artwork_uri",t.artworkUri); put("added_at",t.addedAt)
            put("is_favorite",if(t.isFavorite) 1 else 0); put("play_count",t.playCount); put("last_played_at",t.lastPlayedAt)
        }
        writableDatabase.insertWithOnConflict("tracks",null,v,SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun tracks(): List<MusicTrack> = readableDatabase.rawQuery(
        "SELECT * FROM tracks WHERE id NOT IN(SELECT track_id FROM soundprint_trash) ORDER BY title COLLATE NOCASE",null
    ).use { c -> buildList { while(c.moveToNext()) add(c.toMusicTrack()) } }
    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM tracks WHERE id NOT IN(SELECT track_id FROM soundprint_trash)",null).use { if(it.moveToFirst()) it.getInt(0) else 0 }
    fun toggleFavorite(id:String):Boolean {
        writableDatabase.execSQL("UPDATE tracks SET is_favorite=CASE is_favorite WHEN 1 THEN 0 ELSE 1 END WHERE id=?",arrayOf(id))
        return readableDatabase.rawQuery("SELECT is_favorite FROM tracks WHERE id=?",arrayOf(id)).use { it.moveToFirst() && it.getInt(0)==1 }
    }
    fun markPlayed(id:String) { writableDatabase.execSQL("UPDATE tracks SET play_count=play_count+1,last_played_at=? WHERE id=?",arrayOf(System.currentTimeMillis(),id)) }
    fun createPlaylist(name:String):String {
        val id=UUID.randomUUID().toString(); val now=System.currentTimeMillis()
        writableDatabase.insertOrThrow("playlists",null,ContentValues().apply { put("id",id);put("name",name.trim());put("created_at",now);put("updated_at",now) })
        return id
    }
    fun addToPlaylist(playlistId:String,trackId:String) {
        val pos=readableDatabase.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM playlist_tracks WHERE playlist_id=?",arrayOf(playlistId)).use { if(it.moveToFirst()) it.getInt(0) else 0 }
        writableDatabase.insertWithOnConflict("playlist_tracks",null,ContentValues().apply { put("playlist_id",playlistId);put("track_id",trackId);put("position",pos) },SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun playlists():List<MusicPlaylist> = readableDatabase.rawQuery("SELECT id,name FROM playlists ORDER BY name COLLATE NOCASE",null).use { c ->
        buildList { while(c.moveToNext()) { val id=c.getString(0); val name=c.getString(1)
            val ids=readableDatabase.rawQuery("SELECT track_id FROM playlist_tracks WHERE playlist_id=? ORDER BY position",arrayOf(id)).use { q -> buildList { while(q.moveToNext()) add(q.getString(0)) } }
            add(MusicPlaylist(id,name,ids))
        } }
    }
    fun moveToTrash(id:String) { writableDatabase.insertWithOnConflict("soundprint_trash",null,ContentValues().apply { put("track_id",id);put("removed_at",System.currentTimeMillis());put("reason","soundprint") },SQLiteDatabase.CONFLICT_REPLACE) }
    fun restoreFromTrash(id:String) { writableDatabase.delete("soundprint_trash","track_id=?",arrayOf(id)) }
    fun pruneTrash() { writableDatabase.delete("soundprint_trash","removed_at < ?",arrayOf((System.currentTimeMillis()-30L*86400000).toString())) }
    fun saveSource(uri:String,name:String) { writableDatabase.insertWithOnConflict("library_sources",null,ContentValues().apply { put("uri",uri);put("display_name",name);put("last_scanned_at",System.currentTimeMillis()) },SQLiteDatabase.CONFLICT_REPLACE) }
    fun sources():List<String> = readableDatabase.rawQuery("SELECT uri FROM library_sources",null).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }

    private fun android.database.Cursor.toMusicTrack() = MusicTrack(
        getString(getColumnIndexOrThrow("id")),getString(getColumnIndexOrThrow("uri")),
        getString(getColumnIndexOrThrow("title")),getString(getColumnIndexOrThrow("artist")),
        getString(getColumnIndexOrThrow("album")),getString(getColumnIndexOrThrow("album_artist")),
        getString(getColumnIndexOrThrow("genre")),getLong(getColumnIndexOrThrow("duration_ms")),
        getInt(getColumnIndexOrThrow("track_number")),getInt(getColumnIndexOrThrow("year")),
        getString(getColumnIndexOrThrow("mime_type")),getLong(getColumnIndexOrThrow("size_bytes")),
        getLong(getColumnIndexOrThrow("modified_at")),getString(getColumnIndexOrThrow("folder")),
        getString(getColumnIndexOrThrow("artwork_uri")),getLong(getColumnIndexOrThrow("added_at")),
        getInt(getColumnIndexOrThrow("is_favorite"))==1,getInt(getColumnIndexOrThrow("play_count")),
        getColumnIndex("last_played_at").let { if(isNull(it)) null else getLong(it) }
    )
}

class MusicLibraryRepository(context:Context) {
    private val app=context.applicationContext
    private val db=MusicLibraryDatabase(app)

    suspend fun importTree(treeUri:Uri):Int = withContext(Dispatchers.IO) {
        val root=DocumentFile.fromTreeUri(app,treeUri) ?: error("Android could not open the selected folder.")
        if(!root.canRead()) throw SecurityException("Cadence cannot read this folder.")
        var added=0
        scan(root) { added++ }
        db.saveSource(treeUri.toString(),root.name ?: "Music folder")
        added
    }
    suspend fun rescanSavedFolders():Int = withContext(Dispatchers.IO) {
        var added=0
        db.sources().forEach { saved -> runCatching {
            DocumentFile.fromTreeUri(app,Uri.parse(saved))?.takeIf { it.canRead() }?.let { scan(it) { added++ } }
        } }
        added
    }
    private fun scan(dir:DocumentFile,onImported:()->Unit) {
        dir.listFiles().forEach { f ->
            if(f.isDirectory) scan(f,onImported)
            else if(f.isFile && supported(f)) runCatching { db.upsert(readMetadata(f));onImported() }
        }
    }
    private fun supported(f:DocumentFile):Boolean {
        val mime=f.type?.lowercase(Locale.ROOT).orEmpty()
        val ext=f.name?.substringAfterLast('.',"")?.lowercase(Locale.ROOT).orEmpty()
        return mime.startsWith("audio/") || ext in setOf("mp3","m4a","aac","flac","wav","ogg","opus","wma","aiff","aif","alac")
    }
    private fun readMetadata(f:DocumentFile):MusicTrack {
        val uri=f.uri
        val r=MediaMetadataRetriever()
        var title=f.name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Unknown title"
        var artist="Unknown artist";var album="Unknown album";var albumArtist="";var genre=""
        var duration=0L;var number=0;var year=0;var art:String?=null
        try {
            r.setDataSource(app,uri)
            title=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: title
            artist=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: artist
            album=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() } ?: album
            albumArtist=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).orEmpty()
            genre=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE).orEmpty()
            duration=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            number=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.toIntOrNull() ?: 0
            year=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull() ?: 0
            if(r.embeddedPicture!=null) art="embedded:"+uri.toString()
        } finally { runCatching { r.release() } }
        val mime=f.type ?: app.contentResolver.getType(uri) ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.name?.substringAfterLast('.')?.lowercase(Locale.ROOT).orEmpty()) ?: "application/octet-stream"
        return MusicTrack(uri.toString(),uri.toString(),title.trim(),artist.trim(),album.trim(),albumArtist.trim(),genre.trim(),
            duration,number,year,mime,f.length().coerceAtLeast(0),f.lastModified().coerceAtLeast(0),
            f.parentFile?.name.orEmpty(),art,System.currentTimeMillis(),false,0,null)
    }
    fun tracks()=db.tracks()
    fun count()=db.count()
    fun playlists()=db.playlists()
    fun toggleFavorite(id:String)=db.toggleFavorite(id)
    fun markPlayed(id:String)=db.markPlayed(id)
    fun createPlaylist(name:String)=db.createPlaylist(name)
    fun addToPlaylist(playlistId:String,trackId:String)=db.addToPlaylist(playlistId,trackId)
    fun moveToSoundprintTrash(id:String)=db.moveToTrash(id)
    fun restoreFromSoundprintTrash(id:String)=db.restoreFromTrash(id)
    fun pruneExpiredTrash()=db.pruneTrash()
}
