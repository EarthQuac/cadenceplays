package digital.anchorstudio.soundprint

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
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
    val lyricsPanelRequester = remember { BringIntoViewRequester() }
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

    var discStyle by remember { mutableStateOf(runCatching { PlayerDiscStyle.valueOf(playerPrefs.getString("disc-style", "VINYL") ?: "VINYL") }.getOrDefault(PlayerDiscStyle.VINYL)) }
    var discFinish by remember { mutableStateOf(runCatching { PlayerDiscFinish.valueOf(playerPrefs.getString("disc-finish", "CLASSIC") ?: "CLASSIC") }.getOrDefault(PlayerDiscFinish.CLASSIC)) }
    var spin by remember { mutableStateOf(playerPrefs.getBoolean("disc-spin", true)) }
    var spinSpeed by remember { mutableIntStateOf(playerPrefs.getInt("disc-speed", 4).coerceIn(2, 8)) }
    var tonearm by remember { mutableStateOf(playerPrefs.getBoolean("disc-tonearm", true)) }
    var ambientGlow by remember { mutableStateOf(playerPrefs.getBoolean("disc-glow", true)) }
    var queueOpen by remember { mutableStateOf(false) }
    LaunchedEffect(discStyle, discFinish, spin, spinSpeed, tonearm, ambientGlow) {
        playerPrefs.edit().putString("disc-style", discStyle.name).putString("disc-finish", discFinish.name)
            .putBoolean("disc-spin", spin).putInt("disc-speed", spinSpeed)
            .putBoolean("disc-tonearm", tonearm).putBoolean("disc-glow", ambientGlow).apply()
    }
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
    val playbackOptionsPrefs = remember { context.getSharedPreferences("soundprint_playback_options", 0) }
    var sleepRemaining by remember { mutableLongStateOf(0L) }
    var lyricsExpanded by remember { mutableStateOf(false) }
    var auraNotice by remember { mutableStateOf(false) }
    var aura by remember { mutableStateOf(false) }
    var auraPreset by remember { mutableStateOf("Auto") }
    var audioLabOpen by remember { mutableStateOf(false) }
    var bass by remember { mutableFloatStateOf(0f) }
    var treble by remember { mutableFloatStateOf(0f) }
    var eqLevels by remember { mutableStateOf(List(10) { 0f }) }
    var loudness by remember { mutableStateOf(false) }
    var stopAfterCurrent by remember { mutableStateOf(playbackOptionsPrefs.getBoolean("stop_after_current", false)) }
    var crossfade by remember { mutableIntStateOf(playbackOptionsPrefs.getInt("crossfade_seconds", 0).coerceIn(0, 8)) }
    val doubleTapSeekSeconds = remember { playbackOptionsPrefs.getInt("double_tap_seek_seconds", 10).coerceIn(5, 60) }

    // The service owns the timer, not this screen. Its absolute deadline survives
    // navigation away from Now Playing and fades the audio before pausing.
    LaunchedEffect(Unit) {
        while (true) {
            val endAt = playbackOptionsPrefs.getLong("sleep_ends_at", 0L)
            sleepRemaining = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
            delay(1000L)
        }
    }
    LaunchedEffect(stopAfterCurrent) {
        playbackOptionsPrefs.edit().putBoolean("stop_after_current", stopAfterCurrent).apply()
    }
    LaunchedEffect(crossfade) {
        playbackOptionsPrefs.edit().putInt("crossfade_seconds", crossfade).apply()
    }

    val progress = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(progress, tween(180, easing = FastOutSlowInEasing), label = "player-progress")
    var favorite by remember { mutableStateOf(false) }
    LaunchedEffect(trackId) { favorite = withContext(Dispatchers.IO) { library.isFavorite(trackId) } }
    val track = produceState<MusicTrack?>(null, trackId, title, artist, album, genre, artwork, duration) {
        value = withContext(Dispatchers.IO) { library.song(trackId) }
            ?.copy(title = title, artist = artist, album = album, genre = genre, artworkUri = artwork, durationMs = duration)
    }.value
    val queueTracks = produceState(emptyList<MusicTrack>(), queueIds, currentIndex) {
        val upcoming = queueIds.drop((currentIndex + 1).coerceAtLeast(0))
        value = withContext(Dispatchers.IO) {
            if (upcoming.size <= 200) upcoming.mapNotNull { library.song(it) }
            else library.allSongs().associateBy { it.id }.let { byId -> upcoming.mapNotNull { byId[it] } }
        }
    }.value
    val displaySleep = if (sleepRemaining > 0L) "%d:%02d".format(sleepRemaining / 60000L, (sleepRemaining / 1000L) % 60L) else "Sleep"
    DisposableEffect(controller?.audioSessionId, aura, auraPreset) {
        val sessionId = controller?.audioSessionId ?: 0
        val effects = mutableListOf<android.media.audiofx.AudioEffect>()
        if (aura && sessionId > 0) {
            runCatching {
                BassBoost(0, sessionId).also {
                    it.setStrength((if (auraPreset == "Pulse") 700 else 350).toShort())
                    it.enabled = true
                    effects += it
                }
            }
            runCatching {
                EnvironmentalReverb(0, sessionId).also {
                    it.reverbLevel = if (auraPreset == "Pulse") 900.toShort() else 4200.toShort()
                    it.roomLevel = (-1800).toShort()
                    it.enabled = true
                    effects += it
                }
            }
            runCatching {
                Virtualizer(0, sessionId).also {
                    it.setStrength((if (auraPreset == "Dreamscape") 650 else 350).toShort())
                    it.enabled = true
                    effects += it
                }
            }
        }
        onDispose { effects.forEach { runCatching { it.release() } } }
    }
    LaunchedEffect(aura, auraPreset) {
        controller?.setPlaybackSpeed(if (aura && auraPreset == "Slowed + Reverb") 0.85f else 1f)
    }
    DisposableEffect(controller?.audioSessionId, loudness) {
        val sessionId = controller?.audioSessionId ?: 0
        val enhancer = if (loudness && sessionId > 0) runCatching {
            LoudnessEnhancer(sessionId).also { it.setTargetGain(250); it.enabled = true }
        }.getOrNull() else null
        onDispose { runCatching { enhancer?.release() } }
    }
    DisposableEffect(controller?.audioSessionId, audioLabOpen, bass, treble, eqLevels) {
        val sessionId = controller?.audioSessionId ?: 0
        var equalizer: Equalizer? = null
        if (audioLabOpen && sessionId > 0) runCatching {
            Equalizer(0, sessionId).also { eq ->
                eq.enabled = true
                val range = eq.bandLevelRange
                for (band in 0 until eq.numberOfBands.toInt()) {
                    val frequency = eq.getCenterFreq(band.toShort()) / 1000
                    val bandIndex = when { frequency < 60 -> 0; frequency < 125 -> 1; frequency < 250 -> 2; frequency < 500 -> 3; frequency < 1000 -> 4; frequency < 2000 -> 5; frequency < 4000 -> 6; frequency < 8000 -> 7; frequency < 12000 -> 8; else -> 9 }
                    val adjustment = eqLevels[bandIndex] + if (frequency < 250) bass else if (frequency > 5000) treble else 0f
                    val level = (adjustment * range[1].toFloat()).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                    eq.setBandLevel(band.toShort(), level.toShort())
                }
                equalizer = eq
            }
        }
        onDispose { runCatching { equalizer?.release() } }
    }


    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (ambientGlow && artwork != null) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(.55f).alpha(.25f)) {
                TrackArtwork(artwork, title, Modifier.fillMaxSize().blur(42.dp))
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Go back") }
                    Text("NOW PLAYING", fontSize = 11.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        IconButton(onClick = { queueOpen = true }) { Icon(Icons.Filled.QueueMusic, "Queue") }
                        IconButton(onClick = onBack) { Icon(Icons.Filled.Close, "Close", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    val rotationTransition = rememberInfiniteTransition(label = "vinyl-rotation")
                    val rotation by rotationTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = tween(durationMillis = spinSpeed * 1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "vinyl-angle"
                    )
                    Box(
                        Modifier.widthIn(max = 384.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .10f), RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(PlayerSurface2, MaterialTheme.colorScheme.background, PlayerSurface)))
                            .pointerInput(duration, doubleTapSeekSeconds) {
                                val artworkWidth = size.width.toFloat()
                                detectTapGestures(onDoubleTap = { offset ->
                                    if (artworkWidth > 0f && duration > 0L) {
                                        val direction = when {
                                            offset.x < artworkWidth * .38f -> -1
                                            offset.x > artworkWidth * .62f -> 1
                                            else -> 0
                                        }
                                        if (direction != 0) {
                                            val currentPosition = controller?.currentPosition ?: position
                                            val target = (currentPosition + direction * doubleTapSeekSeconds * 1000L).coerceIn(0L, duration)
                                            controller?.seekTo(target)
                                        }
                                    }
                                })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (discStyle == PlayerDiscStyle.NONE) {
                            TrackArtwork(artwork, title, Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)))
                        } else {
                            if (discStyle == PlayerDiscStyle.VINYL && tonearm) {
                                BoxWithConstraints(Modifier.fillMaxSize()) {
                                    Box(
                                        Modifier.align(Alignment.TopEnd)
                                            .padding(end = maxWidth * .15f, top = maxHeight * .08f)
                                            .width(5.dp).height(maxHeight * .37f)
                                            .rotate(24f).clip(CircleShape)
                                            .background(Brush.verticalGradient(listOf(Color(0xFFE4E4E7), Color(0xFFA1A1AA), Color(0xFF3F3F46))))
                                    )
                                    Box(
                                        Modifier.align(Alignment.TopEnd)
                                            .padding(end = maxWidth * .15f - 5.dp, top = maxHeight * .08f - 6.dp)
                                            .size(16.dp).border(2.dp, Color(0xFFD4D4D8), CircleShape)
                                            .background(Color(0xFF52525B), CircleShape)
                                    )
                                }
                            }
                            Box(Modifier.fillMaxWidth(.82f).aspectRatio(1f).clip(CircleShape)
                                .border(if (discStyle == PlayerDiscStyle.VINYL) 1.dp else 0.dp, Color.White.copy(alpha = .10f), CircleShape)
                                .background(discBrush(discStyle, discFinish))
                                .then(if (spin && playing) Modifier.rotate(rotation) else Modifier),
                                contentAlignment = Alignment.Center) {
                                if (discStyle == PlayerDiscStyle.ARTWORK) TrackArtwork(artwork, title, Modifier.fillMaxSize().clip(CircleShape))
                                else Box(Modifier.fillMaxWidth(.34f).aspectRatio(1f).clip(CircleShape)
                                    .border(4.dp, if (discStyle == PlayerDiscStyle.VINYL) Color.Black.copy(alpha=.70f) else Color.White.copy(alpha=.60f), CircleShape)) {
                                    TrackArtwork(artwork, title, Modifier.fillMaxSize().clip(CircleShape))
                                }
                                if (discStyle == PlayerDiscStyle.VINYL) {
                                    Box(Modifier.fillMaxSize(.84f).border(1.dp, Color.White.copy(alpha = .10f), CircleShape))
                                }
                                if (discStyle == PlayerDiscStyle.CD) {
                                    Box(Modifier.fillMaxSize().border(10.dp, Color.White.copy(alpha = .15f), CircleShape))
                                }
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
                        Text(title.ifBlank { "Nothing playing" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$artist · $album", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { editTitle=title; editArtist=artist; editAlbum=album; editGenre=genre; editOpen=true }) { Icon(Icons.Filled.Edit, "Edit song info", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { library.toggleFavorite(trackId); favorite = library.isFavorite(trackId) }) {
                        Icon(Icons.Filled.Favorite, "Add to favorites", tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(18.dp))
                Slider(
                    value = animatedProgress,
                    onValueChange = { value ->
                        if (duration > 0L) controller?.seekTo((value * duration).toLong().coerceIn(0L, duration))
                    },
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.background,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = .20f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )
                Row(Modifier.fillMaxWidth().padding(top = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatPlayerTime(position), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (duration > 0) "-${formatPlayerTime((duration-position).coerceAtLeast(0L))}" else "Indexing…", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { controller?.shuffleModeEnabled = !shuffle }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Shuffle, "Shuffle", modifier = Modifier.size(20.dp), tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        val player = controller ?: return@IconButton
                        if (player.currentPosition > 4_000L) player.seekTo(0L) else player.seekToPreviousMediaItem()
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.SkipPrevious, "Previous track", modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    FilledIconButton(
                        onClick = { if (playing) controller?.pause() else controller?.play() },
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape
                    ) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", modifier = Modifier.size(30.dp))
                    }
                    IconButton(onClick = { controller?.seekToNextMediaItem() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.SkipNext, "Next track", modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = {
                        controller?.repeatMode = when (repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat", modifier = Modifier.size(20.dp), tint = if (repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Quick actions", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.2).sp)
                        Text("The essentials, within reach", Modifier.padding(top = 2.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(painterResource(R.drawable.lucide_audio_lines), null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerQuickAction("Audio Lab", R.drawable.lucide_gauge, Modifier.weight(1f)) { audioLabOpen = true }
                        PlayerQuickAction("Aura Mode", R.drawable.lucide_waves, Modifier.weight(1f)) { auraNotice = true }
                        PlayerQuickAction("Playlist", R.drawable.lucide_plus, Modifier.weight(1f)) { playlists = library.playlists(); playlistOpen = !playlistOpen }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerQuickAction("Info", R.drawable.lucide_info, Modifier.weight(1f), selected = infoOpen) { infoOpen = !infoOpen }
                        PlayerQuickAction("Lyrics", R.drawable.lucide_book_open_text, Modifier.weight(1f)) { lyricsExpanded = true; scope.launch { delay(80); lyricsPanelRequester.bringIntoView() } }
                        PlayerQuickAction(displaySleep, if (sleepRemaining > 0L) R.drawable.lucide_timer_reset else R.drawable.lucide_moon, Modifier.weight(1f), selected = sleepOpen) {
                            sleepMinutes = if (sleepRemaining > 0L) ((sleepRemaining + 59_999L) / 60_000L).toString() else "30"
                            sleepOpen = true
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f), RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = .6f))
                        .clickable { moreOpen = !moreOpen }.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (moreOpen) "Less" else "More actions", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Icon(painterResource(R.drawable.lucide_chevron_down), null, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = if (moreOpen) 180f else 0f }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(
                    visible = moreOpen,
                    enter = fadeIn(tween(180)) + expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { -it / 12 },
                    exit = fadeOut(tween(130)) + shrinkVertically(animationSpec = tween(180)) +
                        slideOutVertically(animationSpec = tween(180)) { -it / 16 }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlayerAction("Share track", R.drawable.lucide_share_2, Modifier.weight(1f)) { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Listening to “$title” by $artist on Soundprint."), "Share track")) }
                            PlayerAction("Restart track", R.drawable.lucide_rotate_ccw, Modifier.weight(1f)) { controller?.seekTo(0L) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlayerAction("Playback options", R.drawable.lucide_settings_2, Modifier.weight(1f)) { optionsOpen = true }
                            PlayerAction("Customize artwork", R.drawable.lucide_disc_3, Modifier.weight(1f)) { customizerOpen = true }
                        }
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
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { infoOpen = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    ApplyCadenceDialogWindow()
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .42f)).clickable { infoOpen = false },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(180)) + androidx.compose.animation.scaleIn(initialScale = .96f, animationSpec = tween(220)) +
                                slideInVertically(animationSpec = tween(220)) { it / 18 }
                        ) {
                            Surface(
                                onClick = {},
                                modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(18.dp).shadow(24.dp, RoundedCornerShape(28.dp))
                                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .82f),
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.background,
                                tonalElevation = 8.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))
                            ) {
                                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                val fileName = track?.uri?.let { runCatching { Uri.parse(it).lastPathSegment.orEmpty() }.getOrDefault("") }.orEmpty()
                val extension = fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.uppercase()
                val mime = track?.mimeType.orEmpty()
                val format = extension ?: when {
                    mime.contains("mpeg", true) -> "MP3"
                    mime.contains("wav", true) -> "WAV"
                    mime.contains("flac", true) -> "FLAC"
                    mime.contains("ogg", true) -> "OGG"
                    mime.contains("aac", true) -> "AAC"
                    mime.contains("mp4", true) || mime.contains("m4a", true) -> "M4A"
                    mime.isNotBlank() -> mime.substringAfter('/').uppercase()
                    else -> "Unknown"
                }
                val codec = mime.substringAfter('/', "").takeIf { it.isNotBlank() }?.uppercase() ?: "Not identified"
                val size = track?.sizeBytes ?: 0L
                val fileSize = when {
                    size <= 0L -> "Unknown"
                    size < 1024L * 1024L -> "${(size / 1024.0).toInt()} KB"
                    else -> "${"%.2f".format(java.util.Locale.getDefault(), size / (1024.0 * 1024.0))} MB"
                }
                val sampleRate = track?.sampleRate ?: 0
                val channels = track?.channelCount ?: 0
                val addedAt = track?.addedAt ?: 0L
                val lastPlayed = track?.lastPlayedAt
                val dateAdded = if (addedAt > 0L) java.text.DateFormat.getDateInstance().format(java.util.Date(addedAt)) else "Unknown"
                val lastPlayedLabel = lastPlayed?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "Not played yet"
                val detailRows = listOf(
                    "Title" to title,
                    "Artist" to artist,
                    "Album" to album,
                    "Genre" to genre,
                    "Duration" to if (duration > 0L) formatPlayerTime(duration) else "Indexing…",
                    "Format" to format,
                    "Encoding / codec" to codec,
                    "Bitrate" to if ((track?.bitrate ?: 0) > 0) "${track!!.bitrate} kbps" else "Not available",
                    "Sample rate" to if (sampleRate > 0) "${"%.3f".format(java.util.Locale.getDefault(), sampleRate / 1000.0).trimEnd('0').trimEnd('.')} kHz" else "Not available",
                    "Channels" to when (channels) { 1 -> "Mono (1)"; in 2..Int.MAX_VALUE -> "$channels channels"; else -> "Not available" },
                    "File size" to fileSize,
                    "Date added" to dateAdded,
                    "Last played" to lastPlayedLabel,
                    "Times played" to (track?.playCount?.toString() ?: "0"),
                    "Folder" to (track?.folder?.takeIf { it.isNotBlank() } ?: "Unknown")
                )
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Track details", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Local file metadata", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val copied = listOf(title, artist, album, genre, format).filter { it.isNotBlank() }.joinToString(" · ")
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Track details", copied))
                                    android.widget.Toast.makeText(context, "Track info copied", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) { Icon(Icons.Filled.ContentCopy, "Copy track details", modifier = Modifier.size(17.dp)) }
                            IconButton(onClick = { infoOpen = false }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Close, "Hide track details", modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    detailRows.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (label, value) ->
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(10.dp)
                                ) {
                                    Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    Text(value.ifBlank { "Unknown" }, Modifier.padding(top = 2.dp), fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                        if (pair != detailRows.chunked(2).last()) Spacer(Modifier.height(8.dp))
                    }
                }
                        }
                    }
                }
            }
            }
            item {
                Box(Modifier.bringIntoViewRequester(lyricsPanelRequester)) {
                    SoundprintLyricsPanel(
                        trackId = trackId,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        position = position,
                        expanded = lyricsExpanded,
                        onExpandedChange = { lyricsExpanded = it },
                        seek = { target -> controller?.seekTo(target) }
                    )
                }
            }
            item {
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    OutlinedButton(onClick = { /* Find by mood is intentionally paused for this UI pass. */ }) {
                        Icon(Icons.Filled.Psychology, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Find by mood")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { /* queue preview is directly below */ }) {
                        Text("Queue · " + queueTracks.size)
                    }
                }
            }
            if (queueTracks.isNotEmpty()) item {
                PlayerPanel("Up next · ${queueTracks.size}", onClose={}) {
                    queueTracks.forEachIndexed { index, item -> Row(Modifier.fillMaxWidth().clickable { controller?.seekTo((currentIndex+1+index),0L) }.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.title,fontSize=12.sp,fontWeight=FontWeight.Medium,maxLines=1); Text(item.artist,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }; Text("Play",fontSize=10.sp,color=MaterialTheme.colorScheme.primary) } }
                }
            }
        }
    }

    if (queueOpen) SoundprintFloatingDialog(
        onDismissRequest = { queueOpen = false },
        title = "Queue · ${queueIds.size}",
        maxWidth = 440.dp,
        actions = {
            TextButton(onClick = { controller?.clearMediaItems(); queueOpen = false }) { Text("Clear queue", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { queueOpen = false }) { Text("Done") }
        }
    ) {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            itemsIndexed(queueIds) { index, id ->
                val item = library.song(id)
                if (item != null) Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).clickable { controller?.seekTo(index, 0L); queueOpen = false }) {
                        Text(item.title, fontSize = 12.sp, fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.artist, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    if (index > 0) IconButton(onClick = { controller?.moveMediaItem(index, index - 1) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                    if (index < queueIds.lastIndex) IconButton(onClick = { controller?.moveMediaItem(index, index + 1) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                    IconButton(onClick = { controller?.removeMediaItem(index) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Filled.Close, "Remove") }
                }
            }
        }
    }
    if (customizerOpen) {
        ArtworkCustomizerDialog(
            discStyle = discStyle,
            discFinish = discFinish,
            spin = spin,
            spinSpeed = spinSpeed,
            tonearm = tonearm,
            ambientGlow = ambientGlow,
            onDiscStyle = { discStyle = it },
            onDiscFinish = { discFinish = it },
            onSpin = { spin = it },
            onSpinSpeed = { spinSpeed = it },
            onTonearm = { tonearm = it },
            onAmbientGlow = { ambientGlow = it },
            onDismiss = { customizerOpen = false },
            onReset = {
                discStyle = PlayerDiscStyle.VINYL
                discFinish = PlayerDiscFinish.CLASSIC
                spin = true
                spinSpeed = 4
                tonearm = true
                ambientGlow = true
            }
        )
    }
    if (editOpen) SoundprintFloatingDialog(
        onDismissRequest = { editOpen = false },
        title = "Edit song info",
        subtitle = "Update the details saved in your local library.",
        actions = {
            TextButton(onClick = { editOpen = false }) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                library.updateSongMetadata(trackId, editTitle, editArtist, editAlbum, editGenre)
                title = editTitle
                artist = editArtist
                album = editAlbum
                genre = editGenre
                editOpen = false
            }) { Text("Save changes") }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(editTitle, { editTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(editArtist, { editArtist = it }, label = { Text("Artist") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(editAlbum, { editAlbum = it }, label = { Text("Album") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(editGenre, { editGenre = it }, label = { Text("Genre") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
    }
    if (sleepOpen) SoundprintFloatingDialog(
        onDismissRequest = { sleepOpen = false },
        title = "Sleep timer",
        subtitle = "Stop playback after…",
        maxWidth = 340.dp,
        actions = {
            TextButton(onClick = {
                playbackOptionsPrefs.edit().remove("sleep_ends_at").apply()
                sleepRemaining = 0L
                sleepOpen = false
            }) { Text(if (sleepRemaining > 0) "Cancel timer" else "Close") }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                val minutes = (sleepMinutes.toLongOrNull()?.coerceIn(1, 720) ?: 30)
                playbackOptionsPrefs.edit().putLong("sleep_ends_at", System.currentTimeMillis() + minutes * 60_000L).apply()
                sleepRemaining = minutes * 60_000L
                sleepOpen = false
            }) { Text(if (sleepRemaining > 0) "Update timer" else "Start timer") }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    OutlinedButton(onClick = { sleepMinutes = minutes.toString() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) { Text("${minutes}m") }
                }
            }
            OutlinedTextField(sleepMinutes, { sleepMinutes = it }, label = { Text("Custom minutes") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (sleepRemaining > 0) Text("Remaining: $displaySleep", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (optionsOpen) SoundprintFloatingDialog(
        onDismissRequest = { optionsOpen = false },
        title = "Playback options",
        subtitle = "Fine-tune how your music plays.",
        maxWidth = 380.dp,
        actions = {
            Spacer(Modifier.weight(1f))
            Button(onClick = { optionsOpen = false }) { Text("Done") }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Gapless playback", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("Native playlist transitions are enabled by the playback engine", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("ON", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            PlayerToggle("Loudness leveling", "Keep volume consistent", loudness) { loudness = it }
            PlayerToggle("Stop after this song", "Pause at the end of the current track", stopAfterCurrent) { stopAfterCurrent = it }
            Text("Crossfade", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("Blend into the next queued track", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PlayerChoiceRow(listOf("Off", "2s", "4s", "6s", "8s"), if (crossfade == 0) "Off" else "${crossfade}s") {
                crossfade = it.removeSuffix("s").toIntOrNull() ?: 0
            }
        }
    }
    if (auraNotice) SoundprintFloatingDialog(
        onDismissRequest = { auraNotice = false },
        title = "Aura Mode",
        maxWidth = 340.dp,
        actions = {
            Spacer(Modifier.weight(1f))
            Button(onClick = { auraNotice = false }) { Text("Got it") }
        }
    ) {
        Text("Aura Mode is temporarily paused. The audio effects and Audio Lab remain in Soundprint; the immersive mode will return later.", fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (audioLabOpen) SoundprintFloatingDialog(
        onDismissRequest = { audioLabOpen = false },
        title = "Audio Lab · 10-band EQ",
        maxWidth = 420.dp,
        actions = {
            TextButton(onClick = { eqLevels = List(10) { 0f }; bass = 0f; treble = 0f }) { Text("Reset") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { audioLabOpen = false }) { Text("Done") }
        }
    ) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val labels = listOf("31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz")
            labels.forEachIndexed { index, label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.width(54.dp), fontSize = 10.sp)
                    Slider(value = eqLevels[index], onValueChange = { value -> eqLevels = eqLevels.toMutableList().also { it[index] = value } }, valueRange = -1f..1f)
                    Text(if (eqLevels[index] >= 0) "+" + String.format("%.1f", eqLevels[index]) else String.format("%.1f", eqLevels[index]), modifier = Modifier.width(30.dp), fontSize = 9.sp)
                }
            }
            Text("Bass", fontSize = 11.sp)
            Slider(value = bass, onValueChange = { bass = it }, valueRange = -1f..1f)
            Text("Treble", fontSize = 11.sp)
            Slider(value = treble, onValueChange = { treble = it }, valueRange = -1f..1f)
        }
    }
}

@Composable
private fun ApplyCadenceDialogWindow() {
    val view = LocalView.current
    val density = LocalDensity.current
    val blurRadius = with(density) { 14.dp.roundToPx() }
    val window = (view.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.let { dialogWindow ->
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val attributes = dialogWindow.attributes
            attributes.dimAmount = .10f
            dialogWindow.attributes = attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                dialogWindow.setBackgroundBlurRadius(blurRadius)
            }
        }
    }
}

@Composable
private fun SoundprintFloatingDialog(
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String? = null,
    maxWidth: androidx.compose.ui.unit.Dp = 380.dp,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ApplyCadenceDialogWindow()
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = .36f))
                .clickable(onClick = onDismissRequest),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(180)) +
                    androidx.compose.animation.scaleIn(initialScale = .96f, animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                    slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it / 18 }
            ) {
                Surface(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxWidth).padding(18.dp).shadow(26.dp, RoundedCornerShape(28.dp))
                        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .82f),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .65f))
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.25).sp)
                                if (!subtitle.isNullOrBlank()) {
                                    Text(subtitle, Modifier.padding(top = 3.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp)
                                }
                            }
                            IconButton(onClick = onDismissRequest, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Close, "Close", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Column(content = content)
                        Row(
                            Modifier.fillMaxWidth().padding(top = 14.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                            content = actions
                        )
                    }
                }
            }
        }
    }
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
    val loadedBitmap = bitmap
    if (loadedBitmap != null) Image(loadedBitmap.asImageBitmap(), title, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF47382D),Color(0xFF171513)))),contentAlignment=Alignment.Center){Icon(Icons.Filled.MusicNote,null,tint=Color.White.copy(alpha=.8f),modifier=Modifier.size(52.dp))}
}

@Composable
fun SoundprintMiniPlayer(
    controller: MediaController?,
    library: MusicLibraryManager,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onClear: () -> Unit
) {
    var track by remember { mutableStateOf<MusicTrack?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var trackId by remember { mutableStateOf(controller?.currentMediaItem?.mediaId.orEmpty()) }

    LaunchedEffect(controller) {
        while (controller != null) {
            trackId = controller.currentMediaItem?.mediaId.orEmpty()
            position = controller.currentPosition.coerceAtLeast(0L)
            duration = controller.duration.takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET } ?: 0L
            playing = controller.isPlaying
            delay(250)
        }
    }
    LaunchedEffect(trackId) {
        track = if (trackId.isBlank()) null else withContext(Dispatchers.IO) { library.song(trackId) }
    }

    val item = controller?.currentMediaItem
    if (item != null) {
        val title = track?.title?.takeIf(String::isNotBlank) ?: item.mediaMetadata.title?.toString().orEmpty()
        val artist = track?.artist?.takeIf(String::isNotBlank) ?: item.mediaMetadata.artist?.toString().orEmpty()
        val artworkUri = track?.artworkUri
        val progress = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        Column(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .95f))
        ) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = .55f))) {
                Box(Modifier.fillMaxWidth(progress).height(2.dp).background(MaterialTheme.colorScheme.primary))
            }
            Row(
                Modifier.fillMaxWidth().widthIn(max = 672.dp).align(Alignment.CenterHorizontally)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpen)
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackArtwork(artworkUri, title, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(artist, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                MiniPlayerControl(
                    label = if (playing) "Pause" else "Play",
                    icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    onClick = onToggle
                )
                MiniPlayerControl("Next track", Icons.Filled.SkipNext, onNext)
                MiniPlayerControl("Stop and close player", Icons.Filled.Close, onClear, muted = true)
            }
        }
    }
}

@Composable
private fun MiniPlayerControl(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    muted: Boolean = false
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .9f else 1f, animationSpec = tween(120), label = "mini-player-control")
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        interactionSource = interaction
    ) {
        Icon(icon, label, tint = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp))
    }
}


@Composable
private fun PlayerQuickAction(
    label: String,
    icon: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier.height(84.dp).clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (selected) .55f else .38f), RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(painterResource(icon), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable
private fun PlayerAction(label: String, icon: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(88.dp).clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .38f), RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .6f))
            .clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 12.sp)
    }
}
@Composable
private fun PlayerPanel(title:String,onClose:()->Unit,content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxWidth().padding(top=10.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(title,fontWeight=FontWeight.SemiBold,fontSize=14.sp);if(title!="Up next")IconButton(onClick=onClose,modifier=Modifier.size(28.dp)){Icon(Icons.Filled.Close,null,modifier=Modifier.size(16.dp))}};content()}
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerChoiceRow(options:List<String>,selected:String,onSelect:(String)->Unit){
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){options.forEach{item->FilterChip(selected=selected.equals(item,true)||selected.equals(item.replace(" ","_"),true),onClick={onSelect(item)},label={Text(item,fontSize=11.sp)})}}
}
@Composable
private fun PlayerToggle(title:String,detail:String,checked:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,fontSize=12.sp,fontWeight=FontWeight.Medium);Text(detail,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(checked,onChange)}}
private fun discBrush(style:PlayerDiscStyle,finish:PlayerDiscFinish):Brush = when(style){
    PlayerDiscStyle.VINYL -> Brush.radialGradient(
        colors = when (finish) {
            PlayerDiscFinish.CLASSIC -> listOf(Color(0xFF202024), Color(0xFF111114), Color(0xFF29292E), Color(0xFF101013), Color(0xFF202024))
            PlayerDiscFinish.PRISMATIC -> listOf(Color(0xFF37213F), Color(0xFF160F20), Color(0xFF6D315E), Color(0xFF170E21), Color(0xFF37213F))
            PlayerDiscFinish.MIDNIGHT -> listOf(Color(0xFF18212B), Color(0xFF05080D), Color(0xFF293848), Color(0xFF05080D), Color(0xFF18212B))
        },
        tileMode = TileMode.Repeated
    )
    PlayerDiscStyle.CD -> Brush.sweepGradient(when(finish) {
        PlayerDiscFinish.CLASSIC -> listOf(Color(0xFFC8D3DB),Color(0xFFF5D9E5),Color(0xFFA8D5CE),Color(0xFFE9E2B5),Color(0xFFA9BCE8),Color(0xFFF0F0F0))
        PlayerDiscFinish.PRISMATIC -> listOf(Color(0xFFFF6F91),Color(0xFFFFCE70),Color(0xFF7CE7C4),Color(0xFF77A7FF),Color(0xFFD78AFF))
        PlayerDiscFinish.MIDNIGHT -> listOf(Color(0xFF5B6470),Color(0xFFB8C1CC),Color(0xFF353D4B),Color(0xFFE1E5EA),Color(0xFF424A58))
    })
    PlayerDiscStyle.ARTWORK -> Brush.radialGradient(listOf(Color(0xFF27272A),Color(0xFF09090B)))
    PlayerDiscStyle.NONE -> Brush.linearGradient(listOf(Color.DarkGray,Color.Black))
}
private fun formatPlayerTime(ms:Long):String{val seconds=(ms/1000L).coerceAtLeast(0L);return "%d:%02d".format(seconds/60L,seconds%60L)}

@OptIn(ExperimentalLayoutApi::class)
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


@Composable
private fun ArtworkCustomizerDialog(
    discStyle: PlayerDiscStyle,
    discFinish: PlayerDiscFinish,
    spin: Boolean,
    spinSpeed: Int,
    tonearm: Boolean,
    ambientGlow: Boolean,
    onDiscStyle: (PlayerDiscStyle) -> Unit,
    onDiscFinish: (PlayerDiscFinish) -> Unit,
    onSpin: (Boolean) -> Unit,
    onSpinSpeed: (Int) -> Unit,
    onTonearm: (Boolean) -> Unit,
    onAmbientGlow: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onReset: () -> Unit
) {
    val scrollState = rememberScrollState()
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ApplyCadenceDialogWindow()
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .42f)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(180)) +
                    androidx.compose.animation.scaleIn(initialScale = .96f, animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                    slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it / 18 }
            ) {
                Surface(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().widthIn(max = 448.dp).padding(18.dp)
                        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .78f),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 8.dp,
                    shadowElevation = 26.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .65f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Customize artwork", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.25).sp)
                        Text("Make the player feel like yours.", modifier = Modifier.padding(top = 2.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, "Close artwork customizer", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column {
                        Text("ARTWORK STYLE", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ArtworkStyleCard(PlayerDiscStyle.VINYL, "Vinyl", "Classic record", discStyle, Modifier.weight(1f), onDiscStyle)
                                ArtworkStyleCard(PlayerDiscStyle.CD, "CD", "Iridescent disc", discStyle, Modifier.weight(1f), onDiscStyle)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ArtworkStyleCard(PlayerDiscStyle.ARTWORK, "Artwork disc", "Full-cover disc", discStyle, Modifier.weight(1f), onDiscStyle)
                                ArtworkStyleCard(PlayerDiscStyle.NONE, "Cover only", "No disc effect", discStyle, Modifier.weight(1f), onDiscStyle)
                            }
                        }
                    }
                    if (discStyle != PlayerDiscStyle.NONE) {
                        Column {
                            Text("FINISH", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ArtworkFinishCard(PlayerDiscFinish.CLASSIC, "Classic", discFinish, Modifier.weight(1f), onDiscFinish)
                                ArtworkFinishCard(PlayerDiscFinish.PRISMATIC, "Prismatic", discFinish, Modifier.weight(1f), onDiscFinish)
                                ArtworkFinishCard(PlayerDiscFinish.MIDNIGHT, "Midnight", discFinish, Modifier.weight(1f), onDiscFinish)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("MOTION", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).padding(horizontal = 4.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Spin while playing", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("Pause when playback stops", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = spin, onCheckedChange = onSpin)
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Speed", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                Row(
                                    Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    listOf(8 to "Slow", 4 to "Normal", 2 to "Fast").forEach { (value, label) ->
                                        val selected = spinSpeed == value
                                        Box(
                                            Modifier.clip(CircleShape)
                                                .background(if (selected) MaterialTheme.colorScheme.background else Color.Transparent)
                                                .clickable { onSpinSpeed(value) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        if (discStyle == PlayerDiscStyle.VINYL) {
                            ArtworkSwitchRow("Turntable arm", "Show the arm on the record", tonearm, onTonearm)
                        }
                    }
                    ArtworkSwitchRow("Ambient glow", "Soft light behind the artwork", ambientGlow, onAmbientGlow)
                }
                Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = .7f)))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("Reset", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                        Text("Done", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkSwitchRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ArtworkStyleCard(
    style: PlayerDiscStyle,
    label: String,
    detail: String,
    selectedStyle: PlayerDiscStyle,
    modifier: Modifier = Modifier,
    onSelect: (PlayerDiscStyle) -> Unit
) {
    val selected = style == selectedStyle
    Row(
        modifier.clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .6f) else MaterialTheme.colorScheme.outline.copy(alpha = .7f), RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .07f) else MaterialTheme.colorScheme.surface.copy(alpha = .5f))
            .clickable { onSelect(style) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            ArtworkStylePreview(style)
        }
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, Modifier.padding(top = 2.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.size(10.dp).then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = .15f), CircleShape)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f), CircleShape)
            )
        )
    }
}

@Composable
private fun ArtworkStylePreview(style: PlayerDiscStyle) {
    when (style) {
        PlayerDiscStyle.VINYL -> Box(Modifier.size(40.dp).clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = .1f), CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF29292E), Color(0xFF111114), Color(0xFF29292E), Color(0xFF101013)))),
            contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize(.28f).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
        PlayerDiscStyle.CD -> Box(Modifier.size(40.dp).clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = .4f), CircleShape)
            .background(Brush.sweepGradient(listOf(Color(0xFFC8D3DB), Color(0xFFF5D9E5), Color(0xFFA8D5CE), Color(0xFFE9E2B5), Color(0xFFA9BCE8), Color(0xFFF0F0F0)))),
            contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize(.28f).border(1.dp, Color.White.copy(alpha = .7f), CircleShape).background(MaterialTheme.colorScheme.background, CircleShape))
        }
        PlayerDiscStyle.ARTWORK -> Box(Modifier.size(40.dp).clip(CircleShape)
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .8f), Color(0xFFB34B9B).copy(alpha = .5f), Color(0xFF55B8D8).copy(alpha = .7f)))),
            contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize(.28f).border(1.dp, Color.White.copy(alpha = .7f), CircleShape).background(Color.Black.copy(alpha = .5f), CircleShape))
        }
        PlayerDiscStyle.NONE -> Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = .8f),
                            Color(0xFFB34B9B).copy(alpha = .5f),
                            Color(0xFF55B8D8).copy(alpha = .7f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun ArtworkFinishCard(
    finish: PlayerDiscFinish,
    label: String,
    selectedFinish: PlayerDiscFinish,
    modifier: Modifier = Modifier,
    onSelect: (PlayerDiscFinish) -> Unit
) {
    val selected = finish == selectedFinish
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .6f) else MaterialTheme.colorScheme.outline.copy(alpha = .7f), RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .07f) else MaterialTheme.colorScheme.surface.copy(alpha = .5f))
            .clickable { onSelect(finish) }.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp)).background(
            when (finish) {
                PlayerDiscFinish.CLASSIC -> Brush.linearGradient(listOf(Color(0xFF27272A), Color(0xFFD4D4D8), Color(0xFF52525B)))
                PlayerDiscFinish.PRISMATIC -> Brush.linearGradient(listOf(Color(0xFFFB7185), Color(0xFFFACC15), Color(0xFF5EEAD4), Color(0xFF818CF8), Color(0xFFE879F9)))
                PlayerDiscFinish.MIDNIGHT -> Brush.linearGradient(listOf(Color(0xFF020617), Color(0xFF334155), Color(0xFF020617)))
            }
        ))
        Text(label, Modifier.padding(top = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
