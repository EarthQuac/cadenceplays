package digital.anchorstudio.soundprint

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private data class PlaylistPageData(
    val tracks: List<MusicTrack> = emptyList(),
    val playlists: List<ManagedPlaylist> = emptyList(),
    val smartPlaylists: List<NativeSmartPlaylist> = emptyList(),
    val smartResults: Map<String, List<MusicTrack>> = emptyMap(),
    val liked: List<MusicTrack> = emptyList(),
    val recent: List<MusicTrack> = emptyList(),
    val added: List<MusicTrack> = emptyList()
)

private data class PlaylistMixItem(val id: String, val title: String, val tracks: List<MusicTrack>, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val smartOperators = listOf(
    SmartOperator.EQ to "is",
    SmartOperator.NEQ to "is not",
    SmartOperator.CONTAINS to "contains",
    SmartOperator.NOT_CONTAINS to "doesn't contain",
    SmartOperator.GT to "greater than",
    SmartOperator.GTE to "at least",
    SmartOperator.LT to "less than",
    SmartOperator.LTE to "at most"
)

private val smartFields = listOf(
    SmartField.PLAY_COUNT to ("Play count" to "number"),
    SmartField.LAST_PLAYED_DAYS_AGO to ("Last played (days ago)" to "number"),
    SmartField.NEVER_PLAYED to ("Never played" to "boolean"),
    SmartField.SKIP_COUNT to ("Skip count" to "number"),
    SmartField.USUAL_HOUR to ("Usual listening hour" to "number"),
    SmartField.DATE_ADDED to ("Date added (days ago)" to "number"),
    SmartField.GENRE to ("Genre" to "text"),
    SmartField.ARTIST to ("Artist" to "text"),
    SmartField.ALBUM to ("Album" to "text"),
    SmartField.FOLDER to ("Folder" to "text"),
    SmartField.YEAR to ("Year" to "number"),
    SmartField.DURATION to ("Duration (seconds)" to "number"),
    SmartField.FAVORITE to ("Favorite" to "boolean"),
    SmartField.HAS_LYRICS to ("Has lyrics" to "boolean"),
    SmartField.MOOD_LABEL to ("Mood" to "text"),
    SmartField.ENERGY to ("Energy (0–100)" to "number"),
    SmartField.TEMPO to ("Tempo (BPM)" to "number")
)

private fun smartFieldLabel(field: SmartField) = smartFields.first { it.first == field }.second.first
private fun smartFieldType(field: SmartField) = smartFields.first { it.first == field }.second.second
private fun operatorLabel(operator: SmartOperator) = smartOperators.first { it.first == operator }.second
private fun freshSmartPlaylist() = NativeSmartPlaylist(
    name = "New smart playlist",
    groups = listOf(SmartConditionGroup(conditions = listOf(SmartCondition(field = SmartField.GENRE, operator = SmartOperator.CONTAINS, value = ""))))
)
private fun freshCondition() = SmartCondition(field = SmartField.GENRE, operator = SmartOperator.CONTAINS, value = "")
private fun freshGroup() = SmartConditionGroup(conditions = listOf(freshCondition()))

@Composable
fun CadencePlaylistsScreen(
    libraryManager: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    clapScanner: ClapLibraryScanner
) {
    val scan by clapScanner.state.collectAsState()
    var refreshVersion by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<PlaylistPageData?>(null) }
    var query by remember { mutableStateOf("") }
    var createPlaylistOpen by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var editorOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf(freshSmartPlaylist()) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var selectedSmartId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { clapScanner.refreshSummary() }
    LaunchedEffect(scan.completed, scan.failed, scan.running, scan.lastCompletedTrackId, refreshVersion) {
        data = withContext(Dispatchers.IO) {
            val tracks = libraryManager.allSongs()
            val smart = libraryManager.smartPlaylists()
            val results = smart.associate { it.id to libraryManager.evaluateSmartPlaylist(it.id) }
            val recent = libraryManager.listeningHistory(500).mapNotNull { it.second }
                .distinctBy { it.id }.take(30)
            PlaylistPageData(
                tracks = tracks,
                playlists = libraryManager.playlists(),
                smartPlaylists = smart,
                smartResults = results,
                liked = tracks.filter { it.isFavorite },
                recent = recent,
                added = tracks.sortedByDescending { it.addedAt }.take(20)
            )
        }
    }

    val current = data
    if (selectedPlaylistId != null) {
        PlaylistDetailNative(
            playlistId = selectedPlaylistId!!,
            libraryManager = libraryManager,
            playback = playback,
            onBack = { selectedPlaylistId = null },
            onChanged = { refreshVersion++ }
        )
        return
    }
    if (selectedSmartId != null) {
        SmartPlaylistDetailNative(
            smartId = selectedSmartId!!,
            libraryManager = libraryManager,
            playback = playback,
            clapScanner = clapScanner,
            onBack = { selectedSmartId = null },
            refreshVersion = refreshVersion
        )
        return
    }

    val playlists = current?.playlists.orEmpty()
    val filtered = playlists.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val mixes = current?.let {
        listOf(
            PlaylistMixItem("liked", "Liked Songs", it.liked, Icons.Filled.Favorite),
            PlaylistMixItem("recent", "Recently Played", it.recent, Icons.Filled.AccessTime),
            PlaylistMixItem("added", "Recently Added", it.added, Icons.Filled.QueueMusic)
        )
    }.orEmpty()

    Column(Modifier.fillMaxSize()) {
        PlaylistPageHeader(
            title = "Playlists",
            subtitle = "${playlists.size} ${if (playlists.size == 1) "playlist" else "playlists"}",
            action = { createPlaylistOpen = true }
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            PlaylistSearchBox(query, { query = it }, "Search playlists…")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SMART MIXES", fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                mixes.forEach { mix ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                            .clickable { selectedSmartId = mix.id }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Icon(mix.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 11.dp)) {
                            Text(mix.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("${mix.tracks.size} songs", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Smart playlists", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Automatically updated from your listening and library", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { editingId = null; draft = freshSmartPlaylist(); editorOpen = true }, shape = CircleShape, contentPadding = ButtonDefaults.ContentPadding) {
                        androidx.compose.material3.Icon(Icons.Filled.Add, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New smart playlist", fontSize = 11.sp, maxLines = 1)
                    }
                }
                current?.smartPlaylists.orEmpty().forEach { rule ->
                    val count = current?.smartResults?.get(rule.id)?.size ?: 0
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface).padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(Modifier.weight(1f).clickable { selectedSmartId = rule.id }, verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(Icons.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(rule.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$count songs", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            androidx.compose.material3.Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                        if (rule.preset) {
                            Text("Preset", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 5.dp))
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Icon(Icons.Filled.Edit, "Edit ${rule.name}", Modifier.size(17.dp).clip(CircleShape).clickable {
                                    editingId = rule.id
                                    draft = rule.copy(groups = rule.groups.map { it.copy(conditions = it.conditions.toList()) })
                                    editorOpen = true
                                }.padding(2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                androidx.compose.material3.Icon(Icons.Filled.ContentCopy, "Duplicate ${rule.name}", Modifier.size(17.dp).clickable {
                                    val copy = rule.copy(
                                        id = UUID.randomUUID().toString(),
                                        name = "${rule.name} copy",
                                        preset = false,
                                        groups = rule.groups.map { group -> group.copy(id = UUID.randomUUID().toString(), conditions = group.conditions.map { it.copy(id = UUID.randomUUID().toString()) }) }
                                    )
                                    libraryManager.saveSmartPlaylist(copy)
                                    refreshVersion++
                                }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                androidx.compose.material3.Icon(Icons.Filled.Delete, "Delete ${rule.name}", Modifier.size(17.dp).clickable {
                                    libraryManager.deleteSmartPlaylist(rule.id)
                                    refreshVersion++
                                }, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            if (current == null) {
                Text("Loading your playlists…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (filtered.isEmpty()) {
                Text(if (query.isBlank()) "No playlists yet" else "No playlists match your search.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp))
                if (query.isBlank()) Text("Create a playlist to collect songs, or build a smart playlist that updates automatically.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    filtered.forEach { playlist ->
                        val cover = playlist.trackIds.firstNotNullOfOrNull { id -> current?.tracks?.firstOrNull { it.id == id }?.artworkUri }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { selectedPlaylistId = playlist.id }.padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            NativeArtwork(cover, playlist.id, Modifier.size(48.dp), 10)
                            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                Text(playlist.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${playlist.trackIds.size} songs", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            androidx.compose.material3.Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        }
    }

    if (createPlaylistOpen) {
        AlertDialog(
            onDismissRequest = { createPlaylistOpen = false },
            title = { Text("New playlist") },
            text = { TextField(value = playlistName, onValueChange = { playlistName = it }, singleLine = true, placeholder = { Text("Playlist name") }) },
            confirmButton = {
                TextButton(enabled = playlistName.isNotBlank(), onClick = {
                    libraryManager.createPlaylist(playlistName.trim())
                    playlistName = ""
                    createPlaylistOpen = false
                    refreshVersion++
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { createPlaylistOpen = false }) { Text("Cancel") } }
        )
    }
    if (editorOpen) {
        SmartPlaylistEditorDialog(
            draft = draft,
            onDraftChange = { draft = it },
            editing = editingId != null,
            onDismiss = { editorOpen = false },
            onSave = {
                libraryManager.saveSmartPlaylist(draft.copy(name = draft.name.trim(), preset = false))
                editorOpen = false
                refreshVersion++
            }
        )
    }
}

@Composable
private fun PlaylistDetailNative(
    playlistId: String,
    libraryManager: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    var refresh by remember { mutableIntStateOf(0) }
    var renameOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var candidateQuery by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf(emptyList<MusicTrack>()) }
    var libraryTracks by remember { mutableStateOf(emptyList<MusicTrack>()) }
    var detailLoaded by remember { mutableStateOf(false) }
    var playlist by remember { mutableStateOf<ManagedPlaylist?>(null) }
    LaunchedEffect(playlistId, refresh) {
        val loaded = withContext(Dispatchers.IO) {
            val item = libraryManager.playlists().firstOrNull { it.id == playlistId }
            Triple(item, libraryManager.playlistTracks(playlistId), libraryManager.allSongs())
        }
        playlist = loaded.first
        tracks = loaded.second
        libraryTracks = loaded.third
        detailLoaded = true
    }
    val selected = playlist
    if (selected == null) {
        Column(Modifier.fillMaxSize()) {
            PlaylistPageHeader("Playlist", "", back = onBack)
            Text(if (detailLoaded) "Playlist not found" else "Loading playlist…", Modifier.padding(24.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        PlaylistPageHeader(selected.name, "${selected.trackIds.size} songs", back = onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Spacer(Modifier.height(18.dp))
            val cover = tracks.firstNotNullOfOrNull { it.artworkUri }
            NativeArtwork(cover, selected.id, Modifier.size(160.dp).align(Alignment.CenterHorizontally), 24)
            Text(selected.name, Modifier.fillMaxWidth().padding(top = 13.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text("${tracks.size} songs", Modifier.fillMaxWidth().padding(top = 3.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(onClick = { playback.playTracks(tracks) }, enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f), shape = CircleShape) {
                    androidx.compose.material3.Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Play")
                }
                OutlinedButton(onClick = { playback.playTracks(tracks, shuffle = true) }, enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f), shape = CircleShape) {
                    androidx.compose.material3.Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(5.dp)); Text("Shuffle")
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { addOpen = true }) { androidx.compose.material3.Icon(Icons.Filled.Add, null); Text("Add songs") }
                TextButton(onClick = { name = selected.name; renameOpen = true }) { androidx.compose.material3.Icon(Icons.Filled.Edit, null); Text("Rename") }
                TextButton(onClick = { libraryManager.deletePlaylist(selected.id); onBack(); onChanged() }) { androidx.compose.material3.Icon(Icons.Filled.Delete, null); Text("Delete") }
            }
            Spacer(Modifier.height(10.dp))
            if (tracks.isEmpty()) {
                EmptyPlaylistState(onAdd = { addOpen = true })
            } else {
                tracks.forEachIndexed { index, track ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        NativeTrackRow(track, onClick = { playback.playTracks(tracks, index) }, onRemove = { libraryManager.removeFromPlaylist(selected.id, track.id); refresh++; onChanged() }, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.Icon(Icons.Filled.ArrowUpward, "Move up", Modifier.size(16.dp).clickable(enabled = index > 0) { libraryManager.reorderPlaylist(selected.id, index, index - 1); refresh++ }.padding(1.dp), tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline)
                            androidx.compose.material3.Icon(Icons.Filled.ArrowDownward, "Move down", Modifier.size(16.dp).clickable(enabled = index < tracks.lastIndex) { libraryManager.reorderPlaylist(selected.id, index, index + 1); refresh++ }.padding(1.dp), tint = if (index < tracks.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
    if (renameOpen) AlertDialog(
        onDismissRequest = { renameOpen = false }, title = { Text("Rename playlist") },
        text = { TextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { libraryManager.renamePlaylist(playlistId, name.trim()); renameOpen = false; refresh++; onChanged() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } }
    )
    if (addOpen) AddSongsDialog(
        allTracks = libraryTracks,
        existingIds = selected?.trackIds.orEmpty(),
        query = candidateQuery,
        onQuery = { candidateQuery = it },
        onAdd = { id -> libraryManager.addToPlaylist(playlistId, listOf(id)); refresh++; onChanged() },
        onDismiss = { addOpen = false }
    )
}

@Composable
private fun SmartPlaylistDetailNative(
    smartId: String,
    libraryManager: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    clapScanner: ClapLibraryScanner,
    onBack: () -> Unit,
    refreshVersion: Int
) {
    val scan by clapScanner.state.collectAsState()
    var tracks by remember { mutableStateOf(emptyList<MusicTrack>()) }
    var title by remember { mutableStateOf("") }
    var isRule by remember { mutableStateOf(false) }
    LaunchedEffect(smartId, refreshVersion, scan.completed, scan.lastCompletedTrackId, scan.failed) {
        val loaded = withContext(Dispatchers.IO) {
            when (smartId) {
                "liked" -> Triple("Liked Songs", libraryManager.allSongs().filter { it.isFavorite }, false)
                "recent" -> Triple("Recently Played", libraryManager.listeningHistory(500).mapNotNull { it.second }.distinctBy { it.id }.take(30), false)
                "added" -> Triple("Recently Added", libraryManager.allSongs().sortedByDescending { it.addedAt }.take(20), false)
                else -> {
                    val rule = libraryManager.smartPlaylists().firstOrNull { it.id == smartId }
                    Triple(rule?.name ?: "Smart playlist", libraryManager.evaluateSmartPlaylist(smartId), rule != null)
                }
            }
        }
        title = loaded.first
        tracks = loaded.second
        isRule = loaded.third
    }
    Column(Modifier.fillMaxSize()) {
        PlaylistPageHeader(title, "${tracks.size} songs", back = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Spacer(Modifier.height(18.dp))
            NativeArtwork(tracks.firstNotNullOfOrNull { it.artworkUri }, smartId, Modifier.size(160.dp).align(Alignment.CenterHorizontally), 24)
            Text(title, Modifier.fillMaxWidth().padding(top = 13.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text("${tracks.size} songs${if (isRule) " · Updates automatically" else ""}", Modifier.fillMaxWidth().padding(top = 3.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(onClick = { playback.playTracks(tracks) }, enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f), shape = CircleShape) {
                    androidx.compose.material3.Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Play")
                }
                OutlinedButton(onClick = { playback.playTracks(tracks, shuffle = true) }, enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f), shape = CircleShape) {
                    androidx.compose.material3.Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(5.dp)); Text("Shuffle")
                }
            }
            Spacer(Modifier.height(12.dp))
            if (tracks.isEmpty()) {
                Text("No matching songs yet", Modifier.fillMaxWidth().padding(top = 32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("This playlist updates automatically when songs in your library match its rules.", Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else tracks.forEachIndexed { index, track ->
                NativeTrackRow(track, onClick = { playback.playTracks(tracks, index) })
            }
        }
    }
}

@Composable
private fun SmartPlaylistEditorDialog(
    draft: NativeSmartPlaylist,
    onDraftChange: (NativeSmartPlaylist) -> Unit,
    editing: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    fun updateCondition(groupId: String, conditionId: String, patch: SmartCondition) {
        onDraftChange(draft.copy(groups = draft.groups.map { group ->
            if (group.id != groupId) group else group.copy(conditions = group.conditions.map { if (it.id == conditionId) patch else it })
        }))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) "Edit smart playlist" else "Create smart playlist") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(value = draft.name, onValueChange = { onDraftChange(draft.copy(name = it)) }, singleLine = true, placeholder = { Text("Playlist name") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Match groups", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    NativeChoice(draft.groupsJoin.name, listOf("AND" to "All groups", "OR" to "Any group")) { value -> onDraftChange(draft.copy(groupsJoin = SmartJoin.valueOf(value))) }
                }
                draft.groups.forEachIndexed { groupIndex, group ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Group ${groupIndex + 1}", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            NativeChoice(group.join.name, listOf("AND" to "Match all", "OR" to "Match any")) { value ->
                                onDraftChange(draft.copy(groups = draft.groups.map { if (it.id == group.id) it.copy(join = SmartJoin.valueOf(value)) else it }))
                            }
                            if (draft.groups.size > 1) Text("×", Modifier.clickable { onDraftChange(draft.copy(groups = draft.groups.filterNot { it.id == group.id })) }.padding(7.dp), fontSize = 20.sp)
                        }
                        group.conditions.forEach { condition ->
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.background).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                NativeChoice(condition.field.name, smartFields.map { it.first.name to it.second.first }) { value ->
                                    updateCondition(group.id, condition.id, condition.copy(field = SmartField.valueOf(value), operator = SmartOperator.EQ, value = ""))
                                }
                                NativeChoice(condition.operator.name, smartOperators.map { it.first.name to it.second }) { value ->
                                    updateCondition(group.id, condition.id, condition.copy(operator = SmartOperator.valueOf(value)))
                                }
                                if (smartFieldType(condition.field) == "boolean") {
                                    NativeChoice(condition.value, listOf("true" to "Yes", "false" to "No", "" to "Choose…")) { value ->
                                        updateCondition(group.id, condition.id, condition.copy(value = value))
                                    }
                                } else {
                                    TextField(
                                        value = condition.value,
                                        onValueChange = { updateCondition(group.id, condition.id, condition.copy(value = it)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        placeholder = { Text(if (smartFieldType(condition.field) == "number") "Number" else "Value") }
                                    )
                                }
                                TextButton(enabled = group.conditions.size > 1, onClick = {
                                    onDraftChange(draft.copy(groups = draft.groups.map { if (it.id == group.id) it.copy(conditions = it.conditions.filterNot { c -> c.id == condition.id }) else it }))
                                }) { Text("Remove condition") }
                            }
                        }
                        TextButton(onClick = { onDraftChange(draft.copy(groups = draft.groups.map { if (it.id == group.id) it.copy(conditions = it.conditions + freshCondition()) else it })) }) {
                            androidx.compose.material3.Icon(Icons.Filled.Add, null); Text("Add condition")
                        }
                    }
                }
                OutlinedButton(onClick = { onDraftChange(draft.copy(groups = draft.groups + freshGroup())) }, shape = CircleShape) {
                    androidx.compose.material3.Icon(Icons.Filled.Add, null); Text("Add group")
                }
                Text("Sort and limit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                NativeChoice(draft.sortField.name, SmartSortField.entries.map { it.name to it.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase) }) { value ->
                    onDraftChange(draft.copy(sortField = SmartSortField.valueOf(value)))
                }
                NativeChoice(if (draft.ascending) "asc" else "desc", listOf("desc" to "Descending", "asc" to "Ascending")) { value ->
                    onDraftChange(draft.copy(ascending = value == "asc"))
                }
                var limitText by remember(draft.id) { mutableStateOf(draft.limit?.toString().orEmpty()) }
                TextField(value = limitText, onValueChange = {
                    limitText = it.filter(Char::isDigit).take(4)
                    onDraftChange(draft.copy(limit = limitText.toIntOrNull()?.coerceIn(1, 1000)))
                }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Limit (optional)") })
                Text("Mood and energy rules use saved music matches. Tempo and lyrics filters need those details to be available in your library.", fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = draft.name.isNotBlank() && draft.groups.isNotEmpty() && draft.groups.all { it.conditions.isNotEmpty() && it.conditions.all { c -> c.value.isNotBlank() } }, onClick = onSave) { Text("Save smart playlist") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NativeChoice(value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == value }?.second ?: value
    Box {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(9.dp))
            .clickable { expanded = true }.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("⌄", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, text) -> DropdownMenuItem(text = { Text(text, fontSize = 12.sp) }, onClick = { onSelect(key); expanded = false }) }
        }
    }
}

@Composable
private fun AddSongsDialog(
    allTracks: List<MusicTrack>,
    existingIds: List<String>,
    query: String,
    onQuery: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val candidates = allTracks.filter { it.id !in existingIds && "${it.title} ${it.artist} ${it.album}".contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add songs") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                PlaylistSearchBox(query, onQuery, "Search songs to add…")
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    candidates.forEach { track ->
                        Row(Modifier.fillMaxWidth().clickable { onAdd(track.id) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            NativeArtwork(track.artworkUri, track.id, Modifier.size(42.dp), 8)
                            Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            androidx.compose.material3.Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (candidates.isEmpty()) Text("No songs match this search.", Modifier.padding(14.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun EmptyPlaylistState(onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Icons.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
        }
        Text("This playlist is empty", Modifier.padding(top = 12.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Add songs from your library to start building it.", Modifier.padding(top = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAdd, modifier = Modifier.padding(top = 12.dp), shape = CircleShape) { Text("Add songs") }
    }
}

@Composable
private fun NativeTrackRow(track: MusicTrack, onClick: () -> Unit, onRemove: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier.weightlessClickRow(onClick), verticalAlignment = Alignment.CenterVertically) {
        NativeArtwork(track.artworkUri, track.id, Modifier.size(44.dp), 8)
        Column(Modifier.weight(1f).padding(start = 10.dp, end = 7.dp)) {
            Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.artist}${if (track.album.isNotBlank()) " · ${track.album}" else ""}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatNativeDuration(track.durationMs), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRemove != null) {
            androidx.compose.material3.Icon(Icons.Filled.Close, "Remove", Modifier.padding(start = 7.dp).size(17.dp).clickable(onClick = onRemove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Modifier.weightlessClickRow(onClick: () -> Unit): Modifier = this.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp)

@Composable
private fun PlaylistPageHeader(title: String, subtitle: String, action: (() -> Unit)? = null, back: (() -> Unit)? = null) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (back != null) {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = back), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (action != null) Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = action), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(Icons.Filled.Add, "New playlist", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    }
}

@Composable
private fun PlaylistSearchBox(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Row(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(value, onValueChange, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) androidx.compose.material3.Icon(Icons.Filled.Close, "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp).clickable { onValueChange("") })
    }
}

@Composable
internal fun NativeArtwork(uri: String?, seed: String, modifier: Modifier = Modifier, radius: Int = 12) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri, seed) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (uri.isNullOrBlank()) return@runCatching null
                val stream = if (uri.startsWith("/")) File(uri).inputStream() else context.contentResolver.openInputStream(Uri.parse(uri))
                stream?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(radius.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else {
            androidx.compose.material3.Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f), modifier = Modifier.size(25.dp))
        }
    }
}

private fun formatNativeDuration(durationMs: Long): String {
    val total = (durationMs / 1000L).coerceAtLeast(0L)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
