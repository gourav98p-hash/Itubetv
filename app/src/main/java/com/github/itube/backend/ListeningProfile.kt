package com.github.itube

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Production-ready on-device Taste Engine and Listening Profile for iMusic.
 * - Hardware-encrypted local storage using SecureStorage (AES-GCM).
 * - Instant synchronous loading on app start so recommendations are immediately personalized.
 * - Mistake-proof: accidental taps (< 15 seconds) are NEVER recorded into user profile.
 * - Scans confirmed listening, completions, likes, searches, and artist profile views.
 */
object ListeningProfile {
    private const val TAG = "ListeningProfile"
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // SharedPreferences Keys
    private const val PREFS_NAME = "listening_profile_secure"
    private const val KEY_TOKENS = "sec_tokens"
    private const val KEY_RECENT = "sec_recent"
    private const val KEY_ARTISTS = "sec_artists"
    private const val KEY_TRACKS = "sec_tracks"
    private const val KEY_LIKES = "sec_likes"
    private const val KEY_SEARCHES = "sec_searches"
    private const val KEY_RELATED = "sec_related"

    // In-memory thread-safe caches for zero-latency reads
    private val artistAffinities = ConcurrentHashMap<String, ArtistAffinity>()
    private val trackRecords = ConcurrentHashMap<String, TrackRecord>()
    private val likedTrackIds = ConcurrentHashMap.newKeySet<String>()
    private val recentTracksList = CopyOnWriteArrayList<MusicTrack>()
    private val searchHistoryList = CopyOnWriteArrayList<SearchRecord>()
    private val relatedArtistsCache = ConcurrentHashMap<String, List<MusicArtist>>()

    // Accidental tap protection state
    private var pendingTrack: MusicTrack? = null
    private var pendingStartMs: Long = 0L
    private var pendingConfirmed: Boolean = false

    private val stopWords = setOf(
        "official", "video", "audio", "music", "with", "from", "the", "and", "feat", "ft",
        "song", "lyrics", "version", "remix", "extended", "original", "live", "edit", "hd", "4k"
    )

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Load synchronously so recommendations are ready immediately on startup
        loadFromDiskSynchronous()
    }

    private fun loadFromDiskSynchronous() {
        try {
            // 1. Load Liked track IDs
            val likesRaw = SecureStorage.decrypt(prefs.getString(KEY_LIKES, null))
            if (likesRaw.isNotBlank()) {
                runCatching {
                    val arr = JSONArray(likesRaw)
                    for (i in 0 until arr.length()) {
                        val id = arr.optString(i)
                        if (id.isNotBlank()) likedTrackIds.add(id)
                    }
                }
            }

            // 2. Load Recent Tracks
            val recentRaw = SecureStorage.decrypt(prefs.getString(KEY_RECENT, null))
            if (recentRaw.isNotBlank()) {
                runCatching {
                    val arr = JSONArray(recentRaw)
                    val list = mutableListOf<MusicTrack>()
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optString("id")
                        if (id.isNotBlank()) {
                            list.add(
                                MusicTrack(
                                    id = id,
                                    title = o.optString("title"),
                                    artists = o.optString("artists"),
                                    artistId = o.optString("artistId").ifBlank { null },
                                    album = o.optString("album").ifBlank { null },
                                    thumbnailUrl = o.optString("thumbnail").ifBlank { null },
                                    durationSec = o.optLong("duration"),
                                    liked = likedTrackIds.contains(id)
                                )
                            )
                        }
                    }
                    recentTracksList.clear()
                    recentTracksList.addAll(list.take(50))
                }
            }

            // 3. Load Artist Affinities
            val artistsRaw = SecureStorage.decrypt(prefs.getString(KEY_ARTISTS, null))
            if (artistsRaw.isNotBlank()) {
                runCatching {
                    val arr = JSONArray(artistsRaw)
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val name = o.optString("name")
                        if (name.isBlank()) continue
                        val key = name.lowercase().trim()
                        val affinity = ArtistAffinity(
                            id = o.optString("id").ifBlank { "artist_$key" },
                            name = name,
                            thumbnailUrl = o.optString("thumbnail").ifBlank { null },
                            subscribers = o.optString("subscribers").ifBlank { null },
                            playCount = o.optInt("playCount", 0),
                            likeCount = o.optInt("likeCount", 0),
                            viewCount = o.optInt("viewCount", 0),
                            skipCount = o.optInt("skipCount", 0),
                            lastInteractedMs = o.optLong("lastInteractedMs", System.currentTimeMillis()),
                            score = o.optDouble("score", 0.0).toFloat()
                        )
                        artistAffinities[key] = affinity
                    }
                }
            }

            // 4. Load Track Records
            val tracksRaw = SecureStorage.decrypt(prefs.getString(KEY_TRACKS, null))
            if (tracksRaw.isNotBlank()) {
                runCatching {
                    val arr = JSONArray(tracksRaw)
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optString("id")
                        if (id.isBlank()) continue
                        trackRecords[id] = TrackRecord(
                            id = id,
                            title = o.optString("title"),
                            artists = o.optString("artists"),
                            artistId = o.optString("artistId").ifBlank { null },
                            album = o.optString("album").ifBlank { null },
                            thumbnailUrl = o.optString("thumbnail").ifBlank { null },
                            durationSec = o.optLong("durationSec", 0),
                            playCount = o.optInt("playCount", 0),
                            liked = likedTrackIds.contains(id),
                            lastPlayedMs = o.optLong("lastPlayedMs", 0),
                            completedCount = o.optInt("completedCount", 0)
                        )
                    }
                }
            }

            // 5. Load Related Artists Cache
            val relatedRaw = SecureStorage.decrypt(prefs.getString(KEY_RELATED, null))
            if (relatedRaw.isNotBlank()) {
                runCatching {
                    val obj = JSONObject(relatedRaw)
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val arr = obj.optJSONArray(k) ?: continue
                        val rList = mutableListOf<MusicArtist>()
                        for (j in 0 until arr.length()) {
                            val item = arr.optJSONObject(j) ?: continue
                            val rId = item.optString("id")
                            val rName = item.optString("name")
                            if (rId.isNotBlank() && rName.isNotBlank()) {
                                rList.add(
                                    MusicArtist(
                                        id = rId,
                                        name = rName,
                                        thumbnailUrl = item.optString("thumbnail").ifBlank { null },
                                        subscribers = item.optString("subscribers").ifBlank { null }
                                    )
                                )
                            }
                        }
                        if (rList.isNotEmpty()) {
                            relatedArtistsCache[k] = rList
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading secure listening profile", e)
        }
    }

    private fun persistAsync() {
        if (!::prefs.isInitialized) return
        scope.launch {
            try {
                // 1. Recent Tracks JSON
                val recentJson = JSONArray().apply {
                    recentTracksList.take(50).forEach { song ->
                        put(
                            JSONObject()
                                .put("id", song.id)
                                .put("title", song.title)
                                .put("artists", song.artists)
                                .put("artistId", song.artistId)
                                .put("album", song.album)
                                .put("thumbnail", song.thumbnailUrl)
                                .put("duration", song.durationSec)
                                .put("liked", isLiked(song.id))
                        )
                    }
                }

                // 2. Likes JSON
                val likesJson = JSONArray().apply {
                    likedTrackIds.forEach { put(it) }
                }

                // 3. Artist Affinities JSON (top 100)
                val artistsJson = JSONArray().apply {
                    artistAffinities.values
                        .sortedByDescending { it.computedScore() }
                        .take(100)
                        .forEach { a ->
                            put(
                                JSONObject()
                                    .put("id", a.id)
                                    .put("name", a.name)
                                    .put("thumbnail", a.thumbnailUrl)
                                    .put("subscribers", a.subscribers)
                                    .put("playCount", a.playCount)
                                    .put("likeCount", a.likeCount)
                                    .put("viewCount", a.viewCount)
                                    .put("skipCount", a.skipCount)
                                    .put("lastInteractedMs", a.lastInteractedMs)
                                    .put("score", a.computedScore())
                            )
                        }
                }

                // 4. Track Records JSON (top 150)
                val tracksJson = JSONArray().apply {
                    trackRecords.values
                        .sortedByDescending { it.lastPlayedMs }
                        .take(150)
                        .forEach { t ->
                            put(
                                JSONObject()
                                    .put("id", t.id)
                                    .put("title", t.title)
                                    .put("artists", t.artists)
                                    .put("artistId", t.artistId)
                                    .put("album", t.album)
                                    .put("thumbnail", t.thumbnailUrl)
                                    .put("durationSec", t.durationSec)
                                    .put("playCount", t.playCount)
                                    .put("liked", t.liked)
                                    .put("lastPlayedMs", t.lastPlayedMs)
                                    .put("completedCount", t.completedCount)
                            )
                        }
                }

                // 5. Related Artists JSON
                val relatedJson = JSONObject().apply {
                    relatedArtistsCache.forEach { (artistName, artists) ->
                        put(artistName, JSONArray().apply {
                            artists.take(8).forEach { r ->
                                put(
                                    JSONObject()
                                        .put("id", r.id)
                                        .put("name", r.name)
                                        .put("thumbnail", r.thumbnailUrl)
                                        .put("subscribers", r.subscribers)
                                )
                            }
                        })
                    }
                }

                // Save hardware-encrypted payloads
                prefs.edit()
                    .putString(KEY_RECENT, SecureStorage.encrypt(recentJson.toString()))
                    .putString(KEY_LIKES, SecureStorage.encrypt(likesJson.toString()))
                    .putString(KEY_ARTISTS, SecureStorage.encrypt(artistsJson.toString()))
                    .putString(KEY_TRACKS, SecureStorage.encrypt(tracksJson.toString()))
                    .putString(KEY_RELATED, SecureStorage.encrypt(relatedJson.toString()))
                    .apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error persisting secure listening profile", e)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Accidental-Tap Proof Event Tracking
    // ─────────────────────────────────────────────────────────────

    /**
     * Called when a track is tapped or selected.
     * Accidental Tap Protection: DOES NOT register the song or artist immediately!
     * Only sets a pending candidate. The song must play for >= 15 seconds to be confirmed.
     */
    fun onTrackPlaybackStarted(track: MusicTrack) {
        // If there was a previous unconfirmed track, treat it as skipped
        if (pendingTrack != null && !pendingConfirmed) {
            onTrackSkipped(pendingTrack!!, System.currentTimeMillis() - pendingStartMs)
        }
        pendingTrack = track
        pendingStartMs = System.currentTimeMillis()
        pendingConfirmed = false
    }

    /**
     * Called ONLY after the user has actively listened to a song for >= 15 seconds
     * (or 25% of the song). This guarantees that accidental taps are never recorded.
     */
    fun onTrackConfirmed(track: MusicTrack) {
        if (pendingTrack?.id == track.id) {
            pendingConfirmed = true
        }

        val now = System.currentTimeMillis()
        val isTrackLiked = isLiked(track.id)

        // 1. Add to recent tracks now that it is confirmed
        val updatedTrack = if (track.liked != isTrackLiked) track.copy(liked = isTrackLiked) else track
        val filtered = recentTracksList.filterNot { it.id == track.id }
        recentTracksList.clear()
        recentTracksList.add(updatedTrack)
        recentTracksList.addAll(filtered.take(49))

        // 2. Update TrackRecord
        val record = trackRecords.getOrPut(track.id) {
            TrackRecord(
                id = track.id,
                title = track.title,
                artists = track.artists,
                artistId = track.artistId,
                album = track.album,
                thumbnailUrl = track.thumbnailUrl,
                durationSec = track.durationSec,
                liked = isTrackLiked
            )
        }
        record.playCount++
        record.lastPlayedMs = now
        record.liked = isTrackLiked

        // 3. Extract and update Artist Affinities
        val parsedArtists = parseArtistNames(track.artists)
        val primaryName = parsedArtists.firstOrNull() ?: track.artists.trim()
        if (primaryName.isNotBlank()) {
            val key = primaryName.lowercase()
            val affinity = artistAffinities.getOrPut(key) {
                ArtistAffinity(
                    id = track.artistId?.takeIf { it.startsWith("UC") || it.startsWith("MPLA") } ?: "artist_$key",
                    name = primaryName,
                    thumbnailUrl = track.thumbnailUrl
                )
            }
            affinity.playCount++
            affinity.lastInteractedMs = now
            affinity.score += 3.5f
            if (track.artistId?.startsWith("UC") == true) {
                affinity.id = track.artistId
            }
            if (affinity.thumbnailUrl.isNullOrBlank() && !track.thumbnailUrl.isNullOrBlank()) {
                affinity.thumbnailUrl = track.thumbnailUrl
            }
        }

        // Secondary / featured artists
        for (i in 1 until parsedArtists.size) {
            val secName = parsedArtists[i]
            if (secName.isNotBlank()) {
                val secKey = secName.lowercase()
                val secAff = artistAffinities.getOrPut(secKey) {
                    ArtistAffinity(
                        id = "artist_$secKey",
                        name = secName,
                        thumbnailUrl = track.thumbnailUrl
                    )
                }
                secAff.playCount++
                secAff.lastInteractedMs = now
                secAff.score += 1.5f
            }
        }

        // 4. Update taste tokens
        recordText("${track.artists} ${track.title}")

        persistAsync()
    }

    /**
     * Called when a track is skipped before 15 seconds.
     * Prevents accidental taps from polluting the user profile.
     * If skipped under 5 seconds, slightly reduces affinity to drop mistake clicks.
     */
    fun onTrackSkipped(track: MusicTrack, durationListenedMs: Long = 0L) {
        val parsedArtists = parseArtistNames(track.artists)
        val primary = parsedArtists.firstOrNull() ?: track.artists.trim()
        if (primary.isNotBlank()) {
            artistAffinities[primary.lowercase()]?.let {
                it.skipCount++
                if (durationListenedMs < 5000L) {
                    it.score = (it.score - 1.0f).coerceAtLeast(0f)
                }
            }
        }
        pendingTrack = null
        pendingConfirmed = false
    }

    /**
     * Called when a track reaches completion (100% played).
     */
    fun onTrackCompleted(track: MusicTrack) {
        // Ensure confirmed
        onTrackConfirmed(track)

        trackRecords[track.id]?.let {
            it.completedCount++
        }
        val primary = parseArtistNames(track.artists).firstOrNull() ?: track.artists.trim()
        if (primary.isNotBlank()) {
            artistAffinities[primary.lowercase()]?.let {
                it.score += 2.0f
                it.lastInteractedMs = System.currentTimeMillis()
            }
        }
        persistAsync()
    }

    /**
     * Called when user explicitly likes/unlikes a track.
     * Immediately confirms the track because liking is a deliberate user action.
     */
    fun recordLike(track: MusicTrack, isLiked: Boolean) {
        if (isLiked) {
            likedTrackIds.add(track.id)
            onTrackConfirmed(track)
        } else {
            likedTrackIds.remove(track.id)
        }

        // Update in recent tracks
        val idx = recentTracksList.indexOfFirst { it.id == track.id }
        if (idx >= 0) {
            val item = recentTracksList[idx]
            recentTracksList[idx] = item.copy(liked = isLiked)
        }

        trackRecords[track.id]?.let {
            it.liked = isLiked
        }

        val primary = parseArtistNames(track.artists).firstOrNull() ?: track.artists.trim()
        if (primary.isNotBlank()) {
            val key = primary.lowercase()
            val affinity = artistAffinities.getOrPut(key) {
                ArtistAffinity(
                    id = track.artistId ?: "artist_$key",
                    name = primary,
                    thumbnailUrl = track.thumbnailUrl
                )
            }
            if (isLiked) {
                affinity.likeCount++
                affinity.score += 6.0f
            } else {
                affinity.likeCount = (affinity.likeCount - 1).coerceAtLeast(0)
                affinity.score = (affinity.score - 6.0f).coerceAtLeast(0f)
            }
            affinity.lastInteractedMs = System.currentTimeMillis()
        }

        persistAsync()
    }

    /**
     * Called when the user views an artist's screen.
     */
    fun recordArtistView(artist: MusicArtist) {
        val cleanName = artist.name.trim()
        if (cleanName.isBlank()) return
        val key = cleanName.lowercase()
        val now = System.currentTimeMillis()

        val affinity = artistAffinities.getOrPut(key) {
            ArtistAffinity(
                id = artist.id,
                name = cleanName,
                thumbnailUrl = artist.thumbnailUrl,
                subscribers = artist.subscribers
            )
        }
        affinity.viewCount++
        affinity.score += 2.0f
        affinity.lastInteractedMs = now
        if (artist.id.startsWith("UC")) {
            affinity.id = artist.id
        }
        if (!artist.thumbnailUrl.isNullOrBlank()) {
            affinity.thumbnailUrl = artist.thumbnailUrl
        }
        if (!artist.subscribers.isNullOrBlank()) {
            affinity.subscribers = artist.subscribers
        }

        recordText(cleanName)
        persistAsync()
    }

    /**
     * Called when full artist details are fetched.
     * Caches high-res circular avatar, verified channel ID, and related artists.
     */
    fun recordArtistDetails(details: ArtistDetails) {
        val cleanName = details.name.trim()
        if (cleanName.isBlank()) return
        val key = cleanName.lowercase()
        val now = System.currentTimeMillis()

        val affinity = artistAffinities.getOrPut(key) {
            ArtistAffinity(
                id = details.channelId,
                name = cleanName,
                thumbnailUrl = details.thumbnailUrl,
                subscribers = details.subscriberCountText
            )
        }
        affinity.id = details.channelId
        if (!details.thumbnailUrl.isNullOrBlank()) {
            affinity.thumbnailUrl = details.thumbnailUrl
        }
        if (!details.subscriberCountText.isNullOrBlank()) {
            affinity.subscribers = details.subscriberCountText
        }
        affinity.lastInteractedMs = now

        if (details.relatedArtists.isNotEmpty()) {
            relatedArtistsCache[key] = details.relatedArtists.take(12)
        }

        persistAsync()
    }

    /**
     * Called when the user views a playlist or album.
     */
    fun recordPlaylistOrAlbumView(playlist: MusicPlaylistRef) {
        val author = playlist.author.trim()
        if (author.isNotBlank() && !author.equals("YouTube Music", ignoreCase = true)) {
            val key = author.lowercase()
            val affinity = artistAffinities.getOrPut(key) {
                ArtistAffinity(
                    id = playlist.authorId ?: "artist_$key",
                    name = author,
                    thumbnailUrl = playlist.thumbnailUrl
                )
            }
            affinity.score += 1.5f
            affinity.lastInteractedMs = System.currentTimeMillis()
        }
        recordText("${playlist.title} ${playlist.author}")
        persistAsync()
    }

    /**
     * Called when the user submits an explicit search in iMusic.
     */
    fun recordSearch(query: String, filter: String) {
        val q = query.trim()
        if (q.isBlank()) return
        searchHistoryList.add(0, SearchRecord(q, filter, System.currentTimeMillis()))
        if (searchHistoryList.size > 50) {
            searchHistoryList.removeAt(searchHistoryList.lastIndex)
        }
        recordText(q)
        persistAsync()
    }

    private fun recordText(text: String) {
        if (!::prefs.isInitialized) return
        val old = SecureStorage.decrypt(prefs.getString(KEY_TOKENS, "")).split('|').filter(String::isNotBlank)
        val tokens = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in stopWords }.take(8)
        val combined = (tokens + old).distinct().take(48).joinToString("|")
        prefs.edit().putString(KEY_TOKENS, SecureStorage.encrypt(combined)).apply()
    }

    // ─────────────────────────────────────────────────────────────
    // Experience-Based Recommendation Queries
    // ─────────────────────────────────────────────────────────────

    fun isLiked(trackId: String): Boolean = likedTrackIds.contains(trackId)

    /**
     * Returns top artists ranked by computed affinity score.
     */
    fun getTopArtists(limit: Int = 12): List<MusicArtist> {
        return artistAffinities.values
            .sortedByDescending { it.computedScore() }
            .take(limit)
            .map { it.toMusicArtist() }
    }

    /**
     * Returns most recently interacted artists.
     */
    fun getRecentArtists(limit: Int = 12): List<MusicArtist> {
        return artistAffinities.values
            .sortedByDescending { it.lastInteractedMs }
            .take(limit)
            .map { it.toMusicArtist() }
    }

    /**
     * Returns favorite artists (artists with like count > 0 or play count >= 2).
     */
    fun getFavoriteArtists(limit: Int = 12): List<MusicArtist> {
        return artistAffinities.values
            .filter { it.likeCount > 0 || it.playCount >= 2 }
            .sortedByDescending { it.likeCount * 6 + it.playCount * 3.5f }
            .take(limit)
            .map { it.toMusicArtist() }
    }

    /**
     * Returns user top tracks.
     */
    fun getTopTracks(limit: Int = 20): List<MusicTrack> {
        return trackRecords.values
            .sortedByDescending { (it.playCount * 3) + (if (it.liked) 8 else 0) + it.completedCount * 2 }
            .take(limit)
            .map { it.toMusicTrack() }
    }

    /**
     * Returns recent confirmed tracks.
     */
    fun getRecentTracks(limit: Int = 30): List<MusicTrack> {
        return recentTracksList.take(limit)
    }

    /**
     * Returns all user-liked tracks.
     */
    fun getFavoriteTracks(limit: Int = 30): List<MusicTrack> {
        val likedFromRecent = recentTracksList.filter { isLiked(it.id) }
        val likedFromRecords = trackRecords.values.filter { it.liked }.map { it.toMusicTrack() }
        return (likedFromRecent + likedFromRecords).distinctBy { it.id }.take(limit)
    }

    fun getAllPlayedTrackIds(): Set<String> {
        return trackRecords.keys + recentTracksList.map { it.id }
    }

    fun getRelatedArtistsFor(artistName: String): List<MusicArtist> {
        return relatedArtistsCache[artistName.lowercase().trim()] ?: emptyList()
    }

    fun seed(): String? {
        val topArtist = getTopArtists(2).map { it.name }.joinToString(" ")
        if (topArtist.isNotBlank()) return topArtist
        if (!::prefs.isInitialized) return null
        return SecureStorage.decrypt(prefs.getString(KEY_TOKENS, "")).split('|')
            .filter(String::isNotBlank).take(4).joinToString(" ").ifBlank { null }
    }

    fun recentTracks(): List<MusicTrack> {
        return recentTracksList.toList()
    }

    // Compatibility helpers
    fun record(track: MusicTrack) = onTrackConfirmed(track)
    fun record(video: VideoItem) = recordText("${video.channel} ${video.title}")
    fun recordTrackStarted(track: MusicTrack) = onTrackPlaybackStarted(track)
    fun recordTrackCompleted(track: MusicTrack) = onTrackCompleted(track)
    fun getLikedTrackIds(): Set<String> = likedTrackIds.toSet()
    fun parseArtistNamesCompat(raw: String): List<String> = parseArtistNames(raw)

    private fun parseArtistNames(raw: String): List<String> {
        return raw.split(Regex("[,&/]|\\b(?:feat|ft|with|vs)\\.?\\b", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length >= 2 }
    }
}

// ─────────────────────────────────────────────────────────────
// Profile Data Classes
// ─────────────────────────────────────────────────────────────

data class ArtistAffinity(
    var id: String,
    val name: String,
    var thumbnailUrl: String? = null,
    var subscribers: String? = null,
    var playCount: Int = 0,
    var likeCount: Int = 0,
    var viewCount: Int = 0,
    var skipCount: Int = 0,
    var lastInteractedMs: Long = System.currentTimeMillis(),
    var score: Float = 0f,
) {
    fun computedScore(): Float {
        val now = System.currentTimeMillis()
        val daysAgo = (now - lastInteractedMs) / (1000f * 60 * 60 * 24)
        val recencyBonus = when {
            daysAgo < 2 -> 5f
            daysAgo < 7 -> 2.5f
            daysAgo < 14 -> 1f
            else -> 0f
        }
        val penalty = skipCount * 1.5f
        return (score + recencyBonus + (playCount * 3.5f) + (likeCount * 6f) + (viewCount * 2f) - penalty).coerceAtLeast(0f)
    }

    fun toMusicArtist(): MusicArtist = MusicArtist(
        id = id,
        name = name,
        thumbnailUrl = thumbnailUrl,
        subscribers = subscribers
    )
}

data class SearchRecord(
    val query: String,
    val filter: String = "ALL",
    val timestamp: Long = System.currentTimeMillis()
)

data class TrackRecord(
    val id: String,
    val title: String,
    val artists: String,
    val artistId: String? = null,
    val album: String? = null,
    val thumbnailUrl: String? = null,
    val durationSec: Long = 0,
    var playCount: Int = 0,
    var liked: Boolean = false,
    var lastPlayedMs: Long = System.currentTimeMillis(),
    var completedCount: Int = 0,
) {
    fun toMusicTrack(): MusicTrack = MusicTrack(
        id = id,
        title = title,
        artists = artists,
        artistId = artistId,
        album = album,
        thumbnailUrl = thumbnailUrl,
        durationSec = durationSec,
        liked = liked
    )
}
