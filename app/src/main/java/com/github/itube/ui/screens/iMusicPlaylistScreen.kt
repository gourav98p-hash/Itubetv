package com.github.itube.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.github.itube.MusicBackend
import com.github.itube.MusicPlayerState
import com.github.itube.MusicTrack
import com.github.itube.PlaylistDetails
import com.github.itube.RoundedHeavy
import com.github.itube.MusicPlaylistRef
import com.github.itube.pressable
import com.github.itube.ui.components.AnimatedEqualizer
import com.github.itube.ui.components.MaterialLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Flow-style Music Collection / Album / Playlist Screen.
 * Faithful reproduction of Flow's MusicCollectionScreen and MusicCollectionHeader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun iMusicPlaylistScreen(
    playlistId: String,
    initialTitle: String = "",
    initialAuthor: String = "",
    initialThumbnail: String? = null,
    isAlbum: Boolean = false,
    onBackClick: () -> Unit,
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val scrollState = rememberLazyListState()

    var details by remember(playlistId) { mutableStateOf<PlaylistDetails?>(null) }
    var isLoading by remember(playlistId) { mutableStateOf(true) }
    var isBookmarked by remember { mutableStateOf(false) }
    var trackOptionsFor by remember { mutableStateOf<MusicTrack?>(null) }
    var albumOptionsFor by remember { mutableStateOf<MusicPlaylistRef?>(null) }

    LaunchedEffect(playlistId) {
        withContext(Dispatchers.IO) {
            val fetched = MusicBackend.fetchPlaylistDetails(playlistId, isAlbum)
            details = fetched
            isLoading = false
        }
    }

    val displayTitle = details?.title?.takeIf { it.isNotBlank() } ?: initialTitle
    val displayAuthor = details?.author?.takeIf { it.isNotBlank() } ?: initialAuthor
    val displayThumb = details?.thumbnailUrl?.takeIf { it.isNotBlank() } ?: initialThumbnail
    val tracks = details?.tracks.orEmpty()

    val showTopBarTitle by remember {
        derivedStateOf { scrollState.firstVisibleItemIndex > 0 || scrollState.firstVisibleItemScrollOffset > 300 }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = c.background,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = showTopBarTitle,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Text(
                            text = displayTitle,
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = c.onBackground,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = c.onBackground,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareUrl = if (isAlbum) "https://music.youtube.com/browse/$playlistId" else "https://music.youtube.com/playlist?list=$playlistId"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "$displayTitle - $shareUrl")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share"))
                    }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share", tint = c.onBackground)
                    }
                    IconButton(onClick = {
                        albumOptionsFor = MusicPlaylistRef(
                            id = playlistId,
                            title = displayTitle,
                            author = displayAuthor,
                            thumbnailUrl = displayThumb
                        )
                    }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Options", tint = c.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
    ) { padding ->
        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = navBottom + 110.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header: Cover artwork, Title, Author, Metadata, Buttons
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Artwork
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.25f))
                            .clip(RoundedCornerShape(20.dp))
                            .background(c.surfaceContainerHigh),
                    ) {
                        AsyncImage(
                            model = displayThumb,
                            contentDescription = displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Title
                    Text(
                        text = displayTitle,
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        color = c.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(6.dp))

                    // Author
                    if (displayAuthor.isNotBlank()) {
                        Text(
                            text = displayAuthor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = c.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val authorId = details?.authorId.orEmpty()
                                    if (authorId.isNotBlank()) onArtistClick(authorId)
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    // Metadata
                    val metaParts = mutableListOf<String>()
                    metaParts.add(if (isAlbum) "Album" else "Playlist")
                    if (tracks.isNotEmpty()) metaParts.add("${tracks.size} songs")
                    details?.year?.let { if (it.isNotBlank()) metaParts.add(it) }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = metaParts.joinToString(" • "),
                        fontSize = 13.sp,
                        color = c.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(20.dp))

                    // Action buttons: Play, Shuffle, Download, Bookmark
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Play Button
                        Button(
                            onClick = {
                                if (tracks.isNotEmpty()) onTrackClick(tracks.first(), tracks)
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                            modifier = Modifier.height(44.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(Modifier.width(10.dp))

                        // Shuffle Button
                        FilledTonalButton(
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    val shuffled = tracks.shuffled()
                                    onTrackClick(shuffled.first(), shuffled)
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier.height(44.dp),
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }

                        Spacer(Modifier.width(10.dp))

                        // Bookmark Button
                        FilledTonalIconButton(
                            onClick = { isBookmarked = !isBookmarked },
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                contentDescription = "Save",
                                tint = if (isBookmarked) c.primary else c.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        MaterialLoader(color = c.primary, size = 36.dp)
                    }
                }
            } else {
                itemsIndexed(tracks, key = { index, track -> "track_${track.id}_$index" }) { index, track ->
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
                                onClick = { onTrackClick(track, tracks) }
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

                        // Title & Artist
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
                                text = track.artists + if (track.duration.isNotBlank()) " • ${track.duration}" else "",
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
        }

        // ── Track & Album Options Sheets ──
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
    }
}
