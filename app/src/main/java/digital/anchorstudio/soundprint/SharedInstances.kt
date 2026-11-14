package digital.anchorstudio.soundprint

import android.content.Context
import kotlinx.coroutines.CancellationException

/**
 * Hands out one instance per application context. SQLiteOpenHelper must be shared so every
 * screen, import and the playback service use the same connection pool instead of fighting
 * over the same database file.
 */
internal class ContextScopedSingleton<T : Any>(private val create: (Context) -> T) {
    private var owner: Context? = null
    private var value: T? = null

    @Synchronized
    fun get(context: Context): T {
        val app = context.applicationContext
        val current = value
        if (current != null && owner === app) return current
        return create(app).also {
            owner = app
            value = it
        }
    }
}

/** Like [runCatching], but never swallows coroutine cancellation. */
inline fun <T> runCatchingSuspend(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
