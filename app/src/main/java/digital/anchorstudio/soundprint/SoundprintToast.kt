package digital.anchorstudio.soundprint

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SoundprintToastKind { SUCCESS, ERROR, WARNING, INFO, LOADING }

data class SoundprintToastMessage(
    val id: Long,
    val kind: SoundprintToastKind,
    val title: String,
    val description: String? = null
)

/**
 * App-wide Sonner-inspired in-app feedback. This is deliberately separate from
 * Android system notifications used by WorkManager while imports run in background.
 */
object SoundprintToast {
    private val handler = Handler(Looper.getMainLooper())
    private val mutableMessage = MutableStateFlow<SoundprintToastMessage?>(null)
    val message = mutableMessage.asStateFlow()

    private var nextId = 0L
    private var lastKey = ""
    private var lastShownAt = 0L
    private val dismissRunnable = Runnable { mutableMessage.value = null }

    fun success(title: String, description: String? = null) =
        show(SoundprintToastKind.SUCCESS, title, description)

    fun error(title: String, description: String? = null) =
        show(SoundprintToastKind.ERROR, title, description)

    fun warning(title: String, description: String? = null) =
        show(SoundprintToastKind.WARNING, title, description)

    fun info(title: String, description: String? = null) =
        show(SoundprintToastKind.INFO, title, description)

    fun loading(title: String, description: String? = null) =
        show(SoundprintToastKind.LOADING, title, description, durationMs = 0L)

    fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        mutableMessage.value = null
    }

    fun show(
        kind: SoundprintToastKind,
        title: String,
        description: String? = null,
        durationMs: Long = 1900L
    ) {
        val readableTitle = title.trim().ifBlank { "Something happened" }
        val key = "${kind.name}:$readableTitle:${description.orEmpty()}"
        val now = android.os.SystemClock.elapsedRealtime()
        if (key == lastKey && now - lastShownAt < 2200L) return
        lastKey = key
        lastShownAt = now

        handler.removeCallbacks(dismissRunnable)
        mutableMessage.value = SoundprintToastMessage(++nextId, kind, readableTitle, description?.takeIf { it.isNotBlank() })
        if (durationMs > 0L) handler.postDelayed(dismissRunnable, durationMs)
    }
}

@Composable
fun SoundprintToastHost(modifier: Modifier = Modifier) {
    val message by SoundprintToast.message.collectAsState()
    val accent = when (message?.kind) {
        SoundprintToastKind.SUCCESS, SoundprintToastKind.WARNING, SoundprintToastKind.LOADING -> Color(0xFFD6A45C)
        SoundprintToastKind.ERROR -> Color(0xFFD97968)
        SoundprintToastKind.INFO, null -> Color(0xFF9AABBC)
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 })
        ) {
            message?.let { toast ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .shadow(16.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B1A18))
                        .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(12.dp))
                        .heightIn(min = 56.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(2.dp).heightIn(min = 56.dp).background(when (toast.kind) {
                        SoundprintToastKind.SUCCESS, SoundprintToastKind.WARNING, SoundprintToastKind.LOADING -> Color(0xFFD6A45C)
                        SoundprintToastKind.ERROR -> Color(0xFFD97968)
                        SoundprintToastKind.INFO -> Color(0xFF9AABBC)
                    }))
                    Row(
                        modifier = Modifier.weight(1f).padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (toast.kind == SoundprintToastKind.LOADING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(17.dp),
                                color = accent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            val glyph = when (toast.kind) {
                                SoundprintToastKind.SUCCESS -> "✓"
                                SoundprintToastKind.ERROR -> "!"
                                SoundprintToastKind.WARNING -> "!"
                                SoundprintToastKind.INFO -> "i"
                                SoundprintToastKind.LOADING -> ""
                            }
                            Box(
                                modifier = Modifier.size(18.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(glyph, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(
                                toast.title,
                                color = Color(0xFFF3F0E9),
                                fontSize = 13.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            toast.description?.let {
                                Text(
                                    it,
                                    modifier = Modifier.padding(top = 3.dp),
                                    color = Color(0xFFAAA59B),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.padding(end = 8.dp).size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { SoundprintToast.dismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("×", color = Color.White.copy(alpha = 0.62f), fontSize = 17.sp, lineHeight = 18.sp)
                    }
                    Spacer(Modifier.width(2.dp))
                }
            }
        }
    }
}
