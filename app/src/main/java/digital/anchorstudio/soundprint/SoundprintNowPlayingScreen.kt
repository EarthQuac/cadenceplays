package digital.anchorstudio.soundprint

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Virtualizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlin.math.max
import kotlin.math.min

private val PlayerSurface = Color(0xFF171513)
private val PlayerSurface2 = Color(0xFF211E1A)
private val PlayerTextMuted = Color(0xFF9C9792)

private enum class PlayerDiscStyle { VINYL, CD, ARTWORK, NONE }
private enum class PlayerDiscFinish { CLASSIC, PRISMATIC, MIDNIGHT }

@Composable
fun SoundprintNowPlayingScreen(
    controller: MediaController?,
    library: MusicLibraryManager,
    onBack: () -> Unit,
    onOpenAudioLab: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerPrefs = remember { context.getSharedPreferences("soundprint_now_playing", 0) }
    var trackId by remember { mutableStateOf(controller?.currentMediaItem?.mediaId.orEmpty()) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var artwork by remember { mutableStateOf<String?>(null) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(controller?.shuffleModeEnabled ?: false) }
    var repeatMode by remember { mutableIntStateOf(controller?.repeatMode ?: Player.REPEAT_MODE_OFF) }
    var volume by remember { mutableFloatStateOf(controller?.volume ?: 1f) }
    var queueIds by remember { mutableStateOf(emptyList<String>()) }
    var currentIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(controller) {
        while (controller != null) {
            val player = controller
            trackId = player.currentMediaItem?.mediaId.orEmpty()
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET } ?: 0L
            playing = player.isPlaying
            shuffle = player.shuffleModeEnabled
            repeatMode = player.repeatMode
            volume = player.volume
            currentIndex = player.currentMediaItemIndex
            queueIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
            delay(250)
        }
    }
    LaunchedEffect(trackId) {
        val track = withContext(Dispatchers.IO) { library.song(trackId) }
        if (track != null) {
            title = track.title; artist = track.artist; album = track.album; genre = track.genre
            artwork = track.artworkUri
            duration = max(duration, track.durationMs)
        }
    }

    var discStyle by remember { mutableStateOf(PlayerDiscStyle.VINYL) }
    var discFinish by remember { mutableStateOf(PlayerDiscFinish.CLASSIC) }
    var spin by remember { mutableStateOf(true) }
    var spinSpeed by remember { mutableIntStateOf(4) }
    var tonearm by remember { mutableStateOf(true) }
    var ambientGlow by remember { mutableStateOf(true) }
    var customizerOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    var editTitle by remember(title) { mutableStateOf(title) }
    var editArtist by remember(artist) { mutableStateOf(artist) }
    var editAlbum by remember(album) { mutableStateOf(album) }
    var editGenre by remember(genre) { mutableStateOf(genre) }
    var playlistOpen by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf(emptyList<ManagedPlaylist>()) }
    var infoOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var sleepOpen by remember { mutableStateOf(false) }
    var sleepMinutes by remember { mutableStateOf("30") }
    var sleepRemaining by remember { mutableLongStateOf(0L) }
    var moodOpen by remember { mutableStateOf(false) }
    var moodStatus by remember { mutableStateOf("") }
    var moodBusy by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf("") }
    var lyricsSource by remember { mutableStateOf("") }
    var lyricsError by remember { mutableStateOf("") }
    var lyricsBusy by remember { mutableStateOf(false) }
    var lyricsExpanded by remember { mutableStateOf(false) }
    var karaoke by remember { mutableStateOf(false) }
    var lyricsEdit by remember { mutableStateOf(false) }
    var lyricsDraft by remember { mutableStateOf("") }
    var aura by remember { mutableStateOf(false) }
    var auraPreset by remember { mutableStateOf("Auto") }
    var audioLabOpen by remember { mutableStateOf(false) }
    var bass by remember { mutableFloatStateOf(0f) }
    var treble by remember { mutableFloatStateOf(0f) }
    var gapless by remember { mutableStateOf(true) }
    var loudness by remember { mutableStateOf(false) }
    var stopAfterCurrent by remember { mutableStateOf(false) }
    var crossfade by remember { mutableIntStateOf(0) }

    LaunchedEffect(sleepRemaining) {
        while (sleepRemaining > 0L) {
            delay(1000)
            sleepRemaining = (sleepRemaining - 1000L).coerceAtLeast(0L)
            if (sleepRemaining == 0L) controller?.pause()
        }
    }
    LaunchedEffect(controller, stopAfterCurrent) {
        if (!stopAfterCurrent || controller == null) return@LaunchedEffect
        val startId = controller.currentMediaItem?.mediaId
        while (controller.currentMediaItem?.mediaId == startId) delay(250)
        controller.pause()
        stopAfterCurrent = false
    }

    val progress = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(progress, tween(180, easing = FastOutSlowInEasing), label = "player-progress")
    var favorite by remember { mutableStateOf(library.isFavorite(trackId)) }
    LaunchedEffect(trackId) { favorite = library.isFavorite(trackId) }
    val track = remember(trackId, title, artist, album, genre, artwork, duration) {
        library.song(trackId)?.copy(title = title, artist = artist, album = album, genre = genre, artworkUri = artwork, durationMs = duration)
    }
    val queueTracks = remember(queueIds, currentIndex) { queueIds.drop((currentIndex + 1).coerceAtLeast(0)).mapNotNull { library.song(it) } }
    val displaySleep = if (sleepRemaining > 0L) "%d:%02d".format(sleepRemaining / 60000L, (sleepRemaining / 1000L) % 60L) else "Sleep"

    if (aura) {
        AuraListeningStage(
            mood = track?.let { library.clapMoodProfile(it)?.scores?.maxByOrNull { score -> score.value }?.key } ?: "adaptive",
            preset = auraPreset,
            onPreset = { auraPreset = it },
            onExit = { aura = false }
        )
        return
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Go back") }
                    Text("NOW PLAYING", fontSize = 11.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        IconButton(onClick = { /* queue is shown below */ }) { Icon(Icons.Filled.QueueMusic, "Queue") }
                        IconButton(onClick = onBack) { Icon(Icons.Filled.Close, "Close", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    if (ambientGlow) {
                        Box(Modifier.fillMaxWidth(.82f).aspectRatio(1f).clip(CircleShape).background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .20f), Color.Transparent))))
                    }
                    Box(
                        Modifier.fillMaxWidth(.88f).aspectRatio(1f).clip(RoundedCornerShape(28.dp))
                            .background(Brush.linearGradient(listOf(PlayerSurface2, MaterialTheme.colorScheme.background, PlayerSurface)))
                            .clickable { customizerOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (discStyle == PlayerDiscStyle.NONE) {
                            TrackArtwork(artwork, title, Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)))
                        } else {
                            Box(Modifier.fillMaxWidth(.82f).aspectRatio(1f).clip(CircleShape)
                                .background(discBrush(discStyle, discFinish))
                                .then(if (spin && playing) Modifier.rotate((position / 1000f * 360f / spinSpeed).rem(360f)) else Modifier),
                                contentAlignment = Alignment.Center) {
                                if (discStyle == PlayerDiscStyle.ARTWORK) TrackArtwork(artwork, title, Modifier.fillMaxSize().clip(CircleShape))
                                else Box(Modifier.fillMaxWidth(.35f).aspectRatio(1f).clip(CircleShape).border(4.dp, Color.Black.copy(alpha=.65f), CircleShape)) {
                                    TrackArtwork(artwork, title, Modifier.fillMaxSize().clip(CircleShape))
                                }
                            }
                            if (discStyle == PlayerDiscStyle.VINYL && tonearm) {
                                Box(Modifier.align(Alignment.TopEnd).padding(end = 50.dp, top = 24.dp).width(5.dp).height(130.dp).rotate(24f).clip(CircleShape).background(Color.LightGray))
                            }
                        }
                        IconButton(onClick = { customizerOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).background(Color.Black.copy(alpha=.55f), CircleShape)) {
                            Icon(Icons.Filled.Tune, "Customize artwork", tint = Color.White)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title.ifBlank { "Nothing playing" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$artist · $album", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { editTitle=title; editArtist=artist; editAlbum=album; editGenre=genre; editOpen=true }) { Icon(Icons.Filled.Edit, "Edit song info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { library.toggleFavorite(trackId); favorite = library.isFavorite(trackId) }) {
                        Icon(Icons.Filled.Favorite, "Add to favorites", tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Spacer(Modifier.height(18.dp))
                Slider(value = animatedProgress, onValueChange = { controller?.seekTo((it * duration).toLong()) }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatPlayerTime(position), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (duration > 0) "-${formatPlayerTime(duration-position)}" else "Indexing…", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { controller?.shuffleModeEnabled = !shuffle }) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { controller?.seekToPreviousMediaItem() }) { Icon(Icons.Filled.SkipPrevious, "Previous", modifier = Modifier.size(30.dp)) }
                    FilledIconButton(onClick = { if (playing) controller?.pause() else controller?.play() }, modifier = Modifier.size(64.dp)) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = { controller?.seekToNextMediaItem() }) { Icon(Icons.Filled.SkipNext, "Next", modifier = Modifier.size(30.dp)) }
                    IconButton(onClick = {
                        controller?.repeatMode = when (repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
                    }) { Icon(Icons.Filled.Repeat, "Repeat", tint = if (repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary) }
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Quick actions", fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text("The essentials, within reach", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                val actions = listOf(
                    Triple("Audio Lab", Icons.Filled.GraphicEq) { audioLabOpen = true },
                    Triple("Aura Mode", Icons.Filled.Waves) { aura = true },
                    Triple("Playlist", Icons.Filled.Add) { playlists = library.playlists(); playlistOpen = !playlistOpen },
                    Triple("Info", Icons.Filled.Info) { infoOpen = !infoOpen },
                    Triple("Lyrics", Icons.Filled.MusicNote) { lyricsExpanded = true },
                    Triple(displaySleep, Icons.Filled.NightsStay) { sleepOpen = true }
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (label, icon, action) ->
                                Column(Modifier.weight(1f).height(82.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surface).clickable { action() }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                                    Spacer(Modifier.height(7.dp))
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                }
                            }
                            repeat(3-row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).clickable { moreOpen = !moreOpen }.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (moreOpen) "Less" else "More actions", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Icon(if (moreOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                }
                if (moreOpen) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerAction("Share track", Icons.Filled.Share) { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Listening to “$title” by $artist on Soundprint."), "Share track")) }
                        PlayerAction("Restart track", Icons.Filled.Replay) { controller?.seekTo(0L) }
                        PlayerAction("Playback options", Icons.Filled.Settings) { optionsOpen = true }
                        PlayerAction("Customize artwork", Icons.Filled.Album) { customizerOpen = true }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Volume", fontSize = 12.sp, fontWeight = FontWeight.Medium); Text("${(volume*15).toInt()} / 15", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                    Slider(value = volume, onValueChange = { volume=it; controller?.volume=it })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Muted", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Maximum app volume", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (playlistOpen) item {
                PlayerPanel("Add to playlist", onClose = { playlistOpen=false }) {
                    if (playlists.isEmpty()) Text("Create a playlist first from the Playlists section.", color=MaterialTheme.colorScheme.onSurfaceVariant, fontSize=12.sp)
                    playlists.forEach { list -> Row(Modifier.fillMaxWidth().clickable { library.addToPlaylist(list.id, list.trackIds + trackId); playlistOpen=false }.padding(10.dp), verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Filled.QueueMusic,null,tint=MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text(list.name,Modifier.weight(1f)); Text(list.trackIds.size.toString(),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
            }
            if (infoOpen) item {
                PlayerPanel("Track details", onClose = { infoOpen=false }) {
                    val details = listOf("Title" to title,"Artist" to artist,"Album" to album,"Genre" to genre,"Duration" to formatPlayerTime(duration),"Format" to (track?.mimeType ?: "Unknown"),"Bitrate" to "${track?.bitrate ?: 0} kbps","Sample rate" to "${track?.sampleRate ?: 0} Hz","Channels" to (track?.channelCount?.toString() ?: "Unknown"),"File size" to "${(track?.sizeBytes ?: 0L)/1024} KB","Folder" to (track?.folder ?: "Unknown"),"Times played" to (track?.playCount?.toString() ?: "0"))
                    details.chunked(2).forEach { pair -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { pair.forEach { (k,v) -> Column(Modifier.weight(1f).padding(vertical=5.dp)) { Text(k,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant); Text(v,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis) } }; if(pair.size==1) Spacer(Modifier.weight(1f)) } }
                }
            }
            if (moodOpen) item {
                PlayerPanel("Find by mood", onClose={moodOpen=false}) {
                    val moods=listOf("workout","chill","focus","late-night","romantic","melancholy")
                    moods.chunked(2).forEach { pair -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { pair.forEach { mood -> OutlinedButton(onClick={
                        moodBusy=true; moodStatus="Checking your saved audio mood profiles…"
                        val candidates=library.allSongs().filter { it.id!=trackId }
                        val matches=candidates.mapNotNull { candidate -> library.clapMoodProfile(candidate)?.let { candidate to (it.scores[mood] ?: 0f) } }.filter { it.second>=.35f }.sortedByDescending { it.second }.take(20).map { it.first }
                        if(matches.isNotEmpty()) controller?.addMediaItems((currentIndex+1).coerceAtLeast(0),matches.map { it.toSoundprintMediaItem() })
                        moodStatus=if(matches.isEmpty()) "No saved matches yet. Run Discover scan to analyse more songs." else "Added ${matches.size} mood matches to your queue."
                        moodBusy=false
                    },modifier=Modifier.weight(1f),enabled=!moodBusy){ Text(mood.replaceFirstChar { it.uppercase() },fontSize=11.sp) } } } }
                    if(moodStatus.isNotBlank()) Text(moodStatus,fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
                }
            }
            item {
                PlayerPanel("Lyrics", onClose = { lyricsExpanded=false }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={ lyricsBusy=true; lyricsError=""; /* lookup below is driven by the coroutine effect */ }) { Text(if(lyricsBusy) "Searching…" else "Find lyrics online") }
                        OutlinedButton(onClick={ lyricsEdit=true; lyricsDraft=lyrics }) { Text("Edit") }
                        if (lyrics.isNotBlank()) OutlinedButton(onClick={ karaoke=true }) { Text("Karaoke") }
                    }
                    if (lyricsEdit) {
                        OutlinedTextField(value=lyricsDraft,onValueChange={lyricsDraft=it},modifier=Modifier.fillMaxWidth().heightIn(min=150.dp),label={Text("Lyrics / LRC")})
                        Button(onClick={ lyrics=lyricsDraft; lyricsSource="Edited locally"; lyricsEdit=false }) { Text("Save lyrics") }
                    } else if (lyrics.isNotBlank()) {
                        Text(lyrics,modifier=Modifier.fillMaxWidth().heightIn(max=if(lyricsExpanded) 520.dp else 240.dp).verticalScroll(rememberScrollState()),fontSize=13.sp,lineHeight=23.sp,textAlign=TextAlign.Center)
                        Text("Saved on this device · $lyricsSource",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Fetch lyrics, import an LRC file, or add your own. Saved lyrics remain available offline.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(lyricsError.isNotBlank()) Text(lyricsError,color=MaterialTheme.colorScheme.error,fontSize=11.sp)
                    }
                }
            }
            if (queueTracks.isNotEmpty()) item {
                PlayerPanel("Up next · ${queueTracks.size}", onClose={}) {
                    queueTracks.take(8).forEachIndexed { index, item -> Row(Modifier.fillMaxWidth().clickable { controller?.seekTo((currentIndex+1+index),0L) }.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.title,fontSize=12.sp,fontWeight=FontWeight.Medium,maxLines=1); Text(item.artist,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }; Text("Play",fontSize=10.sp,color=MaterialTheme.colorScheme.primary) } }
                }
            }
        }
    }

    if (customizerOpen) AlertDialog(onDismissRequest={customizerOpen=false},title={Text("Customize artwork")},text={
        Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("ARTWORK STYLE",fontSize=10.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
            PlayerChoiceRow(listOf("Vinyl","CD","Artwork disc","Cover only"),discStyle.name) { discStyle=when(it){"CD"->PlayerDiscStyle.CD;"Artwork disc"->PlayerDiscStyle.ARTWORK;"Cover only"->PlayerDiscStyle.NONE;else->PlayerDiscStyle.VINYL} }
            if(discStyle!=PlayerDiscStyle.NONE) {
                Text("FINISH",fontSize=10.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
                PlayerChoiceRow(listOf("Classic","Prismatic","Midnight"),discFinish.name) { discFinish=when(it){"Prismatic"->PlayerDiscFinish.PRISMATIC;"Midnight"->PlayerDiscFinish.MIDNIGHT;else->PlayerDiscFinish.CLASSIC} }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()){ Text("Spin while playing"); Switch(spin,{spin=it}) }
                Text("Speed",fontSize=12.sp); PlayerChoiceRow(listOf("Slow","Normal","Fast"),spinSpeed.toString()) { spinSpeed=when(it){"Slow"->8;"Fast"->2;else->4} }
                if(discStyle==PlayerDiscStyle.VINYL) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()){ Text("Turntable arm"); Switch(tonearm,{tonearm=it}) }
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()){ Text("Ambient glow"); Switch(ambientGlow,{ambientGlow=it}) }
        }
    },confirmButton={TextButton(onClick={customizerOpen=false}){Text("Done")}},dismissButton={TextButton(onClick={discStyle=PlayerDiscStyle.VINYL;discFinish=PlayerDiscFinish.CLASSIC;spin=true;spinSpeed=4;tonearm=true;ambientGlow=true}){Text("Reset")}})
    if(editOpen) AlertDialog(onDismissRequest={editOpen=false},title={Text("Edit song info")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(editTitle,{editTitle=it},label={Text("Title")});OutlinedTextField(editArtist,{editArtist=it},label={Text("Artist")});OutlinedTextField(editAlbum,{editAlbum=it},label={Text("Album")});OutlinedTextField(editGenre,{editGenre=it},label={Text("Genre")})}},confirmButton={TextButton(onClick={library.updateSongMetadata(trackId,editTitle,editArtist,editAlbum,editGenre);title=editTitle;artist=editArtist;album=editAlbum;genre=editGenre;editOpen=false}){Text("Save changes")}},dismissButton={TextButton(onClick={editOpen=false}){Text("Cancel")}})
    if(sleepOpen) AlertDialog(onDismissRequest={sleepOpen=false},title={Text("Sleep timer")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Stop playback after…",fontSize=12.sp);Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf(15,30,45,60).forEach{OutlinedButton(onClick={sleepMinutes=it.toString()}){Text("${it}m")}}};OutlinedTextField(sleepMinutes,{sleepMinutes=it},label={Text("Custom minutes")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));if(sleepRemaining>0)Text("Remaining: $displaySleep")}},confirmButton={TextButton(onClick={sleepRemaining=(sleepMinutes.toLongOrNull()?.coerceIn(1,720)?:30)*60000L;sleepOpen=false}){Text("Start timer")}},dismissButton={TextButton(onClick={sleepRemaining=0;sleepOpen=false}){Text(if(sleepRemaining>0)"Cancel timer" else "Close")}})
    if(optionsOpen) AlertDialog(onDismissRequest={optionsOpen=false},title={Text("Playback options")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){PlayerToggle("Gapless playback","Reduce the gap between tracks",gapless){gapless=it};PlayerToggle("Loudness leveling","Keep volume consistent",loudness){loudness=it};PlayerToggle("Stop after this song","End playback after this track",stopAfterCurrent){stopAfterCurrent=it};Text("Crossfade",fontSize=12.sp,fontWeight=FontWeight.SemiBold);PlayerChoiceRow(listOf("Off","2s","4s","6s","8s"),"${crossfade}s"){crossfade=it.removeSuffix("s").toIntOrNull()?:0}}},confirmButton={TextButton(onClick={optionsOpen=false}){Text("Done")}})
    if(karaoke && lyrics.isNotBlank()) AlertDialog(onDismissRequest={karaoke=false},title={Text("Soundprint · Karaoke")},text={Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)){Text(lyrics.lines().firstOrNull { it.isNotBlank() } ?: "♪",fontSize=26.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary,textAlign=TextAlign.Center);Text("Karaoke view",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}},confirmButton={TextButton(onClick={karaoke=false}){Text("Close")}})
    if(audioLabOpen) AlertDialog(onDismissRequest={audioLabOpen=false},title={Text("Audio Lab")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Bass");Slider(value=bass,onValueChange={bass=it},valueRange=-1f..1f);Text("Treble");Slider(value=treble,onValueChange={treble=it},valueRange=-1f..1f)}},confirmButton={TextButton(onClick={audioLabOpen=false}){Text("Done")}})
}

@Composable
private fun TrackArtwork(uri: String?, title: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                if (uri.isNullOrBlank()) null
                else if (uri.startsWith("/")) BitmapFactory.decodeFile(uri)
                else context.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream)
            }.getOrNull()
        }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), title, modifier)
    else Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF47382D),Color(0xFF171513)))),contentAlignment=Alignment.Center){Icon(Icons.Filled.MusicNote,null,tint=Color.White.copy(alpha=.8f),modifier=Modifier.size(52.dp))}
}

@Composable
private fun RowScope.PlayerAction(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){
    Column(Modifier.weight(1f).height(82.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick=onClick).padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.height(5.dp));Text(label,fontSize=9.sp,textAlign=TextAlign.Center)}
}
@Composable
private fun PlayerPanel(title:String,onClose:()->Unit,content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxWidth().padding(top=10.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(title,fontWeight=FontWeight.SemiBold,fontSize=14.sp);if(title!="Up next")IconButton(onClick=onClose,modifier=Modifier.size(28.dp)){Icon(Icons.Filled.Close,null,modifier=Modifier.size(16.dp))}};content()}
}
@Composable
private fun PlayerChoiceRow(options:List<String>,selected:String,onSelect:(String)->Unit){
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){options.forEach{item->FilterChip(selected=selected.equals(item,true)||selected.equals(item.replace(" ","_"),true),onClick={onSelect(item)},label={Text(item,fontSize=11.sp)})}}
}
@Composable
private fun PlayerToggle(title:String,detail:String,checked:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,fontSize=12.sp,fontWeight=FontWeight.Medium);Text(detail,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(checked,onChange)}}
private fun discBrush(style:PlayerDiscStyle,finish:PlayerDiscFinish):Brush = when(style){
    PlayerDiscStyle.VINYL -> Brush.radialGradient(listOf(Color(0xFF444448),Color(0xFF111114),Color(0xFF29292E),Color(0xFF09090B)))
    PlayerDiscStyle.CD -> Brush.sweepGradient(if(finish==PlayerDiscFinish.PRISMATIC) listOf(Color(0xFFFF6F91),Color(0xFFFFCE70),Color(0xFF7CE7C4),Color(0xFF77A7FF),Color(0xFFD78AFF)) else listOf(Color.LightGray,Color(0xFFF5D9E5),Color(0xFFA8D5CE),Color(0xFFE9E2B5),Color.LightGray))
    PlayerDiscStyle.ARTWORK -> Brush.radialGradient(listOf(Color(0xFF47382D),Color(0xFF171513)))
    PlayerDiscStyle.NONE -> Brush.linearGradient(listOf(Color.DarkGray,Color.Black))
}
private fun formatPlayerTime(ms:Long):String{val seconds=(ms/1000L).coerceAtLeast(0L);return "%d:%02d".format(seconds/60L,seconds%60L)}

@Composable
private fun AuraListeningStage(mood:String,preset:String,onPreset:(String)->Unit,onExit:()->Unit){
    var prompt by remember{mutableIntStateOf(0)}
    val prompts=remember(mood){when(mood){"energetic","workout"->listOf("Feel the first hit land. Let the rhythm move you.","Let the bass give the moment weight.","When the energy rises, let the whole scene level up.");"sad","melancholy"->listOf("Let the song have its honest colour.","Give the feeling room without asking yourself to solve it.","Stay with the sound gently.");else->listOf("Let this song set the atmosphere.","Follow the changes as they happen.","Stay with the feeling the track gives you, exactly as it is.")}}
    LaunchedEffect(Unit){while(true){delay(32000);prompt=(prompt+1)%prompts.size}}
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF17251F),Color.Black))),contentAlignment=Alignment.Center){
        Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(22.dp)){
            Text("ENTERING AURA MODE",fontSize=10.sp,letterSpacing=3.sp,color=Color(0xFFB7D9C5))
            AnimatedContent(prompts[prompt],label="aura-copy"){Text(it,fontSize=22.sp,fontWeight=FontWeight.Light,color=Color.White,textAlign=TextAlign.Center,lineHeight=32.sp)}
            Text("Follow the sound",fontSize=9.sp,letterSpacing=3.sp,color=Color.White.copy(alpha=.4f))
            Text("SOUND ATMOSPHERE",fontSize=9.sp,letterSpacing=2.sp,color=Color.White.copy(alpha=.4f))
            FlowRow(horizontalArrangement=Arrangement.Center){listOf("Auto","Slowed + Reverb","Pulse","Dreamscape","Midnight").forEach{FilterChip(selected=preset==it,onClick={onPreset(it)},label={Text(it,fontSize=10.sp)})}}
            Spacer(Modifier.height(20.dp))
            Text("BEST WITH HEADPHONES · KEEP VOLUME COMFORTABLE",fontSize=9.sp,color=Color.White.copy(alpha=.45f))
            TextButton(onClick=onExit){Text("Tap to return",color=Color.White.copy(alpha=.7f))}
        }
    }
}
