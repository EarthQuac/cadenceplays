package digital.anchorstudio.soundprint

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CadenceAlbumsScreen(
    libraryManager: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    onImportFiles: () -> Unit,
    libraryTrackCount: Int
) {
    var albums by remember { mutableStateOf(emptyList<LibraryAlbum>()) }
    var query by remember { mutableStateOf("") }
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(libraryTrackCount) {
        albums = withContext(Dispatchers.IO) { libraryManager.albums() }
    }

    if (selectedAlbumId != null) {
        AlbumDetailNative(
            albumId = selectedAlbumId!!,
            libraryManager = libraryManager,
            playback = playback,
            onBack = { selectedAlbumId = null }
        )
        return
    }

    val filtered = albums.filter {
        "${it.name} ${it.artist}".contains(query.trim(), ignoreCase = true)
    }

    Column(Modifier.fillMaxSize()) {
        AlbumPageHeader("Albums", "${albums.size} ${if (albums.size == 1) "album" else "albums"}")
        if (albums.isEmpty()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 54.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Filled.Album, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(29.dp))
                }
                Text("No albums yet", Modifier.padding(top = 15.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text("Add music to your device and scan your library — albums are grouped from your files' tags.",
                    Modifier.padding(top = 5.dp), fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Button(onClick = onImportFiles, modifier = Modifier.padding(top = 16.dp), shape = CircleShape) {
                    androidx.compose.material3.Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add music")
                }
            }
        } else {
            AlbumSearchBox(query, { query = it })
            if (filtered.isEmpty()) {
                Text("No albums match “$query”.", Modifier.fillMaxWidth().padding(vertical = 32.dp), textAlign = TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(filtered, key = { it.id }) { album ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { selectedAlbumId = album.id }
                        ) {
                            NativeArtwork(album.artworkUri, album.id, Modifier.fillMaxWidth().aspectRatio(1f), 15)
                            Text(album.name, Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${album.artist} · ${album.tracks.size} ${if (album.tracks.size == 1) "song" else "songs"}",
                                Modifier.padding(top = 2.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailNative(
    albumId: String,
    libraryManager: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    onBack: () -> Unit
) {
    var album by remember { mutableStateOf<LibraryAlbum?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(albumId) {
        album = withContext(Dispatchers.IO) { libraryManager.albums().firstOrNull { it.id == albumId } }
        loaded = true
    }
    val selected = album
    if (selected == null) {
        Column(Modifier.fillMaxSize()) {
            AlbumPageHeader("Album", "", onBack)
            Text(if (loaded) "Album not found" else "Loading album…", Modifier.padding(24.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val tracks = selected.tracks.sortedWith(compareBy<MusicTrack> { it.title.lowercase() }.thenBy { it.artist.lowercase() })
    Column(Modifier.fillMaxSize()) {
        AlbumPageHeader(selected.name, selected.artist, onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 26.dp)) {
            Spacer(Modifier.height(18.dp))
            NativeArtwork(selected.artworkUri, selected.id, Modifier.size(176.dp).align(Alignment.CenterHorizontally), 24)
            Text(selected.name, Modifier.fillMaxWidth().padding(top = 14.dp), textAlign = TextAlign.Center, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${selected.artist} · ${tracks.size} ${if (tracks.size == 1) "song" else "songs"}",
                Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 17.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(onClick = { playback.playTracks(tracks) }, enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f), shape = CircleShape) {
                    androidx.compose.material3.Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Play all")
                }
                OutlinedButton(onClick = { playback.playTracks(tracks, shuffle = true) }, enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f), shape = CircleShape) {
                    androidx.compose.material3.Icon(Icons.Filled.Shuffle, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Shuffle")
                }
            }
            Spacer(Modifier.height(17.dp))
            tracks.forEachIndexed { index, track ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).clickable { playback.playTracks(tracks, index) }.padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text((index + 1).toString(), Modifier.width(27.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Column(Modifier.weight(1f).padding(start = 7.dp, end = 8.dp)) {
                        Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(albumDuration(track.durationMs), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AlbumPageHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    }
}

@Composable
private fun AlbumSearchBox(value: String, onValueChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(46.dp).clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Filter albums or artists…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(value, onValueChange, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) androidx.compose.material3.Icon(Icons.Filled.Close, "Clear filter", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp).clickable { onValueChange("") })
    }
}

private fun albumDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000L).coerceAtLeast(0L)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

