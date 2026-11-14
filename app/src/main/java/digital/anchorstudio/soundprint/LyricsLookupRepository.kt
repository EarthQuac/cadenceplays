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

data class LyricsLookupCandidate(
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Double,
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val provider: String = "LRCLIB"
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

        val params = buildList {
            add("track_name" to title)
            if (artist.isNotBlank()) add("artist_name" to artist)
            if (album.isNotBlank()) add("album_name" to album)
            if (durationMs > 0L) add("duration" to (durationMs / 1000L).toString())
        }
        val exactUrl = baseUrl.trimEnd('/') + "/get?" + params.joinToString("&") {
            encode(it.first) + "=" + encode(it.second)
        }

        // The exact endpoint is best when the local metadata is accurate.
        val exact = if (artist.isNotBlank()) runCatching { JSONObject(request(exactUrl)) }.getOrNull()
            ?.let(::parseTrack)
            ?.takeIf { it.lyrics.isNotBlank() } else null
        if (exact != null) return exact

        // Search endpoint provides alternatives when tags differ from LRCLIB metadata.
        val query = encode(if (artist.isBlank()) title else "$title $artist")
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

    fun discover(query: String): List<LyricsLookupCandidate> {
        if (query.isBlank()) return emptyList()
        val variants = linkedSetOf(query.trim(), "${query.trim()} soundtrack")
        val found = linkedMapOf<String, LyricsLookupCandidate>()
        variants.forEach { variant ->
            runCatching {
                val results = requestJsonArray(baseUrl.trimEnd('/') + "/search?q=" + encode(variant))
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val title = item.optString("trackName").trim()
                    val artist = item.optString("artistName").trim()
                    if (title.isBlank() || artist.isBlank()) continue
                    val candidate = LyricsLookupCandidate(
                        title = title,
                        artist = artist,
                        album = item.optString("albumName"),
                        durationSeconds = item.optDouble("duration", 0.0).coerceAtLeast(0.0),
                        syncedLyrics = item.optString("syncedLyrics").takeIf { it.isNotBlank() },
                        plainLyrics = item.optString("plainLyrics").takeIf { it.isNotBlank() }
                    )
                    val key = normalize(title) + "|" + normalize(artist) + "|" + normalize(candidate.album)
                    found.putIfAbsent(key, candidate)
                }
            }
        }
        return found.values.sortedWith(
            compareByDescending<LyricsLookupCandidate> { it.syncedLyrics != null }
                .thenByDescending { it.plainLyrics != null }
        ).take(10)
    }

    fun fetchWordTimedLyrics(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L
    ): String? {
        val params = buildList {
            add("s" to title)
            add("a" to artist)
            if (album.isNotBlank() && album != "Unknown") add("al" to album)
            if (durationMs > 0L) add("d" to (durationMs / 1000L).toString())
        }.joinToString("&") { encode(it.first) + "=" + encode(it.second) }

        fun providerTime(value: String): Double? {
            val clean = value.trim().removeSuffix("s")
            val parts = clean.split(":")
            val seconds = when (parts.size) {
                3 -> (parts[0].toDoubleOrNull() ?: return null) * 3600.0 +
                    (parts[1].toDoubleOrNull() ?: return null) * 60.0 + (parts[2].toDoubleOrNull() ?: return null)
                2 -> (parts[0].toDoubleOrNull() ?: return null) * 60.0 + (parts[1].toDoubleOrNull() ?: return null)
                else -> clean.toDoubleOrNull() ?: return null
            }
            return seconds.takeIf { it >= 0.0 }
        }
        fun stamp(seconds: Double): String {
            val cs = (seconds * 100.0).toLong().coerceAtLeast(0L)
            return "[%02d:%02d.%02d]".format(cs / 6000L, (cs % 6000L) / 100L, cs % 100L)
        }
        fun cleanText(value: String): String = value
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'")
            .replace("&#39;", "'").replace("&#x27;", "'")
            .trim()

        runCatching {
            val payload = JSONObject(request("https://api.betterlyrics.org/ttml/getLyrics?$params"))
            val ttml = payload.optString("lyrics").ifBlank { payload.optString("ttml") }
            if (ttml.isNotBlank()) {
                val paragraphRegex = Regex("""(?is)<p\b[^>]*\bbegin\s*=\s*["']([^"']+)["'][^>]*>(.*?)</p>""")
                val spanRegex = Regex("""(?is)<span\b[^>]*\bbegin\s*=\s*["']([^"']+)["'][^>]*>(.*?)</span>""")
                val rows = paragraphRegex.findAll(ttml).mapNotNull { paragraph ->
                    val lineTime = providerTime(paragraph.groupValues[1]) ?: return@mapNotNull null
                    val body = paragraph.groupValues[2]
                    val spans = spanRegex.findAll(body).toList()
                    if (spans.isEmpty()) return@mapNotNull null
                    val words = mutableListOf<Pair<Double, String>>()
                    var cursor = 0
                    spans.forEach { span ->
                        val gap = cleanText(body.substring(cursor, span.range.first))
                        if (gap.isNotEmpty() && words.isNotEmpty()) {
                            val last = words.removeAt(words.lastIndex)
                            words += last.first to (last.second + gap)
                        }
                        val wordTime = providerTime(span.groupValues[1])
                        val wordText = cleanText(span.groupValues[2])
                        if (wordTime != null && wordText.isNotEmpty()) words += wordTime to wordText
                        cursor = span.range.last + 1
                    }
                    if (words.isEmpty()) null
                    else stamp(lineTime) + words.joinToString("") { (time, word) -> stamp(time).removePrefix("[").removeSuffix("]") .let { "<$it>$word" } }
                }.toList()
                if (rows.isNotEmpty()) return rows.joinToString("\n")
            }
        }

        runCatching {
            val payload = JSONObject(request("https://api.betterlyrics.org/qq/getLyrics?$params"))
            val xml = payload.optString("lyrics")
            val content = Regex("""(?is)<Lyric_1[^>]*LyricContent=["'](.*?)["']""").find(xml)?.groupValues?.get(1)
                ?.replace("&quot;", "\"")?.replace("&amp;", "&")?.replace("&lt;", "<")?.replace("&gt;", ">")
                ?: return@runCatching null
            val rows = content.lineSequence().mapNotNull { raw ->
                val header = Regex("""^\[(\d+),(\d+)]""").find(raw) ?: return@mapNotNull null
                val lineStart = (header.groupValues[1].toLongOrNull() ?: 0L) / 1000.0
                val rest = raw.substring(header.range.last + 1)
                val wordRegex = Regex("""\((\d+),(\d+)\)""")
                val matches = wordRegex.findAll(rest).toList()
                if (matches.isEmpty()) return@mapNotNull null
                val words = matches.mapIndexedNotNull { index, match ->
                    val end = matches.getOrNull(index + 1)?.range?.first ?: rest.length
                    val text = rest.substring(match.range.last + 1, end)
                    val time = (match.groupValues[1].toLongOrNull() ?: 0L) / 1000.0
                    text.takeIf { it.isNotEmpty() }?.let { time to it }
                }
                stamp(lineStart) + words.joinToString("") { (time, word) ->
                    stamp(time).removePrefix("[").removeSuffix("]").let { "<$it>$word" }
                }
            }.toList()
            if (rows.isNotEmpty()) return rows.joinToString("\n")
        }
        return null
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
        if (artist.isNotBlank()) {
            if (foundArtist == normalize(artist)) score += 100
            else if (foundArtist.contains(normalize(artist)) || normalize(artist).contains(foundArtist)) score += 45
        }
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
