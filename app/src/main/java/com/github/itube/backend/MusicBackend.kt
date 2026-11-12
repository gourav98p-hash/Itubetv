package com.github.itube

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object MusicBackend {
    private const val TAG = "MusicBackend"
    private const val BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
    private const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val NEXT_URL = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false"
    private const val SONG_PARAMS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
    private const val ARTIST_PARAMS = "EgWKAQIgAWoKEAkQBRAKEAMQBA%3D%3D"
    private const val ALBUM_PARAMS = "EgWKAQIYAWoKEAkQBRAKEAMQBA%3D%3D"
    private const val PLAYLIST_PARAMS = "EgWKAQIoAWoKEAkQBRAKEAMQBA%3D%3D"
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchMusicHome(chipFilter: String? = null): MusicHomeFeed = withContext(Dispatchers.IO) {
        val chips = mutableListOf<String>()
        val quickPicks = mutableListOf<MusicTrack>()
        val sections = mutableListOf<MusicSection>()

        val communityPlaylists = mutableListOf<MusicPlaylistRef>()
        val exploreArtists = mutableListOf<MusicArtist>()
        val homeArtists = mutableListOf<MusicArtist>()
        val fromArtistsYouLove = mutableListOf<MusicPlaylistRef>()
        val newReleases = mutableListOf<MusicPlaylistRef>()
        val mixedForYou = mutableListOf<MusicPlaylistRef>()
        val topAlbums = mutableListOf<MusicPlaylistRef>()
        val trendingSongs = mutableListOf<MusicTrack>()
        val chartTopArtists = mutableListOf<MusicArtist>()

        // Recent listening profile & User history
        val recentTracks = ListeningProfile.recentTracks().take(18)
        val userTopArtists = ListeningProfile.getTopArtists(12)
        val userRecentArtists = ListeningProfile.getRecentArtists(12)
        val userFavoriteArtists = ListeningProfile.getFavoriteArtists(12)
        val userTopTracks = ListeningProfile.getTopTracks(16)
        val userRecentTracks = ListeningProfile.getRecentTracks(16)

        val personalQuery = chipFilter?.let { "$it music" } ?: ListeningProfile.seed()

        if (recentTracks.isNotEmpty()) {
            sections += MusicSection(
                title = "Recently played",
                label = "YOUR MUSIC",
                thumbnail = recentTracks.firstOrNull()?.thumbnailUrl,
                items = recentTracks,
            )
        }

        if (!personalQuery.isNullOrBlank()) {
            runCatching { searchMusic(personalQuery) }.getOrDefault(emptyList()).takeIf { it.isNotEmpty() }?.let { tracks ->
                sections += MusicSection(
                    title = if (chipFilter != null) "$chipFilter mix" else "Made for you",
                    label = if (chipFilter != null) "MOOD & GENRE" else "FROM YOUR LISTENING",
                    thumbnail = tracks.firstOrNull()?.thumbnailUrl,
                    items = tracks,
                )
                quickPicks += tracks.take(16)
            }
        }

        // Fetch YouTube Music's Explore
        try {
            val exploreJson = postInnerTube("FEmusic_explore")
            parseShelves(exploreJson, sections, quickPicks, communityPlaylists, exploreArtists, newReleases, topAlbums)
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching FEmusic_explore: ${e.message}")
        }

        // Fetch YouTube Music's Charts (top real artists with circular photos)
        try {
            val chartsJson = postInnerTube("FEmusic_charts")
            parseShelves(chartsJson, sections, quickPicks, communityPlaylists, chartTopArtists, newReleases, topAlbums)
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching FEmusic_charts: ${e.message}")
        }

        // Fetch YouTube Music's Home
        try {
            val homeJson = postInnerTube("FEmusic_home")
            parseChips(homeJson, chips)
            parseShelves(homeJson, sections, quickPicks, communityPlaylists, homeArtists, mixedForYou, fromArtistsYouLove)
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching FEmusic_home: ${e.message}")
        }

        // Default chips if none parsed
        if (chips.isEmpty()) {
            chips.addAll(listOf("Relax", "Workout", "Energize", "Focus", "Party", "Romance", "Chill", "Feel Good"))
        }

        // Popular / trending tracks
        val allParsedTracks = sections.flatMap { it.items }
        trendingSongs += allParsedTracks.distinctBy { it.id }.take(20)

        // ── 1. Popular Artists (Real chart toppers & trending artists) ──
        val popularArtists = (chartTopArtists + exploreArtists + homeArtists)
            .distinctBy { it.name.trim().lowercase() }
            .take(12)

        val popularNamesSet = popularArtists.map { it.name.trim().lowercase() }.toSet()

        // ── 2. Artists For You (Genuine Personalization from User Profile) ──
        val artistsForYouList = mutableListOf<MusicArtist>()

        if (userTopArtists.isNotEmpty()) {
            // Include user's favorite, top, and recent artists
            val userSeeds = (userFavoriteArtists + userTopArtists + userRecentArtists)
                .distinctBy { it.name.trim().lowercase() }
            artistsForYouList.addAll(userSeeds)

            // Expand with real related artists for top 2 user artists
            for (seed in userTopArtists.take(2)) {
                val cached = ListeningProfile.getRelatedArtistsFor(seed.name)
                if (cached.isNotEmpty()) {
                    artistsForYouList.addAll(cached)
                } else if (seed.id.startsWith("UC")) {
                    runCatching {
                        fetchArtistDetails(seed.id)?.relatedArtists
                    }.getOrNull()?.let { related ->
                        if (related.isNotEmpty()) {
                            artistsForYouList.addAll(related)
                        }
                    }
                }
            }
        }

        // If user is new or has few artists, provide curated discovery artists that NEVER duplicate popular artists!
        val distinctCurated = (exploreArtists + homeArtists)
            .filterNot { it.name.trim().lowercase() in popularNamesSet }

        if (artistsForYouList.size < 12) {
            artistsForYouList.addAll(distinctCurated)
        }

        // Fallback diverse genres if still short of 12 (guarantees variety without duplicating popular artists)
        if (artistsForYouList.size < 8) {
            runCatching {
                val indieArtists = searchArtists("Indie").take(6)
                artistsForYouList.addAll(indieArtists.filterNot { it.name.trim().lowercase() in popularNamesSet })
            }
        }

        val finalArtistsForYou = artistsForYouList
            .distinctBy { it.name.trim().lowercase() }
            .take(12)

        // ── 3. From Artists You Love (Real releases from user's loved artists) ──
        if (userTopArtists.isNotEmpty()) {
            val userArtistReleases = mutableListOf<MusicPlaylistRef>()
            for (artist in userTopArtists.take(2)) {
                runCatching {
                    searchAlbums(artist.name).take(3)
                }.getOrNull()?.let { albums ->
                    userArtistReleases.addAll(albums)
                }
            }
            if (userArtistReleases.isNotEmpty()) {
                fromArtistsYouLove.clear()
                fromArtistsYouLove.addAll(userArtistReleases)
            }
        }

        // ── 4. Mixed For You (Personalized mixes based on user taste) ──
        if (userTopArtists.isNotEmpty()) {
            val personalMixes = mutableListOf<MusicPlaylistRef>()
            val top1 = userTopArtists.getOrNull(0)
            val top2 = userTopArtists.getOrNull(1)
            if (top1 != null) {
                personalMixes.add(
                    MusicPlaylistRef(
                        id = "RDAMVM_${top1.id}",
                        title = "${top1.name} Mix",
                        author = "Made for you",
                        thumbnailUrl = top1.thumbnailUrl,
                        isAlbum = false
                    )
                )
            }
            if (top2 != null) {
                personalMixes.add(
                    MusicPlaylistRef(
                        id = "RDAMVM_${top2.id}",
                        title = "${top2.name} Mix",
                        author = "Made for you",
                        thumbnailUrl = top2.thumbnailUrl,
                        isAlbum = false
                    )
                )
            }
            personalMixes.add(
                MusicPlaylistRef(
                    id = "RDAMVM_discover",
                    title = "Discover Mix",
                    author = "Daily discovery",
                    thumbnailUrl = top1?.thumbnailUrl ?: mixedForYou.firstOrNull()?.thumbnailUrl,
                    isAlbum = false
                )
            )
            mixedForYou.addAll(0, personalMixes)
        }

        // ── 5. Speed Dial (User's top & recent tracks first) ──
        val speedDial = (userTopTracks + userRecentTracks + recentTracks + quickPicks + allParsedTracks)
            .distinctBy { it.id }
            .take(18)

        // ── 6. Quick Picks (Radio picks from last track + user favorites) ──
        val lastTrack = userRecentTracks.firstOrNull() ?: recentTracks.firstOrNull()
        val radioPicks = if (lastTrack != null) {
            runCatching { relatedTracks(lastTrack).take(8) }.getOrDefault(emptyList())
        } else emptyList()

        val finalQuickPicks = (radioPicks + quickPicks + userTopTracks + allParsedTracks)
            .distinctBy { it.id }
            .take(16)

        // ── 7. Daily Discover (Unheard songs tailored to taste) ──
        val playedTrackIds = ListeningProfile.getAllPlayedTrackIds()
        val freshTracks = allParsedTracks.filterNot { it.id in playedTrackIds }
        val dailyDiscover = (freshTracks.shuffled() + allParsedTracks.shuffled())
            .distinctBy { it.id }
            .take(12)

        // ── 8. Favorite Artist Mix (Tracks from #1 favorite artist) ──
        val favoriteArtistMix = if (userTopArtists.isNotEmpty()) {
            val favArtist = userTopArtists.first()
            val tracks = runCatching { searchMusic(favArtist.name).take(12) }.getOrDefault(emptyList())
            if (tracks.isNotEmpty()) tracks else allParsedTracks.take(10)
        } else if (popularArtists.isNotEmpty()) {
            val topChartArtist = popularArtists.first()
            val tracks = runCatching { searchMusic(topChartArtist.name).take(12) }.getOrDefault(emptyList())
            if (tracks.isNotEmpty()) tracks else allParsedTracks.take(10)
        } else {
            allParsedTracks.take(10)
        }

        // Energize Vibe & More From
        val energizeVibe = allParsedTracks.shuffled().take(12)
        val moreFrom = allParsedTracks.takeLast(12)

        MusicHomeFeed(
            chips = chips.distinct(),
            speedDial = speedDial,
            quickPicks = finalQuickPicks,
            dailyDiscover = dailyDiscover,
            communityPlaylists = communityPlaylists.distinctBy { it.id }.take(12),
            moodsAndGenres = fetchMoodsAndGenres(),
            artistsForYou = finalArtistsForYou,
            fromArtistsYouLove = fromArtistsYouLove.distinctBy { it.id }.take(12),
            newReleases = newReleases.distinctBy { it.id }.take(12),
            mixedForYou = mixedForYou.distinctBy { it.id }.take(12),
            popularArtists = popularArtists,
            topAlbums = topAlbums.distinctBy { it.id }.take(12),
            favoriteArtistMix = favoriteArtistMix,
            moreFrom = moreFrom,
            energizeVibe = energizeVibe,
            trendingSongs = trendingSongs,
            chartTopArtists = chartTopArtists.distinctBy { it.id }.take(10),
            sections = sections.filter { it.items.isNotEmpty() }.distinctBy { it.title }
        )
    }

    /** Returns all available Moods and Genres with colorful stripes. */
    fun fetchMoodsAndGenres(): List<MoodCategory> {
        return listOf(
            MoodCategory("Chill", 0xFF81C784),
            MoodCategory("Workout", 0xFFFF8A65),
            MoodCategory("Energize", 0xFFFFD54F),
            MoodCategory("Focus", 0xFF64B5F6),
            MoodCategory("Party", 0xFFBA68C8),
            MoodCategory("Romance", 0xFFF06292),
            MoodCategory("Sleep", 0xFF7986CB),
            MoodCategory("Feel Good", 0xFF4DB6AC),
            MoodCategory("Sad & Emotional", 0xFF90A4AE),
            MoodCategory("Commute", 0xFFA1887F),
            MoodCategory("Pop Hits", 0xFFFF4081),
            MoodCategory("Hip-Hop & Rap", 0xFFFF6E40),
            MoodCategory("Rock Classics", 0xFFE57373),
            MoodCategory("Electronic & Dance", 0xFF00E676),
            MoodCategory("R&B & Soul", 0xFFAB47BC),
            MoodCategory("Indie & Alternative", 0xFF26A69A),
            MoodCategory("Jazz & Blues", 0xFFFFB74D),
            MoodCategory("Classical Focus", 0xFF5C6BC0),
            MoodCategory("Country Roads", 0xFF8D6E63),
            MoodCategory("Heavy Metal", 0xFF78909C),
        )
    }

    /**
     * Fetches detailed artist profile matching Flow's ArtistPage:
     * Hero photo, name, subscribers, monthly listeners, bio, top tracks, albums, singles, videos, and related artists.
     *
     * When [channelId] is NOT a valid YTM browse ID (UC* or MPLA*), it's treated as an artist
     * name and we search for the real channel ID first, then browse it.
     */
    suspend fun fetchArtistDetails(channelId: String): ArtistDetails? = withContext(Dispatchers.IO) {
        try {
            // Resolve real browseId if channelId looks like a name rather than a YTM ID
            val resolvedId: String = if (channelId.startsWith("UC") || channelId.startsWith("MPLA")) {
                channelId
            } else {
                // Search YTM for this artist name and get the real channel ID
                resolveArtistChannelId(channelId) ?: channelId
            }
            val root = postInnerTube(resolvedId)
            val header = root.optJSONObject("header")?.optJSONObject("musicImmersiveHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicVisualHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicHeaderRenderer")

            val name = header?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
            val thumb = header?.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                ?.let { thumbs -> thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") }
                ?.highQualityThumbnail()
                ?: header?.optJSONObject("foregroundThumbnail")?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    ?.let { thumbs -> thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") }
                    ?.highQualityThumbnail()

            val subs = header?.optJSONObject("subscriptionButton")?.optJSONObject("subscribeButtonRenderer")
                ?.optJSONObject("subscriberCountText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: header?.optJSONObject("subscriptionButton")?.optJSONObject("subscribeButtonRenderer")
                    ?.optJSONObject("subscriberCountWithSubscribeText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")

            val monthlyListeners = header?.optJSONObject("monthlyListenersText")?.optJSONArray("runs")
                ?.optJSONObject(0)?.optString("text")

            val bio = header?.optJSONObject("description")?.optJSONArray("runs")
                ?.optJSONObject(0)?.optString("text").orEmpty()

            val topTracks = mutableListOf<MusicTrack>()
            val albums = mutableListOf<MusicPlaylistRef>()
            val singles = mutableListOf<MusicPlaylistRef>()
            val videos = mutableListOf<MusicTrack>()
            val relatedArtists = mutableListOf<MusicArtist>()
            val featuredOn = mutableListOf<MusicPlaylistRef>()

            root.walk { key, shelf ->
                if (key == "musicShelfRenderer") {
                    val shelfTitle = shelf.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                    val contents = shelf.optJSONArray("contents") ?: return@walk
                    for (i in 0 until contents.length()) {
                        val obj = contents.optJSONObject(i) ?: continue
                        parseTrackItem(obj)?.let { topTracks.add(it) }
                    }
                } else if (key == "musicCarouselShelfRenderer") {
                    val cHeader = shelf.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    val cTitle = cHeader?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                    val contents = shelf.optJSONArray("contents") ?: return@walk

                    for (i in 0 until contents.length()) {
                        val itemObj = contents.optJSONObject(i) ?: continue
                        val artist = parseArtistItem(itemObj)
                        val playlist = parsePlaylistItem(itemObj)
                        val track = parseTrackItem(itemObj)

                        when {
                            cTitle.contains("Album", ignoreCase = true) && playlist != null -> albums.add(playlist.copy(isAlbum = true))
                            cTitle.contains("Single", ignoreCase = true) && playlist != null -> singles.add(playlist.copy(isAlbum = true))
                            cTitle.contains("Video", ignoreCase = true) && track != null -> videos.add(track.copy(isVideo = true))
                            cTitle.contains("Featured", ignoreCase = true) && playlist != null -> featuredOn.add(playlist)
                            (cTitle.contains("Fans", ignoreCase = true) || cTitle.contains("Similar", ignoreCase = true)) && artist != null -> relatedArtists.add(artist)
                            playlist != null -> albums.add(playlist)
                        }
                    }
                }
            }

            // If name came back blank (bad browseId), use the original channelId if it looks like a name
            val displayName = name.ifBlank {
                if (!channelId.startsWith("UC") && !channelId.startsWith("MPLA")) channelId else "Artist"
            }

            // Fallback if top songs were empty - search by artist name
            if (topTracks.isEmpty() && displayName.isNotBlank() && displayName != "Artist") {
                val fallbackTracks = searchMusic(displayName).take(10)
                topTracks.addAll(fallbackTracks)
            }

            ArtistDetails(
                channelId = resolvedId,
                name = displayName,
                thumbnailUrl = thumb,
                bannerUrl = thumb,
                subscriberCountText = subs,
                monthlyListenersText = monthlyListeners,
                description = bio,
                topTracks = topTracks.distinctBy { it.id }.take(10),
                albums = albums.distinctBy { it.id },
                singles = singles.distinctBy { it.id },
                videos = videos.distinctBy { it.id },
                relatedArtists = relatedArtists.distinctBy { it.id },
                featuredOn = featuredOn.distinctBy { it.id },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching artist details: ${e.message}", e)
            null
        }
    }

    /**
     * Fetches detailed playlist or album metadata and song list matching Flow's MusicCollectionScreen.
     */
    suspend fun fetchPlaylistDetails(playlistId: String, isAlbum: Boolean = false): PlaylistDetails? = withContext(Dispatchers.IO) {
        try {
            val browseId = if (isAlbum || playlistId.startsWith("MPRE") || playlistId.startsWith("VL")) playlistId else "VL$playlistId"
            val root = postInnerTube(browseId)

            val header = root.optJSONObject("header")?.optJSONObject("musicResponsiveHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicDetailHeaderRenderer")

            val title = header?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
            val thumb = header?.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                ?.let { thumbs -> thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") }
                ?.highQualityThumbnail()

            val straplineRuns = header?.optJSONObject("straplineTextOne")?.optJSONArray("runs")
                ?: header?.optJSONObject("subtitle")?.optJSONArray("runs")
            val author = straplineRuns?.optJSONObject(0)?.optString("text").orEmpty()
            val authorId = straplineRuns?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")?.optString("browseId")

            val secondSubtitle = header?.optJSONObject("secondSubtitle")?.optJSONArray("runs")
            val durationText = secondSubtitle?.optJSONObject(0)?.optString("text")

            val desc = header?.optJSONObject("description")?.optJSONObject("musicDescriptionShelfRenderer")
                ?.optJSONObject("description")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")

            val tracks = mutableListOf<MusicTrack>()
            root.walk { key, shelf ->
                if (key == "musicPlaylistShelfRenderer" || key == "musicShelfRenderer") {
                    val contents = shelf.optJSONArray("contents") ?: return@walk
                    for (i in 0 until contents.length()) {
                        val itemObj = contents.optJSONObject(i) ?: continue
                        parseTrackItem(itemObj)?.let { tracks.add(it) }
                    }
                }
            }

            PlaylistDetails(
                id = playlistId,
                title = title.ifBlank { if (isAlbum) "Album" else "Playlist" },
                thumbnailUrl = thumb,
                author = author.ifBlank { "YouTube Music" },
                authorId = authorId,
                trackCount = tracks.size,
                durationText = durationText,
                description = desc,
                isAlbum = isAlbum,
                tracks = tracks,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlist details: ${e.message}", e)
            null
        }
    }

    /**
     * Official YouTube Music Radio queue fetching using the official /v1/next endpoint.
     * Parses authentic playlistPanelVideoRenderer items.
     */
    suspend fun fetchRadioQueue(videoId: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()
        try {
            val root = postInnerTubeNext(videoId)
            root.walk { key, itemObj ->
                if (key == "playlistPanelVideoRenderer") {
                    parsePlaylistPanelVideo(itemObj)?.let {
                        if (it.id != videoId) tracks.add(it)
                    }
                } else if (key == "musicResponsiveListItemRenderer") {
                    val wrapper = JSONObject().put(key, itemObj)
                    parseTrackItem(wrapper)?.let {
                        if (it.id != videoId) tracks.add(it)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchRadioQueue failed for videoId $videoId: ${e.message}")
        }
        tracks.distinctBy { it.id }
    }

    /**
     * Builds an authentic, user-experience continuous radio queue for the playing song.
     * Uses YouTube Music's algorithmic radio, enriched with user artist affinities,
     * completely eliminating random search text spam.
     */
    suspend fun buildUserExperienceQueue(seedTrack: MusicTrack): List<MusicTrack> = withContext(Dispatchers.IO) {
        val queue = mutableListOf<MusicTrack>()

        // 1. Fetch official YouTube Music algorithmic radio queue
        val officialRadio = runCatching { fetchRadioQueue(seedTrack.id) }.getOrDefault(emptyList())
        if (officialRadio.isNotEmpty()) {
            queue.addAll(officialRadio)
        }

        // 2. If official radio gave fewer than 8 tracks, supplement with artist's top tracks (NOT raw text search!)
        if (queue.size < 8 && !seedTrack.artistId.isNullOrBlank()) {
            runCatching {
                fetchArtistDetails(seedTrack.artistId)?.topTracks
            }.getOrNull()?.let { topTracks ->
                val remaining = topTracks.filter { it.id != seedTrack.id && it.id !in queue.map { q -> q.id } }
                queue.addAll(remaining)
            }
        }

        // 3. User Experience Biasing:
        // Rank / interleave tracks by user's confirmed artist affinities
        val topAffinities = ListeningProfile.getTopArtists(8).map { it.name.trim().lowercase() }.toSet()
        val likedTrackIds = ListeningProfile.getLikedTrackIds()

        val personalizedQueue = queue.map { track ->
            val isLiked = track.id in likedTrackIds || ListeningProfile.isLiked(track.id)
            if (isLiked && !track.liked) track.copy(liked = true) else track
        }.sortedWith(
            compareByDescending<MusicTrack> { track ->
                val parsed = ListeningProfile.parseArtistNamesCompat(track.artists)
                val matchesAffinity = parsed.any { it.trim().lowercase() in topAffinities }
                if (matchesAffinity) 2 else 0
            }.thenByDescending { it.liked }
        )

        val finalQueue = (listOf(seedTrack) + personalizedQueue)
            .distinctBy { it.id }
            .take(50)

        finalQueue.filter { it.id != seedTrack.id }
    }

    /** Builds an authentic radio queue from the current song, excluding the playing song. */
    suspend fun relatedTracks(track: MusicTrack): List<MusicTrack> = buildUserExperienceQueue(track)

    private fun postInnerTubeNext(videoId: String, playlistId: String? = null): JSONObject {
        val clientObj = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", "1.20240101.01.00")
            .put("hl", "en")
            .put("gl", "US")

        val body = JSONObject()
            .put("videoId", videoId)
            .put("isAudioOnly", true)
            .put("context", JSONObject().put("client", clientObj))

        if (!playlistId.isNullOrBlank()) {
            body.put("playlistId", playlistId)
        } else {
            body.put("playlistId", "RDAMVM$videoId")
        }

        val request = Request.Builder()
            .url(NEXT_URL)
            .post(body.toString().toRequestBody(JSON_TYPE))
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("X-Youtube-Client-Name", "67")
            .header("X-Youtube-Client-Version", "1.20240101.01.00")
            .build()

        Net.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("InnerTube next request failed: ${response.code}")
            }
            val text = response.body?.string().orEmpty()
            return JSONObject(text)
        }
    }

    private fun parsePlaylistPanelVideo(panel: JSONObject): MusicTrack? {
        val videoId = panel.optString("videoId")
        if (videoId.isNullOrBlank()) return null

        val title = panel.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
        val bylineRuns = panel.optJSONObject("longBylineText")?.optJSONArray("runs")
            ?: panel.optJSONObject("shortBylineText")?.optJSONArray("runs")

        var artists = ""
        var artistId: String? = null
        var album: String? = null
        var albumId: String? = null

        if (bylineRuns != null && bylineRuns.length() > 0) {
            val nonDelimiterRuns = mutableListOf<Pair<String, String?>>()
            for (r in 0 until bylineRuns.length()) {
                val runObj = bylineRuns.optJSONObject(r)
                val t = runObj?.optString("text").orEmpty().trim()
                val bId = runObj?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                if (t.isNotEmpty() && t != "•" && t != "·" && !t.contains("views") && !t.contains("likes")) {
                    nonDelimiterRuns.add(t to bId)
                }
            }
            if (nonDelimiterRuns.isNotEmpty()) {
                artists = nonDelimiterRuns[0].first
                artistId = nonDelimiterRuns[0].second
                if (nonDelimiterRuns.size > 1) {
                    album = nonDelimiterRuns[1].first
                    albumId = nonDelimiterRuns[1].second
                }
            }
        }

        val lenText = panel.optJSONObject("lengthText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
        val durationSec = if (lenText.isNotBlank()) parseTimeString(lenText) else 0L

        val thumbUrl = panel.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.let { thumbs ->
                thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
            }?.highQualityThumbnail()

        return MusicTrack(
            id = videoId,
            title = title.ifBlank { "Unknown Title" },
            artists = artists.ifBlank { "Various Artists" },
            artistId = artistId,
            album = album,
            albumId = albumId,
            thumbnailUrl = thumbUrl,
            durationSec = durationSec
        )
    }

    private fun postInnerTube(browseId: String, params: String? = null): JSONObject {
        val clientObj = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", "1.20240101.01.00")
            .put("hl", "en")
            .put("gl", "US")

        val body = JSONObject()
            .put("browseId", browseId)
            .put("context", JSONObject().put("client", clientObj))

        if (params != null) {
            body.put("params", params)
        }

        val request = Request.Builder()
            .url(BROWSE_URL)
            .post(body.toString().toRequestBody(JSON_TYPE))
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("X-Youtube-Client-Name", "67")
            .header("X-Youtube-Client-Version", "1.20240101.01.00")
            .build()

        Net.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("InnerTube request failed: ${response.code}")
            }
            val text = response.body?.string().orEmpty()
            return JSONObject(text)
        }
    }

    private fun parseChips(root: JSONObject, outChips: MutableList<String>) {
        val contents = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer") ?: return

        val headerChips = contents.optJSONObject("header")
            ?.optJSONObject("chipCloudRenderer")
            ?.optJSONArray("chips") ?: return

        for (i in 0 until headerChips.length()) {
            val chip = headerChips.optJSONObject(i)?.optJSONObject("chipCloudChipRenderer")
            val text = chip?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            if (!text.isNullOrBlank()) {
                outChips.add(text)
            }
        }
    }

    private fun parseShelves(
        root: JSONObject,
        outSections: MutableList<MusicSection>,
        outQuickPicks: MutableList<MusicTrack>,
        outPlaylists: MutableList<MusicPlaylistRef>? = null,
        outArtists: MutableList<MusicArtist>? = null,
        outNewReleases: MutableList<MusicPlaylistRef>? = null,
        outAlbums: MutableList<MusicPlaylistRef>? = null,
    ) {
        root.walk { key, shelfRenderer ->
            if (key != "musicCarouselShelfRenderer") return@walk
            val header = shelfRenderer.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
            val title = header?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
            val label = header?.optJSONObject("strapline")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            val itemsArray = shelfRenderer.optJSONArray("contents") ?: return@walk

            val tracks = mutableListOf<MusicTrack>()
            for (j in 0 until itemsArray.length()) {
                val itemObj = itemsArray.optJSONObject(j) ?: continue
                val track = parseTrackItem(itemObj)
                if (track != null) {
                    tracks.add(track)
                }
                val artist = parseArtistItem(itemObj)
                if (artist != null && outArtists != null) {
                    outArtists.add(artist)
                }
                val playlist = parsePlaylistItem(itemObj)
                if (playlist != null) {
                    if (playlist.isAlbum && outAlbums != null) {
                        outAlbums.add(playlist)
                    } else if (title.contains("New", ignoreCase = true) && outNewReleases != null) {
                        outNewReleases.add(playlist)
                    } else if (outPlaylists != null) {
                        outPlaylists.add(playlist)
                    }
                }
            }

            if (tracks.isNotEmpty()) {
                val isQuickPickCandidate = title.contains("Quick pick", ignoreCase = true) ||
                        title.contains("Trending", ignoreCase = true) ||
                        title.contains("Hits", ignoreCase = true)
                if (isQuickPickCandidate && outQuickPicks.isEmpty()) {
                    outQuickPicks.addAll(tracks)
                }
                outSections.add(
                    MusicSection(
                        title = title,
                        label = label,
                        thumbnail = tracks.firstOrNull()?.thumbnailUrl,
                        items = tracks
                    )
                )
            }
        }
    }

    private fun parseArtistItem(itemObj: JSONObject): MusicArtist? {
        val twoRow = itemObj.optJSONObject("musicTwoRowItemRenderer")
        if (twoRow != null) {
            val nav = twoRow.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint") ?: return null
            val browseId = nav.optString("browseId")
            val pageType = nav.optJSONObject("browseEndpointContextSupportedConfigs")
                ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType")

            if (pageType != "MUSIC_PAGE_TYPE_ARTIST" && !browseId.startsWith("UC")) return null

            val name = twoRow.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
            val subs = twoRow.optJSONObject("subtitle")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            val thumbUrl = twoRow.optJSONObject("thumbnailRenderer")
                ?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")?.let { thumbs ->
                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                }?.highQualityThumbnail()

            return MusicArtist(
                id = browseId,
                name = name.ifBlank { "Artist" },
                thumbnailUrl = thumbUrl,
                subscribers = subs,
            )
        }

        val row = itemObj.optJSONObject("musicResponsiveListItemRenderer")
        if (row != null) {
            val nav = row.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                ?: row.optJSONArray("flexColumns")?.optJSONObject(0)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)
                    ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                ?: return null

            val browseId = nav.optString("browseId")
            val pageType = nav.optJSONObject("browseEndpointContextSupportedConfigs")
                ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType")

            if (pageType != "MUSIC_PAGE_TYPE_ARTIST" && !browseId.startsWith("UC")) return null

            val flexCols = row.optJSONArray("flexColumns")
            val name = flexCols?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
            val subs = flexCols?.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")

            val thumbUrl = row.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")?.let { thumbs ->
                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                }?.highQualityThumbnail()

            return MusicArtist(
                id = browseId,
                name = name.ifBlank { "Artist" },
                thumbnailUrl = thumbUrl,
                subscribers = subs,
            )
        }

        return null
    }

    private fun parsePlaylistItem(itemObj: JSONObject): MusicPlaylistRef? {
        val twoRow = itemObj.optJSONObject("musicTwoRowItemRenderer")
        if (twoRow != null) {
            val nav = twoRow.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint") ?: return null
            val browseId = nav.optString("browseId")
            val pageType = nav.optJSONObject("browseEndpointContextSupportedConfigs")
                ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType")

            val isAlbum = pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPRE")
            val isPlaylist = pageType == "MUSIC_PAGE_TYPE_PLAYLIST" || browseId.startsWith("VL") || browseId.startsWith("PL")

            if (!isAlbum && !isPlaylist) return null

            val title = twoRow.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
            val subRuns = twoRow.optJSONObject("subtitle")?.optJSONArray("runs")
            val author = subRuns?.optJSONObject(0)?.optString("text").orEmpty()
            val authorId = subRuns?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")?.optString("browseId")

            val thumbUrl = twoRow.optJSONObject("thumbnailRenderer")
                ?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")?.let { thumbs ->
                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                }?.highQualityThumbnail()

            return MusicPlaylistRef(
                id = browseId,
                title = title.ifBlank { if (isAlbum) "Album" else "Playlist" },
                author = author,
                authorId = authorId,
                thumbnailUrl = thumbUrl,
                isAlbum = isAlbum,
            )
        }

        val row = itemObj.optJSONObject("musicResponsiveListItemRenderer")
        if (row != null) {
            val nav = row.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                ?: row.optJSONArray("flexColumns")?.optJSONObject(0)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)
                    ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                ?: return null

            val browseId = nav.optString("browseId")
            val pageType = nav.optJSONObject("browseEndpointContextSupportedConfigs")
                ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType")

            val isAlbum = pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPRE")
            val isPlaylist = pageType == "MUSIC_PAGE_TYPE_PLAYLIST" || browseId.startsWith("VL") || browseId.startsWith("PL")

            if (!isAlbum && !isPlaylist) return null

            val flexCols = row.optJSONArray("flexColumns")
            val title = flexCols?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
            val subRuns = flexCols?.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")
            val author = subRuns?.optJSONObject(0)?.optString("text").orEmpty()
            val authorId = subRuns?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")?.optString("browseId")

            val thumbUrl = row.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")?.let { thumbs ->
                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                }?.highQualityThumbnail()

            return MusicPlaylistRef(
                id = browseId,
                title = title.ifBlank { if (isAlbum) "Album" else "Playlist" },
                author = author,
                authorId = authorId,
                thumbnailUrl = thumbUrl,
                isAlbum = isAlbum,
            )
        }

        return null
    }

    private fun parseTrackItem(itemObj: JSONObject): MusicTrack? {
        // Handle musicResponsiveListItemRenderer (common in Trending / charts / search)
        itemObj.optJSONObject("musicResponsiveListItemRenderer")?.let { responsive ->
            val videoId = responsive.optJSONObject("playlistItemData")?.optString("videoId")
                ?: responsive.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
                ?: responsive.optJSONObject("overlay")?.optJSONObject("musicItemThumbnailOverlayRenderer")
                    ?.optJSONObject("content")?.optJSONObject("musicPlayButtonRenderer")
                    ?.optJSONObject("playNavigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
            if (videoId.isNullOrBlank()) return null

            val flexColumns = responsive.optJSONArray("flexColumns")
            val title = flexColumns?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text").orEmpty()

            val subRuns = flexColumns?.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")

            var artists = ""
            var artistId: String? = null
            var album: String? = null
            var albumId: String? = null
            var durationSec = 0L

            if (subRuns != null && subRuns.length() > 0) {
                val nonDelimiterRuns = mutableListOf<Pair<String, String?>>()
                for (r in 0 until subRuns.length()) {
                    val runObj = subRuns.optJSONObject(r)
                    val t = runObj?.optString("text").orEmpty().trim()
                    val bId = runObj?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                    if (t.isNotEmpty() && t != "•" && t != "·") {
                        if (t.contains(":") && t.matches(Regex("\\d+:\\d+(:\\d+)?"))) {
                            durationSec = parseTimeString(t)
                        } else {
                            nonDelimiterRuns.add(t to bId)
                        }
                    }
                }
                if (nonDelimiterRuns.isNotEmpty()) {
                    artists = nonDelimiterRuns[0].first
                    artistId = nonDelimiterRuns[0].second
                    if (nonDelimiterRuns.size > 1) {
                        album = nonDelimiterRuns[1].first
                        albumId = nonDelimiterRuns[1].second
                    }
                }
            }

            val thumbUrl = responsive.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
                ?.let { thumbs ->
                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                }?.highQualityThumbnail()

            return MusicTrack(
                id = videoId,
                title = title.ifBlank { "Unknown Title" },
                artists = artists.ifBlank { "Various Artists" },
                artistId = artistId,
                album = album,
                albumId = albumId,
                thumbnailUrl = thumbUrl,
                durationSec = durationSec
            )
        }

        // Handle musicTwoRowItemRenderer (common in carousel cards)
        itemObj.optJSONObject("musicTwoRowItemRenderer")?.let { twoRow ->
            val watchEndpoint = twoRow.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
            val videoId = watchEndpoint?.optString("videoId")
            if (videoId.isNullOrBlank()) return null

            val title = twoRow.optJSONObject("title")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text").orEmpty()

            val subtitleRuns = twoRow.optJSONObject("subtitle")?.optJSONArray("runs")
            val artists = subtitleRuns?.optJSONObject(0)?.optString("text").orEmpty()
            val artistId = subtitleRuns?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")?.optString("browseId")

            val thumbUrl = twoRow.optJSONObject("thumbnailRenderer")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
                ?.let { thumbs ->
                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                }?.highQualityThumbnail()

            return MusicTrack(
                id = videoId,
                title = title.ifBlank { "Unknown Title" },
                artists = artists.ifBlank { "Various Artists" },
                artistId = artistId,
                thumbnailUrl = thumbUrl
            )
        }

        return null
    }

    private fun postInnerTubeSearch(query: String, params: String? = null): JSONObject {
        val clientObj = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", "1.20240101.01.00")
            .put("hl", "en")
            .put("gl", "US")

        val body = JSONObject()
            .put("query", query)
            .put("context", JSONObject().put("client", clientObj))

        if (params != null) {
            body.put("params", params)
        }

        val request = Request.Builder()
            .url(SEARCH_URL)
            .post(body.toString().toRequestBody(JSON_TYPE))
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("X-Youtube-Client-Name", "67")
            .header("X-Youtube-Client-Version", "1.20240101.01.00")
            .build()

        Net.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("InnerTube search failed: ${response.code}")
            }
            val text = response.body?.string().orEmpty()
            return JSONObject(text)
        }
    }

    suspend fun searchMusic(query: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()
        try {
            val root = postInnerTubeSearch(query, SONG_PARAMS)
            root.walk { key, itemObj ->
                if (key == "musicResponsiveListItemRenderer") {
                    val wrapper = JSONObject().put("musicResponsiveListItemRenderer", itemObj)
                    parseTrackItem(wrapper)?.let { tracks.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for '$query': ${e.message}")
        }

        if (tracks.isEmpty()) {
            try {
                val service = ServiceList.YouTube
                val searchInfo = SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(query))
                for (item in searchInfo.relatedItems) {
                    if (item is StreamInfoItem) {
                        tracks.add(
                            MusicTrack(
                                id = item.url.substringAfter("v=").substringBefore("&"),
                                title = item.name,
                                artists = item.uploaderName.orEmpty(),
                                thumbnailUrl = item.thumbnails.lastOrNull()?.url?.highQualityThumbnail(),
                                durationSec = item.duration
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "NewPipe fallback search failed: ${e.message}")
            }
        }

        tracks.distinctBy { it.id }
    }

    suspend fun searchArtists(query: String): List<MusicArtist> = withContext(Dispatchers.IO) {
        val artists = mutableListOf<MusicArtist>()
        try {
            val root = postInnerTubeSearch(query, ARTIST_PARAMS)
            root.walk { key, itemObj ->
                if (key == "musicResponsiveListItemRenderer" || key == "musicTwoRowItemRenderer") {
                    val wrapper = JSONObject().put(key, itemObj)
                    parseArtistItem(wrapper)?.let { artists.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search artists failed for '$query': ${e.message}")
        }
        artists.distinctBy { it.id }
    }

    suspend fun searchAlbums(query: String): List<MusicPlaylistRef> = withContext(Dispatchers.IO) {
        val albums = mutableListOf<MusicPlaylistRef>()
        try {
            val root = postInnerTubeSearch(query, ALBUM_PARAMS)
            root.walk { key, itemObj ->
                if (key == "musicResponsiveListItemRenderer" || key == "musicTwoRowItemRenderer") {
                    val wrapper = JSONObject().put(key, itemObj)
                    parsePlaylistItem(wrapper)?.let {
                        albums.add(it.copy(isAlbum = true))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search albums failed for '$query': ${e.message}")
        }
        albums.distinctBy { it.id }
    }

    suspend fun searchPlaylists(query: String): List<MusicPlaylistRef> = withContext(Dispatchers.IO) {
        val playlists = mutableListOf<MusicPlaylistRef>()
        try {
            val root = postInnerTubeSearch(query, PLAYLIST_PARAMS)
            root.walk { key, itemObj ->
                if (key == "musicResponsiveListItemRenderer" || key == "musicTwoRowItemRenderer") {
                    val wrapper = JSONObject().put(key, itemObj)
                    parsePlaylistItem(wrapper)?.let {
                        playlists.add(it.copy(isAlbum = false))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search playlists failed for '$query': ${e.message}")
        }
        playlists.distinctBy { it.id }
    }

    /**
     * Searches YouTube Music for an artist by name and returns the first valid UC* channel ID found.
     * Used as fallback when we only have a name string instead of a real channel ID.
     */
    private suspend fun resolveArtistChannelId(artistName: String): String? = withContext(Dispatchers.IO) {
        try {
            // Use YTM search with artist filter params
            val root = postInnerTubeSearch(artistName, ARTIST_PARAMS)
            var foundId: String? = null
            root.walk { key, item ->
                if (foundId != null) return@walk
                if (key == "musicTwoRowItemRenderer" || key == "musicResponsiveListItemRenderer") {
                    val nav = item.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                        ?: item.optJSONArray("flexColumns")?.optJSONObject(0)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)
                            ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                        ?: return@walk
                    val id = nav.optString("browseId")
                    val pageType = nav.optJSONObject("browseEndpointContextSupportedConfigs")
                        ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType")
                    if ((pageType == "MUSIC_PAGE_TYPE_ARTIST" || id.startsWith("UC")) && id.isNotBlank()) {
                        foundId = id
                    }
                }
            }
            Log.d(TAG, "Resolved artist '$artistName' -> $foundId")
            foundId
        } catch (e: Exception) {
            Log.w(TAG, "resolveArtistChannelId failed for '$artistName': ${e.message}")
            null
        }
    }

    suspend fun resolveAudioStream(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val info = org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(ServiceList.YouTube, url)

            val audioStreams = info.audioStreams
            if (audioStreams.isNullOrEmpty()) {
                Log.w(TAG, "No audio streams found for $videoId")
                return@withContext null
            }

            val bestM4a = audioStreams
                .filter { it.format == MediaFormat.M4A && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.audioTrackType != AudioTrackType.DUBBED }
                .maxByOrNull { it.averageBitrate }

            val bestAny = audioStreams
                .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.audioTrackType != AudioTrackType.DUBBED }
                .maxByOrNull { it.averageBitrate }

            val chosen = bestM4a ?: bestAny ?: audioStreams.firstOrNull()
            Log.d(TAG, "Selected stream: ${chosen?.format} @ ${chosen?.averageBitrate} kbps")
            return@withContext chosen?.content
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve stream for $videoId: ${e.message}", e)
            return@withContext null
        }
    }

    suspend fun resolveAudioStreamUrl(videoId: String): String? = resolveAudioStream(videoId)
}
