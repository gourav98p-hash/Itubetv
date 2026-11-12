package com.github.itube

import com.github.itube.ui.screens.MusicTrackOptionsSheet
import com.github.itube.ui.screens.MusicArtistOptionsSheet
import com.github.itube.ui.screens.MusicAlbumOptionsSheet

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Surface
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.OfflinePin
import com.github.itube.MusicCacheManager
import com.github.itube.LikedSongsStore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.QueueMusic
import com.github.itube.ui.components.MaterialLoader
import com.github.itube.ui.components.AnimatedEqualizer
import com.github.itube.ui.components.TrackBlurPopup
import com.github.itube.ui.components.popupContainerColor
import com.github.itube.ui.components.popupScrimColor
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.github.itube.ui.components.flowArtistShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeoutOrNull

// ── Home / Subscriptions / Library ─────────────────────────────────────────────
// Signed in, these show the account's real feeds. Signed out, they explain what signing
// in brings rather than showing sample content.

val LocalPlaylistMenu = staticCompositionLocalOf<(PlaylistRef) -> Unit> { {} }

@Composable
fun HomeScreen(
    lib: LibraryData,
    shorts: ShortsState,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onOpenShort: (VideoItem) -> Unit,
    onPlayCollection: (PublicCollection) -> Unit,
    onSearch: () -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
) {
    val signedIn = Account.signedIn
    LaunchedEffect(signedIn) { if (signedIn) lib.home.ensureLoaded() }
    LaunchedEffect(Unit) { shorts.ensureFeed() }
    CollapsingHeaderLayout(
        resetKey = scrollToTop to lib.home.generation,
        header = { ScreenHeader(title = "iTube", brandMark = true) { UpdateAction(); AccountAction(onAccount) } },
    ) { top ->
        if (signedIn) {
            FeedList(
                lib.home,
                contentPadding.withTop(top),
                onOpen,
                videoBatchSizes = listOf(3, 4),
                afterVideoBatch = { list, batch ->
                    list.item(key = "homeShorts-$batch") {
                        HomeShortsShelf(shorts.items, batch, onOpenShort)
                    }
                    if (batch == 0 && lib.home.collections.isNotEmpty()) {
                        list.item(key = "homeMixesAndPlaylists") {
                            HomeCollectionsShelf(lib.home.collections, onPlayCollection)
                        }
                    }
                },
                scrollToTop = scrollToTop,
                indicatorTop = top,
            )
        } else {
            LazyColumn(contentPadding = contentPadding.withTop(top)) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Subscriptions,
                        title = "Your recommendations, minus the ads",
                        body = "Sign in with Google to see your own YouTube home feed, or just search.",
                        action = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PillButton("Sign in with Google", onSignIn)
                                Spacer(Modifier.height(12.dp))
                                TextLink("Search instead", onSearch)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** A compact real Shorts rail, sourced from the same InnerTube feed as the Shorts tab. */
@Composable
private fun HomeShortsShelf(shorts: List<VideoItem>, shelfIndex: Int, onOpenShort: (VideoItem) -> Unit) {
    val shelfShorts = shorts.drop(shelfIndex * 4).take(4)
    if (shelfShorts.isEmpty()) return
    val menu = LocalVideoMenu.current
    Column(Modifier.padding(top = 4.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("i", color = Color(0xFFFF0000), fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text("Shorts", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        }
        shelfShorts.chunked(2).forEachIndexed { rowIndex, rowShorts ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowShorts.forEach { short ->
                    Box(
                        Modifier.weight(1f)
                            .aspectRatio(9f / 16f)
                            .prefetchOnPress(short.id)
                            .pressable(pressedScale = 0.98f, onLongClick = { menu(short) }, onClick = { onOpenShort(short) })
                            .clip(RoundedCornerShape(16.dp)),
                    ) {
                        Thumbnail(
                            url = short.shortThumbnail,
                            modifier = Modifier.fillMaxSize(),
                            radius = 0,
                            fallbackUrl = short.thumbnail,
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(0.42f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f)),
                            ),
                        )
                        if (rowIndex == 0) {
                            Box(
                                Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp))
                                    .background(Color.White).padding(horizontal = 8.dp, vertical = 3.dp),
                            ) { Text("New", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        }
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "Short options",
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(19.dp)
                                .clickable { menu(short) },
                        )
                        Text(
                            short.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 11.dp),
                        )
                    }
                }
                if (rowShorts.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Real YouTube Home Mixes and playlists; selecting a card queues its actual playlist tracks. */
@Composable
private fun HomeCollectionsShelf(collections: List<PublicCollection>, onPlay: (PublicCollection) -> Unit) {
    if (collections.isEmpty()) return
    val c = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 4.dp, bottom = 10.dp)) {
        SectionTitle("Mixes & playlists")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(collections, key = { "home-collection-${it.url}" }) { collection ->
                val isMix = collection.subtitle.contains("mix", ignoreCase = true) ||
                    collection.url.substringAfter("list=", "").startsWith("RD")
                Column(Modifier.width(190.dp).pressable(pressedScale = 0.98f, onClick = { onPlay(collection) })) {
                    Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(18.dp))) {
                        Thumbnail(collection.thumbnail, Modifier.fillMaxSize(), radius = 0)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.25f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))))
                        Text(
                            if (isMix) "MIX" else "PLAYLIST",
                            Modifier.align(Alignment.TopStart).padding(9.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.58f)).padding(horizontal = 9.dp, vertical = 4.dp),
                            color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                        )
                        Icon(Icons.Rounded.PlayArrow, "Play ${collection.title}", tint = Color.White,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(9.dp).size(30.dp)
                                .clip(CircleShape).background(c.primary).padding(5.dp))
                    }
                    Text(collection.title, Modifier.padding(top = 7.dp), color = c.onSurface, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (isMix) "Play mix · all tracks" else "Play playlist · all videos", color = c.onSurfaceVariant,
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun SubscriptionsScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
) {
    val signedIn = Account.signedIn
    LaunchedEffect(signedIn) {
        if (signedIn) {
            lib.subscriptions.ensureLoaded()
            if (lib.channels.isEmpty()) lib.loadChannels()
        }
    }
    CollapsingHeaderLayout(
        resetKey = scrollToTop to lib.subscriptions.generation,
        header = { ScreenHeader(title = "Subscriptions") { AccountAction(onAccount) } },
    ) { top ->
        if (signedIn) {
            FeedList(lib.subscriptions, contentPadding.withTop(top), onOpen, header = {
                // Always present (empty until loaded) so its arrival never shifts the list.
                item(key = "channels") { if (lib.channels.isNotEmpty()) ChannelRail(lib.channels) }
            }, scrollToTop = scrollToTop, indicatorTop = top)
        } else {
            LazyColumn(contentPadding = contentPadding.withTop(top)) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Subscriptions,
                        title = "Your subscriptions",
                        body = "Sign in with Google to see new videos from the channels you follow.",
                        action = { PillButton("Sign in with Google", onSignIn) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRail(channels: List<ChannelRef>) {
    val c = MaterialTheme.colorScheme
    val openChannel = LocalChannelOpener.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
    ) {
        items(channels, key = { it.id }) { ch ->
            Column(
                Modifier
                    .width(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .pressable(pressedScale = 0.94f) { openChannel(ch.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ChannelAvatar(ch.avatar, ch.name, size = 56)
                Spacer(Modifier.height(6.dp))
                Text(ch.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun LibraryScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onOpenShort: (VideoItem) -> Unit = onOpen,
    onOpenHistory: () -> Unit = {},
    onOpenPlaylist: (PlaylistRef) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenCachedMusic: () -> Unit = {},
    onOpenLikedMusic: () -> Unit = {},
    onOpenCollection: (PublicCollection) -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
) {
    val signedIn = Account.signedIn
    LaunchedEffect(signedIn) {
        if (signedIn) {
            lib.history.ensureLoaded()
            if (lib.playlists.isEmpty()) lib.loadPlaylists()
        }
    }
    CollapsingHeaderLayout(
        resetKey = scrollToTop to lib.history.generation,
        header = { ScreenHeader(title = "Library") { AccountAction(onAccount) } },
    ) { top ->
        PullToRefresh(
            enabled = signedIn,
            refreshing = lib.history.refreshing,
            onRefresh = { lib.history.refresh(); lib.loadPlaylists() },
            topPadding = top,
        ) {
        LazyColumn(state = rememberScrollToTop(scrollToTop), contentPadding = contentPadding.withTop(top)) {
            if (!signedIn) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.VideoLibrary,
                        title = "Your library",
                        body = "Sign in with Google for your history, Watch later, liked videos and playlists.",
                        action = { PillButton("Sign in with Google", onSignIn) },
                    )
                }
            }
            if (signedIn) {
                item(key = "historyTitle") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "History",
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                            letterSpacing = (-0.5).sp,
                        )
                        Text(
                            "View all",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.pressable(onClick = onOpenHistory).padding(4.dp),
                        )
                    }
                }
                item(key = "history") {
                    val history = lib.history.videos
                    when {
                        history.isNotEmpty() -> LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(history.take(20), key = { "h-" + it.id }) { video ->
                                HistoryCard(video) {
                                    if (video.isShortVideo) onOpenShort(video) else onOpen(video)
                                }
                            }
                        }
                        lib.history.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                            MaterialLoader(color = MaterialTheme.colorScheme.primary, size = 28.dp)
                        }
                        else -> Text(
                            lib.history.error ?: "Nothing watched yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            }
            item(key = "offlineMediaTitle") { SectionTitle("Offline & Downloads") }
            item(key = "offlineMedia") {
                val cachedCount = MusicCacheManager.cachedTracks.size
                val cachedBytes = MusicCacheManager.totalStorageBytes.longValue
                val cachedSizeStr = MusicCacheManager.formatStorageSize(cachedBytes)
                val likedCount = LikedSongsStore.likedTracks.size
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PlaylistTile(
                            PlaylistRef("DL", "Downloads", null, "${DownloadLibrary.entries.size} videos"),
                            Modifier.weight(1f),
                            onOpenDownloads,
                        )
                        CachedSongsTile(
                            title = "Cached songs",
                            subtitle = "$cachedCount songs • $cachedSizeStr",
                            modifier = Modifier.weight(1f),
                            onClick = onOpenCachedMusic
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LikedSongsTile(
                            title = "Liked songs",
                            subtitle = "$likedCount songs",
                            modifier = Modifier.weight(1f),
                            onClick = onOpenLikedMusic
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (SavedCollections.items.isNotEmpty()) {
                item(key = "savedCollectionsTitle") { SectionTitle("Saved collections") }
                items(SavedCollections.items, key = { "saved-collection-${it.url}" }) { collection ->
                    PublicCollectionRow(collection, onOpenCollection)
                }
            }
            if (!signedIn) return@LazyColumn
            item(key = "playlistsTitle") { SectionTitle("Playlists") }
            val pinned = listOf(
                PlaylistRef("WL", "Watch later", null, null),
                PlaylistRef("LL", "Liked videos", null, null),
            )
            items((pinned + lib.playlists).chunked(2), key = { row -> row.joinToString { it.id } }) { row ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { pl -> PlaylistTile(pl, Modifier.weight(1f)) { onOpenPlaylist(pl) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        }
    }
}

@Composable
private fun HistoryCard(video: VideoItem, onClick: () -> Unit) {
    val menu = LocalVideoMenu.current
    Column(Modifier.width(168.dp).prefetchOnPress(video.id).pressable(pressedScale = 0.97f, onLongClick = { menu(video) }, onClick = onClick)) {
        Box {
            Thumbnail(video.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 20)
            DurationPill(video.durationSec, Modifier.align(Alignment.BottomEnd).padding(6.dp), small = true)
        }
        Spacer(Modifier.height(7.dp))
        Text(video.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaylistTile(playlist: PlaylistRef, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val openOptions = LocalPlaylistMenu.current
    val canManage = playlist.id !in setOf("WL", "LL", "DL")
    val shape = RoundedCornerShape(26.dp)
    val (icon, tone) = when (playlist.id) {
        "WL" -> Icons.Rounded.WatchLater to c.primaryContainer
        "LL" -> Icons.Rounded.ThumbUp to c.tertiaryContainer
        "DL" -> Icons.Rounded.Download to c.primaryContainer
        else -> Icons.AutoMirrored.Rounded.PlaylistPlay to c.secondaryContainer
    }
    Column(
        modifier = modifier
            .height(132.dp)
            .pressable(pressedScale = 0.97f, onLongClick = { if (canManage) openOptions(playlist) }, onClick = onClick)
            .clip(shape)
            .background(c.surfaceContainerLow)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (playlist.thumbnail != null) {
            Thumbnail(playlist.thumbnail, Modifier.size(width = 72.dp, height = 44.dp), radius = 12)
        } else {
            Box(Modifier.size(52.dp).clip(CookieShape).background(tone), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = contentColorFor(tone))
            }
        }
        Column {
            Text(playlist.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            playlist.subtitle?.let { Text(it, color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
    }
}

@Composable
private fun CachedSongsTile(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier
            .height(132.dp)
            .pressable(pressedScale = 0.97f, onClick = onClick)
            .clip(shape)
            .background(c.surfaceContainerLow)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CookieShape).background(c.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.OfflinePin,
                contentDescription = null,
                tint = contentColorFor(c.tertiaryContainer),
                modifier = Modifier.size(24.dp)
            )
        }
        Column {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun LikedSongsTile(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier
            .height(132.dp)
            .pressable(pressedScale = 0.97f, onClick = onClick)
            .clip(shape)
            .background(c.surfaceContainerLow)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CookieShape).background(Color(0xFFFF4565).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = Color(0xFFFF4565),
                modifier = Modifier.size(24.dp)
            )
        }
        Column {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistOptionsSheet(
    playlist: PlaylistRef,
    onDismiss: () -> Unit,
    onDelete: (PlaylistRef) -> Unit,
    onReload: (PlaylistRef) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmDelete by remember(playlist.id) { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                if (playlist.thumbnail != null) Thumbnail(playlist.thumbnail, Modifier.fillMaxSize(), radius = 0)
                else Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = c.onSurfaceVariant)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                playlist.subtitle?.let { Text(it, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        PlaylistAction(Icons.Rounded.Refresh, "Refresh") { onReload(playlist); onDismiss() }
        if (playlist.id !in setOf("WL", "LL", "DL")) {
            PlaylistAction(Icons.Rounded.Share, "Share playlist") {
                shareLink(context, "https://www.youtube.com/playlist?list=${playlist.id}", playlist.title)
                onDismiss()
            }
            PlaylistAction(Icons.Rounded.Delete, "Delete playlist", c.error) { confirmDelete = true }
        }
        Spacer(Modifier.height(18.dp))
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("“${playlist.title}” will be deleted from your YouTube account.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(playlist) }) { Text("Delete", color = c.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        )
    }
}

@Composable
private fun PlaylistAction(icon: ImageVector, title: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(pressedScale = 0.98f, onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(title, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

/** Videos saved into Downloads/iTube; opening a row uses its stored file URI and stored metadata. */
@Composable
fun DownloadsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (DownloadedVideo) -> Unit,
) {
    val downloads = DownloadLibrary.entries
    val active = DownloadLibrary.activeDownloads
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    CollapsingHeaderLayout(
        resetKey = downloads.size + active.size,
        header = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(12.dp))
                Text("Downloads", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            }
        },
    ) { top ->
        if (downloads.isEmpty() && active.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(top = top), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Rounded.Download, "No downloads yet", "Videos you download will appear here and play offline.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding.withTop(top)) {
                if (active.isNotEmpty()) {
                    item { SectionTitle("Downloading") }
                    items(active, key = { "active-download-${it.id}" }) { task ->
                        ActiveDownloadRow(task) { VideoDownloader.cancel(context, task.id) }
                    }
                }
                if (downloads.isNotEmpty()) {
                    item { SectionTitle("Downloaded videos") }
                    items(downloads, key = { "download-" + it.item.id }) { download ->
                        DownloadedVideoRow(download) { onOpen(download) }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ActiveDownloadRow(download: ActiveDownload, onCancel: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(112.dp).aspectRatio(16f / 9f)) {
            Thumbnail(download.item.thumbnail, Modifier.fillMaxSize(), radius = 14)
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp)) {
                if (download.progress == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { download.progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(download.item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(download.status, color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onCancel) { Icon(Icons.Rounded.Cancel, contentDescription = "Cancel download", tint = c.error) }
    }
}

@Composable
private fun DownloadedVideoRow(download: DownloadedVideo, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(132.dp).aspectRatio(16f / 9f)) {
            Thumbnail(download.item.thumbnail, Modifier.fillMaxSize(), radius = 16)
            DurationPill(download.item.durationSec, Modifier.align(Alignment.BottomEnd).padding(6.dp), small = true)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(download.item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(download.item.channel, color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    download.item.views.takeIf { it >= 0 }?.let { "${formatCount(it)} views" },
                    download.item.uploaded,
                ).joinDots().ifBlank { "Downloaded" },
                color = c.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Dedicated full history screen with pagination, Shorts shelf, and smooth navigation. */
@Composable
fun HistoryScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    onOpenShort: (VideoItem) -> Unit = onOpen,
) {
    val feed = lib.history
    val videoMenu = LocalVideoMenu.current
    LaunchedEffect(Unit) { feed.ensureLoaded() }
    val historyShorts = remember(feed.videos) { feed.videos.filter { it.isShortVideo } }
    val regularVideos = remember(feed.videos, historyShorts) {
        if (historyShorts.isNotEmpty()) feed.videos.filterNot { it.isShortVideo } else feed.videos
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        CollapsingHeaderLayout(
            resetKey = feed.generation,
            header = {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "History",
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        letterSpacing = (-0.8).sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        ) { top ->
            if (feed.videos.isEmpty() && !feed.loading && feed.error == null) {
                Box(Modifier.fillMaxSize().padding(top = top), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Rounded.History, "No watch history", "Videos and shorts you watch will appear here.")
                }
            } else {
                FeedList(
                    feed = feed,
                    contentPadding = contentPadding.withTop(top),
                    videosOverride = if (historyShorts.isNotEmpty()) regularVideos else null,
                    onOpen = { video -> if (video.isShortVideo) onOpenShort(video) else onOpen(video) },
                    header = {
                        if (historyShorts.isNotEmpty()) {
                            item(key = "history-shorts-shelf") {
                                PlaylistShortsShelf(shorts = historyShorts, onOpenShort = onOpenShort, onVideoLongPress = videoMenu)
                            }
                        }
                    },
                    indicatorTop = top,
                )
            }
        }
    }
}

/** A playlist's tracks stay separate from YouTube's recommendation shelf. */
@Composable
fun PlaylistScreen(
    playlist: PlaylistRef,
    feed: FeedState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    onOpenShort: (VideoItem) -> Unit = onOpen,
    onPlayPlaylist: (List<VideoItem>, Int) -> Unit = { videos, index -> videos.getOrNull(index)?.let(onOpen) },
    onVideoLongPress: ((VideoItem) -> Unit)? = null,
) {
    val parentVideoMenu = LocalVideoMenu.current
    val videoMenu = LocalVideoMenu.current
    val playlistMenu = LocalPlaylistMenu.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val savedShorts = playlist.title.contains("short", ignoreCase = true)
    val isLikedVideos = playlist.id == "LL" || playlist.title.contains("Liked", ignoreCase = true)
    val likedShorts = remember(feed.videos, isLikedVideos) {
        if (isLikedVideos) feed.videos.filter { it.isShortVideo } else emptyList()
    }
    val regularVideos = remember(feed.videos, isLikedVideos, likedShorts) {
        if (isLikedVideos && likedShorts.isNotEmpty()) feed.videos.filterNot { it.isShortVideo } else feed.videos
    }
    fun playPlaylistItem(video: VideoItem) {
        scope.launch {
            val all = feed.loadAllVideos()
            val playlistVideos = if (isLikedVideos && likedShorts.isNotEmpty()) all.filterNot { it.isShortVideo } else all
            val index = playlistVideos.indexOfFirst { it.id == video.id }
            if (index >= 0) onPlayPlaylist(playlistVideos, index) else onOpen(video)
        }
    }
    LaunchedEffect(playlist.id) { feed.ensureLoaded() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    CompositionLocalProvider(LocalVideoMenu provides { video -> onVideoLongPress?.invoke(video) ?: parentVideoMenu(video) }) {
    CollapsingHeaderLayout(
        resetKey = feed.generation,
        header = {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
            Text(
                playlist.title,
                fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-0.8).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { playlistMenu(playlist) }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        },
    ) { top ->
        val showShortsGrid = savedShorts || (isLikedVideos && regularVideos.isEmpty() && likedShorts.isNotEmpty())
        if (feed.videos.isEmpty() && feed.recommendations.isEmpty() && !feed.loading && feed.error == null && !feed.hasMore) {
            Box(Modifier.padding(top = top)) { EmptyState(Icons.Rounded.VideoLibrary, "Nothing here yet", "Videos you add to this playlist will show up here.") }
        } else if (showShortsGrid) {
            val shortsList = if (savedShorts) feed.videos else likedShorts
            val listState = rememberLazyListState()
            val nearEnd by remember(listState, shortsList.size) {
                derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 3 } == true }
            }
            LaunchedEffect(nearEnd, shortsList.size) { if (nearEnd && feed.hasMore && !feed.loading) feed.loadMore() }
            LazyColumn(state = listState, contentPadding = contentPadding.withTop(top)) {
                item(key = "playlist-actions") {
                    PlaylistPlaybackActions(
                        count = shortsList.size,
                        onPlay = { shortsList.firstOrNull()?.let(onOpenShort) },
                        onShuffle = { shortsList.shuffled().firstOrNull()?.let(onOpenShort) },
                    )
                }
                items(shortsList.chunked(2), key = { row -> row.joinToString(prefix = "shorts-") { it.id } }) { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { short ->
                            Box(
                                Modifier.weight(1f)
                                    .aspectRatio(9f / 16f)
                                    .prefetchOnPress(short.id)
                                    .pressable(pressedScale = 0.98f, onLongClick = { videoMenu(short) }, onClick = { onOpenShort(short) })
                                    .clip(RoundedCornerShape(18.dp)),
                            ) {
                                Thumbnail(
                                    url = short.shortThumbnail,
                                    modifier = Modifier.fillMaxSize(),
                                    radius = 0,
                                    fallbackUrl = short.thumbnail,
                                )
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.74f))))
                                Text(short.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (feed.loading) item { Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) { MaterialLoader(color = MaterialTheme.colorScheme.primary, size = 26.dp) } }
            }
        } else {
            val displayVideos = if (likedShorts.isNotEmpty()) regularVideos else feed.videos
            FeedList(
                feed = feed,
                contentPadding = contentPadding.withTop(top),
                videosOverride = if (likedShorts.isNotEmpty()) regularVideos else null,
                onOpen = { video ->
                    if (video.isShortVideo) {
                        onOpenShort(video)
                    } else if (feed.videos.any { it.id == video.id }) {
                        playPlaylistItem(video)
                    } else {
                        onOpen(video)
                    }
                },
                header = {
                    item(key = "playlist-actions") {
                        PlaylistPlaybackActions(
                            count = displayVideos.size,
                            onPlay = {
                                scope.launch {
                                    val all = if (likedShorts.isNotEmpty()) feed.loadAllVideos().filterNot { it.isShortVideo } else feed.loadAllVideos()
                                    onPlayPlaylist(all, 0)
                                }
                            },
                            onShuffle = {
                                scope.launch {
                                    val all = (if (likedShorts.isNotEmpty()) feed.loadAllVideos().filterNot { it.isShortVideo } else feed.loadAllVideos()).shuffled()
                                    onPlayPlaylist(all, 0)
                                }
                            },
                        )
                    }
                    if (likedShorts.isNotEmpty()) {
                        item(key = "playlist-shorts-shelf") {
                            PlaylistShortsShelf(
                                shorts = likedShorts,
                                onOpenShort = onOpenShort,
                                onVideoLongPress = { video -> onVideoLongPress?.invoke(video) ?: parentVideoMenu(video) },
                            )
                        }
                    }
                    if (displayVideos.isEmpty() && feed.recommendations.isNotEmpty()) {
                        item(key = "playlist-empty-tip") {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(Icons.Rounded.VideoLibrary, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No videos in this playlist yet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Add some from the recommendations below to get started", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                },
                indicatorTop = top,
                footer = {
                    if ((!feed.hasMore || displayVideos.isEmpty()) && feed.recommendations.isNotEmpty()) {
                        item(key = "playlist-recommended-title") {
                            PlaylistRecommendationsHeader(count = feed.recommendations.size)
                        }
                        items(feed.recommendations, key = { "playlist-recommendation-${it.id}" }) { video ->
                            RecommendedVideoRow(
                                video = video,
                                onOpen = {
                                    if (video.isShortVideo) onOpenShort(video) else onOpen(video)
                                },
                                onAdd = {
                                    feed.addPlaylistVideo(playlist.id, video)
                                },
                                onMenu = { videoMenu(video) },
                            )
                        }
                    }
                },
            )
        }
    }
    }
    }
}

@Composable
private fun PlaylistShortsShelf(
    shorts: List<VideoItem>,
    onOpenShort: (VideoItem) -> Unit,
    onVideoLongPress: (VideoItem) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF0000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Shorts",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                )
            }
            Text(
                "${shorts.size} shorts",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.onSurfaceVariant,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(shorts, key = { "playlist-short-${it.id}" }) { short ->
                Box(
                    Modifier
                        .width(140.dp)
                        .aspectRatio(9f / 16f)
                        .prefetchOnPress(short.id)
                        .pressable(
                            pressedScale = 0.96f,
                            onLongClick = { onVideoLongPress(short) },
                            onClick = { onOpenShort(short) },
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surfaceContainerHigh),
                ) {
                    Thumbnail(
                        url = short.shortThumbnail,
                        modifier = Modifier.fillMaxSize(),
                        radius = 0,
                        fallbackUrl = short.thumbnail,
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0.4f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.85f),
                            ),
                        ),
                    )
                    IconButton(
                        onClick = { onVideoLongPress(short) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp),
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        short.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistRecommendationsHeader(count: Int) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 12.dp)
            .padding(horizontal = 16.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            color = c.outlineVariant.copy(alpha = 0.4f),
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(c.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = c.onSecondaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Recommended",
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            letterSpacing = (-0.3).sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(c.tertiaryContainer)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "SUGGESTIONS",
                                color = c.onTertiaryContainer,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            )
                        }
                    }
                    Text(
                        "Based on videos in this playlist",
                        fontSize = 12.sp,
                        color = c.onSurfaceVariant,
                    )
                }
            }
            if (count > 0) {
                Text(
                    "$count",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecommendedVideoRow(
    video: VideoItem,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onMenu: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceContainerLow)
            .prefetchOnPress(video.id)
            .pressable(pressedScale = 0.98f, onLongClick = onMenu, onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            Thumbnail(video.thumbnail, Modifier.fillMaxSize(), radius = 0)
            DurationPill(video.durationSec, Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.tertiaryContainer.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    "Recommendation",
                    color = c.onTertiaryContainer,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                video.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = c.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                metaLine(video.channel, video.views, video.uploaded),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = c.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = onAdd,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(c.primaryContainer),
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = "Add to playlist",
                tint = c.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = onMenu,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = c.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PlaylistPlaybackActions(count: Int, onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PlaylistActionButton("Play all", Icons.Rounded.PlayArrow, Modifier.weight(1f), onPlay)
        PlaylistActionButton("Shuffle", Icons.Rounded.Shuffle, Modifier.weight(1f), onShuffle)
        if (count > 0) Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp), fontSize = 13.sp)
    }
}

@Composable
private fun PlaylistActionButton(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier.clip(CircleShape).background(c.secondaryContainer).pressable(pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = c.onSecondaryContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = c.onSecondaryContainer, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        modifier = Modifier.pressable(onClick = onClick).padding(8.dp),
    )
}

// ── Search ─────────────────────────────────────────────────────────────────────

enum class SearchCategory {
    Videos,
    Music,
}

enum class MusicSearchFilter { Songs, Artists, Albums, Playlists }

/** Lives above the tab switcher so results survive switching tabs. */
@Stable
class SearchState(private val scope: CoroutineScope) {
    var query by mutableStateOf("")
    var category by mutableStateOf(SearchCategory.Videos)

    // Video search state
    var submitted by mutableStateOf("")
        private set
    var results by mutableStateOf<List<SearchResult>>(emptyList())
        private set
    /** All is the default and uses YouTube's real ALL result filter. */
    var filter by mutableStateOf(SearchFilter.All)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var generation by mutableStateOf(0)
        private set
    private var job: Job? = null

    // Music search state
    var musicSubmitted by mutableStateOf("")
        private set
    var musicResults by mutableStateOf<List<MusicTrack>>(emptyList())
        private set
    var musicLoading by mutableStateOf(false)
        private set
    var musicError by mutableStateOf<String?>(null)
        private set
    var musicGeneration by mutableStateOf(0)
        private set
    private var musicJob: Job? = null
    var musicFilter by mutableStateOf(MusicSearchFilter.Songs)
        private set
    var musicArtistResults by mutableStateOf<List<MusicArtist>>(emptyList())
        private set
    var musicPlaylistResults by mutableStateOf<List<MusicPlaylistRef>>(emptyList())
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    private var suggestionsJob: Job? = null

    fun updateQuery(value: String) {
        query = value
        suggestionsJob?.cancel()
        if (value.trim().length < 2) {
            suggestions = emptyList()
            return
        }
        suggestionsJob = scope.launch {
            kotlinx.coroutines.delay(180)
            suggestions = runCatching { YouTube.suggestions(value.trim()) }.getOrDefault(emptyList())
        }
    }

    fun selectCategory(newCategory: SearchCategory) {
        if (category == newCategory) return
        category = newCategory
        val activeQuery = query.trim().ifEmpty {
            if (newCategory == SearchCategory.Videos) musicSubmitted else submitted
        }
        if (activeQuery.isNotEmpty()) {
            query = activeQuery
            submit()
        }
    }

    /** Selecting a row in the search filter sheet immediately refreshes the active query. */
    fun selectFilter(newFilter: SearchFilter) {
        if (filter == newFilter) return
        filter = newFilter
        if (category == SearchCategory.Videos && query.trim().isNotEmpty()) submit()
    }

    fun selectMusicFilter(newFilter: MusicSearchFilter) {
        if (musicFilter == newFilter) return
        musicFilter = newFilter
        if (category == SearchCategory.Music && musicSubmitted.isNotEmpty()) submitMusic()
    }

    fun submit() {
        val q = query.trim()
        if (q.isEmpty()) return
        SearchHistory.add(q)
        if (category == SearchCategory.Videos) {
            submitted = q
            loading = true
            error = null
            job?.cancel()
            job = scope.launch {
                runCatching { YouTube.search(q, filter) }
                    .onSuccess { results = it; generation++ }
                    .onFailure { error = YouTube.friendlyError(it) }
                loading = false
            }
        } else {
            musicSubmitted = q
            submitMusic()
        }
    }

    private fun submitMusic() {
        musicLoading = true
        musicError = null
        musicJob?.cancel()
        musicResults = emptyList()
        musicArtistResults = emptyList()
        musicPlaylistResults = emptyList()
        ListeningProfile.recordSearch(musicSubmitted, musicFilter.name)
        musicJob = scope.launch {
            runCatching {
                when (musicFilter) {
                    MusicSearchFilter.Songs -> {
                        musicResults = MusicBackend.searchMusic(musicSubmitted)
                    }
                    MusicSearchFilter.Artists -> {
                        musicArtistResults = MusicBackend.searchArtists(musicSubmitted)
                    }
                    MusicSearchFilter.Albums -> {
                        musicPlaylistResults = MusicBackend.searchAlbums(musicSubmitted)
                    }
                    MusicSearchFilter.Playlists -> {
                        musicPlaylistResults = MusicBackend.searchPlaylists(musicSubmitted)
                    }
                }
            }.onFailure { musicError = it.localizedMessage ?: "Failed to search music" }
            musicLoading = false
        }
    }
}

private sealed interface SearchDisplayItem {
    data class Normal(val result: SearchResult) : SearchDisplayItem
    data class ShortsShelf(val key: String, val shorts: List<VideoItem>) : SearchDisplayItem
}

private fun groupSearchResults(results: List<SearchResult>): List<SearchDisplayItem> {
    if (results.isEmpty()) return emptyList()
    val displayItems = ArrayList<SearchDisplayItem>()
    val currentShorts = ArrayList<VideoItem>()
    var shelfCounter = 0

    fun flush() {
        if (currentShorts.isNotEmpty()) {
            displayItems.add(
                SearchDisplayItem.ShortsShelf(
                    key = "shorts_shelf_${shelfCounter++}_${currentShorts.first().id}",
                    shorts = ArrayList(currentShorts)
                )
            )
            currentShorts.clear()
        }
    }

    for (res in results) {
        val video = res.video
        if (video != null && video.isShortVideo) {
            currentShorts.add(video)
        } else {
            flush()
            displayItems.add(SearchDisplayItem.Normal(res))
        }
    }
    flush()
    return displayItems
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    state: SearchState,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onOpenShort: (VideoItem) -> Unit = {},
    onOpenResult: (SearchResult) -> Unit,
    onMusicArtistClick: (MusicArtist) -> Unit = {},
    onMusicPlaylistClick: (MusicPlaylistRef) -> Unit = {},
    onSettings: () -> Unit,
    scrollToTop: Int = 0,
) {
    val c = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val view = LocalView.current
    // Search and Enter both dismiss the keyboard — every field, every time. A hide issued
    // while the key event is still being dispatched can be undone by the IME, so the
    // window-level hide is posted to run after it.
    fun submit() {
        focus.clearFocus(force = true)
        keyboard?.hide()
        view.postDelayed({
            view.context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
        }, 120)
        state.submit()
    }
    var focused by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    var trackOptionsFor by remember { mutableStateOf<MusicTrack?>(null) }
    var artistOptionsFor by remember { mutableStateOf<MusicArtist?>(null) }
    var albumOptionsFor by remember { mutableStateOf<MusicPlaylistRef?>(null) }
    // The keyboard closed by Back or its own button leaves the field focused; drop the focus
    // with it so the history gives way to the results again.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) { if (!imeVisible && focused) focus.clearFocus() }

    val currentSubmitted = if (state.category == SearchCategory.Videos) state.submitted else state.musicSubmitted
    // Past searches, filtered by what's typed: while typing, and on the empty page.
    val history = SearchHistory.matching(state.query)
    val showHistory = history.isNotEmpty() && (focused || currentSubmitted.isEmpty())
    val showSuggestions = focused && state.query.trim().length >= 2 && state.suggestions.isNotEmpty()

    // The search bar rides with the header: both slide away together while scrolling results.
    CollapsingHeaderLayout(
        resetKey = listOf(scrollToTop, state.generation, state.musicGeneration, state.category, showHistory),
        header = { Column {
        // This remains a Search page; filters are separate from application settings.
        ScreenHeader(
            title = "Search",
        ) {
            if (state.category == SearchCategory.Videos) {
                CircleAction(Icons.Rounded.Tune, "Search filters") { filtersOpen = true }
            }
        }
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(60.dp)
                .clip(CircleShape)
                .background(c.surfaceContainerHigh)
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = c.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (state.query.isEmpty()) {
                    Text(
                        if (state.category == SearchCategory.Videos) "Search iTube" else "Search iMusic",
                        color = c.onSurfaceVariant,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = state::updateQuery,
                    singleLine = true,
                    textStyle = OwnDirectionText.merge(TextStyle(color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }, onDone = { submit() }),
                    // Hardware Enter doesn't go through the IME action; treat it the same.
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.onPreviewKeyEvent { e ->
                        if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
                            if (e.type == KeyEventType.KeyDown) submit()
                            true
                        } else false
                    },
                )
            }
            if (state.query.isNotEmpty()) {
                Box(
                    modifier = Modifier.size(44.dp).pressable { state.updateQuery("") },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, "Clear", tint = c.onSurfaceVariant) }
            }
        }

        // Switch the source without losing the typed query.
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val isVideos = state.category == SearchCategory.Videos
            val videosBg by animateColorAsState(
                targetValue = if (isVideos) c.primary else c.surfaceContainerHigh,
                animationSpec = tween(200),
                label = "videosBg"
            )
            val videosFg by animateColorAsState(
                targetValue = if (isVideos) c.onPrimary else c.onSurfaceVariant,
                animationSpec = tween(200),
                label = "videosFg"
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(videosBg)
                    .clickable { state.selectCategory(SearchCategory.Videos) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = videosFg,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "iTube",
                        color = videosFg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            val isMusic = state.category == SearchCategory.Music
            val musicBg by animateColorAsState(
                targetValue = if (isMusic) c.primary else c.surfaceContainerHigh,
                animationSpec = tween(200),
                label = "musicBg"
            )
            val musicFg by animateColorAsState(
                targetValue = if (isMusic) c.onPrimary else c.onSurfaceVariant,
                animationSpec = tween(200),
                label = "musicFg"
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(musicBg)
                    .clickable { state.selectCategory(SearchCategory.Music) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = musicFg,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "iMusic",
                        color = musicFg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        // iMusic filter chips (only when Music tab selected)
        if (state.category == SearchCategory.Music) {
            LazyRow(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(MusicSearchFilter.entries.toList()) { filter ->
                    val selected = state.musicFilter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { state.selectMusicFilter(filter) },
                        label = { Text(filter.name, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = c.primary,
                            selectedLabelColor = c.onPrimary,
                        ),
                        shape = CircleShape,
                        border = null,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        } },
    ) { top ->
        Box(Modifier.fillMaxSize()) {
            if (state.category == SearchCategory.Videos) {
                // Suggestions are already a scrolling list with the header inset. Including
                // them here avoids adding that inset a second time (which left a huge blank gap).
                val listShown = showSuggestions || showHistory || (state.results.isNotEmpty() && state.error == null)
                Box(Modifier.padding(top = if (listShown) 0.dp else top)) {
                    when {
                        showSuggestions -> SearchSuggestionsList(
                            suggestions = state.suggestions,
                            contentPadding = contentPadding.withTop(top),
                            onSearch = { state.updateQuery(it); submit() },
                        )
                        showHistory -> SearchHistoryList(
                            history = history,
                            contentPadding = contentPadding.withTop(top),
                            onSearch = { state.query = it; submit() },
                        )
                        state.loading && state.results.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), Alignment.Center) {
                            MaterialLoader(color = c.primary, size = 36.dp)
                        }
                        state.error != null -> EmptyState(
                            icon = Icons.Rounded.CloudOff,
                            title = "Search didn't load",
                            body = state.error.orEmpty(),
                            action = { PillButton("Try again", { state.submit() }) },
                        )
                        state.submitted.isEmpty() -> EmptyState(
                            icon = Icons.Rounded.TravelExplore,
                            title = "Search without the ads",
                            body = "Results play straight away, with no ads and no sign-in.",
                        )
                        state.results.isEmpty() -> EmptyState(
                            icon = Icons.Rounded.History,
                            title = "Nothing found",
                            body = "No videos matched “${state.submitted}”.",
                        )
                        else -> {
                            val list = rememberScrollToTop(scrollToTop)
                            LaunchedEffect(state.generation) { list.scrollToItem(0) }
                            val displayItems = remember(state.results) { groupSearchResults(state.results) }
                            LazyColumn(state = list, contentPadding = contentPadding.withTop(top)) {
                                items(
                                    items = displayItems,
                                    key = { item ->
                                        when (item) {
                                            is SearchDisplayItem.Normal -> item.result.id
                                            is SearchDisplayItem.ShortsShelf -> item.key
                                        }
                                    }
                                ) { item ->
                                    when (item) {
                                        is SearchDisplayItem.Normal -> SearchResultCard(item.result, onOpen, onOpenShort, onOpenResult)
                                        is SearchDisplayItem.ShortsShelf -> SearchShortsShelf(item.shorts, onOpenShort)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Music Search Results (AirBeats / Flow style with Filter Tabs)
                val hasResults = when (state.musicFilter) {
                    MusicSearchFilter.Songs -> state.musicResults.isNotEmpty()
                    MusicSearchFilter.Artists -> state.musicArtistResults.isNotEmpty()
                    MusicSearchFilter.Albums, MusicSearchFilter.Playlists -> state.musicPlaylistResults.isNotEmpty()
                }
                val listShown = showHistory || (hasResults && state.musicError == null)
                Box(Modifier.padding(top = if (listShown) 0.dp else top)) {
                    when {
                        showHistory -> SearchHistoryList(
                            history = history,
                            contentPadding = contentPadding.withTop(top),
                            onSearch = { state.query = it; submit() },
                        )
                        state.musicLoading && !hasResults -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), Alignment.Center) {
                            MaterialLoader(color = c.primary, size = 36.dp)
                        }
                        state.musicError != null -> EmptyState(
                            icon = Icons.Rounded.CloudOff,
                            title = "Music search didn't load",
                            body = state.musicError.orEmpty(),
                            action = { PillButton("Try again", { state.submit() }) },
                        )
                        state.musicSubmitted.isEmpty() -> EmptyState(
                            icon = Icons.Rounded.MusicNote,
                            title = "Search YouTube Music",
                            body = "Search for songs, artists, albums, and playlists.",
                        )
                        !hasResults -> EmptyState(
                            icon = Icons.Rounded.SearchOff,
                            title = when (state.musicFilter) {
                                MusicSearchFilter.Songs -> "No songs found"
                                MusicSearchFilter.Artists -> "No artists found"
                                MusicSearchFilter.Albums -> "No albums found"
                                MusicSearchFilter.Playlists -> "No playlists found"
                            },
                            body = "No results matched “${state.musicSubmitted}”.",
                        )
                        else -> {
                            val list = rememberScrollToTop(scrollToTop)
                            LaunchedEffect(state.musicGeneration) { list.scrollToItem(0) }
                            when (state.musicFilter) {
                                MusicSearchFilter.Songs -> {
                                    LazyColumn(state = list, contentPadding = contentPadding.withTop(top)) {
                                        items(state.musicResults, key = { it.id }) { track ->
                                            MusicSearchTrackRow(
                                                track = track,
                                                onClick = {
                                                    MusicPlayerState.play(track, null)
                                                },
                                                onLongClick = {
                                                    trackOptionsFor = track
                                                }
                                            )
                                        }
                                    }
                                }
                                MusicSearchFilter.Artists -> {
                                    val artistShape = flowArtistShape()
                                    LazyColumn(state = list, contentPadding = contentPadding.withTop(top)) {
                                        items(state.musicArtistResults, key = { it.id }) { artist ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .pressable(
                                                        pressedScale = 0.98f,
                                                        onLongClick = { artistOptionsFor = artist },
                                                        onClick = { onMusicArtistClick(artist) }
                                                    )
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .clip(artistShape)
                                                        .background(c.surfaceContainerHigh),
                                                ) {
                                                    AsyncImage(
                                                        model = artist.thumbnailUrl,
                                                        contentDescription = artist.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                }
                                                Spacer(Modifier.width(16.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        text = artist.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp,
                                                        color = c.onBackground,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(
                                                        text = artist.subscribers ?: "Artist",
                                                        fontSize = 13.sp,
                                                        color = c.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                MusicSearchFilter.Albums, MusicSearchFilter.Playlists -> {
                                    LazyColumn(state = list, contentPadding = contentPadding.withTop(top)) {
                                        items(state.musicPlaylistResults, key = { it.id }) { item ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .pressable(
                                                        pressedScale = 0.98f,
                                                        onLongClick = { albumOptionsFor = item },
                                                        onClick = { onMusicPlaylistClick(item) }
                                                    )
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(c.surfaceContainerHigh),
                                                ) {
                                                    AsyncImage(
                                                        model = item.thumbnailUrl,
                                                        contentDescription = item.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                }
                                                Spacer(Modifier.width(16.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.title,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = c.onBackground,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(
                                                        text = item.author.ifBlank { if (item.isAlbum) "Album" else "Playlist" },
                                                        fontSize = 13.sp,
                                                        color = c.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    trackOptionsFor?.let { track ->
        MusicTrackOptionsSheet(
            track = track,
            onDismiss = { trackOptionsFor = null },
            onGoToArtist = { artistName ->
                trackOptionsFor = null
                onMusicArtistClick(MusicArtist(id = artistName, name = artistName))
            }
        )
    }

    artistOptionsFor?.let { artist ->
        MusicArtistOptionsSheet(
            artist = artist,
            onDismiss = { artistOptionsFor = null },
            onViewArtist = {
                artistOptionsFor = null
                onMusicArtistClick(artist)
            }
        )
    }

    albumOptionsFor?.let { album ->
        MusicAlbumOptionsSheet(
            playlist = album,
            onDismiss = { albumOptionsFor = null },
            onViewAlbum = {
                albumOptionsFor = null
                onMusicPlaylistClick(album)
            },
            onGoToArtist = { artistName ->
                albumOptionsFor = null
                onMusicArtistClick(MusicArtist(id = artistName, name = artistName))
            }
        )
    }

    if (filtersOpen) SearchFiltersSheet(
        selected = state.filter,
        onSelected = { filter ->
            state.selectFilter(filter)
            filtersOpen = false
        },
        onDismiss = { filtersOpen = false },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SearchFiltersSheet(
    selected: SearchFilter,
    onSelected: (SearchFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    TrackBlurPopup()
    val c = MaterialTheme.colorScheme
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = popupContainerColor(c.surfaceContainerLow),
        scrimColor = popupScrimColor(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Search filters", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Spacer(Modifier.height(8.dp))
            Text("Choose what to search for", color = c.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            SearchFilter.entries.forEach { filter ->
                val active = selected == filter
                Row(
                    Modifier.fillMaxWidth().pressable { onSelected(filter) }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(filter.label, modifier = Modifier.weight(1f), fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 16.sp)
                    if (active) Icon(Icons.Rounded.Check, "Selected", tint = c.primary)
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    onOpen: (VideoItem) -> Unit,
    onOpenShort: (VideoItem) -> Unit,
    onOpenResult: (SearchResult) -> Unit
) {
    result.video?.let { video ->
        if (video.isShortVideo) {
            SearchShortsShelf(shorts = listOf(video), onOpenShort = onOpenShort)
        } else {
            VideoCard(video = video, onClick = { onOpen(video) })
        }
        return
    }
    val c = MaterialTheme.colorScheme
    when (result.kind) {
        SearchResultKind.Channel -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surfaceContainerLow)
                    .pressable { onOpenResult(result) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                ) {
                    Thumbnail(result.thumbnail, Modifier.fillMaxSize(), radius = 0)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = result.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = c.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = result.subtitle.ifBlank { "Channel" },
                        color = c.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.primaryContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "View",
                        color = c.onPrimaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        SearchResultKind.Collection -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surfaceContainerLow)
                    .pressable { onOpenResult(result) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    Thumbnail(result.thumbnail, Modifier.fillMaxSize(), radius = 0)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (result.isAlbum) "ALBUM" else "PLAYLIST",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = result.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = c.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = result.subtitle.ifBlank { if (result.isAlbum) "Album" else "Playlist" },
                        color = c.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = c.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        else -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surfaceContainerLow)
                    .pressable { onOpenResult(result) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumbnail(result.thumbnail, Modifier.size(68.dp), radius = 16)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(result.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(result.subtitle, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun SearchShortsShelf(shorts: List<VideoItem>, onOpenShort: (VideoItem) -> Unit) {
    if (shorts.isEmpty()) return
    val menu = LocalVideoMenu.current
    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("i", color = Color(0xFFFF0000), fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text("Shorts", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        }
        shorts.chunked(2).forEach { rowShorts ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowShorts.forEach { short ->
                    Box(
                        Modifier.weight(1f)
                            .aspectRatio(9f / 16f)
                            .prefetchOnPress(short.id)
                            .pressable(pressedScale = 0.98f, onLongClick = { menu(short) }, onClick = { onOpenShort(short) })
                            .clip(RoundedCornerShape(16.dp)),
                    ) {
                        Thumbnail(
                            url = short.shortThumbnail,
                            modifier = Modifier.fillMaxSize(),
                            radius = 0,
                            fallbackUrl = short.thumbnail,
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(0.42f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f)),
                            ),
                        )
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "Short options",
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(19.dp)
                                .clickable { menu(short) },
                        )
                        Text(
                            short.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 11.dp),
                        )
                    }
                }
                if (rowShorts.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SearchSuggestionsList(suggestions: List<String>, contentPadding: PaddingValues, onSearch: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    LazyColumn(contentPadding = contentPadding) {
        items(suggestions, key = { it }) { suggestion ->
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp).pressable { onSearch(suggestion) }.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.onSurfaceVariant, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(16.dp))
                Text(suggestion, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** AirBeats-styled music search track item row with cover art, titles, badges, and quick play. */
@Composable
fun MusicSearchTrackRow(
    track: MusicTrack,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isCurrent = MusicPlayerState.currentTrack?.id == track.id
    val isPlaying = isCurrent && MusicPlayerState.isPlaying
    val c = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(
                pressedScale = 0.98f,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with rounded corners and playing indicator
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = track.thumbnailUrl?.highQualityThumbnail(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedEqualizer(
                        isPlaying = isPlaying,
                        color = Color.White,
                        size = 24.dp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title and artist
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = if (isCurrent) c.primary else c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AirBeats style "Music" badge
                Box(
                    modifier = Modifier
                        .background(
                            color = c.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = "Music",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 9.sp
                        ),
                        color = c.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = track.artists + if (track.durationSec > 0) " • ${formatMusicDuration(track.durationSec)}" else "",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Play/Pause Action Icon
        Surface(
            shape = CircleShape,
            color = if (isCurrent) c.primary.copy(alpha = 0.15f) else c.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isCurrent) c.primary else c.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Music discovery feed: real YouTube Music shelves with a bounded catalog-search fallback. */
@Stable
class MusicHomeState(private val scope: CoroutineScope) {
    data class Mix(val title: String, val query: String, val tracks: List<VideoItem> = emptyList())
    private val fallbackMixes = listOf(
        Mix("Quick picks", "popular songs"),
        Mix("New releases", "new songs 2026"),
        Mix("Trending", "trending music"),
        Mix("Made for you", "top hits playlist"),
        Mix("Indian hits", "latest Indian hit songs"),
        Mix("Chill", "chill relaxing music"),
        Mix("Workout", "workout music hits"),
        Mix("Focus", "instrumental focus music"),
    )
    var mixes by mutableStateOf(fallbackMixes)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var started = false
    private var loadJob: Job? = null

    fun load(force: Boolean = false) {
        if (started && !force) return
        started = true
        loadJob?.cancel()
        loadJob = scope.launch {
            loading = true
            error = null

            // If YouTube changes or blocks the home endpoint, keep the page usable with
            // independent catalog searches; one slow request cannot keep the whole screen spinning.
            val requested = fallbackMixes
            mixes = requested
            // Sending every query at the same time makes YouTube reject the extractor as bot
            // traffic. Load each shelf in order and reveal it immediately.
            requested.forEachIndexed { index, mix ->
                val tracks = withTimeoutOrNull(15_000) {
                    runCatching { YouTube.search(mix.query) }.getOrNull()
                }.orEmpty()
                if (tracks.isNotEmpty()) {
                    mixes = mixes.toMutableList().also { it[index] = mix.copy(tracks = tracks) }
                } else if (error == null) {
                    error = "Some music shelves could not be loaded."
                }
            }
            loading = false
            if (mixes.none { it.tracks.isNotEmpty() } && error == null) error = "No music loaded. Check your connection and try again."
        }
    }
}
@Composable
fun MusicHomeScreen(
    state: MusicHomeState,
    contentPadding: PaddingValues,
    onSearch: () -> Unit,
    onPlay: (List<VideoItem>, Int) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    LaunchedEffect(state) { state.load() }
    val featured = state.mixes.firstNotNullOfOrNull { it.tracks.firstOrNull() }
    LazyColumn(contentPadding = contentPadding) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("iTube Music", color = c.onSurface, fontSize = 29.sp, fontWeight = FontWeight.Black)
                    Text("Your music, ready to play", color = c.onSurfaceVariant, fontSize = 14.sp)
                }
                Icon(Icons.Rounded.Equalizer, "Music", tint = c.primary, modifier = Modifier.size(30.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(CircleShape)
                    .background(c.surfaceContainerHigh).pressable(onClick = onSearch)
                    .padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text("Search songs, artists, albums", color = c.onSurfaceVariant, fontSize = 16.sp)
            }
            Spacer(Modifier.height(18.dp))
            if (featured != null) {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(270.dp)
                        .clip(RoundedCornerShape(26.dp)).pressable {
                            val mix = state.mixes.firstOrNull { it.tracks.any { song -> song.id == featured.id } }
                            val list = mix?.tracks.orEmpty()
                            onPlay(list, list.indexOfFirst { it.id == featured.id }.coerceAtLeast(0))
                        },
                ) {
                    Thumbnail(featured.thumbnail, Modifier.fillMaxSize(), radius = 0)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))))
                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp)) {
                        Text("FEATURED SONG", color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        Text(featured.title, Modifier.padding(top = 5.dp), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(featured.channel, Modifier.weight(1f), color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.clip(CircleShape).background(c.primary).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.PlayArrow, null, tint = c.onPrimary, modifier = Modifier.size(18.dp))
                                Text("Play", Modifier.padding(start = 5.dp), color = c.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (state.loading && state.mixes.none { it.tracks.isNotEmpty() }) {
            item { Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { MaterialLoader(color = c.primary, size = 32.dp) } }
        }
        state.error?.let { message ->
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), color = c.onSurfaceVariant, fontSize = 13.sp)
                    TextLink("Retry") { state.load(force = true) }
                }
            }
        }
        state.mixes.filter { it.tracks.isNotEmpty() }.forEach { mix ->
            item(key = "music-section-${mix.query}") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(mix.title, color = c.onSurface, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("From YouTube Music", color = c.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Icon(Icons.Rounded.PlayArrow, "Play ${mix.title}", tint = c.primary, modifier = Modifier.size(28.dp).pressable { onPlay(mix.tracks, 0) })
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(mix.tracks.take(16), key = { "${mix.query}-${it.id}" }) { track ->
                        Column(Modifier.width(158.dp).pressable {
                            onPlay(mix.tracks, mix.tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
                        }) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))) {
                                Thumbnail(track.thumbnail, Modifier.fillMaxSize(), radius = 0)
                                Box(Modifier.align(Alignment.BottomEnd).padding(9.dp).size(38.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.PlayArrow, "Play ${track.title}", tint = c.onPrimary)
                                }
                            }
                            Text(track.title, Modifier.padding(top = 9.dp), color = c.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(track.channel, color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().pressable(onClick = onSearch).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Album, null, tint = c.primary)
                Spacer(Modifier.width(12.dp))
                Text("Explore more songs and artists", color = c.onSurface, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.QueueMusic, null, tint = c.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SearchHistoryList(history: List<String>, contentPadding: PaddingValues, onSearch: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    LazyColumn(contentPadding = contentPadding) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            ) {
                SectionTitle("Recent searches", Modifier.weight(1f).alignByBaseline())
                Box(Modifier.alignByBaseline()) { TextLink("Clear all") { SearchHistory.clear() } }
            }
        }
        items(history, key = { it }) { query ->
            Row(
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .pressable { onSearch(query) }
                    .height(52.dp)
                    .padding(start = 24.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.History, null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    query,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier.size(44.dp).pressable { SearchHistory.remove(query) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, "Remove", tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}
