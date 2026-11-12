package com.github.itube

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.github.itube.ui.components.HomeScreenSkeleton
import com.github.itube.ui.components.MaterialLoader
import com.github.itube.ui.components.ModernPullToRefreshBox
import kotlinx.coroutines.launch

/** A pageable list of videos from the signed-in account (home, subscriptions, history, a playlist). */
@Stable
class FeedState(
    private val scope: CoroutineScope,
    /** Warm up the stream data of the first videos once a page lands (Home, Subscriptions). */
    private val prefetchTop: Int = 0,
    /**
     * Home: every refresh should bring different suggestions, as in the YouTube app. The
     * signed-in web feed returns much the same first page each time, so a refresh reads
     * on into the recommendation stream and keeps only videos not yet shown this session.
     */
    private val freshOnRefresh: Boolean = false,
    private val loader: suspend (next: String?) -> FeedPage,
) {
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var recommendations by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var collections by mutableStateOf<List<PublicCollection>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Bumped when a refresh lands; lists scroll back to the top on it. */
    var generation by mutableStateOf(0)
        private set
    /** A pull/button refresh is running (the first load shows its own spinner). */
    var refreshing by mutableStateOf(false)
        private set
    private var next: String? = null
    private var loaded = false
    /** Ids already put in front of the user this session (fresh-refresh feeds only). */
    private val shown = HashSet<String>()
    private var job: Job? = null
    val hasMore get() = next != null

    fun ensureLoaded() { if (!loaded && !loading) refresh() }

    fun refresh() {
        loaded = true
        loading = true
        refreshing = videos.isNotEmpty()
        error = null
        job?.cancel()
        job = scope.launch {
            runCatching { if (freshOnRefresh && shown.isNotEmpty()) loadFresh() else loader(null) }
                .onSuccess {
                    videos = it.videos; recommendations = it.recommendations; collections = it.collections; next = it.next; generation++
                    if (freshOnRefresh) it.videos.forEach { v -> shown += v.id }
                    it.videos.take(prefetchTop).forEach { v -> StreamCache.prefetch(v.id) }
                }
                .onFailure { error = friendly(it) }
            loading = false
            refreshing = false
        }
    }

    fun loadMore() {
        val token = next ?: return
        if (loading) return
        loading = true
        job = scope.launch {
            runCatching { loader(token) }
                .onSuccess { page ->
                    val seen = videos.mapTo(HashSet()) { it.id }
                    val added = page.videos.filter { seen.add(it.id) }
                    videos = videos + added
                    if (page.recommendations.isNotEmpty()) {
                        val seenRecommendations = recommendations.mapTo(HashSet()) { it.id }
                        recommendations = recommendations + page.recommendations.filter { seenRecommendations.add(it.id) }
                    }
                    if (page.collections.isNotEmpty()) {
                        val seenCollections = collections.mapTo(HashSet()) { it.url }
                        collections = collections + page.collections.filter { seenCollections.add(it.url) }
                    }
                    if (freshOnRefresh) added.forEach { v -> shown += v.id }
                    next = page.next
                }
                .onFailure { error = friendly(it) }
            loading = false
        }
    }

    /** Fetch every remaining playlist page before starting Play all / Shuffle. */
    suspend fun loadAllVideos(): List<VideoItem> {
        if (loading) job?.join()
        var token = next
        if (token == null) return videos
        loading = true
        val all = LinkedHashMap<String, VideoItem>().apply { videos.forEach { put(it.id, it) } }
        val allRecommendations = LinkedHashMap<String, VideoItem>().apply { recommendations.forEach { put(it.id, it) } }
        try {
            var pageCount = 0
            while (token != null && pageCount++ < 100) {
                val page = loader(token)
                page.videos.forEach { all.putIfAbsent(it.id, it) }
                page.recommendations.forEach { allRecommendations.putIfAbsent(it.id, it) }
                token = page.next
            }
            videos = all.values.toList()
            recommendations = allRecommendations.values.toList()
            next = token
            return videos
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            this.error = friendly(error)
            return videos
        } finally {
            loading = false
        }
    }

    /** Removes one selected track from the current YouTube playlist, rolling back if the write fails. */
    fun removePlaylistVideo(playlistId: String, video: VideoItem) {
        if (videos.none { it.id == video.id }) return
        val before = videos
        videos = before.filterNot { it.id == video.id }
        scope.launch {
            runCatching {
                when (playlistId) {
                    "WL" -> InnerTube.setInWatchLater(video.id, add = false)
                    "LL" -> InnerTube.setLike(video.id, LikeStatus.None)
                    else -> InnerTube.setInPlaylist(playlistId, video.id, add = false)
                }
            }.onFailure {
                videos = before
                error = "Couldn't remove ${video.title} from the playlist."
            }
        }
    }

    /** Adds one selected recommendation into the current YouTube playlist, updating the feed state immediately. */
    fun addPlaylistVideo(playlistId: String, video: VideoItem, onComplete: ((Boolean) -> Unit)? = null) {
        if (videos.any { it.id == video.id }) return
        val beforeVideos = videos
        val beforeRecs = recommendations
        videos = beforeVideos + video
        recommendations = beforeRecs.filterNot { it.id == video.id }
        scope.launch {
            runCatching {
                when (playlistId) {
                    "WL" -> InnerTube.setInWatchLater(video.id, add = true)
                    "LL" -> InnerTube.setLike(video.id, LikeStatus.Like)
                    else -> InnerTube.setInPlaylist(playlistId, video.id, add = true)
                }
            }.onSuccess {
                onComplete?.invoke(true)
            }.onFailure {
                videos = beforeVideos
                recommendations = beforeRecs
                error = "Couldn't add ${video.title} to the playlist."
                onComplete?.invoke(false)
            }
        }
    }

    /**
     * Unseen videos only: reads up to [FRESH_PAGES] pages into the feed until there are
     * [FRESH_TARGET]. If YouTube has nothing new at all, falls back to the plain first page.
     */
    private suspend fun loadFresh(): FeedPage {
        val first = loader(null)
        val fresh = LinkedHashMap<String, VideoItem>()
        var token = first.next
        first.videos.filter { it.id !in shown }.forEach { fresh[it.id] = it }
        var pages = 1
        while (fresh.size < FRESH_TARGET && token != null && pages < FRESH_PAGES) {
            val page = loader(token)
            page.videos.filter { it.id !in shown }.forEach { fresh.putIfAbsent(it.id, it) }
            token = page.next
            pages++
        }
        if (fresh.isEmpty()) return first
        return FeedPage(fresh.values.toList(), token, collections = first.collections)
    }

    fun reset() {
        job?.cancel()
        shown.clear()
        videos = emptyList(); recommendations = emptyList(); collections = emptyList(); next = null; loaded = false; loading = false; error = null
    }

    private companion object {
        const val FRESH_TARGET = 16
        const val FRESH_PAGES = 5
    }

    private fun friendly(t: Throwable) = when (t) {
        is NotSignedInException -> "You're signed out."
        else -> t.message?.takeIf { it.startsWith("YouTube") } ?: YouTube.friendlyError(t)
    }
}

/** Everything the signed-in tabs show, kept above the tab switcher so it survives tab changes. */
@Stable
class LibraryData(scope: CoroutineScope) {
    val home = FeedState(scope, prefetchTop = 2, freshOnRefresh = true) { InnerTube.home(it) }
    val subscriptions = FeedState(scope, prefetchTop = 2) { InnerTube.subscriptionsFeed(it) }
    val history = FeedState(scope) { InnerTube.history(it) }
    var channels by mutableStateOf<List<ChannelRef>>(emptyList())
        private set
    var playlists by mutableStateOf<List<PlaylistRef>>(emptyList())
        private set
    private val scope = scope

    fun loadChannels() = scope.launch { runCatching { InnerTube.channels() }.onSuccess { channels = it } }
    fun loadPlaylists() = scope.launch { runCatching { InnerTube.playlists() }.onSuccess { playlists = it } }

    fun forgetPlaylist(id: String) { playlists = playlists.filterNot { it.id == id } }

    fun playlistFeed(id: String) = FeedState(scope) { InnerTube.playlist(id, it) }

    fun reset() {
        home.reset(); subscriptions.reset(); history.reset()
        channels = emptyList(); playlists = emptyList()
    }
}

/**
 * A list state that animates back to the top whenever [signal] changes (tapping the
 * already-selected tab). The value it was created with is ignored, so merely showing a
 * tab never scrolls it.
 */
@Composable
fun rememberScrollToTop(signal: Int): LazyListState {
    val state = rememberLazyListState()
    val initial = remember { signal }
    LaunchedEffect(signal) { if (signal != initial) state.animateScrollToItem(0) }
    return state
}

/** A feed as a column of video cards with infinite scroll and designed loading/error states. */
@Composable
fun FeedList(
    feed: FeedState,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    videosOverride: List<VideoItem>? = null,
    header: LazyListScope.() -> Unit = {},
    /** Optional content placed after the first normal video, never above it. */
    afterFirstVideo: (LazyListScope.() -> Unit)? = null,
    /** A repeating 3/4-video rhythm for Home shelves, followed by [afterVideoBatch]. */
    videoBatchSizes: List<Int>? = null,
    afterVideoBatch: ((LazyListScope, Int) -> Unit)? = null,
    scrollToTop: Int = 0,
    pullToRefresh: Boolean = true,
    /** Collapsing header height: the refresh spinner appears just below it. */
    indicatorTop: Dp = 0.dp,
    footer: (LazyListScope.() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    val list = rememberScrollToTop(scrollToTop)
    val displayVideos = videosOverride ?: feed.videos
    // Fresh content always starts at the top (first load and every refresh).
    LaunchedEffect(feed.generation) { list.scrollToItem(0) }
    val nearEnd by remember {
        derivedStateOf {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= list.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, feed.videos.size) { if (nearEnd && feed.videos.isNotEmpty()) feed.loadMore() }
    val isTv = com.github.itube.ui.tv.LocalIsTv.current
    PullToRefresh(enabled = pullToRefresh, refreshing = feed.refreshing, onRefresh = feed::refresh, topPadding = indicatorTop) {
    LazyColumn(state = list, contentPadding = contentPadding) {
        header()
        if (afterVideoBatch != null && !videoBatchSizes.isNullOrEmpty()) {
            var offset = 0
            var batch = 0
            while (offset < displayVideos.size) {
                val limit = videoBatchSizes[batch % videoBatchSizes.size].coerceAtLeast(1)
                val end = (offset + limit).coerceAtMost(displayVideos.size)
                val group = displayVideos.subList(offset, end)
                if (isTv) {
                    val rows = group.chunked(2)
                    items(rows, key = { it.first().id }) { row ->
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        ) {
                            for (video in row) {
                                Box(Modifier.weight(1f)) {
                                    VideoCard(video, onClick = { onOpen(video) })
                                }
                            }
                            if (row.size < 2) {
                                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(group, key = { it.id }) { video -> VideoCard(video, onClick = { onOpen(video) }) }
                }
                offset = end
                // Keep cadence stable as more pages arrive: don't insert a shelf after an
                // incomplete tail until its next videos have loaded.
                if (group.size == limit) afterVideoBatch(this, batch++)
            }
        } else if (afterFirstVideo == null) {
            if (isTv) {
                val rows = displayVideos.chunked(2)
                items(rows, key = { it.first().id }) { row ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        for (video in row) {
                            Box(Modifier.weight(1f)) {
                                VideoCard(video, onClick = { onOpen(video) })
                            }
                        }
                        if (row.size < 2) {
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(displayVideos, key = { it.id }) { video -> VideoCard(video, onClick = { onOpen(video) }) }
            }
        } else {
            displayVideos.firstOrNull()?.let { first ->
                item(key = first.id) { VideoCard(first, onClick = { onOpen(first) }) }
            }
            afterFirstVideo()
            if (isTv) {
                val rows = displayVideos.drop(1).chunked(2)
                items(rows, key = { it.first().id }) { row ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        for (video in row) {
                            Box(Modifier.weight(1f)) {
                                VideoCard(video, onClick = { onOpen(video) })
                            }
                        }
                        if (row.size < 2) {
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(displayVideos.drop(1), key = { it.id }) { video -> VideoCard(video, onClick = { onOpen(video) }) }
            }
        }
        footer?.invoke(this)
        when {
            // No keys on these trailing items: a keyed loader at index 0 makes LazyColumn
            // hold it in view when videos arrive above it, landing at the bottom of the page.
            feed.error != null && feed.videos.isEmpty() -> item {
                EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = "Couldn't load this",
                    body = feed.error.orEmpty(),
                    action = { PillButton("Try again", { feed.refresh() }) },
                )
            }
            feed.loading && feed.videos.isEmpty() -> item {
                HomeScreenSkeleton()
            }
            feed.loading || feed.hasMore -> item {
                Box(Modifier.fillMaxWidth().padding(28.dp), Alignment.Center) {
                    MaterialLoader(color = c.primary, size = 32.dp)
                }
            }
        }
    }
    }
}

/**
 * Swipe down at the top to reload with the modern Material 3 Expressive loader from AirBeats.
 */
@Composable
fun PullToRefresh(enabled: Boolean, refreshing: Boolean, onRefresh: () -> Unit, topPadding: Dp = 0.dp, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    ModernPullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        indicatorOffset = topPadding,
    ) { content() }
}
