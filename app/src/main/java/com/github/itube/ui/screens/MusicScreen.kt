package com.github.itube.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.github.itube.AccountAction
import com.github.itube.CircleAction
import com.github.itube.LocalVideoMenu
import com.github.itube.MusicPlayerState
import com.github.itube.RoundedHeavy
import com.github.itube.ScreenHeader
import com.github.itube.UpdateAction
import com.github.itube.MoodCategory
import com.github.itube.MusicArtist
import com.github.itube.MusicBackend
import com.github.itube.MusicHomeFeed
import com.github.itube.MusicPlaylistRef
import com.github.itube.MusicTrack
import com.github.itube.highQualityThumbnail
import com.github.itube.pressable
import com.github.itube.ui.components.AnimatedEqualizer
import com.github.itube.ui.components.ModernPullToRefreshBox
import com.github.itube.ui.components.MusicScreenSkeleton
import com.github.itube.ui.components.flowArtistShape
import kotlinx.coroutines.launch

/**
 * iMusic Home Screen — Faithful reproduction of Flow's Music Home Feed.
 * Includes:
 * - Speed Dial (3 rows compact horizontal tiles with shuffle tile)
 * - Quick Picks (4 rows horizontal grid with "Play all" action button)
 * - "Artists for you" in 9-sided Cookie style (`flowArtistShape()`)
 * - "From community" playlists
 * - "Your daily discover" with slide carousel and "Play all" action
 * - "Mood and genres" shelf with colored accent stripes + "See all" action
 * - "From artists you love", "New releases", "Mixed for you"
 * - "Popular artists" in 9-sided Cookie style
 * - "Top albums", "Favourite artist mix" slideshow
 * - "More from...", "Energize vibe", "Trends & Charts"
 */
@Composable
fun MusicScreen(
    contentPadding: PaddingValues,
    onOpenMusic: (MusicTrack, List<MusicTrack>?) -> Unit,
    onArtistClick: (MusicArtist) -> Unit = {},
    onPlaylistClick: (MusicPlaylistRef) -> Unit = {},
    onMoodsClick: () -> Unit = {},
    onSearch: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    val artistShape = flowArtistShape()

    var feed by remember { mutableStateOf<MusicHomeFeed?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedChip by remember { mutableStateOf<String?>(null) }

    var trackOptionsFor by remember { mutableStateOf<MusicTrack?>(null) }
    var albumOptionsFor by remember { mutableStateOf<MusicPlaylistRef?>(null) }
    var artistOptionsFor by remember { mutableStateOf<MusicArtist?>(null) }

    fun loadData(chip: String? = null) {
        scope.launch {
            isLoading = true
            feed = MusicBackend.fetchMusicHome(chip)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
    ) {
        // ── Top Header with tag "iMusic" ("i" in red) ──
        ScreenHeader(title = "iMusic", brandMark = true) {
            UpdateAction()
            CircleAction(Icons.Rounded.Search, "Search", onSearch)
            AccountAction(onAccount)
        }

        ModernPullToRefreshBox(
            isRefreshing = isLoading && feed != null,
            onRefresh = { loadData(selectedChip) },
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading && feed == null) {
                MusicScreenSkeleton(modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding() + 24.dp))
            } else {
                val currentFeed = feed ?: MusicHomeFeed()

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // ── 1. Mood & Activity Chips ──
                    if (currentFeed.chips.isNotEmpty()) {
                        item(key = "chips") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(currentFeed.chips, key = { idx, chip -> "chip_${chip}_$idx" }) { _, chip ->
                                    val selected = chip == selectedChip
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            selectedChip = if (selected) null else chip
                                            loadData(selectedChip)
                                        },
                                        label = {
                                            Text(
                                                text = chip,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFFFF0000),
                                            selectedLabelColor = Color.White,
                                            containerColor = c.surfaceContainerHigh,
                                            labelColor = c.onSurface
                                        ),
                                        shape = RoundedCornerShape(20.dp),
                                        border = null
                                    )
                                }
                            }
                        }
                    }

                    // ── 2. Speed Dial Section (3 rows + Shuffle Tile) ──
                    val speedDialTracks = currentFeed.speedDial.takeIf { it.isNotEmpty() } ?: currentFeed.quickPicks.take(18)
                    if (speedDialTracks.isNotEmpty()) {
                        item(key = "speed_dial") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                MusicShelfTitle(title = "Speed dial")
                                Spacer(Modifier.height(10.dp))

                                val tileList = speedDialTracks.take(17)
                                LazyHorizontalGrid(
                                    rows = GridCells.Fixed(3),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp),
                                ) {
                                    // First item: Shuffle Tile
                                    item(key = "shuffle_tile") {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = c.secondaryContainer,
                                            modifier = Modifier
                                                .width(220.dp)
                                                .fillMaxHeight()
                                                .pressable {
                                                    val shuffled = speedDialTracks.shuffled()
                                                    onOpenMusic(shuffled.first(), shuffled)
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(CircleShape)
                                                        .background(c.primary),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Shuffle,
                                                        contentDescription = "Shuffle",
                                                        tint = c.onPrimary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                    "Shuffle all",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = c.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }

                                    itemsIndexed(tileList, key = { idx, track -> "sd_${track.id}_$idx" }) { _, track ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = c.surfaceContainerHigh.copy(alpha = 0.75f),
                                            modifier = Modifier
                                                .width(230.dp)
                                                .fillMaxHeight()
                                                .pressable(
                                                    onLongClick = { trackOptionsFor = track },
                                                    onClick = { onOpenMusic(track, speedDialTracks) }
                                                )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(c.surfaceContainerHighest),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    AsyncImage(
                                                        model = track.thumbnailUrl,
                                                        contentDescription = track.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                    if (MusicPlayerState.currentTrack?.id == track.id) {
                                                        Box(
                                                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            AnimatedEqualizer(
                                                                isPlaying = MusicPlayerState.isPlaying,
                                                                color = Color.White,
                                                                size = 20.dp
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        track.title,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = c.onSurface
                                                    )
                                                    Text(
                                                        track.artists,
                                                        fontSize = 11.sp,
                                                        color = c.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 3. Quick Picks (4-row grouped grid + Play All) ──
                    if (currentFeed.quickPicks.isNotEmpty()) {
                        item(key = "quick_picks") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                MusicShelfTitle(
                                    title = "Quick picks",
                                    subtitle = "START RADIO FROM A SONG",
                                    onPlayAll = {
                                        onOpenMusic(currentFeed.quickPicks.first(), currentFeed.quickPicks)
                                    }
                                )
                                Spacer(Modifier.height(10.dp))

                                val chunked = currentFeed.quickPicks.chunked(4)
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    itemsIndexed(chunked, key = { cIdx, _ -> "qp_col_$cIdx" }) { _, columnTracks ->
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.width(310.dp)
                                        ) {
                                            columnTracks.forEach { track ->
                                                QuickPickRow(
                                                    track = track,
                                                    onClick = { onOpenMusic(track, currentFeed.quickPicks) },
                                                    onLongClick = { trackOptionsFor = track }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 4. Artists For You (9-sided Cookie Shape!) ──
                    val artistsForYou = currentFeed.artistsForYou
                    if (artistsForYou.isNotEmpty()) {
                        item(key = "artists_for_you") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                MusicShelfTitle(title = "Artists for you")
                                Spacer(Modifier.height(12.dp))

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    itemsIndexed(artistsForYou, key = { idx, artist -> "afy_${artist.id}_$idx" }) { _, artist ->
                                        Column(
                                            modifier = Modifier
                                                .width(96.dp)
                                                .pressable(
                                                    onLongClick = { artistOptionsFor = artist },
                                                    onClick = { onArtistClick(artist) }
                                                ),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(88.dp)
                                                    .clip(artistShape)
                                                    .background(c.surfaceContainerHigh)
                                            ) {
                                                AsyncImage(
                                                    model = artist.thumbnailUrl,
                                                    contentDescription = artist.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = artist.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = c.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 5. Your Daily Discover (Hero Carousel with Slide Animation & Play All) ──
                    val dailyItems = currentFeed.dailyDiscover.takeIf { it.isNotEmpty() } ?: currentFeed.quickPicks.take(6)
                    if (dailyItems.isNotEmpty()) {
                        item(key = "daily_discover") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                MusicShelfTitle(
                                    title = "Your daily discover",
                                    subtitle = "FRESH FINDS FOR YOU",
                                    onPlayAll = { onOpenMusic(dailyItems.first(), dailyItems) }
                                )
                                Spacer(Modifier.height(10.dp))

                                val pagerState = rememberPagerState(pageCount = { dailyItems.size })
                                HorizontalPager(
                                    state = pagerState,
                                    contentPadding = PaddingValues(horizontal = 32.dp),
                                    pageSpacing = 16.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(210.dp)
                                ) { page ->
                                    val item = dailyItems[page]
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(c.surfaceContainerHigh)
                                            .pressable(
                                                onLongClick = { trackOptionsFor = item },
                                                onClick = { onOpenMusic(item, dailyItems) }
                                            )
                                    ) {
                                        AsyncImage(
                                            model = item.thumbnailUrl,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        // Gradient scrim overlay
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        0.4f to Color.Transparent,
                                                        1f to Color.Black.copy(alpha = 0.85f)
                                                    )
                                                )
                                        )
                                        // Title and play icon at bottom
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    item.title,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    item.artists,
                                                    fontSize = 13.sp,
                                                    color = Color.White.copy(alpha = 0.75f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Rounded.PlayArrow,
                                                    contentDescription = "Play",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 6. From the Community (Playlists) ──
                    if (currentFeed.communityPlaylists.isNotEmpty()) {
                        item(key = "from_community") {
                            CollectionShelf(
                                title = "From the community",
                                items = currentFeed.communityPlaylists,
                                onPlaylistClick = onPlaylistClick,
                                onPlaylistLongClick = { albumOptionsFor = it }
                            )
                        }
                    }

                    // ── 7. Moods & Genres (3 Rows with Left Colored Stripes + See All) ──
                    if (currentFeed.moodsAndGenres.isNotEmpty()) {
                        item(key = "moods_and_genres") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Moods & genres",
                                        fontFamily = RoundedHeavy,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = c.onBackground
                                    )
                                    TextButton(onClick = onMoodsClick) {
                                        Text("See all", fontWeight = FontWeight.Bold, color = c.primary)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))

                                val moodItems = currentFeed.moodsAndGenres.take(12)
                                LazyHorizontalGrid(
                                    rows = GridCells.Fixed(3),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(170.dp)
                                ) {
                                    itemsIndexed(moodItems, key = { idx, mood -> "mg_${mood.title}_$idx" }) { _, mood ->
                                        MoodCard(
                                            mood = mood,
                                            onClick = onMoodsClick,
                                            modifier = Modifier.width(180.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── 8. From Artists You Love ──
                    if (currentFeed.fromArtistsYouLove.isNotEmpty()) {
                        item(key = "from_artists_you_love") {
                            CollectionShelf(
                                title = "From artists you love",
                                items = currentFeed.fromArtistsYouLove,
                                onPlaylistClick = onPlaylistClick,
                                onPlaylistLongClick = { albumOptionsFor = it }
                            )
                        }
                    }

                    // ── 9. New Releases (Albums & Singles) ──
                    if (currentFeed.newReleases.isNotEmpty()) {
                        item(key = "new_releases") {
                            CollectionShelf(
                                title = "New releases",
                                items = currentFeed.newReleases,
                                onPlaylistClick = onPlaylistClick,
                                onPlaylistLongClick = { albumOptionsFor = it }
                            )
                        }
                    }

                    // ── 10. Mixed For You ──
                    if (currentFeed.mixedForYou.isNotEmpty()) {
                        item(key = "mixed_for_you") {
                            CollectionShelf(
                                title = "Mixed for you",
                                items = currentFeed.mixedForYou,
                                onPlaylistClick = onPlaylistClick,
                                onPlaylistLongClick = { albumOptionsFor = it }
                            )
                        }
                    }

                    // ── 11. Popular Artists (9-sided Cookie Shape!) ──
                    if (currentFeed.popularArtists.isNotEmpty()) {
                        item(key = "popular_artists") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                MusicShelfTitle(title = "Popular artists")
                                Spacer(Modifier.height(12.dp))

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    itemsIndexed(currentFeed.popularArtists, key = { idx, artist -> "pop_${artist.id}_$idx" }) { _, artist ->
                                        Column(
                                            modifier = Modifier
                                                .width(96.dp)
                                                .pressable(
                                                    onLongClick = { artistOptionsFor = artist },
                                                    onClick = { onArtistClick(artist) }
                                                ),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(88.dp)
                                                    .clip(artistShape)
                                                    .background(c.surfaceContainerHigh)
                                            ) {
                                                AsyncImage(
                                                    model = artist.thumbnailUrl,
                                                    contentDescription = artist.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = artist.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = c.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 12. Top Albums ──
                    if (currentFeed.topAlbums.isNotEmpty()) {
                        item(key = "top_albums") {
                            CollectionShelf(
                                title = "Top albums",
                                items = currentFeed.topAlbums,
                                onPlaylistClick = onPlaylistClick
                            )
                        }
                    }

                    // ── 13. Favourite Artist Mix (Slide show animation) ──
                    if (currentFeed.favoriteArtistMix.isNotEmpty()) {
                        item(key = "favorite_artist_mix") {
                            TrackCollectionShelf(
                                title = "Favourite artist mix",
                                items = currentFeed.favoriteArtistMix,
                                onTrackClick = { track ->
                                    onOpenMusic(track, currentFeed.favoriteArtistMix)
                                }
                            )
                        }
                    }

                    // ── 14. Energize Vibe ──
                    if (currentFeed.energizeVibe.isNotEmpty()) {
                        item(key = "energize_vibe") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                MusicShelfTitle(
                                    title = "Energize vibe",
                                    onPlayAll = { onOpenMusic(currentFeed.energizeVibe.first(), currentFeed.energizeVibe) }
                                )
                                Spacer(Modifier.height(10.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    itemsIndexed(currentFeed.energizeVibe, key = { idx, track -> "ev_${track.id}_$idx" }) { _, track ->
                                        TrackCard(track = track, onClick = { onOpenMusic(track, currentFeed.energizeVibe) })
                                    }
                                }
                            }
                        }
                    }

                    // ── 15. Trends & Charts ──
                    if (currentFeed.trendingSongs.isNotEmpty() || currentFeed.chartTopArtists.isNotEmpty()) {
                        item(key = "charts_and_trends") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (currentFeed.trendingSongs.isNotEmpty()) {
                                    MusicShelfTitle(
                                        title = "Trends & Charts",
                                        subtitle = "TOP CHARTS RIGHT NOW",
                                        onPlayAll = { onOpenMusic(currentFeed.trendingSongs.first(), currentFeed.trendingSongs) }
                                    )
                                    Spacer(Modifier.height(10.dp))

                                    val chunked = currentFeed.trendingSongs.take(16).chunked(4)
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        itemsIndexed(chunked, key = { cIdx, _ -> "chart_col_$cIdx" }) { _, columnTracks ->
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.width(310.dp)
                                            ) {
                                                columnTracks.forEachIndexed { idx, track ->
                                                    RankedTrackRow(
                                                        rank = idx + 1,
                                                        track = track,
                                                        onClick = { onOpenMusic(track, currentFeed.trendingSongs) },
                                                        onLongClick = { trackOptionsFor = track }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (currentFeed.chartTopArtists.isNotEmpty()) {
                                    Spacer(Modifier.height(20.dp))
                                    MusicShelfTitle(title = "Charts top artists")
                                    Spacer(Modifier.height(12.dp))

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        itemsIndexed(currentFeed.chartTopArtists, key = { idx, artist -> "cta_${artist.id}_$idx" }) { _, artist ->
                                            Column(
                                                modifier = Modifier
                                                    .width(96.dp)
                                                    .pressable { onArtistClick(artist) },
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(88.dp)
                                                        .clip(artistShape)
                                                        .background(c.surfaceContainerHigh)
                                                ) {
                                                    AsyncImage(
                                                        model = artist.thumbnailUrl,
                                                        contentDescription = artist.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    text = artist.name,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = c.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 16. More From Dynamic Sections ──
                    if (currentFeed.moreFrom.isNotEmpty()) {
                        item(key = "more_from") {
                            TrackCollectionShelf(
                                title = "More to explore",
                                items = currentFeed.moreFrom,
                                onTrackClick = { track ->
                                    onOpenMusic(track, currentFeed.moreFrom)
                                },
                                onTrackLongClick = { track ->
                                    trackOptionsFor = track
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── iMusic Long-Press Context Sheets ──
        trackOptionsFor?.let { track ->
            MusicTrackOptionsSheet(
                track = track,
                onDismiss = { trackOptionsFor = null },
                onGoToArtist = { artistId ->
                    onArtistClick(MusicArtist(id = artistId, name = track.artists))
                }
            )
        }

        albumOptionsFor?.let { album ->
            MusicAlbumOptionsSheet(
                playlist = album,
                onDismiss = { albumOptionsFor = null },
                onViewAlbum = { onPlaylistClick(album) }
            )
        }

        artistOptionsFor?.let { artist ->
            MusicArtistOptionsSheet(
                artist = artist,
                onDismiss = { artistOptionsFor = null },
                onViewArtist = { onArtistClick(artist) }
            )
        }
    }
}

@Composable
private fun MusicShelfTitle(
    title: String,
    subtitle: String? = null,
    onPlayAll: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = c.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Text(
                text = title,
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = c.onBackground
            )
        }
        if (onPlayAll != null) {
            ElevatedButton(
                onClick = onPlayAll,
                shape = CircleShape,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = c.surfaceContainerHighest,
                    contentColor = c.onSurface
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play all", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Play all", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CollectionShelf(
    title: String,
    items: List<MusicPlaylistRef>,
    onPlaylistClick: (MusicPlaylistRef) -> Unit,
    onPlaylistLongClick: ((MusicPlaylistRef) -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MusicShelfTitle(title = title)
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(items, key = { idx, pl -> "col_${title}_${pl.id}_$idx" }) { _, pl ->
                Column(
                    modifier = Modifier
                        .width(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .pressable(
                            onLongClick = { onPlaylistLongClick?.invoke(pl) },
                            onClick = { onPlaylistClick(pl) }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.2f))
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        AsyncImage(
                            model = pl.thumbnailUrl,
                            contentDescription = pl.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = pl.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (pl.author.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = pl.author,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun TrackCollectionShelf(
    title: String,
    items: List<MusicTrack>,
    onTrackClick: (MusicTrack) -> Unit,
    onTrackLongClick: ((MusicTrack) -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MusicShelfTitle(title = title)
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(items, key = { idx, track -> "trk_${title}_${track.id}_$idx" }) { _, track ->
                TrackCard(
                    track = track,
                    onClick = { onTrackClick(track) },
                    onLongClick = { onTrackLongClick?.invoke(track) }
                )
            }
        }
    }
}

@Composable
private fun TrackCard(
    track: MusicTrack,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(14.dp))
            .pressable(onLongClick = onLongClick, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (MusicPlayerState.currentTrack?.id == track.id) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedEqualizer(
                        isPlaying = MusicPlayerState.isPlaying,
                        color = Color.White,
                        size = 28.dp
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = track.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = track.artists,
            fontSize = 11.sp,
            color = c.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickPickRow(
    track: MusicTrack,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = c.surfaceContainerHigh.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth().pressable(onLongClick = onLongClick, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(50.dp)
            ) {
                AsyncImage(
                    model = track.thumbnailUrl?.highQualityThumbnail(),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                )
                if (MusicPlayerState.currentTrack?.id == track.id) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedEqualizer(
                            isPlaying = MusicPlayerState.isPlaying,
                            color = Color.White,
                            size = 22.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artists,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RankedTrackRow(
    rank: Int,
    track: MusicTrack,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = c.surfaceContainerHigh.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth().pressable(onLongClick = onLongClick, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$rank",
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = if (rank <= 3) Color(0xFFFF0000) else c.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(50.dp)
            ) {
                AsyncImage(
                    model = track.thumbnailUrl?.highQualityThumbnail(),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                )
                if (MusicPlayerState.currentTrack?.id == track.id) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedEqualizer(
                            isPlaying = MusicPlayerState.isPlaying,
                            color = Color.White,
                            size = 22.dp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artists,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

