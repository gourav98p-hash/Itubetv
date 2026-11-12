package com.github.itube

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.json.JSONArray
import org.json.JSONObject

/** Metadata for a completed video download. The video itself stays in Downloads/iTube. */
data class DownloadedVideo(
    val item: VideoItem,
    val uri: String,
    val description: String,
    val fileName: String,
    val downloadedAt: Long,
)

data class ActiveDownload(
    val id: Int,
    val item: VideoItem,
    val status: String,
    val progress: Int? = null,
)

/** A small local index for the Library's Downloads page; no comments or network data are stored. */
object DownloadLibrary {
    private const val PREFS = "offline_downloads"
    private const val KEY_ENTRIES = "videos"
    private lateinit var prefs: android.content.SharedPreferences

    var entries by mutableStateOf<List<DownloadedVideo>>(emptyList())
        private set

    var activeDownloads by mutableStateOf<List<ActiveDownload>>(emptyList())
        private set

    fun updateActive(download: ActiveDownload) {
        Snapshot.withMutableSnapshot {
            activeDownloads = listOf(download) + activeDownloads.filterNot { it.id == download.id }
        }
    }

    fun finishActive(id: Int) {
        Snapshot.withMutableSnapshot { activeDownloads = activeDownloads.filterNot { it.id == id } }
    }

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        entries = decode(prefs.getString(KEY_ENTRIES, null))
    }

    fun register(context: Context, item: VideoItem, description: String, uri: Uri, fileName: String) {
        if (!::prefs.isInitialized) init(context)
        val record = DownloadedVideo(item, uri.toString(), description, fileName, System.currentTimeMillis())
        entries = listOf(record) + entries.filterNot { it.item.id == item.id }
        prefs.edit().putString(KEY_ENTRIES, JSONArray().apply { entries.forEach { put(it.toJson()) } }.toString()).apply()
    }

    fun find(videoId: String): DownloadedVideo? = entries.firstOrNull { it.item.id == videoId }

    private fun decode(raw: String?): List<DownloadedVideo> = runCatching {
        val json = JSONArray(raw ?: return emptyList())
        buildList {
            for (index in 0 until json.length()) {
                val record = json.optJSONObject(index) ?: continue
                val item = record.optJSONObject("video")?.let(::videoFromJson) ?: continue
                val uri = record.optString("uri")
                if (uri.isBlank()) continue
                add(
                    DownloadedVideo(
                        item = item,
                        uri = uri,
                        description = record.optString("description"),
                        fileName = record.optString("fileName"),
                        downloadedAt = record.optLong("downloadedAt"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun DownloadedVideo.toJson() = JSONObject()
        .put("video", item.toJson())
        .put("uri", uri)
        .put("description", description)
        .put("fileName", fileName)
        .put("downloadedAt", downloadedAt)

    private fun VideoItem.toJson() = JSONObject()
        .put("id", id).put("title", title).put("channel", channel)
        .put("channelUrl", channelUrl).put("thumbnail", thumbnail).put("avatar", avatar)
        .put("durationSec", durationSec).put("views", views).put("uploaded", uploaded)

    private fun videoFromJson(json: JSONObject) = VideoItem(
        id = json.optString("id"),
        title = json.optString("title"),
        channel = json.optString("channel"),
        channelUrl = json.optString("channelUrl").ifBlank { null },
        thumbnail = json.optString("thumbnail").ifBlank { null },
        avatar = json.optString("avatar").ifBlank { null },
        durationSec = json.optLong("durationSec"),
        views = json.optLong("views", -1L),
        uploaded = json.optString("uploaded").ifBlank { null },
    )
}

/** Encodes the local file URI in the media id so Media3 can pass it through a session safely. */
internal object OfflineMedia {
    private const val PREFIX = "itube-offline|"

    fun mediaId(videoId: String, uri: Uri): String = PREFIX + videoId + "|" +
        Base64.encodeToString(uri.toString().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)

    fun isOffline(mediaId: String): Boolean = mediaId.startsWith(PREFIX)

    fun videoId(mediaId: String): String? = if (isOffline(mediaId)) mediaId.removePrefix(PREFIX).substringBefore('|') else null

    fun uri(mediaId: String): Uri? = if (!isOffline(mediaId)) null else runCatching {
        val encoded = mediaId.substringAfter('|', "").substringAfter('|', "")
        Uri.parse(String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8))
    }.getOrNull()
}
