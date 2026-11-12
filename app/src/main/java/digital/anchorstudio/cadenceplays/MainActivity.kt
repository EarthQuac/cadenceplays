package digital.anchorstudio.cadenceplays

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CadenceBackground = Color(0xFF090A0D)
private val CadenceSurface = Color(0xFF15171C)
private val CadenceAccent = Color(0xFFB7F36B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = CadenceBackground,
                    surface = CadenceSurface,
                    primary = CadenceAccent,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                CadenceApp()
            }
        }
    }
}

@Composable
private fun CadenceApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Home", "Songs", "Albums", "Playlists")

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CadenceBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 26.dp)
        ) {
            Text(
                text = "CADENCE",
                color = CadenceAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(34.dp))
            Text(
                text = tabs[selectedTab],
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your music. Your library. Your space.",
                color = Color(0xFF9297A1),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(30.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(CadenceSurface, RoundedCornerShape(24.dp))
                    .padding(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .height(76.dp)
                            .fillMaxWidth(0.55f)
                            .background(Color(0xFF242A20), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.LibraryMusic,
                            contentDescription = null,
                            tint = CadenceAccent
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = "Your library starts here",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Add your music to begin listening offline.",
                        color = Color(0xFF9297A1),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                tabs.forEachIndexed { index, label ->
                    androidx.compose.material3.TextButton(
                        onClick = { selectedTab = index }
                    ) {
                        Text(
                            text = label,
                            color = if (selectedTab == index) CadenceAccent else Color(0xFF9297A1),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
