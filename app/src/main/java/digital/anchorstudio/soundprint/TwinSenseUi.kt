package digital.anchorstudio.soundprint

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
internal fun SongsLibraryScreen(
    library: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    onImportFiles: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember(context, library) { TwinSenseScanner(context.applicationContext, library) }
    var songs by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var recycleItems by remember { mutableStateOf<List<RecycleBinEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("Title") }
    var descending by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var soundprintOpen by remember { mutableStateOf(false) }
    var recycleOpen by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<TwinSenseScanResult?>(null) }
    var scanProgress by remember { mutableStateOf(TwinSenseScanProgress(0, 0, "", 0, 0)) }
    var scanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf("") }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var pendingRemoval by remember { mutableStateOf<MusicTrack?>(null) }
    var confirmSmartCleanup by remember { mutableStateOf(false) }
    var confirmEmptyBin by remember { mutableStateOf(false) }

    suspend fun refreshLibrary() {
        val loaded = withContext(Dispatchers.IO) {
            library.allSongs() to library.recycleBin()
        }
        songs = loaded.first
        recycleItems = loaded.second
    }

    LaunchedEffect(library) {
        runCatching { refreshLibrary() }
        scanResult = scanner.loadSavedResults()
        loading = false
    }

    val filteredSongs = remember(songs, query, sort, descending) {
        val needle = query.trim()
        val list = songs.filter { needle.isBlank() || listOf(it.title, it.artist, it.album, it.genre, it.folder).any { value -> value.contains(needle, true) } }
        val sorted = when (sort) {
            "Artist" -> list.sortedWith(compareBy<MusicTrack> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
            "Album" -> list.sortedWith(compareBy<MusicTrack> { it.album.lowercase() }.thenBy { it.trackNumber })
            "Date added" -> list.sortedBy { it.addedAt }
            "Duration" -> list.sortedBy { it.durationMs }
            else -> list.sortedBy { it.title.lowercase() }
        }
        if (descending) sorted.reversed() else sorted
    }

    val cleanupTracks = remember(scanResult) {
        scanResult?.let { TwinSenseCleanupPlanner.tracksToRemove(it) }.orEmpty()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Songs", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text("${songs.size} songs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onImportFiles, shape = RoundedCornerShape(50)) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add music", fontSize = 12.sp)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Filter songs, artists, albums…", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        shape = RoundedCornerShape(14.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { sortMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Sort, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sort: $sort", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                listOf("Title", "Artist", "Album", "Date added", "Duration").forEach { option ->
                                    DropdownMenuItem(text = { Text(option) }, onClick = { sort = option; sortMenu = false })
                                }
                            }
                        }
                        OutlinedButton(onClick = { descending = !descending }, modifier = Modifier.weight(1f)) {
                            Text(if (descending) "Descending" else "Ascending", fontSize = 11.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { soundprintOpen = !soundprintOpen; if (soundprintOpen) recycleOpen = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.GraphicEq, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("TwinSense", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                recycleOpen = !recycleOpen
                                if (recycleOpen) soundprintOpen = false
                                scope.launch { recycleItems = withContext(Dispatchers.IO) { library.recycleBin() } }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.LibraryMusic, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Recycle Bin (${recycleItems.size})", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    if (query.isNotBlank()) Text("${filteredSongs.size} matches", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (soundprintOpen) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f))
                        Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("TwinSense", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Compare the actual audio stored on this device.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (!scanning) {
                                            scanError = ""
                                            scanning = true
                                            scanProgress = TwinSenseScanProgress(0, songs.size, "", 0, 0)
                                            scanJob = scope.launch {
                                                try {
                                                    scanResult = scanner.scan { scanProgress = it }
                                                    if ((scanResult?.failed ?: 0) > 0) {
                                                        scanError = "${scanResult?.failed} tracks could not be decoded. Available tracks were still compared."
                                                    }
                                                } catch (cancel: CancellationException) {
                                                    scanError = "Scan cancelled. Completed fingerprints remain saved for the next scan."
                                                } catch (error: Exception) {
                                                    scanError = error.message ?: "TwinSense could not complete this scan."
                                                } finally {
                                                    scanning = false
                                                    scanJob = null
                                                }
                                            }
                                        }
                                    },
                                    enabled = !scanning && songs.size >= 2
                                ) {
                                    if (scanning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Filled.GraphicEq, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text(if (scanning) "Scanning…" else if (scanResult != null) "Scan again" else "Scan library")
                                }
                                if (scanning) TextButton(onClick = { scanJob?.cancel() }) { Text("Cancel") }
                                else if (scanResult != null) Text("${scanResult!!.groups.size} groups", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (scanning) {
                                LinearProgressIndicator(progress = { scanProgress.percent / 100f }, modifier = Modifier.fillMaxWidth())
                                Text(
                                    "${scanProgress.percent}% · ${scanProgress.completed}/${scanProgress.total} · ${scanProgress.currentTitle}",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (scanResult != null && !scanning) {
                                val result = scanResult!!
                                Text(
                                    "${result.tracksScanned} tracks scanned · ${result.fingerprintsReused} cached fingerprints reused · ${result.groups.size} duplicate groups · ${result.matchCount} matching pairs",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (cleanupTracks.isNotEmpty()) {
                                    OutlinedButton(onClick = { confirmSmartCleanup = true }) {
                                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(7.dp))
                                        Text("Smart Cleanup · ${cleanupTracks.size}")
                                    }
                                }
                                if (result.groups.isEmpty()) {
                                    Text("No duplicate audio found in your library.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    result.groups.forEachIndexed { index, group ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            shape = RoundedCornerShape(16.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.48f))
                                        ) {
                                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Duplicate group ${index + 1}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                    Text("${group.tracks.size} tracks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                group.tracks.forEach { track ->
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Column(Modifier.weight(1f)) {
                                                            Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            Text("${track.artist} · ${formatTwinSenseDuration(track.durationMs / 1000.0)} · ${track.sizeBytes / (1024 * 1024)} MB",
                                                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }
                                                        TextButton(onClick = { pendingRemoval = track }) { Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                                                    }
                                                }
                                                group.matches.forEach { match ->
                                                    val label = when (match.kind) {
                                                        TwinSenseMatchKind.EXACT_AUDIO -> "Exact audio"
                                                        TwinSenseMatchKind.ACOUSTIC -> "Acoustic match"
                                                        TwinSenseMatchKind.METADATA -> "Possible match"
                                                    }
                                                    Text("$label · ${match.confidence}% confidence${if (match.versionWarning) " · possible alternate version" else ""}",
                                                        fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                                    match.reasons.forEach { reason ->
                                                        Text("• $reason", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (!scanning) {
                                Text(
                                    if (songs.size < 2) "Add at least two tracks before scanning." else "TwinSense compares local audio fingerprints to find duplicate and alternate tracks. Audio never leaves this device.",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (scanError.isNotBlank()) Text(scanError, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            if (recycleOpen) {
                item {
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Recycle Bin", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text("Items are kept for 30 days. Original audio files are never deleted.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (recycleItems.isNotEmpty()) TextButton(onClick = { confirmEmptyBin = true }) {
                                    Text("Empty bin", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (recycleItems.isEmpty()) {
                                Text("Your Recycle Bin is empty.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else recycleItems.forEach { entry ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.track.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${entry.track.artist} · ${entry.reason} · ${daysRemaining(entry.removedAt)} days left",
                                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    }
                                    TextButton(onClick = {
                                        scope.launch {
                                            runCatching { withContext(Dispatchers.IO) { library.restoreFromRecycleBin(entry.track.id) } }
                                            refreshLibrary()
                                        }
                                    }) {
                                        Icon(Icons.Filled.Restore, null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Restore", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (loading) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (filteredSongs.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                        Text(if (query.isBlank()) "No music found" else "No matches", fontWeight = FontWeight.SemiBold)
                        Text(if (query.isBlank()) "Add music to your device to see your songs here." else "Try another title, artist or album.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(filteredSongs, key = { it.id }) { track ->
                    NativeSongLibraryRow(
                        track = track,
                        library = library,
                        playback = playback,
                        onPlay = { playback.playTracks(filteredSongs, filteredSongs.indexOfFirst { it.id == track.id }.coerceAtLeast(0)) },
                        onChanged = { scope.launch { refreshLibrary() } }
                    )
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    if (pendingRemoval != null) {
        val track = pendingRemoval!!
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Move this track to Recycle Bin?") },
            text = { Text("${track.title} will leave your library but can be restored for 30 days. The original audio file will remain untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoval = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { library.moveToRecycleBin(track.id, "TwinSense duplicate review") } }
                        scanResult?.let { previous ->
                            val updated = scanner.withoutTracks(previous, setOf(track.id))
                            scanResult = updated
                            scanner.saveResult(updated)
                        }
                        refreshLibrary()
                    }
                }) { Text("Move to Recycle Bin", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } }
        )
    }

    if (confirmSmartCleanup) {
        AlertDialog(
            onDismissRequest = { confirmSmartCleanup = false },
            title = { Text("Run Smart Cleanup?") },
            text = { Text("Move ${cleanupTracks.size} verified duplicate copies to Recycle Bin? The best copy in each exact-audio group will be kept. Similar matches and alternate versions are never auto-removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSmartCleanup = false
                    scope.launch {
                        val moved = mutableSetOf<String>()
                        withContext(Dispatchers.IO) {
                            cleanupTracks.forEach { track ->
                                runCatching { library.moveToRecycleBin(track.id, "TwinSense Smart Cleanup") }
                                    .onSuccess { moved += track.id }
                            }
                        }
                        scanResult?.let { previous ->
                            val updated = scanner.withoutTracks(previous, moved)
                            scanResult = updated
                            scanner.saveResult(updated)
                        }
                        refreshLibrary()
                    }
                }) { Text("Clean up duplicates", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmSmartCleanup = false }) { Text("Cancel") } }
        )
    }

    if (confirmEmptyBin) {
        AlertDialog(
            onDismissRequest = { confirmEmptyBin = false },
            title = { Text("Empty Recycle Bin?") },
            text = { Text("This permanently removes the selected tracks from Soundprint's library and history. Original audio files on your device are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmptyBin = false
                    scope.launch {
                        withContext(Dispatchers.IO) { library.emptyRecycleBin() }
                        recycleItems = emptyList()
                        refreshLibrary()
                    }
                }) { Text("Empty bin", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmptyBin = false }) { Text("Cancel") } }
        )
    }
}


@Composable
private fun NativeSongLibraryRow(
    track: MusicTrack,
    library: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    onPlay: () -> Unit,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    var playlistsOpen by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var artistOpen by remember { mutableStateOf(false) }
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    var genre by remember(track.id) { mutableStateOf(track.genre) }
    val playlists = remember(playlistsOpen) { if (playlistsOpen) library.playlists() else emptyList() }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface).clickable(onClick = onPlay).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.artist} · ${track.album}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTwinSenseDuration(track.durationMs / 1000.0), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            androidx.compose.material3.IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Song options", modifier = Modifier.size(19.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Play next") }, leadingIcon = { Icon(Icons.Filled.PlayArrow, null) }, onClick = { menuOpen = false; playback.enqueue(listOf(track), next = true) })
                DropdownMenuItem(text = { Text("Add to queue") }, leadingIcon = { Icon(Icons.Filled.QueueMusic, null) }, onClick = { menuOpen = false; playback.enqueue(listOf(track)) })
                DropdownMenuItem(text = { Text(if (track.isFavorite) "Remove from favourites" else "Add to favourites") }, leadingIcon = { Icon(Icons.Filled.Favorite, null) }, onClick = {
                    menuOpen = false
                    scope.launch { withContext(Dispatchers.IO) { library.toggleFavorite(track.id) }; onChanged() }
                })
                DropdownMenuItem(text = { Text("Add to playlist") }, leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) }, onClick = { menuOpen = false; playlistsOpen = true })
                DropdownMenuItem(text = { Text("Edit song info") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menuOpen = false; editOpen = true })
                DropdownMenuItem(text = { Text("Go to artist") }, leadingIcon = { Icon(Icons.Filled.Person, null) }, onClick = { menuOpen = false; artistOpen = true })
                DropdownMenuItem(text = { Text("Move to Recycle Bin") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = {
                    menuOpen = false
                    scope.launch { withContext(Dispatchers.IO) { library.moveToRecycleBin(track.id, "Removed from song menu") }; onChanged() }
                })
            }
        }
    }

    if (editOpen) {
        AlertDialog(
            onDismissRequest = { editOpen = false },
            title = { Text("Edit song info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist") }, singleLine = true)
                    OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text("Album") }, singleLine = true)
                    OutlinedTextField(value = genre, onValueChange = { genre = it }, label = { Text("Genre") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editOpen = false
                    scope.launch {
                        withContext(Dispatchers.IO) { library.updateSongMetadata(track.id, title, artist, album, genre) }
                        onChanged()
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editOpen = false }) { Text("Cancel") } }
        )
    }

    if (artistOpen) {
        val artistTracks = remember(track.artist) { library.artistTracks(track.artist) }
        AlertDialog(
            onDismissRequest = { artistOpen = false },
            title = { Text(track.artist) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${artistTracks.size} songs", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    artistTracks.take(12).forEachIndexed { index, artistTrack ->
                        TextButton(onClick = { artistOpen = false; playback.playTracks(artistTracks, index) }) {
                            Text(artistTrack.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { artistOpen = false }) { Text("Close") } }
        )
    }

    if (playlistsOpen) {
        AlertDialog(
            onDismissRequest = { playlistsOpen = false },
            title = { Text("Add to playlist") },
            text = {
                if (playlists.isEmpty()) Text("You haven't created a playlist yet.")
                else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    playlists.forEach { playlist ->
                        TextButton(onClick = {
                            playlistsOpen = false
                            scope.launch { withContext(Dispatchers.IO) { library.addToPlaylist(playlist.id, listOf(track.id)) }; onChanged() }
                        }) { Text(playlist.name) }
                    }
                }
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("New playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newPlaylistName.isNotBlank(),
                    onClick = {
                        val name = newPlaylistName.trim()
                        playlistsOpen = false
                        newPlaylistName = ""
                        scope.launch {
                            withContext(Dispatchers.IO) { library.createPlaylist(name, listOf(track.id)) }
                            onChanged()
                        }
                    }
                ) { Text("Create playlist") }
            },
            dismissButton = { TextButton(onClick = { playlistsOpen = false }) { Text("Close") } }
        )
    }
}

private fun daysRemaining(removedAt: Long): Int =
    max(0, 30 - ((System.currentTimeMillis() - removedAt) / 86_400_000L).toInt())
