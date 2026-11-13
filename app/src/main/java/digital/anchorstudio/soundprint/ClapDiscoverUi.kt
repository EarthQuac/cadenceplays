package digital.anchorstudio.soundprint

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ClapLibraryScanState(
    val running: Boolean = false,
    val downloadingModel: Boolean = false,
    val modelPercent: Int = 0,
    val completed: Int = 0,
    val total: Int = 0,
    val failed: Int = 0,
    val pending: Int = 0,
    val currentTitle: String = "",
    val currentTrackId: String? = null,
    val lastCompletedTrackId: String? = null,
    val stage: String = "",
    val message: String = "",
    val failureMessages: Map<String,String> = emptyMap()
) {
    val percent: Int get() = if (total <= 0) 0 else (completed * 100 / total).coerceIn(0,100)
}

/**
 * One sequential scanner shared by Discover and Settings. Each completed track
 * is committed immediately; cancelling or process death never discards work.
 */
class ClapLibraryScanner(context: Context, private val library: MusicLibraryManager) : AutoCloseable {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val modelManager = ClapModelManager(app)
    private val preferences = app.getSharedPreferences("soundprint_clap_scan", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(ClapLibraryScanState(
        failed = readFailures().size, failureMessages = readFailures()
    ))
    val state: StateFlow<ClapLibraryScanState> = mutableState
    private var scanJob: Job? = null
    private var pendingScanMode: Boolean? = null

    fun refreshSummary() {
        if (mutableState.value.running) return
        scope.launch {
            val tracks = library.allSongs()
            val ids = tracks.map { it.id }.toSet()
            val corrected = moodCorrections().keys.intersect(ids)
            val cached = tracks.filter { library.clapMoodProfile(it) != null }.map { it.id }.toSet()
            val completedIds = cached + corrected
            val failures = readFailures().filterKeys { it in ids && it !in corrected && it !in cached }
            val completed = completedIds.size
            mutableState.value = mutableState.value.copy(
                completed = completed, total = tracks.size, failed = failures.size,
                pending = (tracks.size - completed - failures.size).coerceAtLeast(0),
                failureMessages = failures
            )
        }
    }

    fun savedMoods(): Set<String> =
        preferences.getStringSet("saved_moods", emptySet())?.toSet().orEmpty()

    fun setMoodSaved(moodId: String, saved: Boolean) {
        require(SoundprintMood.entries.any { it.id == moodId })
        val updated = savedMoods().toMutableSet()
        if (saved) updated.add(moodId) else updated.remove(moodId)
        preferences.edit().putStringSet("saved_moods", updated).apply()
    }

    fun autoScanEnabled(): Boolean = preferences.getBoolean("background_scan_enabled", false)

    fun setAutoScanEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("background_scan_enabled", enabled).apply()
        if (enabled) startScan() else pause()
    }

    @Synchronized
    fun startScan(retryFailedOnly: Boolean = false) {
        if (scanJob?.isCompleted == false) {
            pendingScanMode = pendingScanMode?.let { it && retryFailedOnly } ?: retryFailedOnly
            return
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var engine: ClapOnnxEngine? = null
            try {
                val allTracks = library.allSongs()
                val allIds = allTracks.map { it.id }.toSet()
                val correctedIds = moodCorrections().keys.intersect(allIds)
                val cachedIds = allTracks.filter { library.clapMoodProfile(it) != null }.map { it.id }.toSet()
                val completedIds = cachedIds + correctedIds
                val errors = readFailures().filterKeys {
                    it in allIds && it !in correctedIds && it !in cachedIds
                }.toMutableMap()
                val requested = if (retryFailedOnly) {
                    allTracks.filter { errors.containsKey(it.id) }
                } else {
                    allTracks.filter { it.id !in completedIds && it.id !in errors }
                }
                if (retryFailedOnly) requested.forEach { errors.remove(it.id) }
                writeFailures(errors)

                var done = completedIds.size
                var pending = requested.size
                val total = allTracks.size
                if (requested.isEmpty()) {
                    mutableState.value = mutableState.value.copy(
                        running = false, downloadingModel = false,
                        completed = done, total = total, failed = errors.size, pending = 0,
                        failureMessages = errors.toMap(), currentTitle = "", currentTrackId = null,
                        message = when {
                            allTracks.isEmpty() -> "Your library has no tracks to analyse."
                            retryFailedOnly -> "There are no failed tracks to retry."
                            errors.isNotEmpty() -> "No new tracks are pending. Retry the failed tracks to try them again."
                            else -> "All tracks have saved CLAP profiles or manual mood assignments."
                        }
                    )
                    return@launch
                }

                mutableState.value = ClapLibraryScanState(
                    running = true, completed = done, total = total, failed = errors.size,
                    pending = pending, failureMessages = errors.toMap(),
                    stage = "Preparing", message = "Preparing offline music intelligence…"
                )
                val activeEngine = ClapOnnxEngine(app)
                engine = activeEngine
                activeEngine.prepare { progress ->
                    mutableState.value = mutableState.value.copy(
                        downloadingModel = true, modelPercent = progress.percent,
                        stage = "Downloading CLAP model",
                        message = "Installing the offline model · " + progress.currentAsset
                    )
                }
                mutableState.value = mutableState.value.copy(
                    downloadingModel = false, stage = "Scanning your library",
                    message = "CLAP is analysing audio on this device."
                )
                requested.forEach { track ->
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    mutableState.value = mutableState.value.copy(
                        currentTitle = track.title, currentTrackId = track.id,
                        stage = "Decoding audio", message = "Analysing " + track.title
                    )
                    try {
                        val scores = activeEngine.analyse(track) {
                            mutableState.value = mutableState.value.copy(
                                stage = "Scoring with CLAP",
                                message = "Scoring the audio mood for " + track.title
                            )
                        }
                        mutableState.value = mutableState.value.copy(
                            stage = "Saving mood profile",
                            message = "Saving the mood profile for " + track.title
                        )
                        library.saveClapMoodProfile(track, scores)
                        errors.remove(track.id)
                        done++
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        errors[track.id] = error.message ?: "CLAP could not analyse this audio."
                    }
                    pending = (pending - 1).coerceAtLeast(0)
                    writeFailures(errors)
                    mutableState.value = mutableState.value.copy(
                        completed = done, total = total, failed = errors.size, pending = pending,
                        failureMessages = errors.toMap(), currentTitle = track.title,
                        currentTrackId = null, lastCompletedTrackId = track.id,
                        stage = "Scanning your library",
                        message = "Processed " + track.title
                    )
                }
                writeFailures(errors)
                mutableState.value = mutableState.value.copy(
                    running = false, downloadingModel = false, completed = done,
                    total = total, failed = errors.size, pending = 0,
                    failureMessages = errors.toMap(), currentTitle = "", currentTrackId = null,
                    stage = if (errors.isEmpty()) "Ready" else "Retry needed",
                    message = if (errors.isEmpty()) "Your library is ready for offline mood discovery." else "Scan finished. Some tracks need another attempt."
                )
            } catch (cancel: CancellationException) {
                mutableState.value = mutableState.value.copy(
                    running = false, downloadingModel = false, currentTitle = "", currentTrackId = null,
                    stage = "Paused",
                    message = "Scan paused. Completed profiles are saved and will be reused next time."
                )
                throw cancel
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    running = false, downloadingModel = false, currentTitle = "", currentTrackId = null,
                    stage = "Error",
                    message = error.message ?: "CLAP could not be prepared on this device."
                )
            } finally {
                engine?.close()
                val nextScan = synchronized(this@ClapLibraryScanner) {
                    scanJob = null
                    val queued = pendingScanMode
                    pendingScanMode = null
                    queued
                }
                if (nextScan != null) startScan(nextScan)
            }
        }
        scanJob = job
        job.start()
    }

    @Synchronized
    fun pause() {
        pendingScanMode = null
        scanJob?.cancel()
    }

    fun retryFailed() = startScan(retryFailedOnly = true)

    fun modelInstalled(): Boolean = modelManager.isInstalled()

    fun moodCorrections(): Map<String,String> = runCatching {
        val raw = preferences.getString("mood_corrections", null) ?: return emptyMap()
        val json = JSONObject(raw)
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap())

    fun saveMoodCorrection(trackId: String, moodId: String) {
        require(SoundprintMood.entries.any { it.id == moodId })
        val updated = moodCorrections().toMutableMap()
        updated[trackId] = moodId
        preferences.edit().putString("mood_corrections", JSONObject(updated as Map<*, *>).toString()).apply()
        val failures = readFailures().toMutableMap()
        failures.remove(trackId)
        writeFailures(failures)
        mutableState.value = mutableState.value.copy(failed = failures.size, failureMessages = failures)
    }

    fun removeMoodCorrection(trackId: String) {
        val updated = moodCorrections().toMutableMap()
        updated.remove(trackId)
        preferences.edit().putString("mood_corrections", JSONObject(updated as Map<*, *>).toString()).apply()
    }

    private fun readFailures(): Map<String,String> = runCatching {
        val raw = preferences.getString("failures", null) ?: return emptyMap()
        val root = JSONObject(raw)
        root.keys().asSequence().associateWith { root.getString(it) }
    }.getOrDefault(emptyMap())

    private fun writeFailures(values: Map<String,String>) {
        preferences.edit().putString("failures", JSONObject(values as Map<*, *>).toString()).apply()
    }

    override fun close() {
        pause()
        scope.cancel()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClapDiscoverScreen(
    libraryManager: MusicLibraryManager,
    scanner: ClapLibraryScanner,
    playback: SoundprintPlaybackConnection,
    onBack: () -> Unit
) {
    val scan by scanner.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { scanner.refreshSummary() }
    var tracks by remember { mutableStateOf(emptyList<MusicTrack>()) }
    var profiles by remember { mutableStateOf(emptyMap<String,ClapMoodProfile>()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            val allTracks = libraryManager.allSongs()
            val allProfiles = allTracks.mapNotNull { track ->
                libraryManager.clapMoodProfile(track)?.let { track.id to it }
            }.toMap()
            allTracks to allProfiles
        }
        tracks = loaded.first
        profiles = loaded.second
    }
    androidx.compose.runtime.LaunchedEffect(scan.lastCompletedTrackId) {
        val id = scan.lastCompletedTrackId ?: return@LaunchedEffect
        val profile = withContext(Dispatchers.IO) {
            libraryManager.song(id)?.let { libraryManager.clapMoodProfile(it) }
        }
        if (profile != null) profiles = profiles + (id to profile)
    }
    androidx.compose.runtime.LaunchedEffect(tracks.size, profiles.size) {
        // Fill missing mood matches quietly while the library is available.
        if (tracks.isNotEmpty() && profiles.size < tracks.size && !scan.running) {
            scanner.startScan()
        }
    }
    var corrections by remember { mutableStateOf(scanner.moodCorrections()) }
    var savedMoods by remember { mutableStateOf(scanner.savedMoods()) }
    var selectedMood by remember { mutableStateOf<SoundprintMood?>(null) }
    var editingTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var insightTrackId by remember { mutableStateOf<String?>(null) }

    val recommendations = selectedMood?.let { mood ->
        tracks.mapNotNull { track ->
            val corrected = corrections[track.id]
            if (corrected == mood.id) return@mapNotNull track to 1f
            if (corrected != null) return@mapNotNull null
            val profile = profiles[track.id] ?: return@mapNotNull null
            val score = profile.score(mood)
            if (score < 0.72f) null else track to score
        }.sortedByDescending { it.second }.take(30)
    }.orEmpty()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Discover", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Your library, in a different light", modifier = Modifier.padding(top = 2.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Icon(Icons.Filled.Explore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
        }
        Text(
            "Find your next mood.",
            modifier = Modifier.padding(top = 22.dp),
            fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Pick a feeling or a moment. Cadence finds the tracks in your own library that fit.",
            modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
            fontSize = 13.sp, lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Choose a mood", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text("Tap one to jump straight to matching songs.", modifier = Modifier.padding(top = 4.dp, bottom = 12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SoundprintMood.entries.forEach { mood ->
                val active = selectedMood == mood
                Text(
                    mood.title,
                    modifier = Modifier.clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .border(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), CircleShape)
                        .clickable { selectedMood = mood }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (savedMoods.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("SAVED MOODS", fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                SoundprintMood.entries.filter { it.id in savedMoods }.forEach { mood ->
                    Text(
                        mood.title,
                        modifier = Modifier.clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selectedMood = mood }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (selectedMood != null) {
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PICKED FOR THIS MOOD", fontSize = 10.sp, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(selectedMood!!.title, modifier = Modifier.padding(top = 3.dp), fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                TextButton(onClick = {
                    val moodId = selectedMood!!.id
                    scanner.setMoodSaved(moodId, moodId !in savedMoods)
                    savedMoods = scanner.savedMoods()
                }) { Text(if (selectedMood!!.id in savedMoods) "Saved" else "Save mood") }
                if (recommendations.isNotEmpty()) {
                    TextButton(onClick = { playback.playTracks(recommendations.map { it.first }) }) { androidx.compose.material3.Icon(Icons.Filled.PlayArrow, null); Text("Play all") }
                    TextButton(onClick = { playback.playTracks(recommendations.map { it.first }, shuffle = true) }) { androidx.compose.material3.Icon(Icons.Filled.Shuffle, null) }
                }
            }
            if (recommendations.isEmpty()) {
                Text("No matches yet.", modifier = Modifier.padding(vertical = 16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                recommendations.forEachIndexed { index, (track, score) ->
                    Row(Modifier.fillMaxWidth().clickable { playback.playTracks(recommendations.map { it.first }, startIndex = index) }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        var expanded by remember(track.id) { mutableStateOf(false) }
                        androidx.compose.foundation.layout.Box {
                            Text("···", modifier = Modifier.clickable { expanded = true }.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text("Song insights") }, onClick = { expanded = false; insightTrackId = if (insightTrackId == track.id) null else track.id })
                                DropdownMenuItem(text = { Text("Change mood") }, onClick = { expanded = false; editingTrack = track })
                                if (corrections.containsKey(track.id)) DropdownMenuItem(text = { Text("Reset correction") }, onClick = { scanner.removeMoodCorrection(track.id); corrections = scanner.moodCorrections(); expanded = false })
                            }
                        }
                    }
                    if (insightTrackId == track.id) {
                        val profile = profiles[track.id]
                        Column(Modifier.fillMaxWidth().padding(start = 33.dp, end = 8.dp, bottom = 10.dp)) {
                            Text("SONG INSIGHTS", fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            if (profile != null) {
                                Text("Primary mood · ${profile.primaryMood?.title ?: "Unclassified"}", modifier = Modifier.padding(top = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("Other moods · " + profile.scores.entries.sortedByDescending { it.value }.drop(1).take(3).joinToString(" · ") { "${SoundprintMood.entries.firstOrNull { mood -> mood.id == it.key }?.title ?: it.key} ${(it.value * 100).toInt()}%" }, modifier = Modifier.padding(top = 3.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("This song has a manually selected mood.", modifier = Modifier.padding(top = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                }
            }
        }

        if (scan.failureMessages.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text("SONGS WITHOUT A MATCH", fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            scan.failureMessages.forEach { (id, message) ->
                val track = tracks.firstOrNull { it.id == id }
                if (track != null) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text(track.artist, modifier = Modifier.padding(top = 2.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No mood match is available for this song yet.", modifier = Modifier.padding(top = 3.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { editingTrack = track }) { Text("Assign mood") }
                    }
                }
            }
        }
    }

    if (editingTrack != null) {
        AlertDialog(
            onDismissRequest = { editingTrack = null },
            title = { Text("Correct this song's mood") },
            text = {
                Column(Modifier.height(360.dp).verticalScroll(rememberScrollState())) {
                    SoundprintMood.entries.forEach { mood ->
                        TextButton(onClick = {
                            scanner.saveMoodCorrection(editingTrack!!.id, mood.id)
                            corrections = scanner.moodCorrections()
                            editingTrack = null
                        }, modifier = Modifier.fillMaxWidth()) { Text(mood.title, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editingTrack = null }) { Text("Close") } }
        )
    }
}
