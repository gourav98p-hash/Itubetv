package com.github.itube.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.github.itube.MusicTrack
import com.github.itube.RoundedHeavy
import com.github.itube.LikedSongsStore
import com.github.itube.MusicPlayerState
import com.github.itube.highQualityThumbnail
import com.github.itube.pressable
import com.github.itube.ui.components.AnimatedEqualizer

/**
 * Dedicated Liked Songs screen.
 * Displays all locally liked music tracks with playback controls, shuffle,
 * animated equalizer for currently playing songs, and contextual actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedMusicScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenMusic: (MusicTrack, List<MusicTrack>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme

    val likedList = LikedSongsStore.likedTracks

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredTracks = remember(likedList, searchQuery) {
        if (searchQuery.isBlank()) {
            likedList.toList()
        } else {
            val q = searchQuery.trim().lowercase()
            likedList.filter {
                it.title.lowercase().contains(q) || it.artists.lowercase().contains(q)
            }
        }
    }

    var selectedTrackForMenu by remember { mutableStateOf<MusicTrack?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Navigation Bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = c.onSurface
                    )
                }

                Spacer(Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Liked Songs",
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = c.onSurface
                    )
                    Text(
                        text = "${likedList.size} songs",
                        fontSize = 12.sp,
                        color = c.onSurfaceVariant
                    )
                }

                // Search toggle button
                IconButton(onClick = {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) searchQuery = ""
                }) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search liked songs",
                        tint = if (isSearchActive) c.primary else c.onSurfaceVariant
                    )
                }
            }

            // ── Search Input Field (when expanded) ──
            AnimatedVisibility(
                visible = isSearchActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search liked songs…", fontSize = 14.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, null, tint = c.primary, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, "Clear search", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.primary,
                        unfocusedBorderColor = c.outlineVariant,
                    )
                )
            }

            // ── Main Content Area ──
            if (likedList.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(c.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFFF4565),
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = "No liked songs yet",
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = c.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Tap the heart icon while playing any song or video in iMusic to save it to your local library.",
                            fontSize = 14.sp,
                            color = c.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding
                ) {
                    // Play All & Shuffle Action Header
                    item(key = "liked_actions_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (likedList.isNotEmpty()) {
                                        onOpenMusic(likedList.first(), likedList.toList())
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = c.primary,
                                    contentColor = c.onPrimary
                                )
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play all", fontWeight = FontWeight.SemiBold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    if (likedList.isNotEmpty()) {
                                        val shuffled = likedList.shuffled()
                                        onOpenMusic(shuffled.first(), shuffled)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Shuffle", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Track rows
                    itemsIndexed(
                        items = filteredTracks,
                        key = { _, track -> "liked_${track.id}" }
                    ) { index, track ->
                        val isPlayingThis = MusicPlayerState.currentTrack?.id == track.id
                        LikedTrackRow(
                            index = index + 1,
                            track = track,
                            isPlayingThis = isPlayingThis,
                            isPlaying = MusicPlayerState.isPlaying,
                            onClick = {
                                onOpenMusic(track, filteredTracks)
                            },
                            onLongClick = {
                                selectedTrackForMenu = track
                            },
                            onMenuClick = {
                                selectedTrackForMenu = track
                            }
                        )
                    }

                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(100.dp))
                    }
                }
            }
        }

        // ── Action Bottom Sheet ──
        selectedTrackForMenu?.let { track ->
            ModalBottomSheet(
                onDismissRequest = { selectedTrackForMenu = null },
                sheetState = sheetState,
                containerColor = c.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    // Track Info Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.thumbnailUrl?.highQualityThumbnail(),
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = c.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = track.artists,
                                fontSize = 13.sp,
                                color = c.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Play Now
                    LikedBottomSheetItem(
                        icon = Icons.Rounded.PlayArrow,
                        title = "Play now",
                        onClick = {
                            selectedTrackForMenu = null
                            onOpenMusic(track, filteredTracks)
                        }
                    )

                    // Play Next
                    LikedBottomSheetItem(
                        icon = Icons.Rounded.PlaylistAdd,
                        title = "Play next",
                        onClick = {
                            selectedTrackForMenu = null
                            MusicPlayerState.playNext(track)
                            Toast.makeText(context, "Added to play next", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // Share Link
                    LikedBottomSheetItem(
                        icon = Icons.Rounded.Share,
                        title = "Share link",
                        onClick = {
                            selectedTrackForMenu = null
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, track.title)
                                putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${track.id}")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share song"))
                        }
                    )

                    // Copy Link
                    LikedBottomSheetItem(
                        icon = Icons.Rounded.ContentCopy,
                        title = "Copy link",
                        onClick = {
                            selectedTrackForMenu = null
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("YouTube Link", "https://youtu.be/${track.id}"))
                            Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // Remove from Liked songs
                    LikedBottomSheetItem(
                        icon = Icons.Rounded.Delete,
                        title = "Remove from Liked songs",
                        tint = Color(0xFFFF4565),
                        onClick = {
                            selectedTrackForMenu = null
                            LikedSongsStore.remove(track.id)
                            Toast.makeText(context, "Removed from Liked songs", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun LikedTrackRow(
    index: Int,
    track: MusicTrack,
    isPlayingThis: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isPlayingThis) c.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
            .pressable(
                onLongClick = onLongClick,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Index or Animated Equalizer
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isPlayingThis) {
                AnimatedEqualizer(
                    isPlaying = isPlaying,
                    color = c.primary,
                    size = 16.dp
                )
            } else {
                Text(
                    text = "$index",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.onSurfaceVariant
                )
            }
        }

        // Cover Art Thumbnail
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surfaceContainerHigh)
        ) {
            AsyncImage(
                model = track.thumbnailUrl?.highQualityThumbnail(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.width(12.dp))

        // Title & Artists
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlayingThis) c.primary else c.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artists,
                    fontSize = 12.sp,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.duration.isNotEmpty()) {
                    Text(
                        text = " • ${track.duration}",
                        fontSize = 12.sp,
                        color = c.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        // 3-dot Overflow Menu Button
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = c.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun LikedBottomSheetItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}
