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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Crown
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb

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
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF2F0EB),
    primary = Color(0xFFD98A45), muted = Color(0xFF81796F),
    border = Color(0xFFE7E2DA), onPrimary = Color(0xFF24180E)
)
private val AmberDark = CadencePalette(
    background = Color(0xFF191715), foreground = Color(0xFFF3F0EA),
    surface = Color(0xFF25221F), surface2 = Color(0xFF302C28),
    primary = Color(0xFFE9AD68), muted = Color(0xFFB2AAA0),
    border = Color(0xFF3B3631), onPrimary = Color(0xFF25180D)
)
private val BlueLight = CadencePalette(
    background = Color(0xFFF9FAFC), foreground = Color(0xFF202B40),
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEEF2F8),
    primary = Color(0xFF3869D8), muted = Color(0xFF68758A),
    border = Color(0xFFDCE3EF), onPrimary = Color.White
)
private val BlueDark = CadencePalette(
    background = Color(0xFF171D2B), foreground = Color(0xFFF1F4FC),
    surface = Color(0xFF222B3E), surface2 = Color(0xFF2B3650),
    primary = Color(0xFF8EAEFF), muted = Color(0xFFAAB6CC),
    border = Color(0xFF3A465E), onPrimary = Color(0xFF17213A)
)
private val EmeraldLight = CadencePalette(
    background = Color(0xFFF8FAF7), foreground = Color(0xFF25362F),
    surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEDF3EE),
    primary = Color(0xFF397B61), muted = Color(0xFF718078),
    border = Color(0xFFDCE7DF), onPrimary = Color.White
)
private val EmeraldDark = CadencePalette(
    background = Color(0xFF17221D), foreground = Color(0xFFF0F5F0),
    surface = Color(0xFF222F28), surface2 = Color(0xFF2C3B33),
    primary = Color(0xFF83C5A5), muted = Color(0xFFAAB9AF),
    border = Color(0xFF3A4A40), onPrimary = Color(0xFF14251C)
)
private val MonoLight = CadencePalette(
    background = Color(0xFFF8F8F8), foreground = Color(0xFF252525),
    surface = Color.White, surface2 = Color(0xFFEFEFEF),
    primary = Color(0xFF333333), muted = Color(0xFF777777),
    border = Color(0xFFE2E2E2), onPrimary = Color.White
)
private val MonoDark = CadencePalette(
    background = Color(0xFF171717), foreground = Color(0xFFF3F3F3),
    surface = Color(0xFF242424), surface2 = Color(0xFF303030),
    primary = Color(0xFFE5E5E5), muted = Color(0xFFAAAAAA),
    border = Color(0xFF3D3D3D), onPrimary = Color(0xFF202020)
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
            var darkTheme by remember { mutableStateOf(true) }
            var selectedPalette by remember { mutableStateOf("Amber") }
            val colors = palette(selectedPalette, darkTheme)
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            window.decorView.systemUiVisibility = if (darkTheme) 0 else
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

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
                CadenceApp(
                    darkTheme = darkTheme,
                    onDarkThemeChange = { darkTheme = it },
                    selectedPalette = selectedPalette,
                    onPaletteChange = { selectedPalette = it }
                )
            }
        }
    }
}

private data class TabItem(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("Home", Icons.Filled.Home),
    TabItem("Songs", Icons.Filled.MusicNote),
    TabItem("Albums", Icons.Filled.Album),
    TabItem("Playlists", Icons.Filled.QueueMusic),
    TabItem("Discover", Icons.Filled.GraphicEq)
)

@Composable
private fun CadenceApp(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    selectedPalette: String,
    onPaletteChange: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (settingsOpen) {
                SettingsScreen(
                    darkTheme = darkTheme,
                    onDarkThemeChange = onDarkThemeChange,
                    selectedPalette = selectedPalette,
                    onPaletteChange = onPaletteChange,
                    onBack = { settingsOpen = false }
                )
            } else {
                when (selectedTab) {
                    0 -> HomeScreen(onSettings = { settingsOpen = true })
                    1 -> LibraryScreen("Songs", "Your music, all in one place.", Icons.Filled.MusicNote)
                    2 -> LibraryScreen("Albums", "Browse your albums and artwork.", Icons.Filled.Album)
                    3 -> LibraryScreen("Playlists", "Your playlists, made around your music.", Icons.Filled.QueueMusic)
                    else -> DiscoverScreen()
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
                                    Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(21.dp))
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
                    start = 16.dp, end = 12.dp, top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                    bottom = 16.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Waves, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("YOUR LIBRARY", fontSize = 10.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    Text("Cadence", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderAction(Icons.Filled.Person, "Account") {}
                HeaderAction(Icons.Filled.BarChart, "Listening stats") {}
                HeaderAction(Icons.Filled.Crown, "Cadence Pro", tint = MaterialTheme.colorScheme.primary) {}
                HeaderAction(Icons.Filled.Search, "Search") {}
                HeaderAction(Icons.Filled.Settings, "Settings", onSettings)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
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
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
                    }
                    Spacer(Modifier.height(22.dp))
                    Text("A FRESH START", fontSize = 10.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                    Text("Your music goes here.", fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Add the songs already on your device. Your library stays yours, and you can organise it into albums, playlists and favourites as you go.",
                        fontSize = 14.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary).clickable { }.padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.AudioFile, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("Add music", color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(20.dp))
                    androidx.compose.foundation.layout.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        FeatureLabel(Icons.Filled.MusicNote, "Your own music")
                        FeatureLabel(Icons.Filled.PlayArrow, "Offline playback")
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, description: String, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Box(
        modifier = Modifier.size(34.dp).clip(CircleShape).clickable { },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun FeatureLabel(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun LibraryScreen(title: String, subtitle: String, icon: ImageVector) {
    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle(title, subtitle)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(66.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("No ${title.lowercase()} yet", color = MaterialTheme.colorScheme.onBackground, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Text("Add music to your device and scan your library to see it here.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun DiscoverScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        PageTitle("Discover", "Find the right sound in your own library.")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DiscoverFeature(Icons.Filled.Tune, "Find by Mood", "Explore songs by how they sound and feel.")
            }
            item {
                DiscoverFeature(Icons.Filled.GraphicEq, "Discover", "Explore the moods and patterns in your music library.")
            }
        }
    }
}

@Composable
private fun DiscoverFeature(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
            .clickable { }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(3.dp))
            Text(description, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp, bottom = 16.dp)
    ) {
        Text(title, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsScreen(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeChoice(Modifier.weight(1f), "Light", !darkTheme) { onDarkThemeChange(false) }
                            ThemeChoice(Modifier.weight(1f), "Dark", darkTheme) { onDarkThemeChange(true) }
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
