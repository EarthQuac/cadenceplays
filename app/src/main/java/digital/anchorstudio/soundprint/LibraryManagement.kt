package digital.anchorstudio.soundprint

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class HistoryEventType { PLAY, SKIP, LISTEN }
data class LibraryHistoryEvent(val id:Long,val trackId:String,val type:HistoryEventType,val timestamp:Long,val positionMs:Long,val durationMs:Long)
data class LibraryListeningStats(val trackId:String,val plays:Int,val skips:Int,val lastPlayedAt:Long?,val totalListenedMs:Long)
data class ManagedPlaylist(val id:String,val name:String,val trackIds:List<String>,val createdAt:Long,val updatedAt:Long)
data class RecycleBinEntry(val track:MusicTrack,val removedAt:Long,val reason:String) {
    val expiresAt:Long get()=removedAt+RECYCLE_BIN_RETENTION_MS
}
data class LibraryAlbum(val id:String,val name:String,val artist:String,val tracks:List<MusicTrack>,val artworkUri:String?,val durationMs:Long)
data class LibraryArtist(val name:String,val tracks:List<MusicTrack>,val albums:Int)
data class LibraryCategory(val name:String,val tracks:List<MusicTrack>)
data class AudioRuleProfile(val energy:Double?=null,val tempo:Double?=null,val moodLabel:String?=null,val hasLyrics:Boolean?=null)

enum class SmartField { PLAY_COUNT,LAST_PLAYED_DAYS_AGO,NEVER_PLAYED,SKIP_COUNT,USUAL_HOUR,DATE_ADDED,GENRE,ARTIST,ALBUM,FOLDER,YEAR,DURATION,FAVORITE,HAS_LYRICS,MOOD_LABEL,ENERGY,TEMPO }
enum class SmartOperator { EQ,NEQ,CONTAINS,NOT_CONTAINS,GT,GTE,LT,LTE }
enum class SmartJoin { AND,OR }
enum class SmartSortField { TITLE,ARTIST,ALBUM,PLAY_COUNT,LAST_PLAYED_DAYS_AGO,SKIP_COUNT,DURATION,DATE_ADDED,TEMPO,ENERGY }
data class SmartCondition(val id:String=UUID.randomUUID().toString(),val field:SmartField,val operator:SmartOperator,val value:String)
data class SmartConditionGroup(val id:String=UUID.randomUUID().toString(),val join:SmartJoin=SmartJoin.AND,val conditions:List<SmartCondition>)
data class NativeSmartPlaylist(
    val id:String=UUID.randomUUID().toString(),val name:String,val groupsJoin:SmartJoin=SmartJoin.AND,
    val groups:List<SmartConditionGroup>,val sortField:SmartSortField=SmartSortField.PLAY_COUNT,
    val ascending:Boolean=false,val limit:Int?=null,val preset:Boolean=false
)
data class SmartPlaylistContext(
    val tracks:List<MusicTrack>,val stats:Map<String,LibraryListeningStats>,
    val history:List<LibraryHistoryEvent> = emptyList(),
    val audioProfiles:Map<String,AudioRuleProfile> = emptyMap(),val now:Long=System.currentTimeMillis()
)

private const val RECYCLE_BIN_RETENTION_MS=30L*24L*60L*60L*1000L

private class LibraryManagementDatabase(context:Context):SQLiteOpenHelper(context.applicationContext,"soundprint_library_management.db",null,1) {
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE history_events(id INTEGER PRIMARY KEY AUTOINCREMENT,track_id TEXT NOT NULL,event_type TEXT NOT NULL,timestamp INTEGER NOT NULL,position_ms INTEGER NOT NULL DEFAULT 0,duration_ms INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX history_track_time_idx ON history_events(track_id,timestamp DESC)")
        db.execSQL("CREATE INDEX history_time_idx ON history_events(timestamp DESC)")
        db.execSQL("CREATE TABLE managed_playlists(id TEXT PRIMARY KEY,name TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE managed_playlist_tracks(playlist_id TEXT NOT NULL,track_id TEXT NOT NULL,position INTEGER NOT NULL,PRIMARY KEY(playlist_id,track_id))")
        db.execSQL("CREATE INDEX managed_playlist_order_idx ON managed_playlist_tracks(playlist_id,position)")
        db.execSQL("CREATE TABLE smart_playlists(id TEXT PRIMARY KEY,name TEXT NOT NULL,definition_json TEXT NOT NULL,preset INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE recycle_bin(track_id TEXT PRIMARY KEY,removed_at INTEGER NOT NULL,reason TEXT NOT NULL)")
        db.execSQL("CREATE INDEX recycle_bin_time_idx ON recycle_bin(removed_at DESC)")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {}

    fun addHistory(trackId:String,type:HistoryEventType,positionMs:Long,durationMs:Long,timestamp:Long=System.currentTimeMillis()) {
        writableDatabase.insertOrThrow("history_events",null,ContentValues().apply {
            put("track_id",trackId);put("event_type",type.name);put("timestamp",timestamp)
            put("position_ms",positionMs.coerceAtLeast(0));put("duration_ms",durationMs.coerceAtLeast(0))
        })
    }
    fun history(limit:Int=100,trackId:String?=null):List<LibraryHistoryEvent> {
        val sql=if(trackId==null) "SELECT * FROM history_events ORDER BY timestamp DESC,id DESC LIMIT ?" else "SELECT * FROM history_events WHERE track_id=? ORDER BY timestamp DESC,id DESC LIMIT ?"
        val args=if(trackId==null) arrayOf(limit.coerceIn(1,10000).toString()) else arrayOf(trackId,limit.coerceIn(1,10000).toString())
        return readableDatabase.rawQuery(sql,args).use { c -> buildList {
            while(c.moveToNext()) add(LibraryHistoryEvent(c.getLong(0),c.getString(1),HistoryEventType.valueOf(c.getString(2)),c.getLong(3),c.getLong(4),c.getLong(5)))
        } }
    }
    fun stats():Map<String,LibraryListeningStats> {
        val result=linkedMapOf<String,LibraryListeningStats>()
        readableDatabase.rawQuery("""SELECT track_id,SUM(CASE WHEN event_type='PLAY' THEN 1 ELSE 0 END),
            SUM(CASE WHEN event_type='SKIP' THEN 1 ELSE 0 END),
            MAX(CASE WHEN event_type='PLAY' THEN timestamp ELSE NULL END),
            SUM(CASE WHEN event_type='LISTEN' THEN position_ms ELSE 0 END)
            FROM history_events GROUP BY track_id""",null).use { c ->
            while(c.moveToNext()) { val id=c.getString(0);result[id]=LibraryListeningStats(id,c.getInt(1),c.getInt(2),if(c.isNull(3)) null else c.getLong(3),c.getLong(4)) }
        }
        return result
    }
    fun clearHistory() { writableDatabase.delete("history_events",null,null) }
    fun removeTrackReferences(trackId:String) {
        val db=writableDatabase;db.beginTransaction()
        try {
            db.delete("history_events","track_id=?",arrayOf(trackId))
            db.delete("managed_playlist_tracks","track_id=?",arrayOf(trackId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun createPlaylist(name:String,ids:List<String>):String {
        require(name.isNotBlank()) { "Playlist name cannot be empty." }
        val id=UUID.randomUUID().toString();val now=System.currentTimeMillis();val db=writableDatabase;db.beginTransaction()
        try {
            db.insertOrThrow("managed_playlists",null,ContentValues().apply { put("id",id);put("name",name.trim());put("created_at",now);put("updated_at",now) })
            insertPlaylistTracks(db,id,ids.distinct());db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return id
    }
    private fun insertPlaylistTracks(db:SQLiteDatabase,id:String,ids:List<String>) {
        ids.forEachIndexed { index,trackId -> db.insertWithOnConflict("managed_playlist_tracks",null,ContentValues().apply {
            put("playlist_id",id);put("track_id",trackId);put("position",index)
        },SQLiteDatabase.CONFLICT_IGNORE) }
    }
    fun playlists():List<ManagedPlaylist> = readableDatabase.rawQuery("SELECT id,name,created_at,updated_at FROM managed_playlists ORDER BY name COLLATE NOCASE",null).use { c ->
        buildList { while(c.moveToNext()) {
            val id=c.getString(0)
            val ids=readableDatabase.rawQuery("SELECT track_id FROM managed_playlist_tracks WHERE playlist_id=? ORDER BY position",arrayOf(id)).use { q -> buildList { while(q.moveToNext()) add(q.getString(0)) } }
            add(ManagedPlaylist(id,c.getString(1),ids,c.getLong(2),c.getLong(3)))
        } }
    }
    fun renamePlaylist(id:String,name:String) {
        require(name.isNotBlank()) { "Playlist name cannot be empty." }
        check(writableDatabase.update("managed_playlists",ContentValues().apply { put("name",name.trim());put("updated_at",System.currentTimeMillis()) },"id=?",arrayOf(id))==1) { "Playlist not found." }
    }
    fun deletePlaylist(id:String) { val db=writableDatabase;db.beginTransaction();try { db.delete("managed_playlist_tracks","playlist_id=?",arrayOf(id));db.delete("managed_playlists","id=?",arrayOf(id));db.setTransactionSuccessful() } finally { db.endTransaction() } }
    fun addToPlaylist(id:String,trackIds:List<String>) {
        val db=writableDatabase;db.beginTransaction()
        try {
            db.rawQuery("SELECT 1 FROM managed_playlists WHERE id=?",arrayOf(id)).use { check(it.moveToFirst()) { "Playlist not found." } }
            val existing=mutableSetOf<String>()
            db.rawQuery("SELECT track_id FROM managed_playlist_tracks WHERE playlist_id=?",arrayOf(id)).use { c -> while(c.moveToNext()) existing+=c.getString(0) }
            var position=db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM managed_playlist_tracks WHERE playlist_id=?",arrayOf(id)).use { if(it.moveToFirst()) it.getInt(0) else 0 }
            trackIds.distinct().filterNot { it in existing }.forEach { trackId -> db.insertOrThrow("managed_playlist_tracks",null,ContentValues().apply { put("playlist_id",id);put("track_id",trackId);put("position",position++) }) }
            db.execSQL("UPDATE managed_playlists SET updated_at=? WHERE id=?",arrayOf(System.currentTimeMillis(),id));db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun removeFromPlaylist(id:String,trackId:String) {
        val db=writableDatabase;db.beginTransaction()
        try { db.delete("managed_playlist_tracks","playlist_id=? AND track_id=?",arrayOf(id,trackId));normalizePositions(db,id);db.execSQL("UPDATE managed_playlists SET updated_at=? WHERE id=?",arrayOf(System.currentTimeMillis(),id));db.setTransactionSuccessful() }
        finally { db.endTransaction() }
    }
    fun reorderPlaylist(id:String,from:Int,to:Int) {
        val db=writableDatabase;db.beginTransaction()
        try {
            val ids=mutableListOf<String>();db.rawQuery("SELECT track_id FROM managed_playlist_tracks WHERE playlist_id=? ORDER BY position",arrayOf(id)).use { c -> while(c.moveToNext()) ids+=c.getString(0) }
            require(from in ids.indices && to in ids.indices) { "Playlist position is out of range." }
            val moved=ids.removeAt(from);ids.add(to,moved);db.delete("managed_playlist_tracks","playlist_id=?",arrayOf(id));insertPlaylistTracks(db,id,ids)
            db.execSQL("UPDATE managed_playlists SET updated_at=? WHERE id=?",arrayOf(System.currentTimeMillis(),id));db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private fun normalizePositions(db:SQLiteDatabase,id:String) {
        val ids=mutableListOf<String>();db.rawQuery("SELECT track_id FROM managed_playlist_tracks WHERE playlist_id=? ORDER BY position",arrayOf(id)).use { c -> while(c.moveToNext()) ids+=c.getString(0) }
        db.delete("managed_playlist_tracks","playlist_id=?",arrayOf(id));insertPlaylistTracks(db,id,ids)
    }

    fun saveSmartPlaylist(playlist:NativeSmartPlaylist) {
        require(playlist.name.isNotBlank() && playlist.groups.isNotEmpty()) { "A smart playlist needs a name and at least one group." }
        require(playlist.groups.all { it.conditions.isNotEmpty() && it.conditions.all { c -> c.value.isNotBlank() } }) { "Every smart playlist condition needs a value." }
        val now=System.currentTimeMillis()
        writableDatabase.insertWithOnConflict("smart_playlists",null,ContentValues().apply {
            put("id",playlist.id);put("name",playlist.name.trim());put("definition_json",playlist.toJson().toString());put("preset",if(playlist.preset) 1 else 0);put("created_at",now);put("updated_at",now)
        },SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun smartPlaylists():List<NativeSmartPlaylist> = readableDatabase.rawQuery("SELECT definition_json FROM smart_playlists ORDER BY name COLLATE NOCASE",null).use { c ->
        buildList { while(c.moveToNext()) add(smartPlaylistFromJson(JSONObject(c.getString(0)))) }
    }
    fun deleteSmartPlaylist(id:String) { writableDatabase.delete("smart_playlists","id=? AND preset=0",arrayOf(id)) }

    fun addRecycleBinEntry(id:String,reason:String) {
        writableDatabase.insertWithOnConflict("recycle_bin",null,ContentValues().apply { put("track_id",id);put("removed_at",System.currentTimeMillis());put("reason",reason) },SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun recycleBin():List<Triple<String,Long,String>> = readableDatabase.rawQuery("SELECT track_id,removed_at,reason FROM recycle_bin ORDER BY removed_at DESC",null).use { c ->
        buildList { while(c.moveToNext()) add(Triple(c.getString(0),c.getLong(1),c.getString(2))) }
    }
    fun removeRecycleBinEntry(id:String) { writableDatabase.delete("recycle_bin","track_id=?",arrayOf(id)) }
    fun purgeRecycleBin(cutoff:Long):List<String> {
        val ids=mutableListOf<String>();val db=writableDatabase;db.beginTransaction()
        try { db.rawQuery("SELECT track_id FROM recycle_bin WHERE removed_at<=?",arrayOf(cutoff.toString())).use { c -> while(c.moveToNext()) ids+=c.getString(0) };ids.forEach { db.delete("recycle_bin","track_id=?",arrayOf(it)) };db.setTransactionSuccessful() }
        finally { db.endTransaction() }
        return ids
    }
}

private fun NativeSmartPlaylist.toJson()=JSONObject().apply {
    put("id",id);put("name",name);put("groupsJoin",groupsJoin.name);put("sortField",sortField.name);put("ascending",ascending);put("limit",limit ?: JSONObject.NULL);put("preset",preset)
    put("groups",JSONArray().apply { groups.forEach { group -> put(JSONObject().apply {
        put("id",group.id);put("join",group.join.name);put("conditions",JSONArray().apply { group.conditions.forEach { c -> put(JSONObject().apply {
            put("id",c.id);put("field",c.field.name);put("operator",c.operator.name);put("value",c.value)
        }) } })
    }) } })
}
private fun smartPlaylistFromJson(json:JSONObject):NativeSmartPlaylist {
    val groupArray=json.optJSONArray("groups") ?: JSONArray()
    val groups=(0 until groupArray.length()).map { i ->
        val group=groupArray.getJSONObject(i);val conditionsArray=group.optJSONArray("conditions") ?: JSONArray()
        val conditions=(0 until conditionsArray.length()).map { j ->
            val c=conditionsArray.getJSONObject(j)
            SmartCondition(c.optString("id",UUID.randomUUID().toString()),SmartField.valueOf(c.getString("field")),SmartOperator.valueOf(c.getString("operator")),c.optString("value"))
        }
        SmartConditionGroup(group.optString("id",UUID.randomUUID().toString()),SmartJoin.valueOf(group.optString("join","AND")),conditions)
    }
    return NativeSmartPlaylist(
        id=json.getString("id"),name=json.getString("name"),groupsJoin=SmartJoin.valueOf(json.optString("groupsJoin","AND")),groups=groups,
        sortField=SmartSortField.valueOf(json.optString("sortField","PLAY_COUNT")),ascending=json.optBoolean("ascending",false),
        limit=if(json.isNull("limit")) null else json.optInt("limit").takeIf { it>0 },preset=json.optBoolean("preset",false)
    )
}

/** Complete native library-management logic. UI screens consume this API; this class has no Compose dependency. */
class MusicLibraryManager(context:Context) {
    private val repository=MusicLibraryRepository(context.applicationContext)
    private val state=LibraryManagementDatabase(context.applicationContext)
    private val clapPreferences=context.applicationContext.getSharedPreferences("soundprint_clap_scan",Context.MODE_PRIVATE)

    fun allSongs(query:String="",sort:TrackSort=TrackSort.TITLE,ascending:Boolean=true)=repository.tracks(query,sort,ascending)
    fun song(id:String)=repository.track(id)
    fun saveClapMoodProfile(track:MusicTrack,scores:Map<String,Float>) =
        repository.saveClapMoodProfile(track,CLAP_MODEL_VERSION,scores)
    fun clapMoodProfile(track:MusicTrack):ClapMoodProfile? =
        repository.clapMoodProfile(track,CLAP_MODEL_VERSION)
    fun clearClapMoodProfile(trackId:String) = repository.clearClapMoodProfile(trackId)
    fun clapMoodProfiles():Map<String,ClapMoodProfile> =
        repository.tracks().mapNotNull { track -> repository.clapMoodProfile(track,CLAP_MODEL_VERSION)?.let { track.id to it } }.toMap()
    fun clapAudioRuleProfiles():Map<String,AudioRuleProfile> {
        val energyWeights=mapOf(
            "workout" to .95,"upbeat" to .78,"chill" to .25,"focus" to .30,"sleep" to .05,
            "driving" to .65,"party" to .95,"happy" to .75,"sad" to .15,"romantic" to .35,
            "late-night" to .20,"angry" to .95,"dreamy" to .20,"peaceful" to .10,
            "melancholy" to .15,"confident" to .75,"nostalgic" to .35,"intense" to .95
        )
        val result=clapMoodProfiles().mapValues { (_,profile) ->
            val total=profile.scores.values.sum().coerceAtLeast(1e-9f)
            val energy=profile.scores.entries.sumOf { (mood,score) -> score.toDouble()*(energyWeights[mood] ?: .5) }/total*100.0
            AudioRuleProfile(energy=energy,moodLabel=profile.primaryMood?.id)
        }.toMutableMap()
        val corrections=runCatching {
            val raw=clapPreferences.getString("mood_corrections",null) ?: return@runCatching emptyMap()
            val json=JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
        corrections.forEach { (trackId,moodId) ->
            result[trackId]=AudioRuleProfile(energy=energyWeights[moodId]?.times(100.0),moodLabel=moodId)
        }
        return result
    }
    fun updateSongMetadata(id:String,title:String,artist:String,album:String,genre:String) {
        val current=repository.track(id) ?: throw IllegalArgumentException("Song not found.")
        repository.updateTrack(current.copy(title=title.trim().ifBlank { current.title },artist=artist.trim().ifBlank { current.artist },album=album.trim().ifBlank { current.album },genre=genre.trim()))
    }

    fun albums():List<LibraryAlbum> = repository.tracks().groupBy { it.album.trim().lowercase()+"::"+it.albumArtist.ifBlank { it.artist }.trim().lowercase() }
        .values.map { group ->
            val ordered=group.sortedWith(compareBy<MusicTrack> { it.discNumber }.thenBy { it.trackNumber }.thenBy { it.title.lowercase() })
            val first=ordered.first()
            LibraryAlbum(first.album+"::"+first.albumArtist.ifBlank { first.artist },first.album,first.albumArtist.ifBlank { first.artist },ordered,ordered.firstNotNullOfOrNull { it.artworkUri },ordered.sumOf { it.durationMs })
        }.sortedWith(compareBy<LibraryAlbum> { it.name.lowercase() }.thenBy { it.artist.lowercase() })
    fun albumTracks(albumId:String)=albums().firstOrNull { it.id==albumId }?.tracks.orEmpty()
    fun artists():List<LibraryArtist> = repository.tracks().groupBy { it.artist.trim().ifBlank { "Unknown artist" } }
        .map { (name,tracks) -> LibraryArtist(name,tracks.sortedBy { it.title.lowercase() },tracks.map { it.album.lowercase() }.distinct().size) }.sortedBy { it.name.lowercase() }
    fun artistTracks(artist:String)=repository.tracks().filter { it.artist.equals(artist,true)||it.albumArtist.equals(artist,true) }
        .sortedWith(compareBy<MusicTrack> { it.album.lowercase() }.thenBy { it.discNumber }.thenBy { it.trackNumber })
    fun genres()=categories { it.genre }
    fun genreTracks(genre:String)=repository.tracks().filter { it.genre.equals(genre,true) }.sortedBy { it.title.lowercase() }
    fun folders()=categories { it.folder }
    fun folderTracks(folder:String)=repository.tracks().filter { it.folder==folder }.sortedBy { it.title.lowercase() }
    private fun categories(key:(MusicTrack)->String):List<LibraryCategory> = repository.tracks().groupBy { key(it).trim().ifBlank { "Unknown" } }
        .map { (name,tracks) -> LibraryCategory(name,tracks.sortedBy { it.title.lowercase() }) }.sortedBy { it.name.lowercase() }

    fun favorites()=repository.favorites()
    fun isFavorite(id:String)=repository.track(id)?.isFavorite==true
    fun toggleFavorite(id:String)=repository.toggleFavorite(id)

    fun recordPlay(id:String,positionMs:Long,durationMs:Long) {
        require(repository.track(id)!=null) { "Song not found." };repository.markPlayed(id);state.addHistory(id,HistoryEventType.PLAY,positionMs,durationMs)
    }
    fun recordSkip(id:String,positionMs:Long,durationMs:Long) {
        require(repository.track(id)!=null) { "Song not found." };state.addHistory(id,HistoryEventType.SKIP,positionMs,durationMs)
    }
    fun recordListeningTime(id:String,listenedMs:Long,durationMs:Long) {
        if(listenedMs<=0L) return
        require(repository.track(id)!=null) { "Song not found." }
        state.addHistory(id,HistoryEventType.LISTEN,listenedMs.coerceAtMost(durationMs.takeIf { it>0L } ?: listenedMs),durationMs)
    }
    fun listeningHistory(limit:Int=100):List<Pair<LibraryHistoryEvent,MusicTrack?>> = state.history(limit).map { it to repository.track(it.trackId) }.filter { it.second!=null }
    fun trackHistory(id:String,limit:Int=100)=state.history(limit,id)
    fun listeningStats()=state.stats()
    fun listeningEvents(limit:Int=10000)=state.history(limit.coerceIn(1,10000))
    fun clearListeningHistory() { state.clearHistory();repository.tracks().forEach { repository.resetPlayStats(it.id) } }

    fun createPlaylist(name:String,trackIds:List<String> = emptyList()):String {
        require(trackIds.distinct().all { repository.track(it)!=null }) { "A selected song no longer exists in the library." }
        return state.createPlaylist(name,trackIds)
    }
    fun playlists()=state.playlists()
    fun renamePlaylist(id:String,name:String)=state.renamePlaylist(id,name)
    fun deletePlaylist(id:String)=state.deletePlaylist(id)
    fun addToPlaylist(id:String,trackIds:List<String>) {
        require(trackIds.all { repository.track(it)!=null }) { "A selected song no longer exists in the library." };state.addToPlaylist(id,trackIds)
    }
    fun removeFromPlaylist(id:String,trackId:String)=state.removeFromPlaylist(id,trackId)
    fun reorderPlaylist(id:String,from:Int,to:Int)=state.reorderPlaylist(id,from,to)
    fun playlistTracks(id:String):List<MusicTrack> {
        val ids=state.playlists().firstOrNull { it.id==id }?.trackIds ?: return emptyList()
        val byId=repository.tracks().associateBy { it.id };return ids.mapNotNull { byId[it] }
    }

    fun saveSmartPlaylist(playlist:NativeSmartPlaylist)=state.saveSmartPlaylist(playlist)
    fun smartPlaylists()=nativeSmartPlaylistPresets()+state.smartPlaylists().filterNot { saved -> nativeSmartPlaylistPresets().any { it.id==saved.id } }
    fun deleteSmartPlaylist(id:String)=state.deleteSmartPlaylist(id)
    fun evaluateSmartPlaylist(id:String,profiles:Map<String,AudioRuleProfile> = emptyMap()):List<MusicTrack> {
        val playlist=smartPlaylists().firstOrNull { it.id==id } ?: return emptyList()
        val resolvedProfiles=clapAudioRuleProfiles()+profiles
        return evaluateNativeSmartPlaylist(playlist,SmartPlaylistContext(repository.tracks(),listeningStats(),state.history(10000),resolvedProfiles))
    }

    fun saveSoundprintFingerprint(fingerprint:SoundprintFingerprint)=repository.saveSoundprintFingerprint(fingerprint)
    fun soundprintFingerprint(trackId:String,size:Long,modifiedAt:Long)=repository.soundprintFingerprint(trackId,size,modifiedAt)
    fun deleteSoundprintFingerprint(trackId:String)=repository.deleteSoundprintFingerprint(trackId)

    fun moveToRecycleBin(id:String,reason:String="user") {
        require(repository.track(id)!=null) { "Song not found." };repository.moveToSoundprintTrash(id);state.addRecycleBinEntry(id,reason)
    }
    fun recycleBin():List<RecycleBinEntry> {
        purgeExpiredRecycleBin()
        return state.recycleBin().mapNotNull { (id,removed,reason) -> repository.track(id)?.let { RecycleBinEntry(it,removed,reason) } }
    }
    fun restoreFromRecycleBin(id:String) {
        check(state.recycleBin().any { it.first==id }) { "Recycle Bin item not found." };repository.restoreFromSoundprintTrash(id);state.removeRecycleBinEntry(id)
    }
    /** Removes Soundprint's record only. The original audio file in Android storage is untouched. */
    fun permanentlyRemoveFromLibrary(id:String) { state.removeRecycleBinEntry(id);state.removeTrackReferences(id);repository.removeTrackRecord(id) }
    fun emptyRecycleBin() { state.recycleBin().map { it.first }.forEach { state.removeTrackReferences(it);repository.removeTrackRecord(it);state.removeRecycleBinEntry(it) } }
    fun purgeExpiredRecycleBin(now:Long=System.currentTimeMillis()):Int {
        val expired=state.purgeRecycleBin(now-RECYCLE_BIN_RETENTION_MS);expired.forEach { state.removeTrackReferences(it);repository.removeTrackRecord(it) };return expired.size
    }
    fun searchSongs(query:String,favoritesOnly:Boolean=false,sort:TrackSort=TrackSort.TITLE,ascending:Boolean=true)=
        repository.tracks(query,sort,ascending).filter { !favoritesOnly||it.isFavorite }
}

fun evaluateNativeSmartPlaylist(playlist:NativeSmartPlaylist,context:SmartPlaylistContext):List<MusicTrack> {
    if(playlist.groups.isEmpty()||playlist.groups.any { it.conditions.isEmpty()||it.conditions.any { c -> c.value.isBlank() } }) return emptyList()
    val usualHours=context.history.filter { it.type==HistoryEventType.PLAY }.groupBy { it.trackId }.mapValues { (_,events) ->
        events.groupingBy { java.util.Calendar.getInstance().apply { timeInMillis=it.timestamp }.get(java.util.Calendar.HOUR_OF_DAY) }.eachCount().maxByOrNull { it.value }?.key ?: -1
    }
    fun value(track:MusicTrack,field:SmartField):Any {
        val stats=context.stats[track.id];val profile=context.audioProfiles[track.id]
        return when(field) {
            SmartField.PLAY_COUNT -> stats?.plays ?: track.playCount
            SmartField.LAST_PLAYED_DAYS_AGO -> stats?.lastPlayedAt?.let { ((context.now-it)/86400000L).toInt() } ?: 999999
            SmartField.NEVER_PLAYED -> (stats?.plays ?: track.playCount)==0
            SmartField.SKIP_COUNT -> stats?.skips ?: 0
            SmartField.USUAL_HOUR -> usualHours[track.id] ?: -1
            SmartField.DATE_ADDED -> ((context.now-track.addedAt)/86400000L).toInt()
            SmartField.GENRE -> track.genre
            SmartField.ARTIST -> track.artist
            SmartField.ALBUM -> track.album
            SmartField.FOLDER -> track.folder
            SmartField.YEAR -> track.year
            SmartField.DURATION -> track.durationMs/1000.0
            SmartField.FAVORITE -> track.isFavorite
            SmartField.HAS_LYRICS -> profile?.hasLyrics ?: false
            SmartField.MOOD_LABEL -> profile?.moodLabel.orEmpty()
            SmartField.ENERGY -> profile?.energy ?: 0.0
            SmartField.TEMPO -> profile?.tempo ?: 0.0
        }
    }
    fun matches(actual:Any,condition:SmartCondition):Boolean {
        val expected=condition.value.trim()
        val numeric=expected.toDoubleOrNull()
        return when(condition.operator) {
            SmartOperator.EQ -> if(actual is Number) numeric!=null&&actual.toDouble()==numeric else actual.toString().equals(expected,true)
            SmartOperator.NEQ -> if(actual is Number) numeric==null||actual.toDouble()!=numeric else !actual.toString().equals(expected,true)
            SmartOperator.CONTAINS -> actual.toString().contains(expected,true)
            SmartOperator.NOT_CONTAINS -> !actual.toString().contains(expected,true)
            SmartOperator.GT -> actual is Number&&numeric!=null&&actual.toDouble()>numeric
            SmartOperator.GTE -> actual is Number&&numeric!=null&&actual.toDouble()>=numeric
            SmartOperator.LT -> actual is Number&&numeric!=null&&actual.toDouble()<numeric
            SmartOperator.LTE -> actual is Number&&numeric!=null&&actual.toDouble()<=numeric
        }
    }
    val filtered=context.tracks.filter { track ->
        val groups=playlist.groups.map { group ->
            val outcomes=group.conditions.map { matches(value(track,it.field),it) }
            if(group.join==SmartJoin.AND) outcomes.all { it } else outcomes.any { it }
        }
        if(playlist.groupsJoin==SmartJoin.AND) groups.all { it } else groups.any { it }
    }
    fun sortValue(track:MusicTrack):Any {
        val stats=context.stats[track.id];val profile=context.audioProfiles[track.id]
        return when(playlist.sortField) {
            SmartSortField.TITLE -> track.title
            SmartSortField.ARTIST -> track.artist
            SmartSortField.ALBUM -> track.album
            SmartSortField.PLAY_COUNT -> stats?.plays ?: track.playCount
            SmartSortField.LAST_PLAYED_DAYS_AGO -> stats?.lastPlayedAt?.let { context.now-it } ?: Long.MAX_VALUE
            SmartSortField.SKIP_COUNT -> stats?.skips ?: 0
            SmartSortField.DURATION -> track.durationMs
            SmartSortField.DATE_ADDED -> track.addedAt
            SmartSortField.TEMPO -> profile?.tempo ?: 0.0
            SmartSortField.ENERGY -> profile?.energy ?: 0.0
        }
    }
    val sorted=filtered.sortedWith { a,b ->
        val left=sortValue(a);val right=sortValue(b)
        val result=if(left is Number&&right is Number) left.toDouble().compareTo(right.toDouble()) else left.toString().compareTo(right.toString(),true)
        if(playlist.ascending) result else -result
    }
    return playlist.limit?.takeIf { it>0 }?.let { sorted.take(it) } ?: sorted
}

fun nativeSmartPlaylistPresets():List<NativeSmartPlaylist> = listOf(
    NativeSmartPlaylist(id="preset-night-drive",name="Night drive",preset=true,groupsJoin=SmartJoin.OR,groups=listOf(SmartConditionGroup(join=SmartJoin.OR,conditions=listOf(
        SmartCondition(field=SmartField.MOOD_LABEL,operator=SmartOperator.EQ,value="late-night"),
        SmartCondition(field=SmartField.MOOD_LABEL,operator=SmartOperator.EQ,value="chill")
    ))),sortField=SmartSortField.LAST_PLAYED_DAYS_AGO,ascending=true,limit=40),
    NativeSmartPlaylist(id="preset-workout",name="Workout",preset=true,groups=listOf(SmartConditionGroup(join=SmartJoin.OR,conditions=listOf(
        SmartCondition(field=SmartField.MOOD_LABEL,operator=SmartOperator.EQ,value="workout"),
        SmartCondition(field=SmartField.MOOD_LABEL,operator=SmartOperator.EQ,value="intense"),
        SmartCondition(field=SmartField.MOOD_LABEL,operator=SmartOperator.EQ,value="party"),
        SmartCondition(field=SmartField.MOOD_LABEL,operator=SmartOperator.EQ,value="upbeat")
    ))),sortField=SmartSortField.ENERGY,ascending=false,limit=60),
    NativeSmartPlaylist(id="preset-rediscover",name="Rediscover",preset=true,groups=listOf(SmartConditionGroup(conditions=listOf(
        SmartCondition(field=SmartField.PLAY_COUNT,operator=SmartOperator.LTE,value="2")
    ))),sortField=SmartSortField.LAST_PLAYED_DAYS_AGO,ascending=false,limit=50),
    NativeSmartPlaylist(id="preset-fresh",name="Fresh",preset=true,groups=listOf(SmartConditionGroup(conditions=listOf(
        SmartCondition(field=SmartField.DATE_ADDED,operator=SmartOperator.LTE,value="30")
    ))),sortField=SmartSortField.DATE_ADDED,ascending=false,limit=50),
    NativeSmartPlaylist(id="preset-heavy-rotation",name="Heavy rotation",preset=true,groups=listOf(SmartConditionGroup(conditions=listOf(
        SmartCondition(field=SmartField.PLAY_COUNT,operator=SmartOperator.GTE,value="5")
    ))),sortField=SmartSortField.PLAY_COUNT,ascending=false,limit=50)
)
