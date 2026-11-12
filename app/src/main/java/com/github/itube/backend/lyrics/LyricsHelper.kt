package com.github.itube.backend.lyrics

import android.util.Base64
import android.util.Log
import com.github.itube.LyricsLine
import com.github.itube.Net
import com.github.itube.StreamCache
import com.github.itube.WordTiming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.math.abs

interface LyricsProvider {
    val name: String
    suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String>
}

object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    override suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val encTitle = URLEncoder.encode(title.trim(), StandardCharsets.UTF_8.name())
            val encArtist = URLEncoder.encode(artist.trim(), StandardCharsets.UTF_8.name())
            val url = "https://lrclib.net/api/search?track_name=$encTitle&artist_name=$encArtist"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "AirBeats/4.3")
                .header("Accept", "application/json")
                .build()

            Net.client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("LrcLib error: ${response.code}")
                val text = response.body?.string().orEmpty()
                val array = JSONArray(text)
                var bestLyrics: String? = null
                var minDiff = Int.MAX_VALUE

                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val synced = obj.optString("syncedLyrics")
                    if (synced.isNotBlank()) {
                        val dur = obj.optInt("duration", -1)
                        if (durationSec > 0 && dur > 0) {
                            val diff = abs(dur - durationSec)
                            if (diff < minDiff) {
                                minDiff = diff
                                bestLyrics = synced
                            }
                        } else if (bestLyrics == null) {
                            bestLyrics = synced
                        }
                    }
                }

                bestLyrics ?: throw IllegalStateException("No synced lyrics on LrcLib")
            }
        }
    }
}

object KuGouLyricsProvider : LyricsProvider {
    override val name = "KuGou"

    override suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val query = URLEncoder.encode("$title - $artist".trim(), StandardCharsets.UTF_8.name())
            val searchUrl = "https://mobileservice.kugou.com/api/v3/search/song?version=9108&plat=0&pagesize=5&showtype=0&keyword=$query"

            val searchReq = Request.Builder().url(searchUrl).header("User-Agent", "KuGou/1.0").build()
            val hash = Net.client.newCall(searchReq).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val info = json.optJSONObject("data")?.optJSONArray("info")
                info?.optJSONObject(0)?.optString("hash") ?: throw IllegalStateException("Song not found on KuGou")
            }

            val lyricsSearchUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=$hash"
            val lyricsReq = Request.Builder().url(lyricsSearchUrl).header("User-Agent", "KuGou/1.0").build()
            val (candidateId, accessKey) = Net.client.newCall(lyricsReq).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
                    ?: throw IllegalStateException("No candidates on KuGou")
                Pair(candidate.optString("id"), candidate.optString("accesskey"))
            }

            val downloadUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&id=$candidateId&accesskey=$accessKey"
            val downloadReq = Request.Builder().url(downloadUrl).header("User-Agent", "KuGou/1.0").build()
            Net.client.newCall(downloadReq).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val base64Content = json.optString("content")
                if (base64Content.isBlank()) throw IllegalStateException("Empty content from KuGou")
                String(Base64.decode(base64Content, Base64.DEFAULT), StandardCharsets.UTF_8)
            }
        }
    }
}

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    override val name = "YouTube Captions"

    override suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val info = StreamCache.getOrLoad(id)
            val subTrack = info.subtitles.firstOrNull { it.languageTag.startsWith("en") } ?: info.subtitles.firstOrNull()
                ?: throw IllegalStateException("No captions for video $id")

            val srv3Url = subTrack.content.replace(Regex("([?&])fmt=[^&]*"), "$1fmt=srv3")
            val req = Request.Builder().url(srv3Url).header("User-Agent", Net.USER_AGENT).build()
            Net.client.newCall(req).execute().use { resp ->
                val xml = resp.body?.string().orEmpty()
                parseSrv3XmlToLrc(xml)
            }
        }
    }

    private fun parseSrv3XmlToLrc(xml: String): String {
        val lines = mutableListOf<String>()
        val p = Pattern.compile("<p\\s+t=\"(\\d+)\"(?:\\s+d=\"(\\d+)\")?[^>]*>(.*?)</p>", Pattern.DOTALL)
        val m = p.matcher(xml)
        while (m.find()) {
            val startMs = m.group(1)?.toLongOrNull() ?: continue
            val rawText = m.group(3).orEmpty().replace(Regex("<[^>]+>"), "").trim()
            if (rawText.isNotBlank()) {
                val min = startMs / 60000
                val sec = (startMs % 60000) / 1000
                val cs = (startMs % 1000) / 10
                lines.add(String.format("[%02d:%02d.%02d]%s", min, sec, cs, rawText))
            }
        }
        return lines.joinToString("\n")
    }
}

object LyricsHelper {
    private val memoryCache = ConcurrentHashMap<String, List<LyricsLine>>()
    private val providers = listOf(
        LrcLibLyricsProvider,
        KuGouLyricsProvider,
        YouTubeSubtitleLyricsProvider
    )

    suspend fun loadLyrics(
        songId: String,
        title: String,
        artist: String,
        durationSec: Int = 0
    ): List<LyricsLine> = withContext(Dispatchers.IO) {
        memoryCache[songId]?.let { return@withContext it }

        var rawLrc: String? = null
        for (provider in providers) {
            val result = provider.getLyrics(songId, title, artist, durationSec)
            if (result.isSuccess) {
                val text = result.getOrNull()
                if (!text.isNullOrBlank()) {
                    rawLrc = text
                    Log.d("LyricsHelper", "Found lyrics for $title using ${provider.name}")
                    break
                }
            }
        }

        val parsed = if (!rawLrc.isNullOrBlank()) {
            parseLrc(rawLrc)
        } else {
            emptyList()
        }

        if (parsed.isNotEmpty()) {
            memoryCache[songId] = parsed
        }
        parsed
    }

    private fun unescapeHtml(text: String): String {
        if (!text.contains('&')) return text
        val cleaned = text
            .replace("&#39;", "'")
            .replace("&#039;", "'")
            .replace("&#x27;", "'")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
        return try {
            if (cleaned.contains('&')) {
                android.text.Html.fromHtml(cleaned, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
            } else {
                cleaned
            }
        } catch (_: Throwable) {
            cleaned
        }
    }

    /** Parses standard LRC format into synchronized [LyricsLine] with estimated word timings. */
    fun parseLrc(lrcText: String): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        val linePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\](.*)")

        for (line in lrcText.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val matcher = linePattern.matcher(trimmed)
            if (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msStr = matcher.group(3)
                val ms = when {
                    msStr == null -> 0L
                    msStr.length == 2 -> msStr.toLong() * 10
                    else -> msStr.take(3).toLong()
                }
                val timeMs = min * 60000L + sec * 1000L + ms
                val rawText = matcher.group(4)?.trim().orEmpty()
                val text = unescapeHtml(rawText)
                if (text.isNotBlank()) {
                    lines.add(LyricsLine(timeMs = timeMs, text = text))
                }
            }
        }

        // Sort chronologically and synthesize word timings for liquid fill animations
        val sorted = lines.sortedBy { it.timeMs }
        val enriched = mutableListOf<LyricsLine>()

        for (i in sorted.indices) {
            val cur = sorted[i]
            val nextTime = if (i + 1 < sorted.size) sorted[i + 1].timeMs else cur.timeMs + 4000L
            val lineDuration = (nextTime - cur.timeMs).coerceIn(1200L, 8000L)

            val rawWords = cur.text.split(" ").filter { it.isNotBlank() }
            val words = mutableListOf<WordTiming>()
            if (rawWords.isNotEmpty()) {
                val wordDuration = lineDuration / rawWords.size
                var wordStart = cur.timeMs
                for (w in rawWords) {
                    val wordEnd = wordStart + wordDuration
                    words.add(WordTiming(startMs = wordStart, endMs = wordEnd, text = w))
                    wordStart = wordEnd
                }
            }
            enriched.add(cur.copy(words = words))
        }

        return enriched
    }
}
