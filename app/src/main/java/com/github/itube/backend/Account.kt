package com.github.itube

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import kotlin.random.Random

@Immutable
data class Profile(val name: String, val handle: String?, val avatar: String?)

@Immutable
data class PlaylistRef(val id: String, val title: String, val thumbnail: String?, val subtitle: String?)

/** A playlist a video can be saved to, and whether it's in it ([contains] null: not known). */
@Immutable
data class PlaylistChoice(val id: String, val title: String, val contains: Boolean?, val privacy: String?)

@Immutable
data class ChannelRef(val id: String, val name: String, val avatar: String?)

/** One page of a feed plus the token for the next page, if any. */
@Immutable
data class FeedPage(
    val videos: List<VideoItem>,
    val next: String?,
    val recommendations: List<VideoItem> = emptyList(),
    /** Playlist and Mix cards present in the Home feed; tapping one can queue its full contents. */
    val collections: List<PublicCollection> = emptyList(),
)

enum class LikeStatus { Like, Dislike, None }

@Immutable
data class VideoAccountState(val like: LikeStatus, val subscribed: Boolean?, val channelId: String?, val bell: Boolean? = null)

/** One channel of the account's subscriptions list, with its bell (true = "All"; null = not given). */
data class SubscribedChannel(val ref: ChannelRef, val bell: Boolean?)

class NotSignedInException : IOException("Not signed in")

/**
 * The signed-in Google session (user chose full sync, 2026-10-04).
 *
 * Sign-in happens on Google's own page in a WebView; afterwards only the youtube.com
 * cookies are kept, encrypted with a Keystore key ([SecureStore]). Requests are authorized
 * the way youtube.com itself does it (SAPISIDHASH). Cookies YouTube rotates in responses
 * are merged back in so the session stays alive.
 */
object Account {
    var profile by mutableStateOf<Profile?>(null)
        private set
    val signedIn get() = profile != null

    private var store: SecureStore? = null
    private val cookies = linkedMapOf<String, String>()
    private var clientVersion = FALLBACK_CLIENT_VERSION
    private var visitorData: String? = null

    fun init(context: Context) {
        val s = SecureStore(context, "account").also { store = it }
        s.get(KEY_COOKIES)?.let { parseCookieHeader(it, cookies) }
        s.get(KEY_CLIENT_VERSION)?.let { clientVersion = it }
        visitorData = s.get(KEY_VISITOR)
        val name = s.get(KEY_NAME)
        if (name != null && cookies.containsKey("SAPISID")) {
            profile = Profile(name, s.get(KEY_HANDLE), s.get(KEY_AVATAR))
        }
    }

    /** True once the WebView's cookies prove a completed YouTube sign-in. */
    fun looksSignedIn(cookieHeader: String?): Boolean {
        val names = cookieHeader.orEmpty().split(';').map { it.substringBefore('=').trim() }.toSet()
        return "SAPISID" in names && "LOGIN_INFO" in names
    }

    /** Takes over the WebView session: stores the cookies, then loads the account's name. */
    suspend fun completeSignIn(cookieHeader: String) = withContext(Dispatchers.IO) {
        synchronized(cookies) {
            cookies.clear()
            parseCookieHeader(cookieHeader, cookies)
        }
        persistCookies()
        refreshClientConfig()
        val menu = InnerTube.post("account/account_menu", JSONObject())
        var found: Profile? = null
        menu.walk { key, obj ->
            if (key == "activeAccountHeaderRenderer" && found == null) {
                found = Profile(
                    name = obj.text("accountName") ?: "YouTube account",
                    handle = obj.text("channelHandle"),
                    avatar = obj.optJSONObject("accountPhoto")?.thumbnails()?.lastOrNull(),
                )
            }
        }
        val p = found ?: Profile("YouTube account", null, null)
        store?.apply {
            put(KEY_NAME, p.name); put(KEY_HANDLE, p.handle); put(KEY_AVATAR, p.avatar)
        }
        withContext(Dispatchers.Main) { profile = p }
    }

    fun signOut() {
        synchronized(cookies) { cookies.clear() }
        store?.clear()
        profile = null
        // The bells are the account's; signed out there are none (and no new-video notifications).
        Bells.clear()
        android.webkit.CookieManager.getInstance().apply { removeAllCookies(null); flush() }
    }

    /** youtube.com embeds the current web client version; requests with a stale one get odd results. */
    private fun refreshClientConfig() {
        runCatching {
            val request = Request.Builder().url("https://www.youtube.com/?hl=en")
                .header("User-Agent", Net.USER_AGENT)
                .header("Cookie", cookieHeader())
                .build()
            Net.client.newCall(request).execute().use { r ->
                absorbSetCookies(r.headers("Set-Cookie"))
                val html = r.body?.string().orEmpty()
                Regex("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.let { clientVersion = it }
                Regex("\"VISITOR_DATA\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.let { visitorData = it }
            }
            store?.put(KEY_CLIENT_VERSION, clientVersion)
            store?.put(KEY_VISITOR, visitorData)
        }
    }

    internal fun clientVersion() = clientVersion
    internal fun visitorData() = visitorData

    internal fun cookieHeader(): String = synchronized(cookies) { cookies.entries.joinToString("; ") { "${it.key}=${it.value}" } }

    /** youtube.com's own request signature: SHA-1 of "timestamp SAPISID origin". */
    internal fun authorization(origin: String = ORIGIN): String? {
        val ts = System.currentTimeMillis() / 1000
        fun hash(secret: String): String {
            val digest = MessageDigest.getInstance("SHA-1").digest("$ts $secret $origin".toByteArray())
            return "${ts}_" + digest.joinToString("") { "%02x".format(it) }
        }
        val (sapisid, p1, p3) = synchronized(cookies) {
            Triple(cookies["SAPISID"] ?: cookies["__Secure-3PAPISID"], cookies["__Secure-1PAPISID"], cookies["__Secure-3PAPISID"])
        }
        sapisid ?: return null
        return buildString {
            append("SAPISIDHASH ").append(hash(sapisid))
            p1?.let { append(" SAPISID1PHASH ").append(hash(it)) }
            p3?.let { append(" SAPISID3PHASH ").append(hash(it)) }
        }
    }

    /** Merges rotated cookies from a response (Google refreshes __Secure-*PSIDTS regularly). */
    internal fun absorbSetCookies(setCookies: List<String>) {
        if (setCookies.isEmpty()) return
        var changed = false
        synchronized(cookies) {
            setCookies.forEach { header ->
                val pair = header.substringBefore(';')
                val name = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=', "").trim()
                if (name.isEmpty()) return@forEach
                if (cookies[name] != value) { cookies[name] = value; changed = true }
            }
        }
        if (changed) persistCookies()
    }

    private fun persistCookies() = store?.put(KEY_COOKIES, cookieHeader())

    private fun parseCookieHeader(header: String, into: MutableMap<String, String>) {
        header.split(';').forEach { part ->
            val name = part.substringBefore('=').trim()
            if (name.isNotEmpty()) into[name] = part.substringAfter('=', "").trim()
        }
    }

    const val ORIGIN = "https://www.youtube.com"
    private const val FALLBACK_CLIENT_VERSION = "2.20260901.00.00"
    private const val KEY_COOKIES = "cookies"
    private const val KEY_CLIENT_VERSION = "client_version"
    private const val KEY_VISITOR = "visitor"
    private const val KEY_NAME = "name"
    private const val KEY_HANDLE = "handle"
    private const val KEY_AVATAR = "avatar"
}

/**
 * YouTube's internal web API ("InnerTube"), signed in. Reads: home, subscriptions,
 * channels, history, playlists. Writes: like/dislike, subscribe, Watch later, watch history
 * and watch time.
 */
object InnerTube {
    private const val BASE = "https://www.youtube.com/youtubei/v1/"
    private val JSON_TYPE = "application/json".toMediaType()

    /** Signed in when there's a session, otherwise anonymous (public pages like channels). */
    internal fun postAny(endpoint: String, body: JSONObject): JSONObject =
        if (Account.signedIn) post(endpoint, body) else post(endpoint, body, anonymous = true)

    internal fun post(endpoint: String, body: JSONObject, anonymous: Boolean = false): JSONObject {
        val auth = if (anonymous) null else Account.authorization() ?: throw NotSignedInException()
        body.put(
            "context",
            JSONObject().put(
                "client",
                JSONObject()
                    .put("clientName", "WEB")
                    .put("clientVersion", Account.clientVersion())
                    .put("hl", "en")
                    .put("gl", "US")
                    .apply { Account.visitorData()?.let { put("visitorData", it) } },
            ),
        )
        val request = Request.Builder()
            .url("$BASE$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody(JSON_TYPE))
            .header("User-Agent", Net.USER_AGENT)
            .apply { if (auth != null) { header("Cookie", Account.cookieHeader()); header("Authorization", auth) } }
            .header("Origin", Account.ORIGIN)
            .header("X-Origin", Account.ORIGIN)
            .header("Referer", Account.ORIGIN + "/")
            .header("X-Goog-AuthUser", "0")
            .header("X-Youtube-Client-Name", "1")
            .header("X-Youtube-Client-Version", Account.clientVersion())
            .apply { Account.visitorData()?.let { header("X-Goog-Visitor-Id", it) } }
            .build()
        Net.client.newCall(request).execute().use { r ->
            if (auth != null) Account.absorbSetCookies(r.headers("Set-Cookie"))
            val text = r.body?.string().orEmpty()
            if (r.code == 401 || r.code == 403) throw IOException("YouTube rejected the session (${r.code}). Try signing in again.")
            if (!r.isSuccessful) throw IOException("YouTube returned ${r.code}")
            return JSONObject(text)
        }
    }

    // ── Reads ──────────────────────────────────────────────────────────────────

    suspend fun browse(browseId: String, continuation: String? = null): FeedPage = withContext(Dispatchers.IO) {
        val body = if (continuation != null) JSONObject().put("continuation", continuation) else JSONObject().put("browseId", browseId)
        // YouTube represents Shorts inside playlists with Shorts-specific renderers. Keep the
        // normal video feeds free of duplicate Shorts, but include them when opening a playlist.
        parseFeed(post("browse", body), includeShorts = browseId.startsWith("VL"))
    }

    suspend fun home(next: String? = null): FeedPage = withContext(Dispatchers.IO) {
        val body = if (next != null) JSONObject().put("continuation", next)
        else JSONObject().put("browseId", "FEwhat_to_watch")
        val root = postAny("browse", body)
        parseFeed(root).copy(collections = if (next == null) parseHomeCollections(root) else emptyList())
    }

    /** Real playlist and auto-Mix shelves from the signed-in Home response. */
    private fun parseHomeCollections(root: JSONObject): List<PublicCollection> {
        val out = LinkedHashMap<String, PublicCollection>()
        root.walk { key, obj ->
            val isMix = key == "radioRenderer" || key == "compactRadioRenderer"
            val isPlaylist = key == "playlistRenderer" || key == "gridPlaylistRenderer" ||
                (key == "lockupViewModel" && obj.optString("contentType") == "LOCKUP_CONTENT_TYPE_PLAYLIST")
            if (!isMix && !isPlaylist) return@walk
            val id = when (key) {
                "lockupViewModel" -> obj.optString("contentId")
                else -> obj.optString("playlistId")
            }.ifBlank { return@walk }
            val metadata = obj.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
            val title = if (key == "lockupViewModel") {
                metadata?.optJSONObject("title")?.optString("content").orEmpty()
            } else obj.text("title").orEmpty()
            val mix = isMix || id.startsWith("RD")
            val thumbnail = obj.thumbnails()?.lastOrNull() ?: obj.firstImageSource()
            val collection = PublicCollection(
                url = "https://www.youtube.com/playlist?list=$id",
                title = title.ifBlank { if (mix) "YouTube Mix" else "Playlist" },
                subtitle = if (mix) "Mix" else "Playlist",
                thumbnail = thumbnail,
            )
            out.putIfAbsent(collection.url, collection)
        }
        return out.values.toList()
    }

    /** A channel's uploads (Videos tab), members-only ones filtered out like everywhere else. */
    suspend fun channelVideos(channelId: String, continuation: String? = null): FeedPage = withContext(Dispatchers.IO) {
        val body = if (continuation != null) JSONObject().put("continuation", continuation)
        else JSONObject().put("browseId", channelId).put("params", CHANNEL_VIDEOS_TAB)
        parseFeed(postAny("browse", body))
    }

    /** The channel page's Videos tab selector (as on youtube.com, 2026-10-06). */
    private const val CHANNEL_VIDEOS_TAB = "EgZ2aWRlb3PyBgQKAjoA"
    private const val CHANNEL_SHORTS_TAB = "EgZzaG9ydHPyBgUKA5oBAA=="
    private const val CHANNEL_RELEASES_TAB = "EghyZWxlYXNlc_IGBQoDsgEA"
    private const val CHANNEL_PLAYLISTS_TAB = "EglwbGF5bGlzdHPyBgoKCEIGCgIQaCIA"

    /** Channel Shorts tab via native InnerTube browse request. */
    suspend fun channelShorts(channelId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("browseId", channelId).put("params", CHANNEL_SHORTS_TAB)
        val root = postAny("browse", body)
        val list = mutableListOf<VideoItem>()
        root.walk { key, obj ->
            if (key == "shortsLockupViewModel") {
                val eid = obj.optString("entityId")
                var vid = eid.removePrefix("shorts-shelf-item-")
                if (vid.isBlank()) {
                    val url = obj.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
                        ?.optJSONObject("commandMetadata")?.optJSONObject("webCommandMetadata")?.optString("url").orEmpty()
                    if (url.contains("/shorts/")) vid = url.substringAfter("/shorts/").substringBefore("?")
                }
                if (vid.isNotBlank()) {
                    val title = obj.optJSONObject("overlayMetadata")?.optJSONObject("primaryText")?.optString("content").orEmpty()
                    val viewsStr = obj.optJSONObject("overlayMetadata")?.optJSONObject("secondaryText")?.optString("content").orEmpty()
                    val rawThumb = obj.thumbnails()?.lastOrNull() ?: obj.bestImageSource()
                    val thumb = rawThumb?.let { upgradeThumbnail(it, vid) } ?: "https://i.ytimg.com/vi/$vid/frame0.jpg"
                    list += VideoItem(
                        id = vid,
                        title = title,
                        channel = "",
                        thumbnail = thumb,
                        durationSec = 0L,
                        views = parseViews(viewsStr),
                        uploaded = null,
                        isShort = true,
                    )
                }
            } else if (key == "reelItemRenderer") {
                val vid = obj.optString("videoId")
                if (vid.isNotBlank()) {
                    val title = obj.text("headline") ?: obj.text("title") ?: ""
                    val viewsStr = obj.text("viewCountText") ?: ""
                    val rawThumb = obj.thumbnails()?.lastOrNull() ?: obj.bestImageSource()
                    val thumb = rawThumb?.let { upgradeThumbnail(it, vid) } ?: "https://i.ytimg.com/vi/$vid/frame0.jpg"
                    list += VideoItem(
                        id = vid,
                        title = title,
                        channel = "",
                        thumbnail = thumb,
                        durationSec = 0L,
                        views = parseViews(viewsStr),
                        uploaded = null,
                        isShort = true,
                    )
                }
            }
        }
        list.distinctBy { it.id }
    }

    /** Channel Releases tab (Albums, Singles, EPs) via native InnerTube browse. */
    suspend fun channelReleases(channelId: String): List<PublicCollection> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("browseId", channelId).put("params", CHANNEL_RELEASES_TAB)
        val root = postAny("browse", body)
        parsePublicCollections(root, isAlbum = true)
    }

    /** Channel Playlists tab via native InnerTube browse. */
    suspend fun channelPlaylists(channelId: String): List<PublicCollection> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("browseId", channelId).put("params", CHANNEL_PLAYLISTS_TAB)
        val root = postAny("browse", body)
        parsePublicCollections(root, isAlbum = false)
    }

    private fun parsePublicCollections(root: JSONObject, isAlbum: Boolean): List<PublicCollection> {
        val list = mutableListOf<PublicCollection>()
        root.walk { key, obj ->
            if (key == "playlistRenderer" || key == "gridPlaylistRenderer") {
                val pid = obj.optString("playlistId").ifBlank { return@walk }
                val title = obj.text("title").orEmpty()
                val countText = obj.text("videoCountText") ?: obj.text("videoCountShortText") ?: ""
                val thumb = obj.thumbnails()?.lastOrNull() ?: obj.firstImageSource()
                list += PublicCollection(
                    url = "https://www.youtube.com/playlist?list=$pid",
                    title = title,
                    subtitle = listOfNotNull(countText.takeIf { it.isNotBlank() }, if (isAlbum) "Album" else "Playlist").joinToString(" · "),
                    thumbnail = thumb,
                    isAlbum = isAlbum,
                )
            } else if (key == "lockupViewModel" && obj.optString("contentType") == "LOCKUP_CONTENT_TYPE_PLAYLIST") {
                val pid = obj.optString("contentId").ifBlank { return@walk }
                val meta = obj.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                val title = meta?.optJSONObject("title")?.optString("content").orEmpty()
                val thumb = obj.firstImageSource()
                val sub = obj.collectStrings("content").firstOrNull { it.contains("video", ignoreCase = true) || it.contains("track", ignoreCase = true) }
                    ?: if (isAlbum) "Album" else "Playlist"
                list += PublicCollection(
                    url = "https://www.youtube.com/playlist?list=$pid",
                    title = title,
                    subtitle = sub,
                    thumbnail = thumb,
                    isAlbum = isAlbum,
                )
            }
        }
        return list.distinctBy { it.url }
    }

    /** Loads public playlists and music albums via InnerTube browse with both new lockupViewModel and classic renderers. */
    suspend fun publicCollection(playlistIdOrUrl: String, isAlbum: Boolean = false): PublicCollectionDetails = withContext(Dispatchers.IO) {
        val cleanId = playlistIdOrUrl.removePrefix("VL").let {
            if (it.contains("list=")) it.substringAfter("list=").substringBefore("&") else it
        }
        val browseId = if (cleanId.startsWith("VL")) cleanId else "VL$cleanId"
        val root = postAny("browse", JSONObject().put("browseId", browseId))
        val pages = mutableListOf(root)
        var continuation = parsePlaylistFeed(root).next
        var pageCount = 0
        while (continuation != null && pageCount++ < 100) {
            val page = runCatching { postAny("browse", JSONObject().put("continuation", continuation)) }.getOrNull() ?: break
            pages += page
            continuation = parsePlaylistFeed(page).next
        }

        var title = ""
        var author = ""
        var sub = ""
        var thumb: String? = null

        val hdr = root.optJSONObject("header")
        if (hdr != null) {
            val phr = hdr.optJSONObject("playlistHeaderRenderer")
            if (phr != null) {
                title = phr.text("title").orEmpty()
                author = phr.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                sub = phr.optJSONObject("numVideosText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                thumb = phr.optJSONObject("playlistHeaderBanner")?.optJSONObject("heroPlaylistThumbnailRenderer")?.optJSONObject("thumbnail")?.thumbnails()?.lastOrNull()
            }
            val pageHeader = hdr.optJSONObject("pageHeaderRenderer")?.optJSONObject("content")?.optJSONObject("pageHeaderViewModel")
            if (pageHeader != null) {
                if (title.isBlank()) title = pageHeader.optJSONObject("title")?.optJSONObject("dynamicTextViewModel")?.optJSONObject("text")?.optString("content").orEmpty()
                pageHeader.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")?.let { rows ->
                    for (i in 0 until rows.length()) {
                        val parts = rows.optJSONObject(i)?.optJSONArray("metadataParts") ?: continue
                        for (j in 0 until parts.length()) {
                            val content = parts.optJSONObject(j)?.optJSONObject("text")?.optString("content").orEmpty()
                            if (content.isNotBlank()) {
                                if (author.isBlank() && content.contains("by ", ignoreCase = true)) author = content
                                else if (sub.isBlank()) sub = content
                            }
                        }
                    }
                }
            }
        }

        val videos = mutableListOf<VideoItem>()
        pages.forEach { page -> page.walk { key, obj ->
            if (key == "lockupViewModel" && obj.optString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO") {
                val vid = obj.optString("contentId").ifBlank { return@walk }
                val meta = obj.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                val vt = meta?.optJSONObject("title")?.optString("content").orEmpty()
                var vchan = author
                var vviews = ""
                meta?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")?.let { rows ->
                    for (i in 0 until rows.length()) {
                        val parts = rows.optJSONObject(i)?.optJSONArray("metadataParts") ?: continue
                        for (j in 0 until parts.length()) {
                            val t = parts.optJSONObject(j)?.optJSONObject("text")?.optString("content").orEmpty()
                            if (t.isNotBlank()) {
                                if (t.contains("view", ignoreCase = true)) vviews = t
                                else if (vchan.isBlank() || vchan == author) vchan = t
                            }
                        }
                    }
                }
                val vimg = obj.firstImageSource() ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                if (vt.isNotBlank()) {
                    videos += VideoItem(
                        id = vid,
                        title = vt,
                        channel = vchan,
                        thumbnail = vimg,
                        durationSec = 0L,
                        views = parseViews(vviews),
                        uploaded = null,
                    )
                }
            } else if (key == "playlistVideoRenderer") {
                val vid = obj.optString("videoId").ifBlank { return@walk }
                val vt = obj.text("title").orEmpty()
                val vchan = obj.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: author
                val dur = obj.text("lengthText")
                val vimg = obj.thumbnails()?.lastOrNull() ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                if (vt.isNotBlank()) {
                    videos += VideoItem(
                        id = vid,
                        title = vt,
                        channel = vchan,
                        thumbnail = vimg,
                        durationSec = parseDuration(dur),
                        views = -1L,
                        uploaded = null,
                    )
                }
            }
        } }
        val distinctVideos = videos.distinctBy { it.id }
        val directIds = pages.flatMap { page -> parsePlaylistFeed(page).videos }.mapTo(HashSet()) { it.id }
        val playlistVideos = if (directIds.isEmpty()) distinctVideos else distinctVideos.filter { it.id in directIds }
        val recommendations = if (directIds.isEmpty()) emptyList() else distinctVideos.filter { it.id !in directIds }
        if (thumb == null) thumb = playlistVideos.firstOrNull()?.thumbnail

        val finalSub = listOfNotNull(
            author.removePrefix("by ").removePrefix("By ").takeIf { it.isNotBlank() },
            sub.takeIf { it.isNotBlank() },
            if (isAlbum) "Album" else "Playlist",
            "${playlistVideos.size} songs"
        ).distinct().joinToString(" · ")

        val collection = PublicCollection(
            url = "https://www.youtube.com/playlist?list=$cleanId",
            title = title.ifBlank { if (isAlbum) "Album" else "Playlist" },
            subtitle = finalSub,
            thumbnail = thumb,
            isAlbum = isAlbum,
        )
        PublicCollectionDetails(
            collection = collection,
            description = "",
            videos = playlistVideos,
            recommendations = recommendations,
        )
    }
    suspend fun subscriptionsFeed(next: String? = null) = browse("FEsubscriptions", next)
    suspend fun history(next: String? = null) = browse("FEhistory", next)
    suspend fun playlist(id: String, next: String? = null): FeedPage = withContext(Dispatchers.IO) {
        val browseId = if (id.startsWith("VL")) id else "VL$id"
        val body = if (next == null) JSONObject().put("browseId", browseId) else JSONObject().put("continuation", next)
        val root = postAny("browse", body)
        parsePlaylistFeed(root)
    }

    suspend fun channels(): List<ChannelRef> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, ChannelRef>()
        post("browse", JSONObject().put("browseId", "FEchannels")).walk { key, obj ->
            if (key == "channelRenderer" || key == "gridChannelRenderer") {
                val id = obj.optString("channelId").ifBlank { return@walk }
                out.getOrPut(id) {
                    ChannelRef(id, obj.text("title") ?: "", obj.thumbnails()?.lastOrNull()?.let(::absoluteUrl))
                }
            }
        }
        out.values.toList()
    }

    suspend fun playlists(): List<PlaylistRef> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, PlaylistRef>()
        post("browse", JSONObject().put("browseId", "FEplaylist_aggregation")).walk { key, obj ->
            when (key) {
                "gridPlaylistRenderer", "playlistRenderer" -> {
                    val id = obj.optString("playlistId").ifBlank { return@walk }
                    out.getOrPut(id) {
                        PlaylistRef(id, obj.text("title") ?: "", obj.thumbnails()?.lastOrNull()?.let(::absoluteUrl), obj.text("videoCountText") ?: obj.text("videoCountShortText"))
                    }
                }
                "lockupViewModel" -> if (obj.optString("contentType") == "LOCKUP_CONTENT_TYPE_PLAYLIST") {
                    val id = obj.optString("contentId").ifBlank { return@walk }
                    val meta = obj.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                    out.getOrPut(id) {
                        PlaylistRef(
                            id = id,
                            title = meta?.optJSONObject("title")?.optString("content").orEmpty(),
                            thumbnail = obj.firstImageSource(),
                            subtitle = obj.collectStrings("text").firstOrNull { it.contains("video", ignoreCase = true) },
                        )
                    }
                }
            }
        }
        out.values.filterNot { it.id == "WL" || it.id == "LL" }
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        post("playlist/delete", JSONObject().put("playlistId", playlistId))
        Unit
    }

    /**
     * Like state, subscription and bell for a video, from the signed-in watch page data. The bell
     * it reports is kept in [Bells] (every page that shows it keeps the local copy current).
     */
    suspend fun videoState(videoId: String): VideoAccountState = withContext(Dispatchers.IO) {
        val next = post("next", JSONObject().put("videoId", videoId))
        val state = parseVideoState(next)
        val owner = next.findObject("videoOwnerRenderer")
        state.channelId?.let { Bells.learn(it, state.subscribed, state.bell, owner?.text("title"), owner?.thumbnails()?.lastOrNull()?.let(::absoluteUrl)) }
        state
    }

    /** Exact watch-page figures for the description facts row; fetched only when that panel opens. */
    suspend fun descriptionFacts(videoId: String): VideoDescriptionFacts = withContext(Dispatchers.IO) {
        val next = postAny("next", JSONObject().put("videoId", videoId))
        val primary = next.findObject("videoPrimaryInfoRenderer")
        val viewRenderer = primary?.optJSONObject("viewCount")?.optJSONObject("videoViewCountRenderer")
        val rawViews = viewRenderer?.text("viewCount")
        val originalViews = viewRenderer?.optString("originalViewCount")?.toLongOrNull()?.takeIf { it > 0L }
        val factoids = mutableListOf<Pair<String, String>>()
        next.findObject("videoDescriptionHeaderRenderer")?.optJSONArray("factoid")?.let { array ->
            for (i in 0 until array.length()) {
                val wrapper = array.optJSONObject(i) ?: continue
                val renderer = wrapper.optJSONObject("factoidRenderer")
                    ?: wrapper.keys().asSequence().mapNotNull { key ->
                        wrapper.optJSONObject(key)?.optJSONObject("factoid")?.optJSONObject("factoidRenderer")
                    }.firstOrNull()
                    ?: continue
                val value = renderer.text("value")?.takeIf { it.isNotBlank() } ?: continue
                factoids += value to renderer.text("label").orEmpty()
            }
        }
        val factViews = factoids.firstOrNull { it.second.contains("view", ignoreCase = true) }?.first?.let(::parseViews)?.takeIf { it >= 0L }
        val factLikes = factoids.firstOrNull { it.second.contains("like", ignoreCase = true) }?.first?.let(::parseViews)?.takeIf { it >= 0L }
        VideoDescriptionFacts(
            videoId = videoId,
            viewCount = originalViews ?: factViews ?: rawViews?.let(::parseViews)?.takeIf { it >= 0L },
            viewCountText = rawViews,
            publishedDateText = primary?.text("dateText"),
            likes = factLikes,
        )
    }

    internal fun parseVideoState(next: JSONObject): VideoAccountState {
        var like = LikeStatus.None
        var subscribed: Boolean? = null
        var channelId: String? = null
        var bell: Boolean? = null
        next.walk { key, obj ->
            when (key) {
                "likeStatusEntity" -> like = when (obj.optString("likeStatus")) {
                    "LIKE" -> LikeStatus.Like
                    "DISLIKE" -> LikeStatus.Dislike
                    else -> like
                }
                "subscribeButtonRenderer" -> {
                    if (obj.has("subscribed")) subscribed = obj.optBoolean("subscribed")
                    obj.optString("channelId").ifBlank { null }?.let { channelId = it }
                }
                "subscriptionStateEntity" -> if (obj.has("subscribed")) subscribed = obj.optBoolean("subscribed")
                "subscriptionNotificationToggleButtonRenderer" -> if (bell == null) bell = parseBell(obj)
                "videoOwnerRenderer" -> obj.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")?.takeIf { it.startsWith("UC") }?.let { if (channelId == null) channelId = it }
            }
        }
        return VideoAccountState(like, subscribed, channelId, bell)
    }

    /** Whether the account follows [channelId] (from the channel page's subscribe button); its bell goes to [Bells]. */
    suspend fun channelSubscribed(channelId: String, name: String?, avatar: String?): Boolean? = withContext(Dispatchers.IO) {
        var subscribed: Boolean? = null
        var bell: Boolean? = null
        post("browse", JSONObject().put("browseId", channelId)).walk { key, obj ->
            if ((key == "subscribeButtonRenderer" || key == "subscriptionStateEntity") && obj.has("subscribed") && subscribed == null) {
                subscribed = obj.optBoolean("subscribed")
            }
            if (key == "subscriptionNotificationToggleButtonRenderer" && bell == null) bell = parseBell(obj)
        }
        Bells.learn(channelId, subscribed, bell, name, avatar)
        subscribed
    }

    /**
     * A subscribe button's bell (`subscriptionNotificationToggleButtonRenderer`): true = "All",
     * false = "Personalized" or "None", null = can't tell. Read from the current state's icon
     * (NOTIFICATIONS_ACTIVE = All), else its id (2 All, 3 Personalized, 0 None).
     */
    internal fun parseBell(toggle: JSONObject): Boolean? {
        val current = toggle.optInt("currentStateId", -1)
        val states = toggle.optJSONArray("states")
        for (i in 0 until (states?.length() ?: 0)) {
            val s = states?.optJSONObject(i) ?: continue
            if (s.optInt("stateId", -2) != current) continue
            when (s.collectStrings("iconType").firstOrNull()) {
                "NOTIFICATIONS_ACTIVE" -> return true
                "NOTIFICATIONS_NONE", "NOTIFICATIONS_OFF" -> return false
            }
        }
        return when (current) {
            2 -> true
            0, 3 -> false
            else -> null
        }
    }

    /**
     * Every channel the account follows with its bell, from the subscriptions page (FEchannels),
     * all its pages. Null if a page failed (a partial list mustn't turn bells off).
     */
    suspend fun subscriptions(): List<SubscribedChannel>? = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, SubscribedChannel>()
        var page: JSONObject? = post("browse", JSONObject().put("browseId", "FEchannels"))
        var pages = 0
        while (page != null) {
            val (channels, next) = parseSubscriptions(page)
            channels.forEach { out.putIfAbsent(it.ref.id, it) }
            if (next == null) break
            if (++pages >= 50) return@withContext null
            page = runCatching { post("browse", JSONObject().put("continuation", next)) }.getOrElse {
                Log.w("AirStream", "bells: subscriptions page ${pages + 1} failed (${it.message})")
                return@withContext null
            }
        }
        out.values.toList()
    }

    internal fun parseSubscriptions(root: JSONObject): Pair<List<SubscribedChannel>, String?> {
        val out = LinkedHashMap<String, SubscribedChannel>()
        var next: String? = null
        root.walk { key, obj ->
            when (key) {
                "channelRenderer", "gridChannelRenderer" -> {
                    val id = obj.optString("channelId").ifBlank { return@walk }
                    out.putIfAbsent(id, SubscribedChannel(
                        ChannelRef(id, obj.text("title") ?: "", obj.thumbnails()?.lastOrNull()?.let(::absoluteUrl)),
                        obj.findObject("subscriptionNotificationToggleButtonRenderer")?.let(::parseBell),
                    ))
                }
                "continuationItemRenderer", "continuationItemViewModel" -> obj.collectStrings("token").firstOrNull()?.let { next = it }
            }
        }
        return out.values.toList() to next
    }

    // ── Shorts ─────────────────────────────────────────────────────────────────

    /**
     * One page of the Shorts feed; [token] null for the first. YouTube's Shorts feed is no browse
     * page but the "reel sequence" that follows one short (reel/reel_watch_sequence, as on
     * youtube.com/shorts). The first page starts from a seed: signed in, the first short on your
     * Home (so the sequence is yours); else, or with none there, one from a public "shorts"
     * search — the sequence then works anonymously (checked 2026-10-28). The seed plays first.
     */
    suspend fun shorts(token: String? = null): FeedPage = withContext(Dispatchers.IO) {
        if (token != null) return@withContext parseShorts(postAny("reel/reel_watch_sequence", JSONObject().put("sequenceParams", token)))
        val seed = shortsSeed() ?: throw IOException("YouTube didn't offer any Shorts right now.")
        val page = parseShorts(postAny("reel/reel_watch_sequence", JSONObject().put("sequenceParams", reelSequenceParams(seed))))
        page.copy(videos = listOf(shortItem(seed, null)) + page.videos.filter { it.id != seed })
    }

    private fun shortsSeed(): String? {
        if (Account.signedIn) {
            runCatching { reelIds(post("browse", JSONObject().put("browseId", "FEwhat_to_watch"))).firstOrNull() }
                .getOrNull()?.let { return it }
        }
        // A different public short each time, so the generic feed doesn't always start the same way.
        return reelIds(postAny("search", JSONObject().put("query", "shorts"))).take(10).randomOrNull()
    }

    /** Every short a response links to (Home's Shorts shelf, search results), in order. */
    private fun reelIds(root: JSONObject): List<String> {
        val ids = LinkedHashSet<String>()
        val entries = root.optJSONArray("entries")
        if (entries != null) {
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val reel = entry.optJSONObject("command")?.optJSONObject("reelWatchEndpoint") ?: continue
                val id = reel.optString("videoId").takeIf { it.length == 11 } ?: continue
                if (!isReelAd(entry, reel)) ids.add(id)
            }
        } else {
            root.walk { key, obj ->
                if (key == "reelWatchEndpoint") {
                    val id = obj.optString("videoId").takeIf { it.length == 11 }
                    if (id != null && !obj.optBoolean("isAd", false)) {
                        ids.add(id)
                    }
                }
            }
        }
        return ids.toList()
    }

    /** Checks whether an entry in a Shorts feed is a sponsored ad or promo short. */
    internal fun isReelAd(entry: JSONObject, reel: JSONObject): Boolean {
        if (entry.has("adPlacement") || entry.has("adSlotRenderer") ||
            entry.has("reelAdRenderer") || entry.has("inStreamAd") ||
            entry.has("adLayout") || entry.has("adPlaybackContext") ||
            entry.has("compactPromotedItemRenderer") || entry.has("promotedSparklesWebRenderer") ||
            entry.has("adPlayerOverlayRenderer")) {
            return true
        }
        if (reel.optBoolean("isAd", false)) return true
        if (reel.has("adPlacement") || reel.has("adSlotRenderer")) return true

        val overlay = reel.optJSONObject("overlay")?.optJSONObject("reelPlayerOverlayRenderer")
        if (overlay != null) {
            val style = overlay.optString("style")
            if (style.contains("AD", ignoreCase = true) || style.contains("SPONSOR", ignoreCase = true)) return true
            if (overlay.has("adBadge") || overlay.has("sponsoredBadge") || overlay.has("adInfoRenderer") ||
                overlay.has("adHeaderRenderer") || overlay.has("adLayout")) return true
        }

        val raw = entry.toString()
        if (raw.contains("\"adSlotRenderer\"") || raw.contains("\"reelAdRenderer\"") ||
            raw.contains("\"adPlacement\"") || raw.contains("\"isAd\":true") ||
            raw.contains("REEL_PLAYER_OVERLAY_STYLE_AD") || raw.contains("REEL_PLAYER_OVERLAY_STYLE_SPONSORED") ||
            raw.contains("BADGE_STYLE_TYPE_AD") || raw.contains("\"sponsoredBadge\"")) {
            return true
        }

        return false
    }

    /**
     * The sequence's start, as youtube.com encodes it (the ReelSequence protobuf, per YouTube.js):
     * 1 = the seed's id, 5 = params { 3 = 5 }, 10 = 25. Written by hand; no protobuf library.
     */
    internal fun reelSequenceParams(shortId: String): String {
        val id = shortId.toByteArray()
        val bytes = byteArrayOf(0x0A, id.size.toByte()) + id + byteArrayOf(0x2A, 0x02, 0x18, 0x05, 0x50, 25)
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }

    /**
     * A reel sequence: each `entries[]` item's command carries a reelWatchEndpoint (the short's
     * id and its 9:16 first frame); the next page is `continuationEndpoint`'s token. Titles and
     * channels aren't in it; they come with the streams (YouTube.details).
     */
    internal fun parseShorts(root: JSONObject): FeedPage {
        val videos = LinkedHashMap<String, VideoItem>()
        val entries = root.optJSONArray("entries")
        for (i in 0 until (entries?.length() ?: 0)) {
            val entry = entries?.optJSONObject(i) ?: continue
            val reel = entry.optJSONObject("command")?.optJSONObject("reelWatchEndpoint") ?: continue
            val id = reel.optString("videoId").takeIf { it.length == 11 } ?: continue
            if (isReelAd(entry, reel)) {
                Log.i("AirStream", "shorts: blocked ad reel $id")
                continue
            }
            val frame = reel.optJSONObject("thumbnail")?.collectStrings("url")?.lastOrNull()
            videos.putIfAbsent(id, shortItem(id, frame))
        }
        val next = root.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")?.optString("token")?.ifBlank { null }
        return FeedPage(videos.values.toList(), next)
    }

    /** A short before its details load: just its id and its first frame (9:16). */
    private fun shortItem(id: String, frame: String?) = VideoItem(
        id = id, title = "", channel = "", channelUrl = null,
        thumbnail = frame?.let { upgradeThumbnail(it, id) } ?: "https://i.ytimg.com/vi/$id/frame0.jpg",
        avatar = null, durationSec = 0, views = 0, uploaded = null,
        isShort = true,
    )

    // ── Writes ─────────────────────────────────────────────────────────────────

    suspend fun setLike(videoId: String, status: LikeStatus) = withContext(Dispatchers.IO) {
        val endpoint = when (status) {
            LikeStatus.Like -> "like/like"
            LikeStatus.Dislike -> "like/dislike"
            LikeStatus.None -> "like/removelike"
        }
        post(endpoint, JSONObject().put("target", JSONObject().put("videoId", videoId)))
        Unit
    }

    suspend fun setSubscribed(channelId: String, subscribed: Boolean) = withContext(Dispatchers.IO) {
        post(
            if (subscribed) "subscription/subscribe" else "subscription/unsubscribe",
            JSONObject().put("channelIds", JSONArray().put(channelId)),
        )
        Unit
    }

    /** The channel's bell: [all] = "All" (every upload), else "Personalized", YouTube's default. */
    suspend fun setBell(channelId: String, all: Boolean) = withContext(Dispatchers.IO) {
        post("notification/modify_channel_preference", JSONObject().put("params", bellParams(channelId, if (all) BELL_ALL else BELL_PERSONALIZED)))
        Unit
    }

    private const val BELL_PERSONALIZED = 1
    private const val BELL_ALL = 2

    /**
     * What the bell menu's items carry (modifyChannelNotificationPreferenceEndpoint.params, a
     * protobuf): 1 = channel id, 2 = { 1 = preference }, 3 = 0, 4 = 4, URL-encoded base64 as in the
     * answer. Preferences per YouTube.js: 1 Personalized, 2 All, 3 None. youtube.com's own items
     * add 10 = the watch page's video; the request works without (YouTube.js leaves it out).
     * Built here rather than taken from the menu: the menu isn't at hand everywhere (the
     * subscriptions list, Shorts), and not every menu lists "All" (next.json's, for a channel not
     * subscribed, has only Personalized / None). Written by hand; no protobuf library.
     */
    internal fun bellParams(channelId: String, preference: Int): String {
        val id = channelId.toByteArray()
        val bytes = byteArrayOf(0x0A, id.size.toByte()) + id +
            byteArrayOf(0x12, 0x02, 0x08, preference.toByte(), 0x18, 0x00, 0x20, 0x04)
        return java.net.URLEncoder.encode(java.util.Base64.getEncoder().encodeToString(bytes), "UTF-8")
    }

    suspend fun setInWatchLater(videoId: String, add: Boolean) = setInPlaylist("WL", videoId, add)

    suspend fun setInPlaylist(playlistId: String, videoId: String, add: Boolean) = withContext(Dispatchers.IO) {
        val action = if (add) JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId)
        else JSONObject().put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID").put("removedVideoId", videoId)
        post("browse/edit_playlist", JSONObject().put("playlistId", playlistId).put("actions", JSONArray().put(action)))
        Unit
    }

    /**
     * The account's playlists for [videoId], Watch later first, each saying whether the video is
     * in it: what youtube.com's "Save to…" dialog lists (GitHub issue #12). If that answer can't
     * be read, the Library's playlists stand in, without knowing which hold the video.
     */
    suspend fun saveChoices(videoId: String): List<PlaylistChoice> = withContext(Dispatchers.IO) {
        val answer = post(
            "playlist/get_add_to_playlist",
            JSONObject().put("videoIds", JSONArray().put(videoId)).put("excludeWatchLater", false),
        )
        parseSaveChoices(answer).ifEmpty {
            Log.w("AirStream", "playlists: no choices in the Save dialog's answer, using the Library's list")
            playlists().map { PlaylistChoice(it.id, it.title, contains = null, privacy = null) }
        }
    }

    /** Creates an empty playlist; the caller adds exactly the selected item after creation. */
    suspend fun createPlaylist(title: String, privacy: String): String = withContext(Dispatchers.IO) {
        val answer = post(
            "playlist/create",
            JSONObject().put("title", title).put("privacyStatus", privacy),
        )
        answer.optString("playlistId").ifBlank { throw IOException("YouTube didn't create the playlist") }
    }

    /** One video being watched: the tracking URL for its watch-time pings. */
    class WatchSession(val videoId: String, val watchtimeUrl: String, val cpn: String)

    /**
     * Starts watching [videoId] on the account, as the web player does: fetch the signed-in
     * player response and ping its playback URL, which puts the video in watch history.
     * Returns the session for [pingWatchtime], or null if YouTube gave no tracking URLs.
     */
    fun startWatch(videoId: String): WatchSession? {
        val tracking = post("player", JSONObject().put("videoId", videoId)).optJSONObject("playbackTracking") ?: return null
        val cpn = (1..16).map { CPN_ALPHABET[Random.nextInt(CPN_ALPHABET.length)] }.joinToString("")
        tracking.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl")?.ifBlank { null }?.let { base ->
            ping(base, "ver=2&cpn=$cpn")
        }
        val watchtime = tracking.optJSONObject("videostatsWatchtimeUrl")?.optString("baseUrl")?.ifBlank { null } ?: return null
        return WatchSession(videoId, watchtime, cpn)
    }

    /**
     * Reports what was played since the last ping: the stretches of the video ([starts] →
     * [ends], seconds), where playback is now ([positionSec]) and how long the session has
     * run ([elapsedSec]). The same parameters the web player sends; YouTube's
     * recommendations are built on this watch time.
     */
    fun pingWatchtime(session: WatchSession, positionSec: Double, starts: List<Double>, ends: List<Double>, playing: Boolean, elapsedSec: Double, final: Boolean) {
        fun f(x: Double) = String.format(java.util.Locale.US, "%.3f", x)
        val rt = f(elapsedSec)
        val query = buildString {
            append("ver=2&cpn=").append(session.cpn)
            append("&cmt=").append(f(positionSec))
            append("&st=").append(starts.joinToString(",") { f(it) })
            append("&et=").append(ends.joinToString(",") { f(it) })
            append("&state=").append(if (playing) "playing" else "paused")
            append("&rt=").append(rt).append("&rtn=").append(rt).append("&rti=").append(elapsedSec.toInt())
            append("&lact=").append(Random.nextInt(800, 4000))
            append("&volume=100&muted=0&fs=0&hl=en_US&cr=US&c=WEB&cver=").append(Account.clientVersion())
            if (final) append("&final=1")
        }
        ping(session.watchtimeUrl, query)
    }

    private fun ping(base: String, query: String) {
        val request = Request.Builder().url(base + (if ('?' in base) "&" else "?") + query)
            .header("User-Agent", Net.USER_AGENT)
            .header("Cookie", Account.cookieHeader())
            .header("Referer", Account.ORIGIN + "/")
            .build()
        Net.client.newCall(request).execute().use { r ->
            Account.absorbSetCookies(r.headers("Set-Cookie"))
            if (!r.isSuccessful) throw IOException("stats ping ${r.code}")
        }
    }

    private const val CPN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

    // ── Parsing ────────────────────────────────────────────────────────────────

    /**
     * Pulls every video out of a browse response, whichever renderer YouTube used:
     * the classic videoRenderer family or the newer lockupViewModel. Shorts are included only
     * for playlists, where they are valid saved items and need to open from the account library.
     */
    internal fun parseFeed(root: JSONObject, includeShorts: Boolean = false): FeedPage {
        val videos = LinkedHashMap<String, VideoItem>()
        var next: String? = null
        root.walk { key, obj ->
            when (key) {
                // Members-only videos can't be played (anonymous extraction), and full movies/TV
                // aren't wanted (Derek), so neither is ever shown.
                "videoRenderer", "gridVideoRenderer", "compactVideoRenderer", "playlistVideoRenderer" ->
                    if (!isMembersOnly(obj) && !isMovie(obj)) parseVideoRenderer(obj)?.let { videos.putIfAbsent(it.id, it) }
                "lockupViewModel" -> if (obj.optString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO" && !isMembersOnly(obj) && !isMovie(obj)) {
                    parseLockup(obj)?.let { videos.putIfAbsent(it.id, it) }
                }
                "shortsLockupViewModel" -> if (includeShorts) {
                    parseShortsLockup(obj)?.let { videos.putIfAbsent(it.id, it) }
                }
                "reelItemRenderer" -> if (includeShorts) {
                    parseReelItem(obj)?.let { videos.putIfAbsent(it.id, it) }
                }
                // Only the feed's own "load more" item (old and new names); chip filters
                // carry tokens too and must not be mistaken for the next page.
                "continuationItemRenderer", "continuationItemViewModel" ->
                    obj.collectStrings("token").firstOrNull()?.let { next = it }
            }
        }
        return FeedPage(videos.values.toList(), next)
    }

    /** Keeps the playlist's own video list separate from browse-page recommendations. */
    internal fun parsePlaylistFeed(root: JSONObject): FeedPage {
        val all = parseFeed(root, includeShorts = true)
        val lists = mutableListOf<JSONObject>()
        root.walk { key, obj -> if (key == "playlistVideoListRenderer") lists += obj }
        val direct = LinkedHashMap<String, VideoItem>()
        var next: String? = null
        if (lists.isNotEmpty()) {
            lists.forEach { list ->
                val page = parseFeed(list, includeShorts = true)
                page.videos.forEach { direct.putIfAbsent(it.id, it) }
                if (page.next != null) next = page.next
            }
        } else {
            // Older account playlist responses don't wrap entries in a playlistVideoListRenderer.
            // The playlistVideoRenderer key still distinguishes the actual saved tracks from
            // the generic videoRenderer / lockup recommendation shelves around them.
            root.walk { key, obj ->
                if (key == "playlistVideoRenderer" && !isMembersOnly(obj) && !isMovie(obj)) {
                    parseVideoRenderer(obj)?.let { direct.putIfAbsent(it.id, it) }
                }
            }
        }
        if (direct.isEmpty()) return all
        return FeedPage(
            videos = direct.values.toList(),
            next = next,
            recommendations = all.videos.filterNot { it.id in direct },
        )
    }

    private fun parseShortsLockup(o: JSONObject): VideoItem? {
        var id = o.optString("entityId").removePrefix("shorts-shelf-item-")
        if (id.isBlank()) {
            val url = o.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
                ?.optJSONObject("commandMetadata")?.optJSONObject("webCommandMetadata")?.optString("url").orEmpty()
            if (url.contains("/shorts/")) id = url.substringAfter("/shorts/").substringBefore('?')
        }
        if (id.isBlank()) return null
        val metadata = o.optJSONObject("overlayMetadata")
        val rawThumb = o.thumbnails()?.lastOrNull()
            ?: o.collectStrings("url").lastOrNull { it.contains("ytimg") || it.contains("googleusercontent") || it.contains("ggpht") }
        return VideoItem(
            id = id,
            title = metadata?.optJSONObject("primaryText")?.optString("content").orEmpty().ifBlank { "Short" },
            channel = "",
            thumbnail = rawThumb?.let { upgradeThumbnail(it, id) } ?: "https://i.ytimg.com/vi/$id/frame0.jpg",
            durationSec = 0L,
            views = parseViews(metadata?.optJSONObject("secondaryText")?.optString("content")),
            uploaded = null,
            isShort = true,
        )
    }

    private fun parseReelItem(o: JSONObject): VideoItem? {
        val id = o.optString("videoId").ifBlank { return null }
        val rawThumb = o.thumbnails()?.lastOrNull()
            ?: o.collectStrings("url").lastOrNull { it.contains("ytimg") || it.contains("googleusercontent") || it.contains("ggpht") }
        return VideoItem(
            id = id,
            title = o.text("headline") ?: o.text("title") ?: "Short",
            channel = o.text("shortBylineText") ?: "",
            thumbnail = rawThumb?.let { upgradeThumbnail(it, id) } ?: "https://i.ytimg.com/vi/$id/frame0.jpg",
            durationSec = 0L,
            views = parseViews(o.text("viewCountText")),
            uploaded = null,
            isShort = true,
        )
    }

    /** YouTube's "Members only" badge: BADGE_MEMBERS_ONLY (lockups) / BADGE_STYLE_TYPE_MEMBERS_ONLY (older renderers). */
    internal fun isMembersOnly(o: JSONObject): Boolean =
        o.collectStrings("badgeStyle").any { it.contains("MEMBERS_ONLY") } ||
            o.collectStrings("style").any { it == "BADGE_STYLE_TYPE_MEMBERS_ONLY" }

    /**
     * Full movies and TV (Primetime Channels like HBO Max / Shudder, YouTube Movies): a paid-offer
     * badge ("Try now", "Buy or rent") or a content rating (R, PG-13, TV-MA), which ordinary
     * uploads never carry. Older renderers: BADGE_STYLE_TYPE_YPC / BADGE_STYLE_TYPE_MEDIA;
     * lockups: a BADGE_DEFAULT whose text is the rating (not every movie has one) or a
     * BADGE_COMMERCE offer — ordinary uploads use that style only for "Fundraiser".
     */
    internal fun isMovie(o: JSONObject): Boolean {
        if (o.collectStrings("style").any { it == "BADGE_STYLE_TYPE_YPC" || it == "BADGE_STYLE_TYPE_MEDIA" }) return true
        var movie = false
        o.walk { key, badge ->
            if (key != "badgeViewModel") return@walk
            val text = badge.optString("badgeText")
            if (CONTENT_RATING.matches(text) ||
                (badge.optString("badgeStyle") == "BADGE_COMMERCE" && !text.equals("Fundraiser", ignoreCase = true))
            ) movie = true
        }
        return movie
    }

    private val CONTENT_RATING = Regex("^(G|PG|PG-13|R|NC-17|NR|TV-(Y|Y7|G|PG|14|MA))$")

    private fun parseVideoRenderer(o: JSONObject): VideoItem? {
        val id = o.optString("videoId").ifBlank { return null }
        val byline = o.optJSONObject("ownerText") ?: o.optJSONObject("shortBylineText") ?: o.optJSONObject("longBylineText")
        val channelRun = byline?.optJSONArray("runs")?.optJSONObject(0)
        val channelId = channelRun?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
        val duration = o.text("lengthText")
            ?: o.optString("lengthSeconds").toLongOrNull()?.let { formatDuration(it * 1000) }
            ?: o.optJSONArray("thumbnailOverlays")?.let { arr ->
                (0 until arr.length()).firstNotNullOfOrNull { arr.optJSONObject(it)?.optJSONObject("thumbnailOverlayTimeStatusRenderer")?.let { r -> r.text("text") } }
            }
        // playlistVideoRenderer packs "views • age" into videoInfo runs.
        val infoRuns = o.optJSONObject("videoInfo")?.optJSONArray("runs")?.let { runs ->
            (0 until runs.length()).map { runs.optJSONObject(it).optString("text") }.filter { it.isNotBlank() && it.trim() != "•" }
        }.orEmpty()
        val avatar = o.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")?.thumbnails()?.lastOrNull()
            ?: o.optJSONObject("channelThumbnail")?.thumbnails()?.lastOrNull()
        val durSec = parseDuration(duration)
        val isShort = o.optJSONObject("navigationEndpoint")?.optJSONObject("reelWatchEndpoint") != null ||
            o.optJSONObject("navigationEndpoint")?.optJSONObject("commandMetadata")?.optJSONObject("webCommandMetadata")?.optString("url")?.contains("/shorts/") == true ||
            o.collectStrings("style").any { it.contains("SHORTS") } ||
            (durSec in 1..60 && (duration == "SHORTS" || o.collectStrings("text").any { it.equals("SHORTS", ignoreCase = true) }))
        return VideoItem(
            id = id,
            title = o.text("title") ?: return null,
            channel = channelRun?.optString("text").orEmpty(),
            channelUrl = channelId?.takeIf { it.startsWith("UC") }?.let { "https://www.youtube.com/channel/$it" },
            thumbnail = if (isShort) "https://i.ytimg.com/vi/$id/oar2.jpg" else (o.thumbnails()?.lastOrNull() ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"),
            avatar = avatar?.let(::absoluteUrl),
            durationSec = durSec,
            views = parseViews(o.text("viewCountText") ?: o.text("shortViewCountText") ?: infoRuns.firstOrNull { it.contains("view") }),
            uploaded = o.text("publishedTimeText") ?: infoRuns.firstOrNull { it.contains("ago") },
            isShort = isShort,
        )
    }

    private fun parseLockup(o: JSONObject): VideoItem? {
        val id = o.optString("contentId").ifBlank { return null }
        val meta = o.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val title = meta.optJSONObject("title")?.optString("content")?.ifBlank { null } ?: return null
        // metadataRows: [ [channel], [views, age] ] as text parts.
        val parts = mutableListOf<String>()
        meta.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")?.let { rows ->
            for (r in 0 until rows.length()) {
                val ps = rows.optJSONObject(r)?.optJSONArray("metadataParts") ?: continue
                for (p in 0 until ps.length()) ps.optJSONObject(p)?.optJSONObject("text")?.optString("content")?.ifBlank { null }?.let(parts::add)
            }
        }
        val avatar = meta.optJSONObject("image")?.collectStrings("url")?.firstOrNull()
        val duration = o.optJSONObject("contentImage")?.collectStrings("text")?.firstOrNull { DURATION.matches(it) }
        val durSec = parseDuration(duration)
        val isShort = o.optString("contentType") == "LOCKUP_CONTENT_TYPE_SHORTS" ||
            o.collectStrings("url").any { it.contains("/shorts/") }
        return VideoItem(
            id = id,
            title = title,
            channel = parts.firstOrNull { !it.contains("view") && !it.contains("ago") }.orEmpty(),
            channelUrl = o.collectStrings("browseId").firstOrNull { it.startsWith("UC") }?.let { "https://www.youtube.com/channel/$it" },
            thumbnail = if (isShort) "https://i.ytimg.com/vi/$id/oar2.jpg" else (o.thumbnails()?.lastOrNull() ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"),
            avatar = avatar?.let(::absoluteUrl),
            durationSec = durSec,
            views = parseViews(parts.firstOrNull { it.contains("view") }),
            uploaded = parts.firstOrNull { it.contains("ago") },
            isShort = isShort,
        )
    }

    private val DURATION = Regex("^\\d{1,2}(:\\d{2}){1,2}$")

    private fun parseDuration(text: String?): Long {
        val t = text?.trim() ?: return 0
        if (!DURATION.matches(t)) return 0
        return t.split(':').fold(0L) { acc, part -> acc * 60 + (part.toLongOrNull() ?: 0) }
    }

    /** "1,234,567 views", "1.2M views", "No views" → count, or -1 when unknown. */
    internal fun parseViews(text: String?): Long {
        val t = text?.lowercase()?.replace(",", "")?.trim() ?: return -1
        if (t.startsWith("no ")) return 0
        val m = Regex("([\\d.]+)\\s*([kmb]?)").find(t) ?: return -1
        val n = m.groupValues[1].toDoubleOrNull() ?: return -1
        val mult = when (m.groupValues[2]) { "k" -> 1e3; "m" -> 1e6; "b" -> 1e9; else -> 1.0 }
        return (n * mult).toLong()
    }

    private fun absoluteUrl(url: String) = if (url.startsWith("//")) "https:$url" else url
}

// ── JSON helpers ───────────────────────────────────────────────────────────────

/** Depth-first visit of every object-valued key in the tree. */
internal fun JSONObject.walk(visit: (key: String, obj: JSONObject) -> Unit) {
    val keys = keys()
    while (keys.hasNext()) {
        val k = keys.next()
        when (val v = opt(k)) {
            is JSONObject -> { visit(k, v); v.walk(visit) }
            is JSONArray -> v.walkArray(visit)
        }
    }
}

private fun JSONArray.walkArray(visit: (key: String, obj: JSONObject) -> Unit) {
    for (i in 0 until length()) {
        when (val v = opt(i)) {
            is JSONObject -> v.walk(visit)
            is JSONArray -> v.walkArray(visit)
        }
    }
}

/** Every JSONObject in the tree, including objects that sit directly in arrays (which [walk] skips). */
internal fun JSONObject.forEachObject(visit: (JSONObject) -> Unit) {
    visit(this)
    val keys = keys()
    while (keys.hasNext()) {
        when (val v = opt(keys.next())) {
            is JSONObject -> v.forEachObject(visit)
            is JSONArray -> v.forEachObjectInArray(visit)
        }
    }
}

private fun JSONArray.forEachObjectInArray(visit: (JSONObject) -> Unit) {
    for (i in 0 until length()) {
        when (val v = opt(i)) {
            is JSONObject -> v.forEachObject(visit)
            is JSONArray -> v.forEachObjectInArray(visit)
        }
    }
}

/** The first object stored under [key] anywhere below this one (depth first, as [walk] visits). */
internal fun JSONObject.findObject(key: String): JSONObject? {
    var found: JSONObject? = null
    walk { k, o -> if (found == null && k == key) found = o }
    return found
}

/** YouTube text: {"simpleText": "..."} or {"runs": [{"text": "..."}]}. */
internal fun JSONObject.text(key: String): String? {
    val o = optJSONObject(key) ?: return optString(key).ifBlank { null }
    o.optString("simpleText").ifBlank { null }?.let { return it }
    o.optString("content").ifBlank { null }?.let { return it }
    val runs = o.optJSONArray("runs") ?: return null
    return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }.ifBlank { null }
}

/** URLs from {"thumbnail": {"thumbnails": [...]}} or {"thumbnails": [...]}, smallest first. */
internal fun JSONObject.thumbnails(): List<String>? {
    val arr = optJSONObject("thumbnail")?.optJSONArray("thumbnails") ?: optJSONArray("thumbnails") ?: return null
    return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("url")?.ifBlank { null } }
}

private fun JSONObject.firstImageSource(): String? = collectStrings("url").firstOrNull { it.contains("ytimg") || it.contains("ggpht") }

internal fun JSONObject.bestImageSource(): String? =
    collectStrings("url").lastOrNull { it.contains("ytimg") || it.contains("ggpht") || it.contains("googleusercontent") }

internal fun upgradeThumbnail(url: String, id: String): String {
    if (url.contains("i.ytimg.com/vi/") || url.contains("img.youtube.com/vi/")) {
        return "https://i.ytimg.com/vi/$id/frame0.jpg"
    }
    if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
        return url.replace(Regex("=w\\d+-h\\d+[^\"&]*"), "=w720-h1280-p-k-no-nu")
            .replace(Regex("=s\\d+[^\"&]*"), "=w720-h1280-p-k-no-nu")
    }
    return url
}

/** Every string value stored under [field] anywhere below this object. */
internal fun JSONObject.collectStrings(field: String): List<String> {
    val out = mutableListOf<String>()
    fun visitObj(o: JSONObject) {
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            when (val v = o.opt(k)) {
                is String -> if (k == field && v.isNotBlank()) out += v
                is JSONObject -> visitObj(v)
                is JSONArray -> for (i in 0 until v.length()) (v.opt(i) as? JSONObject)?.let(::visitObj)
            }
        }
    }
    visitObj(this)
    return out
}

/** The Save dialog's playlists (`playlistAddToOptionRenderer`s, Watch later among them). */
internal fun parseSaveChoices(answer: JSONObject): List<PlaylistChoice> {
    val out = LinkedHashMap<String, PlaylistChoice>()
    answer.walk { key, obj ->
        if (key != "playlistAddToOptionRenderer") return@walk
        val id = obj.optString("playlistId").ifBlank { return@walk }
        out.getOrPut(id) {
            PlaylistChoice(
                id = id,
                title = obj.text("title") ?: "",
                contains = when (obj.optString("containsSelectedVideos")) {
                    "ALL" -> true
                    "NONE" -> false
                    else -> null
                },
                privacy = obj.optString("privacy").ifBlank { null },
            )
        }
    }
    return out.values.toList()
}
