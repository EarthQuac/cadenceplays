package digital.anchorstudio.soundprint

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class PlayerMoodChoice(
    val id: String,
    val label: String,
    val clapMoodId: String,
    val icon: String
)

private val playerMoodChoices = listOf(
    PlayerMoodChoice("energetic", "Energetic", "workout", "zap"),
    PlayerMoodChoice("chill", "Chill", "chill", "waves"),
    PlayerMoodChoice("focus", "Focus", "focus", "brain"),
    PlayerMoodChoice("late-night", "Late night", "late-night", "moon"),
    PlayerMoodChoice("romantic", "Romantic", "romantic", "heart"),
    PlayerMoodChoice("sad", "Melancholy", "melancholy", "cloud-rain")
)

@Composable
fun SoundprintNowPlayingMoodSection(
    library: MusicLibraryManager,
    scanner: ClapLibraryScanner,
    playback: SoundprintPlaybackConnection,
    currentTrackId: String,
    queueCount: Int,
    onOpenQueue: () -> Unit
) {
    val scan by scanner.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var selectedMood by remember { mutableStateOf<PlayerMoodChoice?>(null) }
    var tracks by remember { mutableStateOf(emptyList<MusicTrack>()) }
    var profiles by remember { mutableStateOf(emptyMap<String, ClapMoodProfile>()) }
    var corrections by remember { mutableStateOf(emptyMap<String, String>()) }
    var moodStatus by remember { mutableStateOf<String?>(null) }
    var showSongStatuses by remember { mutableStateOf(false) }
    var queuedByMood by remember { mutableStateOf(emptyMap<String, Set<String>>()) }

    LaunchedEffect(scanner) {
        scanner.refreshSummary()
        corrections = scanner.moodCorrections()
    }

    // Read the same saved CLAP profiles and correction records used by Discover.
    // Each completed scan is therefore immediately available here without a second model.
    LaunchedEffect(library, scanner) {
        while (true) {
            val snapshot = withContext(Dispatchers.IO) {
                val songs = library.allSongs()
                val savedProfiles = songs.mapNotNull { song ->
                    library.clapMoodProfile(song)?.let { song.id to it }
                }.toMap()
                Triple(songs, savedProfiles, scanner.moodCorrections())
            }
            tracks = snapshot.first
            profiles = snapshot.second
            corrections = snapshot.third
            delay(1400L)
        }
    }

    val targetMood = selectedMood?.let { choice ->
        SoundprintMood.entries.firstOrNull { it.id == choice.clapMoodId }
    }
    val recommendations = remember(selectedMood, targetMood, tracks, profiles, corrections, currentTrackId) {
        if (selectedMood == null || targetMood == null) emptyList()
        else tracks.mapNotNull { song ->
            if (song.id == currentTrackId) return@mapNotNull null
            val correction = corrections[song.id]
            if (correction != null) {
                if (correction == targetMood.id) song to 1f else null
            } else {
                val score = profiles[song.id]?.score(targetMood) ?: return@mapNotNull null
                if (score >= 0.72f) song to score else null
            }
        }.sortedByDescending { it.second }.take(20)
    }
    val recommendationIds = recommendations.map { it.first.id }

    LaunchedEffect(selectedMood?.id, recommendationIds, scan.pending, scan.running, scan.modelDownloadRequired, scan.failed) {
        val mood = selectedMood ?: return@LaunchedEffect
        val existingPlayer = playback.controller
        val existingQueueIds = existingPlayer?.let { player ->
            (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
        }.orEmpty()
        val alreadyQueued = queuedByMood[mood.id].orEmpty()
        val newMatches = recommendations.filter { it.first.id !in existingQueueIds && it.first.id !in alreadyQueued }
        if (newMatches.isNotEmpty()) {
            playback.enqueue(newMatches.map { it.first }, next = true)
            queuedByMood = queuedByMood + (mood.id to (alreadyQueued + newMatches.map { it.first.id }))
        }

        val unanalysed = tracks.count { song ->
            song.id != currentTrackId &&
                profiles[song.id] == null &&
                corrections[song.id] == null &&
                scan.failureMessages[song.id] == null
        }
        moodStatus = when {
            scan.modelDownloadRequired -> "Download the offline CLAP model once to start matching your songs."
            newMatches.isNotEmpty() && unanalysed > 0 ->
                "Added " + newMatches.size + " matching songs. Checking " + unanalysed + " songs without saved CLAP profiles; new matches will join your queue automatically."
            newMatches.isNotEmpty() ->
                "Added " + newMatches.size + " matching songs to your queue."
            scan.running && unanalysed > 0 ->
                "Checking " + unanalysed + " songs without saved CLAP profiles. New matches will join your queue automatically."
            recommendations.isNotEmpty() ->
                recommendations.size.toString() + " matching " + (if (recommendations.size == 1) "song is" else "songs are") + " already in your queue."
            scan.failed > 0 && !scan.running ->
                "Some songs need attention. Retry the failed scans below."
            scan.pending == 0 && !scan.running ->
                "No strong mood matches were found in your library. Try another mood."
            else -> "Preparing your mood matches…"
        }
    }

    val completeCount = tracks.count { profiles.containsKey(it.id) || corrections.containsKey(it.id) }
    val failedCount = tracks.count { scan.failureMessages.containsKey(it.id) }
    val scanningCount = tracks.count { scan.currentTrackId == it.id }
    val pendingCount = (tracks.size - completeCount - failedCount - scanningCount).coerceAtLeast(0)
    val processedCount = (completeCount + failedCount).coerceAtMost(tracks.size)
    val progress = if (tracks.isEmpty()) 0f else processedCount.toFloat() / tracks.size.toFloat()
    val animatedProgress by animateFloatAsState(progress, tween(500), label = "now-playing-clap-progress")
    val statusColor = when {
        failedCount > 0 && !scan.running -> Color(0xFFD97968)
        scan.running -> MaterialTheme.colorScheme.primary
        completeCount == tracks.size && tracks.isNotEmpty() -> Color(0xFF32A56A)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                CadenceMoodGlyph("brain", MaterialTheme.colorScheme.primary, Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text("Find by mood", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onOpenQueue,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.QueueMusic, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text("Queue · " + queueCount, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160)) + expandVertically(),
            exit = fadeOut(tween(120)) + shrinkVertically()
        ) {
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Find by mood", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    CadenceMoodGlyph("brain", MaterialTheme.colorScheme.primary, Modifier.size(20.dp))
                }

                Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    playerMoodChoices.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { mood ->
                                val active = selectedMood?.id == mood.id
                                Row(
                                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.background)
                                        .clickable {
                                            selectedMood = mood
                                            moodStatus = "Finding " + mood.label.lowercase() + " songs in your library…"
                                            if (!scan.running) scanner.startScan()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CadenceMoodGlyph(mood.icon, MaterialTheme.colorScheme.primary, Modifier.size(17.dp))
                                    Spacer(Modifier.width(9.dp))
                                    Text(
                                        mood.label,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (active && scan.running) {
                                        CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.8.dp)
                                    } else if (active) {
                                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                Text(
                    "Soundprint uses saved CLAP mood profiles first. Songs without a profile are analysed on this device and matching tracks join your queue automatically.",
                    Modifier.padding(top = 12.dp),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (selectedMood != null && moodStatus != null) {
                    Text(
                        moodStatus.orEmpty(),
                        Modifier.fillMaxWidth().padding(top = 9.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = if (scan.stage == "Error") Color(0xFFD97968) else MaterialTheme.colorScheme.primary
                    )
                }

                if (tracks.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    scan.running && scan.currentTitle.isNotBlank() -> "Analysing your songs"
                                    scan.running -> "Preparing your library"
                                    failedCount > 0 -> "Scan finished · attention needed"
                                    completeCount == tracks.size -> "Your library is ready"
                                    else -> "Ready to discover"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                completeCount.toString() + " completed · " + pendingCount + " pending · " + failedCount + " error",
                                Modifier.padding(top = 3.dp),
                                fontSize = 10.sp,
                                color = statusColor
                            )
                        }
                        Text(
                            (progress * 100).toInt().toString() + "%",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(5.dp).clip(CircleShape),
                        color = Color(0xFF32A56A),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    if (scan.currentTrackId != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.8.dp)
                            Text(
                                "Scanning · " + scan.currentTitle + " · " + scan.stage,
                                Modifier.padding(start = 8.dp).weight(1f),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (scan.stage == "Error") {
                        Text(
                            scan.message,
                            Modifier.padding(top = 7.dp),
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFFD97968)
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .clickable { showSongStatuses = !showSongStatuses }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Song status",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                if (showSongStatuses) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                null,
                                modifier = Modifier.padding(start = 3.dp).size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (failedCount > 0) {
                            TextButton(
                                onClick = { scanner.retryFailed() },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Retry errors", fontSize = 10.sp)
                            }
                        }
                    }

                    AnimatedVisibility(visible = showSongStatuses, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            tracks.forEach { song ->
                                val failure = scan.failureMessages[song.id]
                                val isComplete = profiles.containsKey(song.id) || corrections.containsKey(song.id)
                                val isCurrent = scan.currentTrackId == song.id
                                val markColor = when {
                                    failure != null -> Color(0xFFD97968)
                                    isComplete -> Color(0xFF32A56A)
                                    isCurrent -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                }
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    when {
                                        failure != null -> Icon(Icons.Filled.ErrorOutline, null, Modifier.size(15.dp), tint = markColor)
                                        isComplete -> Icon(Icons.Filled.Check, null, Modifier.size(15.dp), tint = markColor)
                                        isCurrent -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.7.dp)
                                        else -> Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                                            Box(Modifier.size(5.dp).clip(CircleShape).background(markColor))
                                        }
                                    }
                                    Column(Modifier.weight(1f).padding(start = 8.dp, end = 6.dp)) {
                                        Text(song.title, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            when {
                                                failure != null -> failure
                                                isComplete -> "Scanned successfully"
                                                isCurrent -> scan.stage.ifBlank { "Analysing on this device" }
                                                else -> "Waiting to be scanned"
                                            },
                                            fontSize = 9.sp,
                                            color = if (failure != null) Color(0xFFD97968) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        when {
                                            failure != null -> "Error"
                                            isComplete -> "Done"
                                            isCurrent -> "Scanning"
                                            else -> "Pending"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = markColor
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "Add music to your library to discover songs by mood.",
                        Modifier.padding(top = 13.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (scan.modelDownloadRequired) {
                    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Text(
                            "Mood discovery needs a one-time offline model download. Download it once while online; analysis then runs on this device.",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { scanner.startScan(allowModelDownload = true) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) { Text("Download model and analyse", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CadenceMoodGlyph(name: String, tint: Color, modifier: Modifier = Modifier) {
    val paths = remember(name) {
        val pathData = when (name) {
            "zap" -> listOf("M15.914 4a1.5 1.5 0 0 0-2.474-1.561l-9 9A1.5 1.5 0 0 0 5.5 14h4.002a.5.5 0 0 1 .471.666L8.086 20a1.5 1.5 0 0 0 2.475 1.56l9-9A1.5 1.5 0 0 0 18.5 10h-3.997a.5.5 0 0 1-.472-.667z")
            "waves" -> listOf(
                "M2 12q2.5 2 5 0t5 0 5 0 5 0",
                "M2 19q2.5 2 5 0t5 0 5 0 5 0",
                "M2 5q2.5 2 5 0t5 0 5 0 5 0"
            )
            "brain" -> listOf(
                "M12 18V5",
                "M15 13a4.17 4.17 0 0 1-3-4 4.17 4.17 0 0 1-3 4",
                "M17.707 6.294A3 3 0 1 0 12 5a3 3 0 1 0-5.707 1.294",
                "M17.996 5.125a4 4 0 0 1 2.526 5.77",
                "M18 18a4 4 0 0 0 2-7.465",
                "M19.967 17.482a4 4 0 1 1-7.967.517 4 4 0 1 1-7.967-.517",
                "M6 18a4 4 0 0 1-2-7.465",
                "M6.004 5.125a4 4 0 0 0-2.526 5.77"
            )
            "moon" -> listOf("M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401")
            "heart" -> listOf("M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5")
            "cloud-rain" -> listOf(
                "M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242",
                "M16 14v6",
                "M8 14v6",
                "M12 16v6"
            )
            else -> emptyList()
        }
        pathData.map { androidx.compose.ui.graphics.vector.PathParser().parsePathString(it).toPath() }
    }
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            paths.forEach { drawPath(it, tint, style = stroke) }
        }
    }
}
