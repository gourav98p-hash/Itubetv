package com.github.itube.ui.screens

import android.content.Intent
import com.github.itube.ListeningProfile
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.github.itube.ArtistDetails
import com.github.itube.MusicArtist
import com.github.itube.MusicBackend
import com.github.itube.MusicPlayerState
import com.github.itube.MusicPlaylistRef
import com.github.itube.MusicTrack
import com.github.itube.RoundedHeavy
import com.github.itube.pressable
import com.github.itube.ui.components.AnimatedEqualizer
import com.github.itube.ui.components.MaterialLoader
import com.github.itube.ui.components.flowArtistShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Flow-style Artist Screen.
 * Faithful reproduction of Flow's ArtistPage and ArtistDetailComponents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun iMusicArtistScreen(
    channelId: String,
    initialName: String = "",
    initialThumbnail: String? = null,
    onBackClick: () -> Unit,
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onAlbumClick: (MusicPlaylistRef) -> Unit,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val scrollState = rememberLazyListState()

    var artist by remember(channelId) { mutableStateOf<ArtistDetails?>(null) }
    var isLoading by remember(channelId) { mutableStateOf(true) }
    var isSubscribed by remember { mutableStateOf(false) }
    var isBioExpanded by remember { mutableStateOf(false) }
    var trackOptionsFor by remember { mutableStateOf<MusicTrack?>(null) }
    var albumOptionsFor by remember { mutableStateOf<MusicPlaylistRef?>(null) }
    var artistOptionsFor by remember { mutableStateOf<MusicArtist?>(null) }

    LaunchedEffect(channelId) {
        withContext(Dispatchers.IO) {
            val fetched = MusicBackend.fetchArtistDetails(channelId)
            artist = fetched
            isLoading = false
            fetched?.let { ListeningProfile.recordArtistDetails(it) }
        }
    }

    val displayName = artist?.name?.takeIf { it.isNotBlank() } ?: initialName
    val heroImageUrl = artist?.thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: artist?.bannerUrl?.takeIf { it.isNotBlank() }
        ?: initialThumbnail

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val heroHeight = (screenWidth * 0.95f).coerceAtMost(380.dp) + statusBarHeight
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val artistShape = flowArtistShape()

    val topBarAlpha by remember {
        derivedStateOf {
            if (scrollState.firstVisibleItemIndex > 0) 1f
            else (scrollState.firstVisibleItemScrollOffset / 350f).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(c.background),
    ) {
        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = navBottom + 110.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Hero Photo + Name + Action Buttons + Bio
            item(key = "artist_hero") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val heroShape = RoundedCornerShape(
                        topStart = CornerSize(0.dp),
                        topEnd = CornerSize(0.dp),
                        bottomStart = CornerSize(32.dp),
                        bottomEnd = CornerSize(32.dp),
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroHeight)
                            .clip(heroShape)
                            .background(c.surfaceContainerHighest),
                    ) {
                        AsyncImage(
                            model = heroImageUrl,
                            contentDescription = displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        // Top gradient scrim for status bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(statusBarHeight + 56.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.45f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        // Bottom gradient transition to background
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            c.background.copy(alpha = 0.6f),
                                            c.background
                                        )
                                    )
                                )
                        )
                    }

                    // Artist Name
                    Text(
                        text = displayName,
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = c.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(top = 18.dp),
                    )

                    // Action buttons: Subscribe, Radio, Shuffle, Play
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Subscribe / Follow Button
                        OutlinedButton(
                            onClick = { isSubscribed = !isSubscribed },
                            shape = CircleShape,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSubscribed) c.secondaryContainer else Color.Transparent,
                                contentColor = if (isSubscribed) c.onSecondaryContainer else c.onBackground,
                            ),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = !isSubscribed),
                        ) {
                            Icon(
                                imageVector = if (isSubscribed) Icons.Rounded.Check else Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isSubscribed) "Subscribed" else "Subscribe",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }

                        // Right icons: Radio, Shuffle, Play
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val topTracks = artist?.topTracks.orEmpty()

                            // Radio
                            FilledTonalIconButton(
                                onClick = {
                                    if (topTracks.isNotEmpty()) onTrackClick(topTracks.first(), topTracks)
                                },
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Rounded.Radio, contentDescription = "Radio", modifier = Modifier.size(20.dp))
                            }

                            // Shuffle
                            FilledTonalIconButton(
                                onClick = {
                                    if (topTracks.isNotEmpty()) {
                                        val shuffled = topTracks.shuffled()
                                        onTrackClick(shuffled.first(), shuffled)
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(20.dp))
                            }

                            // Play button
                            FilledIconButton(
                                onClick = {
                                    if (topTracks.isNotEmpty()) onTrackClick(topTracks.first(), topTracks)
                                },
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.primary, contentColor = c.onPrimary),
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = "Play",
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                    }

                    // Subscriber count & monthly listeners
                    val figures = mutableListOf<String>()
                    val subText = artist?.subscriberCountText.orEmpty()
                    if (subText.isNotBlank()) figures.add(subText)
                    val listeners = artist?.monthlyListenersText.orEmpty()
                    if (listeners.isNotBlank()) figures.add(listeners)

                    if (figures.isNotEmpty()) {
                        Text(
                            text = figures.joinToString(" • "),
                            fontSize = 13.sp,
                            color = c.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 12.dp),
                        )
                    }

                    // Expandable Bio
                    val bio = artist?.description.orEmpty()
                    if (bio.isNotBlank()) {
                        Text(
                            text = bio,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = c.onSurfaceVariant,
                            maxLines = if (isBioExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isBioExpanded = !isBioExpanded }
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 16.dp)
                                .animateContentSize(),
                        )
                    }
                }
            }

            if (isLoading) {
                item(key = "loading_indicator") {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        MaterialLoader(color = c.primary, size = 36.dp)
                    }
                }
            }

            // Popular top tracks (1..5)
            val topTracks = artist?.topTracks.orEmpty()
            if (topTracks.isNotEmpty()) {
                item(key = "header_popular") {
                    SectionHeader("Popular")
                }
                itemsIndexed(topTracks.take(5), key = { idx, tr -> "popular_${tr.id}_$idx" }) { index, track ->
                    val isPlayingThis = MusicPlayerState.currentTrack?.id == track.id
                    val isPlaybackActive = isPlayingThis && MusicPlayerState.isPlaying

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isPlayingThis) c.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
                            .pressable(
                                onLongClick = { trackOptionsFor = track },
                                onClick = { onTrackClick(track, topTracks) }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Index or Animated Equalizer
                        Box(
                            modifier = Modifier.width(28.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (isPlayingThis) {
                                AnimatedEqualizer(isPlaying = MusicPlayerState.isPlaying, color = c.primary, size = 16.dp)
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isPlayingThis) c.primary else c.onSurfaceVariant,
                                )
                            }
                        }

                        // Thumbnail
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.surfaceContainerHigh),
                        ) {
                            AsyncImage(
                                model = track.thumbnailUrl,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        // Title & duration
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (isPlayingThis) c.primary else c.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = track.duration.ifBlank { "Song" },
                                fontSize = 12.sp,
                                color = c.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Options
                        IconButton(
                            onClick = { trackOptionsFor = track },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "More",
                                tint = c.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            // Singles shelf
            val singles = artist?.singles.orEmpty()
            if (singles.isNotEmpty()) {
                item(key = "header_singles") {
                    SectionHeader("Singles")
                }
                item(key = "singles_shelf") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(bottom = 12.dp),
                    ) {
                        itemsIndexed(singles, key = { idx, item -> "singles_${item.id}_$idx" }) { _, item ->
                            PlaylistCard(
                                item = item,
                                onClick = { onAlbumClick(item) },
                                onLongClick = { albumOptionsFor = item }
                            )
                        }
                    }
                }
            }

            // Albums shelf
            val albums = artist?.albums.orEmpty()
            if (albums.isNotEmpty()) {
                item(key = "header_albums") {
                    SectionHeader("Albums")
                }
                item(key = "albums_shelf") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(bottom = 12.dp),
                    ) {
                        itemsIndexed(albums, key = { idx, item -> "albums_${item.id}_$idx" }) { _, item ->
                            PlaylistCard(
                                item = item,
                                onClick = { onAlbumClick(item) },
                                onLongClick = { albumOptionsFor = item }
                            )
                        }
                    }
                }
            }

            // Videos shelf (16:9 widescreen)
            val videos = artist?.videos.orEmpty()
            if (videos.isNotEmpty()) {
                item(key = "header_videos") {
                    SectionHeader("Videos")
                }
                item(key = "videos_shelf") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(bottom = 12.dp),
                    ) {
                        itemsIndexed(videos, key = { idx, video -> "videos_${video.id}_$idx" }) { _, video ->
                            Column(
                                modifier = Modifier
                                    .width(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .pressable(
                                        onLongClick = { trackOptionsFor = video },
                                        onClick = { onTrackClick(video, videos) }
                                    ),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(c.surfaceContainerHigh),
                                ) {
                                    AsyncImage(
                                        model = video.thumbnailUrl,
                                        contentDescription = video.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = video.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = c.onBackground,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // Featured on shelf
            val featured = artist?.featuredOn.orEmpty()
            if (featured.isNotEmpty()) {
                item(key = "header_featured") {
                    SectionHeader("Featured on")
                }
                item(key = "featured_shelf") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(bottom = 12.dp),
                    ) {
                        itemsIndexed(featured, key = { idx, item -> "featured_${item.id}_$idx" }) { _, item ->
                            PlaylistCard(
                                item = item,
                                onClick = { onAlbumClick(item) },
                                onLongClick = { albumOptionsFor = item }
                            )
                        }
                    }
                }
            }

            // Fans also like (Similar Artists in 9-sided Cookie Shape!)
            val related = artist?.relatedArtists.orEmpty()
            if (related.isNotEmpty()) {
                item(key = "header_related") {
                    SectionHeader("Fans also like")
                }
                item(key = "related_shelf") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 16.dp),
                    ) {
                        itemsIndexed(related, key = { idx, relatedArtist -> "related_${relatedArtist.id}_$idx" }) { _, relatedArtist ->
                            Column(
                                modifier = Modifier
                                    .width(96.dp)
                                    .pressable(
                                        onLongClick = {
                                            artistOptionsFor = MusicArtist(
                                                id = relatedArtist.id,
                                                name = relatedArtist.name,
                                                thumbnailUrl = relatedArtist.thumbnailUrl
                                            )
                                        },
                                        onClick = { onArtistClick(relatedArtist.id) }
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(86.dp)
                                        .clip(artistShape)
                                        .background(c.surfaceContainerHigh),
                                ) {
                                    AsyncImage(
                                        model = relatedArtist.thumbnailUrl,
                                        contentDescription = relatedArtist.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = relatedArtist.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = c.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating dynamic Top Bar (seamlessly blends over hero image at top)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.background.copy(alpha = topBarAlpha))
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (topBarAlpha < 0.5f) Color.Black.copy(alpha = 0.35f) else Color.Transparent,
                            CircleShape
                        ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = if (topBarAlpha < 0.5f) Color.White else c.onBackground,
                    )
                }

                Text(
                    text = displayName,
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = c.onBackground.copy(alpha = (topBarAlpha - 0.3f).coerceAtLeast(0f) / 0.7f),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = {
                            val shareUrl = "https://music.youtube.com/channel/$channelId"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "$displayName - $shareUrl")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share"))
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (topBarAlpha < 0.5f) Color.Black.copy(alpha = 0.35f) else Color.Transparent,
                                CircleShape
                            ),
                    ) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = if (topBarAlpha < 0.5f) Color.White else c.onBackground,
                        )
                    }
                    IconButton(
                        onClick = {
                            artistOptionsFor = MusicArtist(id = channelId, name = displayName, thumbnailUrl = heroImageUrl)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (topBarAlpha < 0.5f) Color.Black.copy(alpha = 0.35f) else Color.Transparent,
                                CircleShape
                            ),
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "Options",
                            tint = if (topBarAlpha < 0.5f) Color.White else c.onBackground,
                        )
                    }
                }
            }
        }

        // ── iMusic Track, Album & Artist Options Sheets ──
        trackOptionsFor?.let { track ->
            MusicTrackOptionsSheet(
                track = track,
                onDismiss = { trackOptionsFor = null },
                onGoToArtist = { artistId ->
                    onArtistClick(artistId)
                }
            )
        }

        albumOptionsFor?.let { album ->
            MusicAlbumOptionsSheet(
                playlist = album,
                onDismiss = { albumOptionsFor = null },
                onGoToArtist = { artistId ->
                    onArtistClick(artistId)
                }
            )
        }

        artistOptionsFor?.let { artistItem ->
            MusicArtistOptionsSheet(
                artist = artistItem,
                onDismiss = { artistOptionsFor = null },
                onViewArtist = {
                    if (artistItem.id != channelId) {
                        onArtistClick(artistItem.id)
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontFamily = RoundedHeavy,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun PlaylistCard(
    item: MusicPlaylistRef,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(12.dp))
            .pressable(onLongClick = onLongClick, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val author = item.author
        val year = item.year.orEmpty()
        if (author.isNotBlank() || year.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = listOf(author, year).filter { it.isNotBlank() }.joinToString(" • "),
                fontSize = 11.sp,
                color = c.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
