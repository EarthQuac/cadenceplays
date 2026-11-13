package digital.anchorstudio.soundprint

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Infinity
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ProFeature(val icon: ImageVector, val title: String, val description: String)
private data class ProGuide(val title: String, val where: String, val steps: List<String>)

private val proFeatures = listOf(
    ProFeature(Icons.Filled.AudioFile, "Automatic song-info autofill", "After a lyrics/catalog lookup, fill in available song details like title, artist and album automatically."),
    ProFeature(Icons.Filled.MusicNote, "Discover music for your mood", "Get personalized recommendations for activities and moods, plus local mood matching for songs in your library."),
    ProFeature(Icons.Filled.GraphicEq, "Pro Audio Lab", "Fine-tune your sound with a 10-band equalizer, bass and treble controls, loudness leveling and advanced playback options."),
    ProFeature(Icons.Filled.Headphones, "Gapless playback & crossfade", "Reduce gaps between tracks or blend the end of one song into the next."),
    ProFeature(Icons.Filled.Timer, "Sleep controls", "Use enhanced sleep controls and flexible playback timers."),
    ProFeature(Icons.Filled.MusicNote, "Synced lyrics & karaoke", "Follow timestamped lyric lines during playback and use karaoke-style synced lyrics. Basic lyrics lookup stays free."),
    ProFeature(Icons.Filled.Palette, "Exclusive colour palettes", "Personalize Cadence with the Emerald and Monochrome palettes. Amber and Blue & white remain free."),
    ProFeature(Icons.Filled.QueueMusic, "Smart playlists & folder auto-watch", "Use unlimited rule-based smart playlists and automatically watch music folders for new tracks."),
    ProFeature(Icons.Filled.Shield, "Soundprint Pro", "Advanced on-device audio matching helps surface duplicates even when tags differ, explains match confidence, and flags alternate versions for review—not auto-deletion."),
    ProFeature(Icons.Filled.Waves, "Aura Mode", "Settle into immersive listening with reverb, tonal shaping and slow stereo movement.")
)

private val proGuides = listOf(
    ProGuide("Automatic song-info autofill", "Search or open a track’s lyrics/catalog lookup", listOf("Open a song whose tags are incomplete.", "Run the lyrics or catalog lookup and review the available match.", "Use the autofill action to apply the returned title, artist, album and other available metadata.", "Check the result before leaving the editor; catalog matches can be incomplete or ambiguous.")),
    ProGuide("Discover: recommendations for your mood", "Discover", listOf("Open Discover from the app navigation.", "Choose a mood, genre, activity or recommendation category.", "Browse the suggested tracks and use the available play or queue action to try them.", "Use local mood matching to find tracks already in your own library when available.")),
    ProGuide("Synced lyrics and karaoke", "Now Playing → Lyrics", listOf("Start a track and open its Lyrics panel.", "Look up lyrics if none are available.", "When timestamped lyrics exist, the current line follows playback; tap a line when line-seeking is available.", "Switch to karaoke-style presentation for a cleaner sing-along view. Ordinary lyrics lookup remains free.")),
    ProGuide("Audio Lab: EQ, bass, treble and loudness", "Audio Lab / Equalizer", listOf("Open Audio Lab while a track is playing.", "Enable the equalizer, then adjust the 10 frequency bands: low bands shape bass, middle bands affect body and presence, and high bands affect brightness.", "Use bass and treble controls for broad adjustments; use preamp if boosting several bands to avoid clipping.", "Enable loudness leveling for more consistent perceived volume between tracks. Start with small changes and compare at the same volume.")),
    ProGuide("Gapless playback and crossfade", "Settings → Playback", listOf("Open Playback settings.", "Enable gapless playback if you want tracks to follow with minimal silence.", "Set a short crossfade duration if you prefer the outgoing track to blend into the next.", "Test with two tracks; live recordings and albums intended to flow together usually benefit from shorter or disabled crossfade.")),
    ProGuide("Sleep timer and stop after current song", "Player sleep controls", listOf("Open the sleep timer control from the player or playback settings.", "Choose a countdown when you want playback to stop after a set period.", "Choose stop-after-current-song when you want the present track to finish but not continue into the queue.", "Confirm the timer state before putting the phone down.")),
    ProGuide("Emerald and Monochrome palettes", "Settings → Appearance / Theme", listOf("Open Appearance or Theme settings.", "Choose Emerald for a richer green accent or Monochrome for a restrained black-and-white look.", "Return to the player and library to see the theme applied throughout the interface.", "Amber and Blue & white remain available without Pro.")),
    ProGuide("Smart playlists and folder auto-watch", "Playlists and Folders", listOf("Open Playlists and create a smart/rule-based playlist.", "Add one or more rules such as artist, genre, rating or other available track metadata, then save it.", "The playlist collects tracks matching those rules rather than requiring you to add every song manually.", "Open Folders and enable folder watching for a supported music folder if the option is available; grant Android file access when prompted so new tracks can be detected.")),
    ProGuide("Soundprint Pro: advanced audio matching", "Soundprint / duplicate review", listOf("Open the Soundprint duplicate scan from the library tools.", "Run a scan and allow local audio analysis to finish.", "Review the match confidence and compare suspected duplicates or alternate versions before deciding what to keep.", "Use the review actions deliberately: Soundprint flags candidates for you and should not be treated as permission to auto-delete music.")),
    ProGuide("Sleep, queue, favourites and library basics", "Player and library", listOf("Use the queue to decide what plays next; add tracks from song menus or library lists.", "Tap the favourite control to keep a track easy to find.", "Use Songs, Albums, Artists, Genres and Folders to browse your own local collection.", "If you remove a track from the library, check Recycle Bin if you need to restore its library entry.")),
    ProGuide("Aura Mode: immersive listening", "Now Playing → Aura", listOf("Start a song, preferably one with spacious production, ambient textures or a slow build.", "Open Aura Mode and use headphones at a comfortable, low volume.", "Lie back somewhere comfortable, close your eyes if safe, relax your jaw and shoulders, and breathe slowly without forcing it.", "Let your attention follow one instrument or sound at a time; you do not need to imagine anything specific. Tap anywhere to return to the player.", "Settle into a softer, roomier sound with gentle stereo movement. For the fullest effect, start playback before entering Aura Mode."))
)

private val freeFeatures = listOf(
    "Import and play your own local music",
    "Look up and read lyrics",
    "Create regular playlists, save favourites and manage your queue",
    "Smart Mixes and everyday music-library tools",
    "Basic duplicate scanning and review",
    "Amber and Blue & white colour palettes",
    "Listening history, statistics, and library backup and restore",
    "Monthly and yearly listening recaps, including shareable story and square cards"
)

@Composable
fun ProScreen(onBack: () -> Unit, entitled: Boolean = false, trialEndsAt: String? = null,
              onStartTrial: (() -> Unit)? = null, onClaimFounder: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        PageTitle("Cadence Pro", "", onBack)
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (entitled) {
                ProActiveCard(trialEndsAt)
                ProTutorialSection()
                ProFeaturesGrid()
            } else {
                ProHero()
                ProFounderOffer(onClaimFounder)
                ProPlans()
                ProTrialCard(onStartTrial)
                ProTutorialSection()
                ProFreeFeatures()
                ProFeaturesGrid()
                Text("Cadence remains free to use for your music. Paid plans are not available until checkout launches. Founder Access remains subject to available spots and eligibility.",
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp), fontSize = 11.sp, lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Pro Terms   ·   Refund & Cancellation   ·   Privacy Policy   ·   Terms of Service",
                    modifier = Modifier.fillMaxWidth(), fontSize = 10.sp, lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProHero() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), RoundedCornerShape(26.dp)).padding(20.dp)) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Headphones, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text("Your music. Your way.", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        Text("Meet Cadence Pro.", modifier = Modifier.padding(top = 18.dp), fontSize = 31.sp, lineHeight = 36.sp,
            letterSpacing = (-0.8).sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text("More control, smarter music tools, and a deeper listening experience. Keep enjoying Cadence for free, or unlock the Pro power suite with the plan that works for you.",
            modifier = Modifier.padding(top = 10.dp), fontSize = 13.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.padding(top = 17.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Your music, your library", "Local-first listening", "Flexible plans").forEach { label ->
                Text(label, Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                    .padding(horizontal = 11.dp, vertical = 7.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProFounderOffer(onClaim: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Get Pro free for life", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text("Founder Access is staying. The first 100 eligible accounts can claim lifetime Pro at no cost while spots remain.",
            fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), RoundedCornerShape(24.dp)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WorkspacePremium, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Founder offer — Pro free for the first 100", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            Text("Sign in with a verified Cadence account to check eligibility and claim a founder spot. Availability is confirmed by the account service.",
                modifier = Modifier.padding(top = 7.dp), fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onClaim?.invoke() }, enabled = onClaim != null, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(50.dp)) {
                Text(if (onClaim == null) "Account connection required" else "Claim free Pro")
            }
        }
    }
}

@Composable
private fun ProPlans() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text("Choose your Pro plan", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text("Checkout is not live yet. These are planned prices; you will not be charged from this page.",
                modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProPlanCard("Lifetime", "$24.99", "once", "Pay once. Keep the included Cadence Pro features for life, with no recurring bill.",
                "Best for long-term listeners", true, Modifier.weight(1f), Icons.Filled.Infinity)
            ProPlanCard("Monthly", "$2.99", "/ month", "A smaller payment to get started. Cancel any time; Pro access would end when your paid period ends.",
                "Budget-friendly", false, Modifier.weight(1f), Icons.Filled.Timer)
        }
        ProPlanCard("Annual", "$19.99", "/ year", "One yearly payment for listeners who want Pro value without a lifetime upfront cost.",
            "Save vs. monthly", false, Modifier.fillMaxWidth(), Icons.Filled.Timer)
    }
}

@Composable
private fun ProPlanCard(title: String, price: String, cadence: String, description: String, tag: String,
                        featured: Boolean, modifier: Modifier = Modifier, icon: ImageVector) {
    Column(modifier.clip(RoundedCornerShape(20.dp))
        .background(if (featured) MaterialTheme.colorScheme.primary.copy(alpha = 0.055f) else MaterialTheme.colorScheme.surface)
        .border(1.dp, if (featured) MaterialTheme.colorScheme.primary.copy(alpha = 0.36f) else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
        .padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
            Text(price, fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(cadence, modifier = Modifier.padding(start = 4.dp, bottom = 3.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(description, modifier = Modifier.padding(top = 8.dp), fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(tag, modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(50))
            .background(if (featured) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.background)
            .padding(horizontal = 9.dp, vertical = 6.dp), fontSize = 9.sp,
            fontWeight = if (featured) FontWeight.Medium else FontWeight.Normal,
            color = if (featured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().padding(top = 13.dp), shape = RoundedCornerShape(50.dp),
            contentPadding = ButtonDefaults.ContentPadding) {
            Text(if (title == "Lifetime") "Lifetime checkout coming soon" else if (title == "Monthly") "Monthly plan coming soon" else "Annual plan coming soon",
                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProTrialCard(onStartTrial: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.WorkspacePremium, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Try Pro for 14 days", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("Try the Pro features without entering payment details. This trial does not auto-renew and will not charge you. One trial per account.",
                    modifier = Modifier.padding(top = 5.dp), fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(if (onStartTrial == null) "A verified Cadence account is required. Native account sign-in is not connected yet."
            else "Start your trial below. No payment system is needed for the trial.",
            modifier = Modifier.fillMaxWidth().padding(top = 13.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background).padding(12.dp),
            fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { onStartTrial?.invoke() }, enabled = onStartTrial != null, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            shape = RoundedCornerShape(50.dp)) {
            Text(if (onStartTrial == null) "Sign-in connection required" else "Start 14-day free trial")
        }
    }
}

@Composable
private fun ProTutorialSection() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f), RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("YOUR QUICK-START GUIDE", fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text("Get the most out of Pro", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text("Open a guide below for where to find the feature and how to use it. Some controls vary slightly by device and app version.",
            fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        proGuides.forEachIndexed { index, guide -> ProGuideRow(index + 1, guide) }
    }
}

@Composable
private fun ProGuideRow(number: Int, guide: ProGuide) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(16.dp))) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                Text(number.toString(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                Text(guide.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(guide.where, modifier = Modifier.padding(top = 3.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 53.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                guide.steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text((index + 1).toString() + ".", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(step, modifier = Modifier.padding(start = 7.dp).weight(1f), fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProFreeFeatures() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)).padding(16.dp)) {
        Text("What stays free", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text("Cadence stays useful without Pro. These features are free for everyone.",
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp), fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        freeFeatures.forEach { feature ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)).padding(11.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(feature, modifier = Modifier.padding(start = 9.dp).weight(1f), fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ProFeaturesGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text("Every Pro feature", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text("The tools included with Cadence Pro.", modifier = Modifier.padding(top = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        proFeatures.forEach { feature ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f), RoundedCornerShape(18.dp)).padding(14.dp),
                verticalAlignment = Alignment.Top) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center) {
                    Icon(feature.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                }
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(feature.title, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text(feature.description, modifier = Modifier.padding(top = 5.dp), fontSize = 10.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ProActiveCard(trialEndsAt: String?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(24.dp)).padding(18.dp)) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WorkspacePremium, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text("Pro active", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        Text(if (trialEndsAt != null) "Your 14-day Pro trial is active" else "Welcome to Cadence Pro",
            modifier = Modifier.padding(top = 16.dp), fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(if (trialEndsAt != null) "Your no-payment trial ends " + trialEndsAt + ". No charge will be made, and access ends when the trial expires."
            else "Your Pro entitlement is active on this account. Sign in on your other devices to access your account benefits.",
            modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
