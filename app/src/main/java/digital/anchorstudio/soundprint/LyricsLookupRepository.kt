package digital.anchorstudio.soundprint

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

data class LyricsLookupResult(
    val lyrics: String,
    val source: String,
    val synced: Boolean,
    val matchedTitle: String,
    val matchedArtist: String,
    val matchedAlbum: String
)

/**
 * LRCLIB client. Network access stays outside Compose; callers should invoke this
 * from Dispatchers.IO. Synced LRC is preferred, with plain lyrics as a fallback.
 */
class LyricsLookupRepository(
    private val baseUrl: String = "https://lrclib.net/api"
) {
    fun search(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L
    ): LyricsLookupResult? {
        require(title.isNotBlank()) { "A song title is required to search for lyrics." }
        require(artist.isNotBlank()) { "An artist is required to search for lyrics." }

        val params = buildList {
            add("track_name" to title)
            add("artist_name" to artist)
            if (album.isNotBlank()) add("album_name" to album)
            if (durationMs > 0L) add("duration" to (durationMs / 1000L).toString())
        }
        val exactUrl = baseUrl.trimEnd('/') + "/get?" + params.joinToString("&") {
            encode(it.first) + "=" + encode(it.second)
        }

        // The exact endpoint is best when the local metadata is accurate.
        val exact = runCatching { JSONObject(request(exactUrl)) }.getOrNull()
            ?.let(::parseTrack)
            ?.takeIf { it.lyrics.isNotBlank() }
        if (exact != null) return exact

        // Search endpoint provides alternatives when tags differ from LRCLIB metadata.
        val query = encode("$title $artist")
        val results = requestJsonArray(baseUrl.trimEnd('/') + "/search?q=$query")
        val candidates = mutableListOf<Pair<Int, LyricsLookupResult>>()
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val parsed = parseTrack(item) ?: continue
            if (parsed.lyrics.isBlank()) continue
            val score = score(item, title, artist, album, durationMs)
            candidates += score to parsed
        }
        return candidates.maxByOrNull { it.first }?.second
    }

    private fun score(
        item: JSONObject,
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ): Int {
        val foundTitle = normalize(item.optString("trackName"))
        val foundArtist = normalize(item.optString("artistName"))
        val foundAlbum = normalize(item.optString("albumName"))
        var score = 0
        if (foundTitle == normalize(title)) score += 100
        else if (foundTitle.contains(normalize(title)) || normalize(title).contains(foundTitle)) score += 45
        if (foundArtist == normalize(artist)) score += 100
        else if (foundArtist.contains(normalize(artist)) || normalize(artist).contains(foundArtist)) score += 45
        if (album.isNotBlank() && foundAlbum == normalize(album)) score += 35
        val foundDuration = item.optDouble("duration", -1.0)
        if (durationMs > 0L && foundDuration >= 0.0) {
            val difference = abs(foundDuration - durationMs / 1000.0)
            score += when {
                difference <= 2.0 -> 30
                difference <= 5.0 -> 20
                difference <= 12.0 -> 8
                else -> -20
            }
        }
        if (item.optString("syncedLyrics").isNotBlank()) score += 10
        if (item.optString("plainLyrics").isNotBlank()) score += 2
        return score
    }

    private fun parseTrack(item: JSONObject): LyricsLookupResult? {
        val synced = item.optString("syncedLyrics").takeIf { it.isNotBlank() }
        val plain = item.optString("plainLyrics").takeIf { it.isNotBlank() }
        val text = synced ?: plain ?: return null
        val artist = item.optString("artistName").ifBlank { "Unknown artist" }
        val title = item.optString("trackName").ifBlank { "Unknown track" }
        val album = item.optString("albumName")
        return LyricsLookupResult(
            lyrics = text,
            source = "LRCLIB · $artist",
            synced = synced != null,
            matchedTitle = title,
            matchedArtist = artist,
            matchedAlbum = album
        )
    }

    private fun requestJsonArray(url: String): JSONArray {
        val body = request(url)
        return JSONArray(body)
    }

    private fun requestJsonObject(url: String): JSONObject = JSONObject(request(url))

    private fun request(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Soundprint Android Music Player")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException(
                    when (status) {
                        404 -> "No exact lyrics match was found."
                        429 -> "LRCLIB is receiving too many requests. Try again shortly."
                        in 500..599 -> "LRCLIB is temporarily unavailable. Try again later."
                        else -> "Lyrics lookup failed (HTTP $status)."
                    }
                )
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("\\([^)]*\\)|\\[[^]]*\\]"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
