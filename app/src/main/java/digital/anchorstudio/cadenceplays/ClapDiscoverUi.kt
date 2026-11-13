package digital.anchorstudio.cadenceplays

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
import androidx.compose.material.icons.filled.GraphicEq
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class ClapLibraryScanState(
    val running: Boolean = false,
    val downloadingModel: Boolean = false,
    val modelPercent: Int = 0,
    val completed: Int = 0,
    val total: Int = 0,
    val failed: Int = 0,
    val currentTitle: String = "",
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
    private val preferences = app.getSharedPreferences("cadence_clap_scan", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(ClapLibraryScanState(
        failed = readFailures().size, failureMessages = readFailures()
    ))
    val state: StateFlow<ClapLibraryScanState> = mutableState
    private var scanJob: Job? = null

    fun autoScanEnabled(): Boolean = preferences.getBoolean("background_scan_enabled", false)

    fun setAutoScanEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("background_scan_enabled", enabled).apply()
        if (enabled) startScan() else pause()
    }

    fun startScan(retryFailedOnly: Boolean = false) {
        if (scanJob?.isActive == true) return
        val allTracks = library.allSongs()
        val allIds = allTracks.map { it.id }.toSet()
        val failureMap = readFailures().filterKeys { it in allIds }
        val requested = if (retryFailedOnly) allTracks.filter { failureMap.containsKey(it.id) } else allTracks
        val tracks = requested.filter { library.clapMoodProfile(it) == null }
        if (tracks.isEmpty()) {
            mutableState.value = mutableState.value.copy(
                running = false, downloadingModel = false, completed = requested.size,
                total = requested.size, failed = failureMap.size,
                failureMessages = failureMap, message = if (requested.isEmpty()) "Your library has no tracks to analyse." else if (retryFailedOnly) "There are no failed tracks to retry." else "All available tracks already have saved CLAP profiles."
            )
            return
        }
        scanJob = scope.launch {
            var done = 0
            val errors = readFailures().filterKeys { id -> allIds.contains(id) }.toMutableMap()
            mutableState.value = ClapLibraryScanState(
                running = true, completed = 0, total = tracks.size,
                failed = errors.size, failureMessages = errors, message = "Preparing offline music intelligence…"
            )
            val engine = ClapOnnxEngine(app)
            try {
                engine.prepare { progress ->
                    mutableState.value = mutableState.value.copy(
                        downloadingModel = true, modelPercent = progress.percent,
                        stage = "Downloading CLAP model", message = "Installing the offline model · ${progress.currentAsset}"
                    )
                }
                mutableState.value = mutableState.value.copy(
                    downloadingModel = false, stage = "Scanning your library",
                    message = "CLAP is analysing audio on this device."
                )
                tracks.forEach { track ->
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val cached = library.clapMoodProfile(track)
                    if (cached != null) {
                        errors.remove(track.id)
                    } else {
                        mutableState.value = mutableState.value.copy(
                            currentTitle = track.title, stage = "Decoding and scoring",
                            message = "Analysing ${track.title}"
                        )
                        try {
                            val scores = engine.analyse(track)
                            library.saveClapMoodProfile(track, scores)
                            errors.remove(track.id)
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (error: Exception) {
                            errors[track.id] = error.message ?: "CLAP could not analyse this audio."
                        }
                        writeFailures(errors)
                    }
                    done++
                    mutableState.value = mutableState.value.copy(
                        completed = done, total = tracks.size, failed = errors.size,
                        failureMessages = errors.toMap(), currentTitle = track.title
                    )
                }
                writeFailures(errors)
                mutableState.value = mutableState.value.copy(
                    running = false, downloadingModel = false, completed = done,
                    failed = errors.size, failureMessages = errors.toMap(),
                    message = if (errors.isEmpty()) "Your library is ready for offline mood discovery." else "Scan finished. Some tracks need another attempt."
                )
            } catch (cancel: CancellationException) {
                mutableState.value = mutableState.value.copy(
                    running = false, downloadingModel = false, message = "Scan paused. Completed profiles are saved and will be reused next time."
                )
                throw cancel
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    running = false, downloadingModel = false,
                    message = error.message ?: "CLAP could not be prepared on this device."
                )
            } finally {
                engine.close()
            }
        }
    }

    fun pause() {
        scanJob?.cancel()
        scanJob = null
    }

    fun retryFailed() = startScan(retryFailedOnly = true)

    fun modelInstalled(): Boolean = modelManager.isInstalled()

    fun moodCorrections(): Map<String,String> = runCatching {
        val raw = preferences.getString("mood_corrections", null) ?: return emptyMap()
        val json = JSONObject(raw)
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap())

    fun saveMoodCorrection(trackId: String, moodId: String) {
        require(CadenceMood.entries.any { it.id == moodId })
        val updated = moodCorrections().toMutableMap()
        updated[trackId] = moodId
        preferences.edit().putString("mood_corrections", JSONObject(updated as Map<*, *>).toString()).apply()
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
    playback: CadencePlaybackConnection,
    onBack: () -> Unit
) {
    val scan by scanner.state.collectAsState()
    val tracks = remember { libraryManager.allSongs() }
    val profiles = remember(tracks, scan.completed, scan.failed, scan.running) {
        tracks.mapNotNull { track -> libraryManager.clapMoodProfile(track)?.let { track.id to it } }.toMap()
    }
    var corrections by remember { mutableStateOf(scanner.moodCorrections()) }
    var selectedMood by remember { mutableStateOf<CadenceMood?>(null) }
    var editingTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var showRetryInfo by remember { mutableStateOf(false) }

    val recommendations = selectedMood?.let { mood ->
        tracks.mapNotNull { track ->
            val profile = profiles[track.id] ?: return@mapNotNull null
            val corrected = corrections[track.id]
            val score = when {
                corrected == mood.id -> 1f
                corrected != null -> 0f
                else -> profile.score(mood)
            }
            if (score <= 0f) null else track to score
        }.sortedByDescending { it.second }.take(30)
    }.orEmpty()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("‹", modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 30.sp, color = MaterialTheme.colorScheme.onSurface)
            Column(Modifier.weight(1f)) {
                Text("DISCOVER", fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text("Find your next mood.", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
            androidx.compose.material3.Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
        }
        Text("CLAP matches the sound of your own music. Your library stays on this device.", modifier = Modifier.padding(top = 6.dp, bottom = 18.dp), fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("OFFLINE MUSIC INTELLIGENCE", fontSize = 10.sp, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(if (scanner.modelInstalled()) "CLAP model installed" else "Install the CLAP model", modifier = Modifier.padding(top = 5.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (scanner.modelInstalled()) "Mood analysis runs locally, without a connection." else "One-time model download · about 205 MB", modifier = Modifier.padding(top = 3.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (scan.running) {
                    OutlinedButton(onClick = scanner::pause) { androidx.compose.material3.Icon(Icons.Filled.Pause, null); Spacer(Modifier.width(5.dp)); Text("Pause") }
                } else {
                    Button(onClick = { scanner.startScan() }, enabled = tracks.isNotEmpty()) { Text(if (profiles.isEmpty()) "Scan library" else "Scan missing") }
                }
            }
            if (scan.downloadingModel) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(progress = { scan.modelPercent / 100f }, modifier = Modifier.fillMaxWidth())
                Text("Downloading model · ${scan.modelPercent}%", modifier = Modifier.padding(top = 5.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (scan.running || scan.total > 0) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(progress = { scan.percent / 100f }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${scan.completed} of ${scan.total} analysed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${scan.percent}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                if (scan.currentTitle.isNotBlank()) Text("Now analysing · ${scan.currentTitle}", modifier = Modifier.padding(top = 5.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (scan.message.isNotBlank()) Text(scan.message, modifier = Modifier.padding(top = 10.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${profiles.size} ready", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${scan.failureMessages.size} need retry", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (scan.failureMessages.isNotEmpty() && !scan.running) {
                    TextButton(onClick = scanner::retryFailed) { androidx.compose.material3.Icon(Icons.Filled.Refresh, null); Text("Retry failed") }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("CHOOSE A MOOD", fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("What are you in the mood for?", modifier = Modifier.padding(top = 4.dp, bottom = 12.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CadenceMood.entries.forEach { mood ->
                val active = selectedMood == mood
                Text(
                    mood.title,
                    modifier = Modifier.clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { selectedMood = mood }
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                    fontSize = 12.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (selectedMood != null) {
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PICKED FOR THIS MOOD", fontSize = 10.sp, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(selectedMood!!.title, modifier = Modifier.padding(top = 3.dp), fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                if (recommendations.isNotEmpty()) {
                    TextButton(onClick = { playback.playTracks(recommendations.map { it.first }) }) { androidx.compose.material3.Icon(Icons.Filled.PlayArrow, null); Text("Play all") }
                    TextButton(onClick = { playback.playTracks(recommendations.map { it.first }, shuffle = true) }) { androidx.compose.material3.Icon(Icons.Filled.Shuffle, null) }
                }
            }
            if (recommendations.isEmpty()) {
                Text(if (profiles.isEmpty()) "Scan your library to build mood matches." else "No analysed tracks match this mood yet.", modifier = Modifier.padding(vertical = 16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                recommendations.forEachIndexed { index, (track, score) ->
                    Row(Modifier.fillMaxWidth().clickable { playback.playTracks(recommendations.map { it.first }, startIndex = index) }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${(score * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        var expanded by remember(track.id) { mutableStateOf(false) }
                        androidx.compose.foundation.layout.Box {
                            Text("···", modifier = Modifier.clickable { expanded = true }.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text("Change mood") }, onClick = { expanded = false; editingTrack = track })
                                if (corrections.containsKey(track.id)) DropdownMenuItem(text = { Text("Reset correction") }, onClick = { scanner.removeMoodCorrection(track.id); corrections = scanner.moodCorrections(); expanded = false })
                            }
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                }
            }
        }

        if (scan.failureMessages.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text("TRACKS THAT NEED ATTENTION", fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            scan.failureMessages.forEach { (id, message) ->
                val track = tracks.firstOrNull { it.id == id }
                if (track != null) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(track.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text(message, modifier = Modifier.padding(top = 3.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
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
                    CadenceMood.entries.forEach { mood ->
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
