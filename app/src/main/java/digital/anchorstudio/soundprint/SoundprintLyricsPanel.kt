package digital.anchorstudio.soundprint

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private data class SoundprintLyricWord(val timeMs: Long, val text: String)
private data class SoundprintLyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<SoundprintLyricWord> = emptyList()
)
private data class SoundprintLyricAnchor(val lyricMs: Long, val audioMs: Long)

private fun parseSoundprintLyricTime(minutes: String, seconds: String, fraction: String): Long {
    val fractionMs = when (fraction.length) {
        1 -> (fraction.toIntOrNull() ?: 0) * 100
        2 -> (fraction.toIntOrNull() ?: 0) * 10
        else -> fraction.take(3).toIntOrNull() ?: 0
    }
    return ((minutes.toLongOrNull() ?: 0L) * 60_000L) +
        ((seconds.toLongOrNull() ?: 0L) * 1_000L) + fractionMs
}

private fun parseSoundprintLrc(value: String): List<SoundprintLyricLine> {
    val result = mutableListOf<SoundprintLyricLine>()
    val lineTimeRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    val wordTimeRegex = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>""")
    value.replace("\r", "").lineSequence().forEach { raw ->
        val lineTimes = lineTimeRegex.findAll(raw).toList()
        if (lineTimes.isEmpty()) return@forEach
        val lyricText = lineTimeRegex.replace(raw, "").trim()
        val wordMatches = wordTimeRegex.findAll(lyricText).toList()
        val words = wordMatches.mapIndexedNotNull { index, match ->
            val start = match.range.last + 1
            val end = wordMatches.getOrNull(index + 1)?.range?.first ?: lyricText.length
            val text = lyricText.substring(start, end).replace(wordTimeRegex, "").ifBlank { return@mapIndexedNotNull null }
            SoundprintLyricWord(
                parseSoundprintLyricTime(match.groupValues[1], match.groupValues[2], match.groupValues[3]),
                text
            )
        }
        val plainText = if (words.isNotEmpty()) words.joinToString("") { it.text } else lyricText
        lineTimes.forEach { match ->
            result += SoundprintLyricLine(
                parseSoundprintLyricTime(match.groupValues[1], match.groupValues[2], match.groupValues[3]),
                plainText,
                words
            )
        }
    }
    return result.sortedBy { it.timeMs }
}

private fun alignSoundprintLyricTime(time: Long, anchors: List<SoundprintLyricAnchor>): Long {
    val sorted = anchors.sortedBy { it.lyricMs }
    if (sorted.isEmpty()) return time
    val points = (listOf(SoundprintLyricAnchor(0L, 0L)) + sorted.filter { it.lyricMs > 0L })
        .distinctBy { it.lyricMs }.sortedBy { it.lyricMs }
    if (time <= points.first().lyricMs) {
        return max(0L, time + points.first().audioMs - points.first().lyricMs)
    }
    for (index in 1 until points.size) {
        val left = points[index - 1]
        val right = points[index]
        if (time <= right.lyricMs) {
            val span = right.lyricMs - left.lyricMs
            if (span <= 0L) return right.audioMs
            val progress = (time - left.lyricMs).toDouble() / span.toDouble()
            return (left.audioMs + progress * (right.audioMs - left.audioMs)).toLong().coerceAtLeast(0L)
        }
    }
    val last = points.last()
    return max(0L, time + last.audioMs - last.lyricMs)
}

private fun soundprintLyricClock(ms: Long): String {
    val safe = ms.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(safe / 60L, safe % 60L)
}

@Composable
fun SoundprintLyricsPanel(
    trackId: String,
    title: String,
    artist: String,
    album: String,
    duration: Long,
    position: Long,
    seek: (Long) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("soundprint_now_playing", 0) }
    val scope = rememberCoroutineScope()
    val repository = remember { LyricsLookupRepository() }
    var lyrics by remember(trackId) { mutableStateOf("") }
    var source by remember(trackId) { mutableStateOf("") }
    var matchedTitle by remember(trackId) { mutableStateOf("") }
    var matchedArtist by remember(trackId) { mutableStateOf("") }
    var matchedAlbum by remember(trackId) { mutableStateOf("") }
    var error by remember(trackId) { mutableStateOf("") }
    var loading by remember(trackId) { mutableStateOf(false) }
    var matches by remember(trackId) { mutableStateOf(emptyList<LyricsLookupCandidate>()) }
    var searchQuery by remember(trackId) { mutableStateOf(title) }
    var editorOpen by remember(trackId) { mutableStateOf(false) }
    var draft by remember(trackId) { mutableStateOf("") }
    var showAll by remember(trackId) { mutableStateOf(false) }
    var karaokeMode by remember(trackId) { mutableStateOf(false) }
    var karaokeLoading by remember(trackId) { mutableStateOf(false) }
    var syncOffset by remember(trackId) { mutableFloatStateOf(0f) }
    var syncAnchors by remember(trackId) { mutableStateOf(emptyList<SoundprintLyricAnchor>()) }
    var selectedSyncLine by remember(trackId) { mutableIntStateOf(0) }
    var syncMenuOpen by remember(trackId) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val lines = remember(lyrics) { parseSoundprintLrc(lyrics) }
    val activeIndex = remember(lines, position, syncOffset, syncAnchors) {
        lines.indexOfLast { alignSoundprintLyricTime(it.timeMs, syncAnchors) + (syncOffset * 1000f).toLong() <= position }
    }
    val activeLine = lines.getOrNull(activeIndex)
    val activeWordIndex = remember(activeLine, position, syncOffset, syncAnchors) {
        activeLine?.words?.indexOfLast {
            alignSoundprintLyricTime(it.timeMs, syncAnchors) + (syncOffset * 1000f).toLong() <= position
        } ?: -1
    }

    LaunchedEffect(trackId) {
        lyrics = prefs.getString("lyrics:$trackId", "").orEmpty()
        source = prefs.getString("lyrics-source:$trackId", "").orEmpty()
        matchedTitle = prefs.getString("lyrics-match-title:$trackId", "").orEmpty()
        matchedArtist = prefs.getString("lyrics-match-artist:$trackId", "").orEmpty()
        matchedAlbum = prefs.getString("lyrics-match-album:$trackId", "").orEmpty()
        error = ""
        draft = lyrics
        syncOffset = prefs.getFloat("lyrics-offset:$trackId", 0f).coerceIn(-10f, 10f)
        val restored = runCatching {
            val raw = prefs.getString("lyrics-anchors:$trackId", "[]") ?: "[]"
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { index ->
                json.optJSONObject(index)?.let {
                    SoundprintLyricAnchor(it.optLong("lyric"), it.optLong("audio"))
                }?.takeIf { it.lyricMs >= 0L && it.audioMs >= 0L }
            }.take(100)
        }.getOrDefault(emptyList())
        syncAnchors = restored
    }

    LaunchedEffect(activeIndex, lines.size, karaokeMode) {
        if (!karaokeMode && activeIndex >= 0 && lines.isNotEmpty()) {
            listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }

    fun saveLyrics(
        value: String,
        origin: String,
        foundTitle: String = "",
        foundArtist: String = "",
        foundAlbum: String = ""
    ) {
        lyrics = value
        source = origin
        matchedTitle = foundTitle
        matchedArtist = foundArtist
        matchedAlbum = foundAlbum
        draft = value
        prefs.edit()
            .putString("lyrics:$trackId", value)
            .putString("lyrics-source:$trackId", origin)
            .putString("lyrics-match-title:$trackId", foundTitle)
            .putString("lyrics-match-artist:$trackId", foundArtist)
            .putString("lyrics-match-album:$trackId", foundAlbum)
            .apply()
    }

    fun clearLyrics() {
        lyrics = ""
        source = ""
        matchedTitle = ""
        matchedArtist = ""
        matchedAlbum = ""
        matches = emptyList()
        error = ""
        draft = ""
        karaokeMode = false
        showAll = false
        prefs.edit()
            .remove("lyrics:$trackId")
            .remove("lyrics-source:$trackId")
            .remove("lyrics-match-title:$trackId")
            .remove("lyrics-match-artist:$trackId")
            .remove("lyrics-match-album:$trackId")
            .remove("lyrics-offset:$trackId")
            .remove("lyrics-anchors:$trackId")
            .apply()
        syncOffset = 0f
        syncAnchors = emptyList()
    }

    fun findLyrics(candidate: LyricsLookupCandidate? = null) {
        if (loading) return
        loading = true
        error = ""
        scope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    if (candidate?.syncedLyrics != null || candidate?.plainLyrics != null) {
                        LyricsLookupResult(
                            lyrics = candidate.syncedLyrics ?: candidate.plainLyrics.orEmpty(),
                            source = candidate.provider,
                            synced = candidate.syncedLyrics != null,
                            matchedTitle = candidate.title,
                            matchedArtist = candidate.artist,
                            matchedAlbum = candidate.album
                        )
                    } else {
                        repository.search(
                            candidate?.title ?: title,
                            candidate?.artist ?: artist,
                            candidate?.album ?: album,
                            if (candidate != null && candidate.durationSeconds > 0.0) (candidate.durationSeconds * 1000).toLong() else duration
                        )
                    }
                }
                if (found == null) {
                    error = "Lyrics weren't found in the available catalogs. Check the track's title and artist tags, or import an .lrc file."
                } else {
                    saveLyrics(
                        found.lyrics,
                        found.source + if (found.synced) " · Synced" else " · Plain text",
                        found.matchedTitle,
                        found.matchedArtist,
                        found.matchedAlbum
                    )
                    matches = emptyList()
                }
            } catch (failure: Exception) {
                error = failure.message ?: "Couldn't fetch lyrics. Check your connection."
            } finally {
                loading = false
            }
        }
    }

    fun openKaraoke() {
        if (karaokeLoading) return
        if (lines.any { it.words.isNotEmpty() }) {
            karaokeMode = true
            return
        }
        karaokeLoading = true
        scope.launch {
            try {
                val enhanced = withContext(Dispatchers.IO) {
                    repository.fetchWordTimedLyrics(title, artist, album, duration)
                }
                if (!enhanced.isNullOrBlank()) {
                    saveLyrics(enhanced, source.substringBefore(" ·") + " · Word timed", matchedTitle, matchedArtist, matchedAlbum)
                }
            } catch (_: Exception) {
                // Keep line-synced lyrics if the optional word-timing source is unavailable.
            } finally {
                karaokeLoading = false
                karaokeMode = true
            }
        }
    }

    fun discoverCandidates(query: String = searchQuery) {
        if (loading) return
        val clean = query.trim()
        if (clean.isBlank()) {
            error = "Enter a song, artist, movie or soundtrack name."
            return
        }
        loading = true
        error = ""
        matches = emptyList()
        scope.launch {
            try {
                val found = withContext(Dispatchers.IO) { repository.discover(clean) }
                matches = found
                error = if (found.isEmpty()) "No matches found. Try a song title, artist, or soundtrack name separately." else ""
            } catch (failure: Exception) {
                error = failure.message ?: "Couldn't search for lyrics. Check your connection."
            } finally {
                loading = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty() }.getOrDefault("")
            }
            if (imported.isBlank()) {
                error = "That file is empty or unavailable."
            } else {
                saveLyrics(imported, "Imported file")
                editorOpen = false
                error = ""
            }
        }
    }

    fun saveSyncAnchor() {
        val line = lines.getOrNull(selectedSyncLine)
        if (line == null) {
            error = "Choose a lyric line to align first."
            return
        }
        val next = (syncAnchors.filter { abs(it.lyricMs - line.timeMs) > 10L } +
            SoundprintLyricAnchor(line.timeMs, position.coerceAtLeast(0L))).sortedBy { it.lyricMs }
        syncAnchors = next
        val json = JSONArray().apply {
            next.forEach { anchor ->
                put(JSONObject().put("lyric", anchor.lyricMs).put("audio", anchor.audioMs))
            }
        }
        prefs.edit().putString("lyrics-anchors:$trackId", json.toString()).apply()
    }

    fun changeOffset(delta: Float) {
        syncOffset = (syncOffset + delta).coerceIn(-10f, 10f)
        prefs.edit().putFloat("lyrics-offset:$trackId", syncOffset).apply()
    }

    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f), RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Lyrics", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (lyrics.isNotBlank()) "$source · saved on device" else "Synced, online and offline",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(
                    onClick = { importLauncher.launch(arrayOf("text/plain", "application/octet-stream")) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Import", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                TextButton(
                    onClick = { editorOpen = !editorOpen; draft = lyrics },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(if (editorOpen) "Close" else "Edit", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (editorOpen) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                    placeholder = { Text("Paste lyrics here…\n\nFor karaoke sync, use LRC timestamps like [00:12.50]First line", fontSize = 12.sp) },
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (draft.isBlank()) error = "Add some lyrics first."
                            else {
                                saveLyrics(draft, "Edited locally")
                                editorOpen = false
                                error = ""
                            }
                        },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp)
                    ) { Text("Save lyrics", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = { findLyrics() }, enabled = !loading, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)) {
                        Text(if (loading) "Searching…" else "Find online", fontSize = 12.sp)
                    }
                }
                if (error.isNotBlank()) Text(error, Modifier.padding(top = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                Text("LRC timestamps enable line-by-line sync. Plain text is shown as a readable lyrics sheet.", Modifier.padding(top = 8.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (lyrics.isNotBlank()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = .72f)).padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Selected lyrics", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (matchedTitle.isNotBlank()) "$matchedTitle · $matchedArtist"
                            else if (source.contains("Imported") || source.contains("Edited")) source.substringBefore(" · ")
                            else "Current track",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (matchedTitle.isNotBlank()) {
                        TextButton(
                            onClick = { clearLyrics(); searchQuery = title },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                        ) { Text("Change match", fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                    }
                }

                if (lines.isNotEmpty()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().heightIn(max = if (showAll) 448.dp else 256.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(lines) { index, line ->
                            val active = index == activeIndex
                            val upcoming = index == activeIndex + 1
                            val background by animateColorAsState(
                                when {
                                    active -> MaterialTheme.colorScheme.primary.copy(alpha = .10f)
                                    upcoming -> MaterialTheme.colorScheme.primary.copy(alpha = .045f)
                                    else -> Color.Transparent
                                },
                                tween(500, easing = FastOutSlowInEasing), label = "lyrics-line-background"
                            )
                            val color by animateColorAsState(
                                when {
                                    active -> MaterialTheme.colorScheme.primary
                                    upcoming -> MaterialTheme.colorScheme.onSurface.copy(alpha = .90f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f)
                                },
                                tween(500, easing = FastOutSlowInEasing), label = "lyrics-line-color"
                            )
                            val lineScale = if (active) 1.035f else if (upcoming) 1.01f else 1f
                            Surface(
                                color = background,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                                    .graphicsLayer { scaleX = lineScale; scaleY = lineScale }
                                    .clickable { seek(line.timeMs) }
                            ) {
                                Text(
                                    line.text.ifBlank { "♪" },
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    fontSize = if (active) 18.sp else if (upcoming) 16.sp else 14.sp,
                                    lineHeight = if (active) 25.sp else 22.sp,
                                    fontWeight = if (active) FontWeight.Bold else if (upcoming) FontWeight.SemiBold else FontWeight.Normal,
                                    color = color,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Synced lyrics · ${lines.size} lines", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(
                            onClick = { openKaraoke() },
                            enabled = !karaokeLoading,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            if (karaokeLoading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.8.dp)
                            else Icon(Icons.Filled.Fullscreen, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (karaokeLoading) "Finding timed lyrics…" else "Karaoke mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Text(
                        lyrics,
                        Modifier.fillMaxWidth().heightIn(max = if (showAll) 448.dp else 256.dp).verticalScroll(rememberScrollState()),
                        fontSize = 14.sp, lineHeight = 28.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .85f),
                        textAlign = TextAlign.Center
                    )
                }

                TextButton(
                    onClick = { showAll = !showAll },
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) { Text(if (showAll) "Show less" else "Show more", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }

                if (lines.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.background.copy(alpha = .72f)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Linked lyric alignment · ${syncAnchors.size} custom point${if (syncAnchors.size == 1) "" else "s"}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (syncAnchors.isNotEmpty()) {
                                TextButton(onClick = {
                                    syncAnchors = emptyList()
                                    prefs.edit().remove("lyrics-anchors:$trackId").apply()
                                }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp)) { Text("Clear sync points", fontSize = 10.sp) }
                            }
                        }
                        Text(
                            "If timing drifts, select the line you can hear right now, then save a sync point at the current playback position. Soundprint adjusts timing between points and remembers them for this track on this device.",
                            fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box {
                            OutlinedButton(
                                onClick = { syncMenuOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    lines.getOrNull(selectedSyncLine)?.let { "${soundprintLyricClock(it.timeMs)} · ${it.text}" } ?: "Choose lyric line to align",
                                    modifier = Modifier.weight(1f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(expanded = syncMenuOpen, onDismissRequest = { syncMenuOpen = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                                lines.forEachIndexed { index, line ->
                                    DropdownMenuItem(
                                        text = { Text("${soundprintLyricClock(line.timeMs)} · ${line.text}", maxLines = 2, fontSize = 11.sp) },
                                        onClick = { selectedSyncLine = index; syncMenuOpen = false }
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = { saveSyncAnchor() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .10f), contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Align selected line to current audio position (${soundprintLyricClock(position)})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Global offset " + when {
                                    syncOffset > 0f -> "· delayed ${"%.1f".format(syncOffset)}s"
                                    syncOffset < 0f -> "· advanced ${"%.1f".format(abs(syncOffset))}s"
                                    else -> "· default"
                                },
                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { changeOffset(-.5f) }, shape = CircleShape, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) { Text("−0.5s", fontSize = 11.sp) }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(onClick = { changeOffset(-syncOffset) }, shape = CircleShape, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) { Text("Reset", fontSize = 11.sp) }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(onClick = { changeOffset(.5f) }, shape = CircleShape, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) { Text("+0.5s", fontSize = 11.sp) }
                        }
                    }
                }
                TextButton(
                    onClick = { clearLyrics() },
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                ) { Text("Remove saved lyrics", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 16.dp)
                    .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.background.copy(alpha = .72f))
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Text("Bring this track to life", Modifier.padding(top = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Fetch lyrics, import an .lrc file, or add your own. Lyrics are cached locally once saved.",
                    Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Song, artist, movie or soundtrack", fontSize = 11.sp) },
                        shape = CircleShape,
                        textStyle = MaterialTheme.typography.bodySmall,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { discoverCandidates() })
                    )
                    OutlinedButton(
                        onClick = { discoverCandidates() },
                        enabled = !loading && searchQuery.isNotBlank(),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) { Text("Search matches", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                }
                if (error.isNotBlank()) Text(error, Modifier.padding(top = 12.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                if (matches.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        matches.forEachIndexed { index, match ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, if (index == 0) Color(0xFF16A36A).copy(alpha = .5f) else MaterialTheme.colorScheme.outline.copy(alpha = .45f), RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface).clickable(enabled = !loading) { findLyrics(match) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(match.title, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (index == 0) Text("Strongest match", fontSize = 10.sp, color = Color(0xFF16865B), fontWeight = FontWeight.SemiBold)
                                }
                                Text(
                                    match.artist + (if (match.album.isNotBlank()) " · ${match.album}" else "") +
                                        (if (match.durationSeconds > 0) " · ${soundprintLyricClock((match.durationSeconds * 1000).toLong())}" else ""),
                                    Modifier.padding(top = 2.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    when {
                                        match.syncedLyrics != null -> "Synced lyrics available"
                                        match.plainLyrics != null -> "Plain lyrics available"
                                        else -> "Lyrics will be fetched after selection"
                                    },
                                    Modifier.padding(top = 5.dp), fontSize = 10.sp,
                                    color = if (match.syncedLyrics != null || match.plainLyrics != null) Color(0xFF16865B) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = { findLyrics() },
                    enabled = !loading,
                    modifier = Modifier.padding(top = 16.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Filled.Search, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (loading) "Finding lyrics…" else "Find lyrics online", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Suggestions are not assumed to be correct. Confirm a song to fetch its lyrics.",
                    Modifier.padding(top = 8.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (karaokeMode && lines.isNotEmpty()) {
            Dialog(
                onDismissRequest = { karaokeMode = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { karaokeMode = false },
                        modifier = Modifier.align(Alignment.TopEnd).size(40.dp)
                    ) { Icon(Icons.Filled.Close, "Close karaoke mode", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("SOUNDPRINT · KARAOKE", Modifier.padding(bottom = 38.dp), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
                        Text(
                            lines.getOrNull(activeIndex - 1)?.text ?: "♪",
                            Modifier.fillMaxWidth().padding(bottom = 28.dp),
                            fontSize = 16.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f), textAlign = TextAlign.Center
                        )
                        AnimatedContent(targetState = activeIndex, label = "karaoke-current-line") { index ->
                            val line = lines.getOrNull(index)
                            Text(
                                buildAnnotatedString {
                                    if (line?.words?.isNotEmpty() == true) {
                                        line.words.forEachIndexed { wordIndex, word ->
                                            val wordColor = when {
                                                wordIndex == activeWordIndex -> MaterialTheme.colorScheme.primary
                                                wordIndex < activeWordIndex -> MaterialTheme.colorScheme.primary.copy(alpha = .75f)
                                                else -> MaterialTheme.colorScheme.primary.copy(alpha = .45f)
                                            }
                                            pushStyle(SpanStyle(color = wordColor))
                                            append(word.text)
                                            pop()
                                        }
                                    } else append(line?.text ?: "Waiting for lyrics")
                                },
                                Modifier.fillMaxWidth(),
                                fontSize = 38.sp, lineHeight = 48.sp, fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            lines.getOrNull(activeIndex + 1)?.text ?: "♪",
                            Modifier.fillMaxWidth().padding(top = 28.dp),
                            fontSize = 16.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f), textAlign = TextAlign.Center
                        )
                        Row(Modifier.padding(top = 44.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { if (activeIndex > 0) seek(lines[activeIndex - 1].timeMs) },
                                enabled = activeIndex > 0, shape = CircleShape
                            ) { Icon(Icons.Filled.ExpandLess, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Previous line", fontSize = 11.sp) }
                            OutlinedButton(
                                onClick = { if (activeIndex >= 0 && activeIndex < lines.lastIndex) seek(lines[activeIndex + 1].timeMs) },
                                enabled = activeIndex >= 0 && activeIndex < lines.lastIndex, shape = CircleShape
                            ) { Text("Next line", fontSize = 11.sp); Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.size(16.dp)) }
                        }
                        Text(
                            if (activeIndex >= 0) "${activeIndex + 1} / ${lines.size} lines" else "Waiting for the first lyric timestamp",
                            Modifier.padding(top = 20.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
