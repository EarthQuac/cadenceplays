package digital.anchorstudio.soundprint

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

private val SoundprintFontFamily = FontFamily(Font(R.font.inter_tight_variable))

private data class SoundprintPalette(
    val background: Color,
    val foreground: Color,
    val surface: Color,
    val surface2: Color,
    val primary: Color,
    val muted: Color,
    val border: Color,
    val onPrimary: Color
)

private val AmberLight = SoundprintPalette(
    background = Color(0xFFFBFAF7), foreground = Color(0xFF17130F),
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF2F0EC),
    primary = Color(0xFFDF7A32), muted = Color(0xFF6B655F),
    border = Color(0xFFE0DEDA), onPrimary = Color(0xFF160D07)
)
private val AmberDark = SoundprintPalette(
    background = Color(0xFF0C0A07), foreground = Color(0xFFF3F1EF),
    surface = Color(0xFF161310), surface2 = Color(0xFF201C18),
    primary = Color(0xFFFC9E47), muted = Color(0xFF9C9792),
    border = Color(0x17FFFFFF), onPrimary = Color(0xFF160D07)
)
private val BlueLight = SoundprintPalette(
    background = Color(0xFFFAFCFE), foreground = Color(0xFF0C1723),
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEEF4FB),
    primary = Color(0xFF156CDD), muted = Color(0xFF5E6A7A),
    border = Color(0xFFD7DFE8), onPrimary = Color.White
)
private val BlueDark = SoundprintPalette(
    background = Color(0xFF090F1A), foreground = Color(0xFFF1F6FA),
    surface = Color(0xFF121B29), surface2 = Color(0xFF1A2435),
    primary = Color(0xFF59AAF8), muted = Color(0xFF99A6B4),
    border = Color(0x1CFFFFFF), onPrimary = Color(0xFF0A121F)
)
private val EmeraldLight = SoundprintPalette(
    background = Color(0xFFF6FCF7), foreground = Color(0xFF0C1F17),
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEBF5EE),
    primary = Color(0xFF007852), muted = Color(0xFF5C6E65),
    border = Color(0xFFD7E1DA), onPrimary = Color(0xFFF4FAF5)
)
private val EmeraldDark = SoundprintPalette(
    background = Color(0xFF05130E), foreground = Color(0xFFEDF4EE),
    surface = Color(0xFF0A1C16), surface2 = Color(0xFF10271F),
    primary = Color(0xFF5ACA94), muted = Color(0xFF99AA9E),
    border = Color(0x1CFFFFFF), onPrimary = Color(0xFF0B1E15)
)
private val MonoLight = SoundprintPalette(
    background = Color(0xFFF8F8F8), foreground = Color(0xFF121212),
    surface = Color.White, surface2 = Color(0xFFF0F0F0),
    primary = Color(0xFF1F1F1F), muted = Color(0xFF696969),
    border = Color(0xFFDBDBDB), onPrimary = Color.White
)
private val MonoDark = SoundprintPalette(
    background = Color(0xFF090909), foreground = Color(0xFFF5F5F5),
    surface = Color(0xFF141414), surface2 = Color(0xFF1D1D1D),
    primary = Color(0xFFE8E8E8), muted = Color(0xFFA4A4A4),
    border = Color(0x1FFFFFFF), onPrimary = Color(0xFF141414)
)
private fun palette(name: String, dark: Boolean): SoundprintPalette = when (name) {
    "Blue" -> if (dark) BlueDark else BlueLight
    "Emerald" -> if (dark) EmeraldDark else EmeraldLight
    "Mono" -> if (dark) MonoDark else MonoLight
    else -> if (dark) AmberDark else AmberLight
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val preferences = remember { getSharedPreferences("soundprint_native_preferences", MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(preferences.getString("theme", "dark") ?: "dark") }
            var selectedPalette by remember { mutableStateOf(preferences.getString("palette", "Amber") ?: "Amber") }
            var enteredLibrary by remember { mutableStateOf(preferences.getBoolean("has_seen_launch_landing", false)) }
            val libraryRepository = remember { MusicLibraryRepository.getInstance(applicationContext) }
            val libraryManager = remember { MusicLibraryManager.getInstance(applicationContext) }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            var libraryTrackCount by remember { mutableIntStateOf(0) }
            var librarySources by remember { mutableStateOf(emptyList<MusicLibrarySource>()) }
            var libraryStatus by remember { mutableStateOf("Checking your saved music locations…") }
            val scope = rememberCoroutineScope()
            suspend fun refreshLibraryState() {
                val snapshot = withContext(Dispatchers.IO) {
                    libraryRepository.count() to libraryRepository.allSources()
                }
                libraryTrackCount = snapshot.first
                librarySources = snapshot.second
            }
            fun enqueueMusicImport(label: String, enqueue: () -> Unit) {
                try {
                    enqueue()
                    libraryStatus = "$label queued. Soundprint will keep working if you rotate the phone or leave the app."
                } catch (error: Exception) {
                    libraryStatus = "$label could not be queued: ${error.message ?: "Check the selected file permissions and try again."}"
                }
            }
            val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
                if (uri != null) enqueueMusicImport("Folder import") {
                    SoundprintMusicImportQueue.enqueueFolder(applicationContext, uri)
                }
            }
            val singleFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null) enqueueMusicImport("File import") {
                    SoundprintMusicImportQueue.enqueueFiles(applicationContext, listOf(uri))
                }
            }
            val filesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                if (uris.isNotEmpty()) enqueueMusicImport("File import") {
                    SoundprintMusicImportQueue.enqueueFiles(applicationContext, uris)
                }
            }
            val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            contentResolver.openOutputStream(uri)?.use { libraryRepository.writeBackup(it) }
                                ?: error("Android could not create the backup file.")
                        }
                    }
                    libraryStatus = result.fold({ "Library backup saved." }, { "Backup failed: ${it.message ?: "Unknown error"}" })
                }
            }
            val backupOpener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            contentResolver.openInputStream(uri)?.use { libraryRepository.restoreBackup(it, false) }
                                ?: error("Android could not open this backup.")
                        }
                    }
                    if (result.isSuccess) {
                        val scan = runScanWithoutSwallowingCancellation { withContext(Dispatchers.IO) { libraryRepository.rescanAllSavedSources() } }
                        refreshLibraryState()
                        libraryStatus = "Backup restored (${result.getOrNull()} records). " +
                            scan.fold({ "Recovery scan found ${it.unavailable} unavailable locations." }, { "Recovery scan failed: ${it.message}" })
                    } else libraryStatus = "Restore failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                }
            }
            LaunchedEffect(Unit) {
                WorkManager.getInstance(applicationContext)
                    .getWorkInfosForUniqueWorkFlow("soundprint_music_import_queue")
                    .collect { workInfos ->
                        if (workInfos.isNotEmpty()) {
                            val active = workInfos.lastOrNull { !it.state.isFinished }
                            if (active != null) {
                                libraryStatus = "Music import is running in the background. You can safely leave Soundprint."
                            } else {
                                val latest = workInfos.last()
                                when (latest.state) {
                                    WorkInfo.State.SUCCEEDED -> {
                                        refreshLibraryState()
                                        val output = latest.outputData
                                        libraryStatus = "Import complete: ${output.getInt("imported", 0)} added, ${output.getInt("updated", 0)} updated, ${output.getInt("skipped", 0)} skipped, ${output.getInt("errors", 0)} errors."
                                    }
                                    WorkInfo.State.FAILED -> libraryStatus = "Music import failed: ${latest.outputData.getString("error") ?: "Open the notification for details."}"
                                    WorkInfo.State.CANCELLED -> libraryStatus = "Music import was cancelled."
                                    else -> Unit
                                }
                            }
                        }
                    }
            }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                val queuedImports = WorkManager.getInstance(applicationContext)
                    .getWorkInfosForUniqueWorkFlow("soundprint_music_import_queue")
                    .first()
                if (queuedImports.any { !it.state.isFinished }) {
                    libraryStatus = "Music import is continuing in the background."
                } else {
                    // Clear an orphaned progress notification left by an older Activity-scoped importer.
                    SoundprintImportNotifications(applicationContext).dismiss()
                    val recovery = runScanWithoutSwallowingCancellation {
                        withContext(Dispatchers.IO) { libraryRepository.rescanAllSavedSources() }
                    }
                    refreshLibraryState()
                    libraryStatus = recovery.fold(
                        { "Startup recovery: ${it.imported} added, ${it.updated} updated, ${it.unavailable} unavailable, ${it.errors.size} errors." },
                        { "Startup recovery failed: ${it.message ?: "Unknown error"}" }
                    )
                }
            }
            val darkTheme = when (themeMode) {
                "light" -> false
                "system" -> isSystemInDarkTheme()
                else -> true
            }
            val colors = palette(selectedPalette, darkTheme)
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            window.decorView.setBackgroundColor(colors.background.toArgb())
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }

            MaterialTheme(
                colorScheme = if (darkTheme) darkColorScheme(
                    primary = colors.primary, onPrimary = colors.onPrimary,
                    background = colors.background, onBackground = colors.foreground,
                    surface = colors.surface, onSurface = colors.foreground,
                    surfaceVariant = colors.surface2, onSurfaceVariant = colors.muted,
                    outline = colors.border
                ) else lightColorScheme(
                    primary = colors.primary, onPrimary = colors.onPrimary,
                    background = colors.background, onBackground = colors.foreground,
                    surface = colors.surface, onSurface = colors.foreground,
                    surfaceVariant = colors.surface2, onSurfaceVariant = colors.muted,
                    outline = colors.border
                )
            ) {
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(fontFamily = SoundprintFontFamily)) {
                SoundprintApp(
                    libraryManager = libraryManager,
                    themeMode = themeMode,
                    onThemeModeChange = { mode -> themeMode = mode; preferences.edit().putString("theme", mode).apply() },
                    selectedPalette = selectedPalette,
                    onPaletteChange = { name -> selectedPalette = name; preferences.edit().putString("palette", name).apply() },
                    enteredLibrary = enteredLibrary,
                    onEnterLibrary = {
                        enteredLibrary = true
                        preferences.edit().putBoolean("has_seen_launch_landing", true).apply()
                    },
                    libraryTrackCount = libraryTrackCount,
                    librarySources = librarySources,
                    libraryStatus = libraryStatus,
                    onImportMusic = { folderPicker.launch(null) },
                    // Include generic-MIME files too; the importer validates audio MIME types and extensions.
                    onImportFiles = { filesPicker.launch(arrayOf("audio/*", "application/octet-stream")) },
                    onImportSingle = { singleFilePicker.launch(arrayOf("audio/*", "application/octet-stream")) },
                    onRescan = {
                        scope.launch {
                            val result = runScanWithoutSwallowingCancellation { withContext(Dispatchers.IO) { libraryRepository.rescanAllSavedSources() } }
                            refreshLibraryState()
                            libraryStatus = result.fold(
                                { "Rescan complete: ${it.imported} added, ${it.updated} updated, ${it.unavailable} unavailable, ${it.errors.size} errors." },
                                { "Rescan failed: ${it.message ?: "Unknown error"}" }
                            )
                        }
                    },
                    onRemoveSource = { sourceUri ->
                        scope.launch {
                            libraryRepository.removeSource(sourceUri)
                            refreshLibraryState()
                            libraryStatus = "Music location removed. Original audio files were not deleted."
                        }
                    },
                    onBackup = { backupSaver.launch("soundprint-library-backup.json") },
                    onRestore = { backupOpener.launch(arrayOf("application/json")) },
                    onClearLibrary = {
                        scope.launch {
                            withContext(Dispatchers.IO) { libraryManager.clearAllLibraryData() }
                            refreshLibraryState()
                            libraryStatus = "Library cleared. Your original audio files are still on your device and can be imported again."
                        }
                    }
                )
                }
            }
        }
    }
}

private data class TabItem(val label: String, val icon: Int)

private val tabs = listOf(
    TabItem("Home", R.drawable.lucide_home),
    TabItem("Songs", R.drawable.lucide_music),
    TabItem("Albums", R.drawable.lucide_disc_3),
    TabItem("Playlists", R.drawable.lucide_list_music),
    TabItem("Discover", R.drawable.lucide_audio_lines)
)

@Composable
private fun SoundprintApp(
    libraryManager: MusicLibraryManager,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    selectedPalette: String,
    onPaletteChange: (String) -> Unit,
    enteredLibrary: Boolean,
    onEnterLibrary: () -> Unit,
    libraryTrackCount: Int,
    librarySources: List<MusicLibrarySource>,
    libraryStatus: String,
    onImportMusic: () -> Unit,
    onImportFiles: () -> Unit,
    onImportSingle: () -> Unit,
    onRescan: () -> Unit,
    onRemoveSource: (String) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onClearLibrary: () -> Unit
) {
    val appContext = LocalContext.current
    val clapScanner = remember { ClapLibraryScanner(appContext, libraryManager) }
    val playbackConnection = remember { SoundprintPlaybackConnection(appContext) }
    val screenScope = rememberCoroutineScope()
    DisposableEffect(clapScanner, playbackConnection) {
        onDispose {
            clapScanner.close()
            playbackConnection.close()
        }
    }
    LaunchedEffect(enteredLibrary, libraryTrackCount) {
        if (enteredLibrary && libraryTrackCount > 0) clapScanner.startScan()
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }
    var statsOpen by remember { mutableStateOf(false) }
    var proOpen by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars), color = MaterialTheme.colorScheme.background) {
        if (!enteredLibrary) {
            PublicHomeScreen(onContinue = onEnterLibrary)
        } else Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (proOpen) {
                    ProScreen(onBack = { proOpen = false })
                } else if (statsOpen) {
                    ListeningStatsScreen(libraryManager = libraryManager, onBack = { statsOpen = false })
                } else if (settingsOpen) {
                    SettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        selectedPalette = selectedPalette,
                        onPaletteChange = onPaletteChange,
                        onBack = { settingsOpen = false },
                        onOpenPro = { settingsOpen = false; proOpen = true },
                        libraryTrackCount = libraryTrackCount,
                        librarySources = librarySources,
                        libraryStatus = libraryStatus,
                        onImportMusic = onImportMusic,
                        onImportFiles = onImportFiles,
                        onRescan = onRescan,
                        onRemoveSource = onRemoveSource,
                        onBackup = onBackup,
                        onRestore = onRestore,
                        onClearLibrary = {
                            screenScope.launch {
                                clapScanner.clearLibraryState()
                                playbackConnection.clearQueue()
                                onClearLibrary()
                            }
                        },
                    )
                } else {
                    when (selectedTab) {
                        0 -> HomeScreen(
                            libraryManager = libraryManager,
                            playback = playbackConnection,
                            libraryTrackCount = libraryTrackCount,
                            onSettings = { settingsOpen = true },
                            onImportAudio = onImportFiles,
                            onImportSingle = onImportSingle,
                            onImportFolder = onImportMusic,
                            onRescan = onRescan,
                            onListeningStats = { statsOpen = true },
                            onOpenPro = { proOpen = true },
                            onOpenSongs = { selectedTab = 1 }
                        )
                        1 -> LibraryScreen("Songs", "Your music, all in one place.", Icons.Filled.MusicNote, libraryManager, playbackConnection, onImportFiles) { selectedTab = 0 }
                        2 -> SoundprintAlbumsScreen(libraryManager, playbackConnection, onImportFiles, libraryTrackCount)
                        3 -> SoundprintPlaylistsScreen(libraryManager, playbackConnection, clapScanner)
                        else -> ClapDiscoverScreen(libraryManager, clapScanner, playbackConnection) { selectedTab = 0 }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            ) {
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f),
                    thickness = 0.7.dp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 672.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(
                            start = 4.dp, end = 4.dp, top = 0.dp,
                            bottom = 6.dp
                        ),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, item ->
                        val selected = selectedTab == index
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    settingsOpen = false
                                    statsOpen = false
                                    selectedTab = index
                                }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .widthIn(min = 56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (!settingsOpen && !statsOpen && selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(item.icon),
                                    contentDescription = item.label,
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(
                                        if (!settingsOpen && !statsOpen && selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            Text(
                                item.label,
                                fontSize = 11.sp,
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                color = if (!settingsOpen && selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicHomeScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 24.dp,
                    start = 20.dp,
                    end = 20.dp
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.soundprint_icon),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Soundprint",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.6).sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(
                    bottom = 32.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Your music.",
                fontSize = 38.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1.1).sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Your rhythm.",
                fontSize = 38.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1.1).sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 320.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onContinue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Continue to Soundprint",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    libraryManager: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    libraryTrackCount: Int,
    onSettings: () -> Unit,
    onImportAudio: () -> Unit,
    onImportSingle: () -> Unit,
    onImportFolder: () -> Unit,
    onRescan: () -> Unit,
    onListeningStats: () -> Unit,
    onOpenPro: () -> Unit,
    onOpenSongs: () -> Unit
) {
    var searchOpen by remember { mutableStateOf(false) }
    if (searchOpen) {
        HomeSearchScreen(libraryManager, playback) { searchOpen = false }
        return
    }
    val recentTracks = remember(libraryTrackCount) {
        libraryManager.allSongs(sort = TrackSort.DATE_ADDED, ascending = false).take(8)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Image(painterResource(R.drawable.soundprint_icon), "Soundprint", Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("YOUR LIBRARY", fontSize = 10.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    Text("Soundprint", fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderAction(R.drawable.lucide_user_round, "Account", onClick = {})
                HeaderAction(R.drawable.lucide_chart_no_axes_column_increasing, "Listening stats", onClick = onListeningStats)
                HeaderAction(R.drawable.lucide_crown, "Soundprint Pro", tint = MaterialTheme.colorScheme.primary, onClick = onOpenPro)
                HeaderAction(R.drawable.lucide_search, "Search", onClick = { searchOpen = true })
                HeaderAction(R.drawable.lucide_settings, "Settings", onClick = onSettings)
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (libraryTrackCount == 0) item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.48f), RoundedCornerShape(22.dp))
                        .padding(20.dp)
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.lucide_music), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary))
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("A FRESH START", fontSize = 12.sp, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Your music goes here.", fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(10.dp))
                    Text("Add the songs already on your device. Your library stays yours, and you can organise it into albums, playlists and favourites as you go.", fontSize = 14.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    AddMusicMenuButton(
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        onAudioPicker = onImportAudio,
                        onSinglePicker = onImportSingle,
                        onFolderPicker = onImportFolder,
                        onRescan = onRescan
                    )
                    Spacer(Modifier.height(28.dp))
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeatureLabel(R.drawable.lucide_music, "Your own music")
                        FeatureLabel(R.drawable.lucide_list_music, "Playlists that fit")
                        FeatureLabel(R.drawable.lucide_headphones, "Listen offline")
                    }
                }
            } else item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("YOUR MUSIC", fontSize = 10.sp, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                            Text("$libraryTrackCount songs in your library", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        }
                        TextButton(onClick = onOpenSongs) { Text("See all") }
                    }
                    recentTracks.forEachIndexed { index, track ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surface)
                                .clickable { playback.playTracks(recentTracks, index) }.padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NativeArtwork(track.artworkUri, track.id, Modifier.size(44.dp), 10)
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                Text(track.artist, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    OutlinedButton(onClick = onImportAudio, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Add more music")
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSearchScreen(library: MusicLibraryManager, playback: SoundprintPlaybackConnection, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var songs by remember { mutableStateOf(emptyList<MusicTrack>()) }
    var albums by remember { mutableStateOf(emptyList<LibraryAlbum>()) }
    var artists by remember { mutableStateOf(emptyList<LibraryArtist>()) }
    var playlists by remember { mutableStateOf(emptyList<ManagedPlaylist>()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val term = query.trim()
        if (term.isBlank()) {
            songs = emptyList(); albums = emptyList(); artists = emptyList(); playlists = emptyList()
        } else {
            loading = true
            val result = withContext(Dispatchers.IO) {
                Triple(
                    library.searchSongs(term),
                    library.albums().filter { "${it.name} ${it.artist}".contains(term, true) }.take(8),
                    Pair(
                        library.artists().filter { it.name.contains(term, true) }.take(8),
                        library.playlists().filter { it.name.contains(term, true) }.take(8)
                    )
                )
            }
            songs = result.first
            albums = result.second
            artists = result.third.first
            playlists = result.third.second
            loading = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderAction(R.drawable.lucide_arrow_left, "Back", onClick = onBack)
            Spacer(Modifier.width(8.dp))
            Text("Search your music", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        }
        SearchBox(query, { query = it }, "Songs, artists, albums or playlists…")
        when {
            query.isBlank() -> Text("Search songs, artists, albums and playlists in your library.", Modifier.padding(20.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            loading -> Text("Searching…", Modifier.padding(20.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty() ->
                Text("No matches found.", Modifier.padding(20.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (songs.isNotEmpty()) {
                    item { Text("SONGS · ${songs.size}", Modifier.padding(start = 4.dp, top = 8.dp), fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(songs, key = { it.id }) { track ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { playback.playTracks(songs, songs.indexOfFirst { it.id == track.id }) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            NativeArtwork(track.artworkUri, track.id, Modifier.size(42.dp), 9)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${track.artist} · ${track.album}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (albums.isNotEmpty()) {
                    item { Text("ALBUMS", Modifier.padding(start = 4.dp, top = 12.dp), fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(albums, key = { it.id }) { album ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { playback.playTracks(album.tracks) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            NativeArtwork(album.artworkUri, album.id, Modifier.size(42.dp), 9)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(album.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                Text("${album.artist} · ${album.tracks.size} songs", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (artists.isNotEmpty()) {
                    item { Text("ARTISTS", Modifier.padding(start = 4.dp, top = 12.dp), fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(artists, key = { it.name }) { artist ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            val artistTracks = library.artistTracks(artist.name)
                            playback.playTracks(artistTracks)
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(artist.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                Text("${artist.tracks.size} songs", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (playlists.isNotEmpty()) {
                    item { Text("PLAYLISTS", Modifier.padding(start = 4.dp, top = 12.dp), fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(playlists, key = { it.id }) { playlist ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            playback.playTracks(library.playlistTracks(playlist.id))
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(playlist.name, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMusicMenuButton(
    modifier: Modifier = Modifier,
    onAudioPicker: () -> Unit,
    onSinglePicker: () -> Unit,
    onFolderPicker: () -> Unit,
    onRescan: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
                .clickable { expanded = true }.padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(painterResource(R.drawable.lucide_folder_plus), contentDescription = null,
                modifier = Modifier.size(16.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary))
            Spacer(Modifier.width(8.dp))
            Text("Add music", color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Audio picker") },
                leadingIcon = { Icon(Icons.Filled.AudioFile, null) },
                onClick = { expanded = false; onAudioPicker() }
            )
            DropdownMenuItem(
                text = { Text("Single picker") },
                leadingIcon = { Icon(Icons.Filled.MusicNote, null) },
                onClick = { expanded = false; onSinglePicker() }
            )
            DropdownMenuItem(
                text = { Text("Folder picker") },
                leadingIcon = { Icon(Icons.Filled.LibraryMusic, null) },
                onClick = { expanded = false; onFolderPicker() }
            )
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
            )
            DropdownMenuItem(
                text = { Text("Rescan folders") },
                leadingIcon = { Icon(Icons.Filled.GraphicEq, null) },
                onClick = { expanded = false; onRescan() }
            )
        }
    }
}

@Composable
private fun HeaderAction(icon: Int, description: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(icon), contentDescription = description, modifier = Modifier.size(20.dp), colorFilter = ColorFilter.tint(tint))
    }
}

@Composable
private fun FeatureLabel(icon: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(14.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun LibraryScreen(
    title: String,
    subtitle: String,
    icon: ImageVector,
    libraryManager: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    onImportFiles: () -> Unit,
    onBack: () -> Unit
) {
    when (title) {
        "Songs" -> SongsScreen(libraryManager, playback, onImportFiles, onBack)
        "Albums" -> AlbumsScreen(onBack)
        else -> PlaylistsScreen(onBack)
    }
}

@Composable
private fun SongsScreen(
    libraryManager: MusicLibraryManager,
    playback: SoundprintPlaybackConnection,
    onImportFiles: () -> Unit,
    onBack: () -> Unit
) {
    SongsLibraryScreen(libraryManager, playback, onImportFiles, onBack)
}

@Composable
private fun AlbumsScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle("Albums", "0 albums", onBack)
        EmptyLibraryState(
            icon = Icons.Filled.Album,
            title = "No albums yet",
            description = "Add music to your device and scan your library — albums are grouped from your files' tags."
        )
    }
}

@Composable
private fun PlaylistsScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var createOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .border(width = 0.dp, color = Color.Transparent)
                .padding(
                    start = 12.dp, end = 12.dp,
                    top = 12.dp,
                    bottom = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack).padding(8.dp), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.lucide_arrow_left), null, Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Playlists", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, color = MaterialTheme.colorScheme.onBackground)
                Text("0 playlists", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable { createOpen = !createOpen },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Add, "New playlist", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
        }
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(19.dp)
        ) {
            SearchBox(query, { query = it }, "Search playlists…")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SMART MIXES", fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PlaylistMix(Icons.Filled.Favorite, "Liked Songs", "0 songs")
                PlaylistMix(Icons.Filled.AccessTime, "Recently Played", "0 songs")
                PlaylistMix(Icons.Filled.QueueMusic, "Recently Added", "0 songs")
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Smart playlists", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Automatically updated from your listening and library", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                            .clickable { createOpen = !createOpen }.padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
                            Text("New smart playlist", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                if (createOpen) {
                    Text("Playlist creation will be connected to your local music library in the next implementation pass.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (query.isNotBlank()) {
                Text("No playlists match your search.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                EmptyLibraryState(
                    icon = Icons.Filled.QueueMusic,
                    title = "No playlists yet",
                    description = "Create a playlist to collect songs, or build a smart playlist that updates automatically."
                )
            }
        }
    }
}

@Composable
private fun DiscoverScreen(onBack: () -> Unit) {
    val moods = listOf(
        "Workout", "Driving", "Chill", "Focus", "Party", "Upbeat",
        "Happy", "Sad", "Romantic", "Sleep", "Late Night", "Angry",
        "Dreamy", "Peaceful", "Melancholy", "Confident", "Nostalgic", "Intense"
    )
    var selectedMood by remember { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle("Discover", "Your music, in a different light", onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column {
                Text("YOUR LIBRARY, IN A DIFFERENT LIGHT", fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text("Find your next mood.", modifier = Modifier.padding(top = 5.dp), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, color = MaterialTheme.colorScheme.onBackground)
                Text("Pick a feeling or a moment. Soundprint finds the tracks in your own library that fit.", modifier = Modifier.padding(top = 4.dp), fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(
                Modifier.fillMaxWidth().border(
                    width = 0.dp, color = Color.Transparent
                ).padding(vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF4DAA78)))
                    Spacer(Modifier.width(8.dp))
                    Text("Your music library", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("0 tracks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Column {
                    Text("Choose a mood", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Tap one to jump straight to matching songs.", modifier = Modifier.padding(top = 3.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                moods.chunked(3).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        group.forEach { mood ->
                            MoodChip(mood, selectedMood == mood, Modifier.weight(1f)) { selectedMood = mood }
                        }
                        if (group.size < 3) repeat(3 - group.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (selectedMood != null) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.48f), RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Text("PICKED FOR THIS MOMENT", fontSize = 10.sp, letterSpacing = 1.3.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(selectedMood!!, modifier = Modifier.padding(top = 5.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("0 tracks", modifier = Modifier.padding(top = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Add music to Soundprint, then return here to explore it by mood.", modifier = Modifier.padding(top = 12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SearchBox(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(value, onValueChange, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) {
            Icon(Icons.Filled.Close, "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp).clickable { onValueChange("") })
        }
    }
}

@Composable
private fun UtilityButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.height(40.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.48f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SoundprintActionButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick).padding(horizontal = 17.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary) }
}

@Composable
private fun EmptyLibraryState(icon: ImageVector, title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        }
        Text(title, modifier = Modifier.padding(top = 15.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text(description, modifier = Modifier.padding(top = 5.dp), fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SoundprintActionButton("Add music", Modifier.padding(top = 16.dp)) {}
    }
}

@Composable
private fun PlaylistMix(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface).clickable { }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun MoodChip(mood: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.height(37.dp).clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(mood, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = 12.dp, end = 12.dp,
                top = 12.dp,
                bottom = 12.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(R.drawable.lucide_arrow_left), null, Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp, color = MaterialTheme.colorScheme.onBackground)
                if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f))
    }
}

@Composable
private fun SettingsScreen(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    selectedPalette: String,
    onPaletteChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenPro: () -> Unit,
    libraryTrackCount: Int,
    librarySources: List<MusicLibrarySource>,
    libraryStatus: String,
    onImportMusic: () -> Unit,
    onImportFiles: () -> Unit,
    onRescan: () -> Unit,
    onRemoveSource: (String) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onClearLibrary: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var gapless by remember { mutableStateOf(true) }
    var fadeOnPause by remember { mutableStateOf(false) }
    var loudness by remember { mutableStateOf(true) }
    var crossfade by remember { mutableIntStateOf(0) }
    var resume by remember { mutableStateOf(true) }
    var rememberPosition by remember { mutableStateOf(true) }
    var seekSeconds by remember { mutableIntStateOf(10) }
    var replaceQueue by remember { mutableStateOf(false) }
    var autoAdd by remember { mutableStateOf(true) }
    var customArtwork by remember { mutableStateOf(true) }
    var ignoreShortTracks by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var historyDialog by remember { mutableStateOf(false) }
    var helpDialog by remember { mutableStateOf(false) }
    val needle = query.trim().lowercase()
    fun matches(vararg words: String): Boolean = needle.isEmpty() || words.any { it.lowercase().contains(needle) }

    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle("Settings", "", onBack)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SettingsSearchBox(query, { query = it })
            if (matches("appearance theme colour palette amber blue emerald mono")) {
                SettingsSection("APPEARANCE") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text("Theme", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeChoice(Modifier.weight(1f), "System", R.drawable.lucide_sun_moon, themeMode == "system") { onThemeModeChange("system") }
                            ThemeChoice(Modifier.weight(1f), "Light", R.drawable.lucide_sun, themeMode == "light") { onThemeModeChange("light") }
                            ThemeChoice(Modifier.weight(1f), "Dark", R.drawable.lucide_moon, themeMode == "dark") { onThemeModeChange("dark") }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Colour palette", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PaletteChoice("Amber", selectedPalette == "Amber", Modifier.weight(1f)) { onPaletteChange("Amber") }
                                PaletteChoice("Blue & white", selectedPalette == "Blue", Modifier.weight(1f)) { onPaletteChange("Blue") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PaletteChoice("Emerald", selectedPalette == "Emerald", Modifier.weight(1f)) { onPaletteChange("Emerald") }
                                PaletteChoice("Mono", selectedPalette == "Mono", Modifier.weight(1f)) { onPaletteChange("Mono") }
                            }
                        }
                    }
                }
            }
            if (matches("audio gapless playback fade pause loudness leveling equalizer crossfade")) {
                SettingsSection("AUDIO") {
                    SettingsToggleRow("Gapless playback", "Preloads the next track to reduce the gap", gapless) { gapless = it }
                    SettingsToggleRow("Fade on pause", "Short fade instead of an abrupt stop", fadeOnPause) { fadeOnPause = it }
                    SettingsToggleRow("Loudness leveling", "Smooths playback volume between tracks", loudness) { loudness = it }
                    SettingsActionRow("Equalizer", "Adjust your sound with the free 10-band EQ; Pro adds advanced audio controls", "Open Equalizer")
                    SettingsValueRow("Crossfade", if (crossfade == 0) "Off" else "$crossfade seconds") {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(0, 2, 4, 6, 8).forEach { seconds ->
                                Box(
                                    Modifier.width(31.dp).height(30.dp).clip(RoundedCornerShape(9.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.48f), RoundedCornerShape(9.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (seconds == 0) "Off" else "$seconds s", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (matches("playback resume position double tap seek skip seconds")) {
                SettingsSection("PLAYBACK") {
                    SettingsToggleRow("Resume playback", "Restore the last song when Soundprint opens", resume) { resume = it }
                    SettingsToggleRow("Remember position", "Continue from where you stopped", rememberPosition) { rememberPosition = it }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                        Text("Double-tap to seek", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Choose how far to skip when you double-tap the Now Playing artwork.", modifier = Modifier.padding(top = 3.dp), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        val seekOptions = listOf(5, 10, 15, 20, 30, 45, 60)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            seekOptions.chunked(4).forEach { rowOptions ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowOptions.forEach { seconds ->
                                        Box(
                                            Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(12.dp))
                                                .background(if (seekSeconds == seconds) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
                                                .border(1.dp, if (seekSeconds == seconds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                                .clickable { seekSeconds = seconds },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("$seconds s", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (seekSeconds == seconds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                    repeat(4 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        Text("Current: $seekSeconds seconds", modifier = Modifier.padding(top = 8.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (matches("queue replace auto add newly discovered songs folder auto watch scan")) {
                SettingsSection("QUEUE") {
                    SettingsToggleRow("Replace queue on play", "Playing a list clears the previous queue", replaceQueue) { replaceQueue = it }
                    SettingsToggleRow("Auto-add newly discovered songs", "New imports join your library automatically", autoAdd) { autoAdd = it }
                    SettingsActionRow("Folder auto-watch", "Choose a folder to scan every 30 seconds while Soundprint is open.", "Choose")
                }
            }
            if (matches("library custom artwork add music your music rescan folders audio tag editor playback history backup restore ignore clips clear library")) {
                SettingsSection("LIBRARY") {
                    SettingsToggleRow("Custom artwork", "Show Soundprint artwork only when a song has no embedded cover. Turn this off anytime to remove Soundprint artwork from your library.", customArtwork) { customArtwork = it }
                    SettingsActionRow("Your music", "$libraryTrackCount songs stored on this device", "Add folder", onClick = onImportMusic)
                    SettingsActionRow("Import audio files", "Choose individual audio files from your device", "Import", onClick = onImportFiles)
                    SettingsActionRow("Rescan folders", "Recover saved locations and check for new or changed songs", "Rescan", onClick = onRescan)
                    if (libraryStatus.isNotBlank()) Text(libraryStatus, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (librarySources.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("IMPORTED LOCATIONS", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            librarySources.forEach { source ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(source.displayName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(if (source.lastScannedAt > 0L) "Saved music location" else "Individual audio file", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { onRemoveSource(source.uri) }) { Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                                }
                            }
                        }
                        SettingsDivider()
                    }
                    SettingsActionRow("Audio tag editor", "Manually edit title, artist, album and genre — free for everyone", "Open")
                    SettingsActionRow("Playback history", "Clear recently played songs, play counts, listening time and streaks", "Clear") { historyDialog = true }
                    SettingsActionRow("Backup library", "Export your library records, playlists, favourites and listening stats", "Backup", onClick = onBackup)
                    SettingsActionRow("Restore backup", "Restore a Soundprint library backup from your device", "Restore", onClick = onRestore)
                    SettingsToggleRow("Ignore clips under 30s", "Skips ringtones and notification sounds", ignoreShortTracks) { ignoreShortTracks = it }
                    SettingsActionRow("Clear library", "Removes imported tracks, playlists, favourites and listening history; original audio files stay untouched", "Clear", destructive = true) { clearDialog = true }
                }
            }
            if (matches("tutorial replay tutorial")) {
                SettingsSection("TUTORIAL") {
                    SettingsActionRow("Replay tutorial", "Walk through the main Soundprint controls again", "Replay tutorial")
                }
            }
            if (matches("about help info soundprint pro about legal version privacy terms")) {
                SettingsSection("ABOUT") {
                    SettingsActionRow("Help & Info", "Learn how local playback, stats and backups work", "Open") { helpDialog = true }
                    SettingsActionRow("Soundprint Pro", "Explore Pro features", "View Pro", onClick = onOpenPro)
                    SettingsActionRow("About & Legal", "Privacy, Terms, Pro terms, refunds and copyright", "View policies")
                    SettingsActionRow("Version", "Soundprint 1.0 · offline-first player", "1.0.0")
                }
            }
        }
    }
    if (clearDialog) AlertDialog(
        onDismissRequest = { clearDialog = false },
        title = { Text("Clear your library?") },
        text = { Text("This removes imported song entries, saved folder and file locations, playlists, favourites, listening history, and library analysis data from Soundprint. It does not delete or move your actual audio files. You can import them again afterwards.") },
        confirmButton = { TextButton(onClick = { clearDialog = false; onClearLibrary() }) { Text("Clear everything", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } }
    )
    if (historyDialog) AlertDialog(
        onDismissRequest = { historyDialog = false },
        title = { Text("Clear playback history?") },
        text = { Text("This clears recently played songs, play counts, listening time and streaks on this device. Your audio files, library, playlists and favourites will remain.") },
        confirmButton = { TextButton(onClick = { historyDialog = false }) { Text("Clear history", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { historyDialog = false }) { Text("Cancel") } }
    )
    if (helpDialog) AlertDialog(
        onDismissRequest = { helpDialog = false },
        title = { Text("Help & Info") },
        text = { Text("Soundprint plays music stored on your device. Add audio files from Settings → Library → Your music. Use Rescan folders to check folders you have already selected. Your local audio stays on your device during normal playback.") },
        confirmButton = { TextButton(onClick = { helpDialog = false }) { Text("Got it") } }
    )
}

@Composable
private fun SettingsSearchBox(value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 2.dp)
            .heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.48f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.lucide_search), null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Search settings…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(value, onValueChange, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text("×", modifier = Modifier.clickable { onValueChange("") }, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsToggleRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                Text(description, modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        SettingsDivider()
    }
}

@Composable
private fun SettingsActionRow(
    label: String,
    description: String,
    action: String,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Column {
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                Text(description, modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(action, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, maxLines = 1)
        }
        SettingsDivider()
    }
}

@Composable
private fun SettingsValueRow(label: String, description: String, control: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 6.dp)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(description, modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            control()
        }
        SettingsDivider()
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.70f), RoundedCornerShape(18.dp))) { content() }
    }
}

@Composable
private fun SettingsDivider() {
    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.80f), thickness = 0.7.dp)
}

@Composable
private fun MoodCountCard(label: String, value: String) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 9.dp).width(88.dp)) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, modifier = Modifier.padding(top = 3.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ThemeChoice(modifier: Modifier, label: String, icon: Int?, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = modifier.height(76.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.48f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) Image(painterResource(icon), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant))
        Text(label, modifier = Modifier.padding(top = if (icon == null) 0.dp else 6.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PaletteChoice(name: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = when (name) {
        "Blue & white" -> listOf(Color(0xFFFFFFFF), Color(0xFF527FE2))
        "Emerald" -> listOf(Color(0xFF10271F), Color(0xFF5ACA94))
        "Mono" -> listOf(Color(0xFF141414), Color(0xFFE8E8E8))
        else -> listOf(Color(0xFF24211E), Color(0xFFE6A15D))
    }
    Row(
        modifier = modifier.height(76.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            colors.forEach { color -> Box(Modifier.size(20.dp).clip(CircleShape).background(color).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)) }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Text(if (name == "Emerald" || name == "Mono") "Unlock with Pro" else if (name == "Amber") "Original warm dark theme" else "Cool blue on clean white", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

private suspend fun <T> runScanWithoutSwallowingCancellation(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
