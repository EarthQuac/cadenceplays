package digital.anchorstudio.cadenceplays

import android.os.Bundle
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

private val CadenceFontFamily = FontFamily(Font(R.font.inter_tight_variable))

private data class CadencePalette(
    val background: Color,
    val foreground: Color,
    val surface: Color,
    val surface2: Color,
    val primary: Color,
    val muted: Color,
    val border: Color,
    val onPrimary: Color
)

private val AmberLight = CadencePalette(
    background = Color(0xFFFAF9F6), foreground = Color(0xFF302D29),
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF1EFEB),
    primary = Color(0xFFC77B43), muted = Color(0xFF77716A),
    border = Color(0xFFE6E2DC), onPrimary = Color(0xFF21160F)
)
private val AmberDark = CadencePalette(
    background = Color(0xFF171512), foreground = Color(0xFFF3F1ED),
    surface = Color(0xFF24211E), surface2 = Color(0xFF302C27),
    primary = Color(0xFFE6A15D), muted = Color(0xFFAAA39A),
    border = Color(0x17FFFFFF), onPrimary = Color(0xFF21160F)
)
private val BlueLight = CadencePalette(
    background = Color(0xFFFAFCFE), foreground = Color(0xFF0C1723),
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEEF4FB),
    primary = Color(0xFF156CDD), muted = Color(0xFF5E6A7A),
    border = Color(0xFFD7DFE8), onPrimary = Color.White
)
private val BlueDark = CadencePalette(
    background = Color(0xFF090F1A), foreground = Color(0xFFF1F6FA),
    surface = Color(0xFF121B29), surface2 = Color(0xFF1A2435),
    primary = Color(0xFF59AAF8), muted = Color(0xFF99A6B4),
    border = Color(0x1CFFFFFF), onPrimary = Color(0xFF0A121F)
)
private val EmeraldLight = CadencePalette(
    background = Color(0xFFF6FCF7), foreground = Color(0xFF0C1F17),
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEBF5EE),
    primary = Color(0xFF007852), muted = Color(0xFF5C6E65),
    border = Color(0xFFD7E1DA), onPrimary = Color(0xFFF4FAF5)
)
private val EmeraldDark = CadencePalette(
    background = Color(0xFF05130E), foreground = Color(0xFFEDF4EE),
    surface = Color(0xFF0A1C16), surface2 = Color(0xFF10271F),
    primary = Color(0xFF5ACA94), muted = Color(0xFF99AA9E),
    border = Color(0x1CFFFFFF), onPrimary = Color(0xFF0B1E15)
)
private val MonoLight = CadencePalette(
    background = Color(0xFFF8F8F8), foreground = Color(0xFF121212),
    surface = Color.White, surface2 = Color(0xFFF0F0F0),
    primary = Color(0xFF1F1F1F), muted = Color(0xFF696969),
    border = Color(0xFFDEDEDE), onPrimary = Color.White
)
private val MonoDark = CadencePalette(
    background = Color(0xFF090909), foreground = Color(0xFFF5F5F5),
    surface = Color(0xFF141414), surface2 = Color(0xFF1D1D1D),
    primary = Color(0xFFE8E8E8), muted = Color(0xFFA4A4A4),
    border = Color(0x1FFFFFFF), onPrimary = Color(0xFF141414)
)
private fun palette(name: String, dark: Boolean): CadencePalette = when (name) {
    "Blue" -> if (dark) BlueDark else BlueLight
    "Emerald" -> if (dark) EmeraldDark else EmeraldLight
    "Mono" -> if (dark) MonoDark else MonoLight
    else -> if (dark) AmberDark else AmberLight
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences = remember { getSharedPreferences("cadence_native_preferences", MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(preferences.getString("theme", "dark") ?: "dark") }
            var selectedPalette by remember { mutableStateOf(preferences.getString("palette", "Amber") ?: "Amber") }
            val darkTheme = when (themeMode) {
                "light" -> false
                "system" -> isSystemInDarkTheme()
                else -> true
            }
            val colors = palette(selectedPalette, darkTheme)
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            window.decorView.systemUiVisibility = if (darkTheme) 0 else
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

            MaterialTheme(
                typography = androidx.compose.material3.Typography(defaultFontFamily = CadenceFontFamily),
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
                CadenceApp(
                    themeMode = themeMode,
                    onThemeModeChange = { mode -> themeMode = mode; preferences.edit().putString("theme", mode).apply() },
                    selectedPalette = selectedPalette,
                    onPaletteChange = { name -> selectedPalette = name; preferences.edit().putString("palette", name).apply() }
                )
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
private fun CadenceApp(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    selectedPalette: String,
    onPaletteChange: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (settingsOpen) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    selectedPalette = selectedPalette,
                    onPaletteChange = onPaletteChange,
                    onBack = { settingsOpen = false }
                )
            } else {
                when (selectedTab) {
                    0 -> HomeScreen(onSettings = { settingsOpen = true })
                    1 -> LibraryScreen("Songs", "Your music, all in one place.", Icons.Filled.MusicNote) { selectedTab = 0 }
                    2 -> LibraryScreen("Albums", "Browse your albums and artwork.", Icons.Filled.Album) { selectedTab = 0 }
                    3 -> PlaylistsScreen { selectedTab = 0 }
                    else -> DiscoverScreen { selectedTab = 0 }
                }
                NavigationBar(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    tabs.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (selectedTab == index) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else Color.Transparent)
                                        .padding(horizontal = 17.dp, vertical = 5.dp)
                                ) {
                                    Image(painterResource(item.icon), contentDescription = item.label, modifier = Modifier.size(21.dp), colorFilter = ColorFilter.tint(if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant))
                                }
                            },
                            label = { Text(item.label, fontSize = 11.sp, maxLines = 1) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 12.dp, end = 12.dp, top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
                    bottom = 16.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.cadence_icon),
                    contentDescription = "Cadence",
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("YOUR LIBRARY", fontSize = 10.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    Text("Cadence", fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderAction(R.drawable.lucide_user_round, "Account", onClick = {})
                HeaderAction(R.drawable.lucide_chart_no_axes_column_increasing, "Listening stats", onClick = {})
                HeaderAction(R.drawable.lucide_crown, "Cadence Pro", tint = MaterialTheme.colorScheme.primary, onClick = {})
                HeaderAction(R.drawable.lucide_search, "Search", onClick = {})
                HeaderAction(R.drawable.lucide_settings, "Settings", onClick = onSettings)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
                        .padding(20.dp)
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("A FRESH START", fontSize = 12.sp, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Your music goes here.", fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Add the songs already on your device. Your library stays yours, and you can organise it into albums, playlists and favourites as you go.",
                        fontSize = 14.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary).clickable { }.padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(painterResource(R.drawable.lucide_folder_plus), contentDescription = null, modifier = Modifier.size(16.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary))
                        Spacer(Modifier.width(9.dp))
                        Text("Add music", color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(24.dp))
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        FeatureLabel(R.drawable.lucide_music, "Your own music")
                        FeatureLabel(R.drawable.lucide_list_music, "Playlists that fit")
                        FeatureLabel(R.drawable.lucide_headphones, "Listen offline")
                    }
                }
            }
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(14.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun LibraryScreen(title: String, subtitle: String, icon: ImageVector, onBack: () -> Unit) {
    when (title) {
        "Songs" -> SongsScreen(onBack)
        "Albums" -> AlbumsScreen(onBack)
        else -> PlaylistsScreen()
    }
}

@Composable
private fun SongsScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("Title") }
    var descending by remember { mutableStateOf(false) }
    var soundprintOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle("Songs", "0 songs", onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            SearchBox(query, { query = it }, "Filter songs, artists, albums…")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UtilityButton(Icons.Filled.FilterList, "Sort: $sort", Modifier.weight(1f)) {
                    sort = when (sort) { "Title" -> "Artist"; "Artist" -> "Album"; "Album" -> "Date added"; "Date added" -> "Duration"; else -> "Title" }
                }
                UtilityButton(Icons.Filled.Sort, if (descending) "Descending" else "Ascending", Modifier.weight(1f)) {
                    descending = !descending
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UtilityButton(Icons.Filled.GraphicEq, "Soundprint", Modifier.weight(1f)) { soundprintOpen = !soundprintOpen }
                UtilityButton(Icons.Filled.LibraryMusic, "Recycle Bin", Modifier.weight(1f)) { }
            }
            if (soundprintOpen) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                        .padding(15.dp)
                ) {
                    Text("Soundprint", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Find duplicate tracks in your library.", modifier = Modifier.padding(top = 3.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CadenceActionButton("Basic scan", Modifier.padding(top = 12.dp)) {}
                }
            }
            if (query.isNotBlank()) {
                Text("0 matches", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            EmptyLibraryState(
                icon = Icons.Filled.MusicNote,
                title = "No music found",
                description = "Add music to your device and scan your library to see your songs here."
            )
        }
    }
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
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
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
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
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
                Text("Pick a feeling or a moment. Cadence finds the tracks in your own library that fit.", modifier = Modifier.padding(top = 4.dp), fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(
                Modifier.fillMaxWidth().border(
                    width = 0.dp, color = Color.Transparent
                ).padding(vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF4DAA78)))
                    Spacer(Modifier.width(8.dp))
                    Text("CLAP analysed · 0/0", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Text("PICKED FOR THIS MOMENT", fontSize = 10.sp, letterSpacing = 1.3.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(selectedMood!!, modifier = Modifier.padding(top = 5.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("0 tracks", modifier = Modifier.padding(top = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Add music to Cadence, then return here to explore it by mood.", modifier = Modifier.padding(top = 12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
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
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
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
private fun CadenceActionButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
        CadenceActionButton("Add music", Modifier.padding(top = 16.dp)) {}
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
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
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
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
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
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    }
}

@Composable
private fun SettingsScreen(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    selectedPalette: String,
    onPaletteChange: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = 18.dp, end = 18.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp,
                bottom = 16.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("Back", color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(8.dp))
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection("APPEARANCE") {
                    Column(Modifier.padding(15.dp)) {
                        Text("Theme", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            ThemeChoice(Modifier.weight(1f), "System", themeMode == "system") { onThemeModeChange("system") }
                            ThemeChoice(Modifier.weight(1f), "Light", themeMode == "light") { onThemeModeChange("light") }
                            ThemeChoice(Modifier.weight(1f), "Dark", themeMode == "dark") { onThemeModeChange("dark") }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("Colour palette", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(10.dp))
                        listOf("Amber", "Blue", "Emerald", "Mono").forEach { name ->
                            PaletteChoice(name, selectedPalette == name) { onPaletteChange(name) }
                        }
                    }
                }
            }
            item {
                SettingsSection("ABOUT CADENCE") {
                    Column(Modifier.padding(15.dp)) {
                        Text("Offline-first music player", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text("Your music library, organised around the music already on your device.", fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(18.dp))) {
            content()
        }
    }
}

@Composable
private fun ThemeChoice(modifier: Modifier, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(if (selected) 1.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PaletteChoice(name: String, selected: Boolean, onClick: () -> Unit) {
    val swatch = when (name) {
        "Blue" -> Color(0xFF527FE2)
        "Emerald" -> Color(0xFF5D9D7E)
        "Mono" -> Color(0xFF777777)
        else -> Color(0xFFE9AD68)
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 11.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(17.dp).clip(CircleShape).background(swatch))
        Spacer(Modifier.width(11.dp))
        Text(name, modifier = Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        if (selected) Text("Selected", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    }
}
