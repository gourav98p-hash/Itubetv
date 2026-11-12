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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.github.itube.MusicTrack
import com.github.itube.RoundedHeavy
import com.github.itube.CachedMusicTrack
import com.github.itube.MusicCacheManager
import com.github.itube.pressable
import com.github.itube.withTop

/**
 * Dedicated Cached Music screen.
 * Displays all offline audio songs saved locally, with complete metadata,
 * playback controls, queue building, and long-press contextual actions:
 * - Play
 * - Share link
 * - Copy link
 * - Remove from cache
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CachedMusicScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenMusic: (MusicTrack, List<MusicTrack>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val cachedList = MusicCacheManager.cachedTracks
    val totalBytes = MusicCacheManager.totalStorageBytes.longValue
    val maxLimitMB = MusicCacheManager.maxCacheLimitMB.longValue

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var selectedTrackForOptions by remember { mutableStateOf<CachedMusicTrack?>(null) }

    val filteredList = remember(cachedList, searchQuery) {
        if (searchQuery.isBlank()) cachedList.toList()
        else cachedList.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artists.contains(searchQuery, ignoreCase = true) ||
            (it.album?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    val allMusicTracks = remember(filteredList) {
        filteredList.map { it.toMusicTrack() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top App Bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = c.onSurface
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "Cached songs",
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = c.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${cachedList.size} songs • ${MusicCacheManager.formatStorageSize(totalBytes)}" +
                                if (maxLimitMB > 0) " (Limit: ${maxLimitMB}MB)" else "",
                        fontSize = 12.sp,
                        color = c.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { isSearchActive = !isSearchActive }) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "Search cached songs",
                        tint = if (isSearchActive) c.primary else c.onSurfaceVariant
                    )
                }

                if (cachedList.isNotEmpty()) {
                    IconButton(onClick = { showClearConfirmDialog = true }) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            contentDescription = "Clear all cached music",
                            tint = c.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Search Field (Animated expandable) ──
            AnimatedVisibility(
                visible = isSearchActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search cached songs or artists…", fontSize = 14.sp) },
                    singleLine = true,
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
            if (cachedList.isEmpty()) {
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
                                Icons.Rounded.OfflinePin,
                                contentDescription = null,
                                tint = c.primary,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = "No cached songs yet",
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = c.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Songs you listen to in iMusic will automatically be saved here with full audio and metadata for 100% offline playback.",
                            fontSize = 14.sp,
                            color = c.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                    item(key = "cached_actions_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (allMusicTracks.isNotEmpty()) {
                                        onOpenMusic(allMusicTracks.first(), allMusicTracks)
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
                                Spacer(Modifier.width(8.dp))
                                Text("Play all", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            FilledTonalButton(
                                onClick = {
                                    if (allMusicTracks.isNotEmpty()) {
                                        val shuffled = allMusicTracks.shuffled()
                                        onOpenMusic(shuffled.first(), shuffled)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Shuffle", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    // Cached items count
                    item(key = "cached_count_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (searchQuery.isBlank()) "ALL CACHED SONGS (${filteredList.size})"
                                else "SEARCH RESULTS (${filteredList.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.onSurfaceVariant,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    // Song List Items
                    itemsIndexed(
                        items = filteredList,
                        key = { idx, item -> "cached_${item.id}_$idx" }
                    ) { _, item ->
                        CachedSongRow(
                            track = item,
                            onClick = {
                                val currentIdx = allMusicTracks.indexOfFirst { it.id == item.id }
                                val queue = if (currentIdx >= 0) {
                                    allMusicTracks.drop(currentIdx) + allMusicTracks.take(currentIdx)
                                } else allMusicTracks
                                onOpenMusic(item.toMusicTrack(), queue)
                            },
                            onLongClick = {
                                selectedTrackForOptions = item
                            },
                            onOptionsClick = {
                                selectedTrackForOptions = item
                            }
                        )
                    }
                }
            }
        }
    }

    // ── Long-Press / Options Bottom Sheet ──
    selectedTrackForOptions?.let { track ->
        CachedTrackOptionsSheet(
            track = track,
            onDismiss = { selectedTrackForOptions = null },
            onPlay = {
                val currentIdx = allMusicTracks.indexOfFirst { it.id == track.id }
                val queue = if (currentIdx >= 0) {
                    allMusicTracks.drop(currentIdx) + allMusicTracks.take(currentIdx)
                } else allMusicTracks
                onOpenMusic(track.toMusicTrack(), queue)
                selectedTrackForOptions = null
            },
            onShare = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, track.title)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Listen to \"${track.title}\" by ${track.artists}\nhttps://music.youtube.com/watch?v=${track.id}"
                    )
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share song"))
                selectedTrackForOptions = null
            },
            onCopyLink = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Song Link", "https://music.youtube.com/watch?v=${track.id}")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                selectedTrackForOptions = null
            },
            onRemove = {
                MusicCacheManager.removeCachedTrack(track.id)
                Toast.makeText(context, "Removed from cached songs", Toast.LENGTH_SHORT).show()
                selectedTrackForOptions = null
            }
        )
    }

    // ── Clear All Confirmation Dialog ──
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text("Clear cached songs?", fontFamily = RoundedHeavy, fontWeight = FontWeight.Bold)
            },
            text = {
                Text("This will permanently delete all ${cachedList.size} offline audio songs (${MusicCacheManager.formatStorageSize(totalBytes)}) from your device storage.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        MusicCacheManager.clearAllCache()
                        showClearConfirmDialog = false
                        Toast.makeText(context, "Cached audio cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.error, contentColor = c.onError)
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Single row item for a cached music track with thumbnail, title, artists,
 * offline badge, file size, and duration.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CachedSongRow(
    track: CachedMusicTrack,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with Rounded Corners & Offline Indicator
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceContainerHigh)
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Small badge in corner
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.OfflinePin,
                    contentDescription = "Cached",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // Title, Artists, File Size & Duration
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = track.artists,
                    fontSize = 12.sp,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "•",
                    fontSize = 12.sp,
                    color = c.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = MusicCacheManager.formatStorageSize(track.fileSizeBytes),
                    fontSize = 11.sp,
                    color = c.primary,
                    fontWeight = FontWeight.Medium
                )
                if (track.durationSec > 0) {
                    Text(
                        text = "•",
                        fontSize = 12.sp,
                        color = c.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = com.github.itube.formatDuration(track.durationSec * 1000L),
                        fontSize = 11.sp,
                        color = c.onSurfaceVariant
                    )
                }
            }
        }

        // More Options Button
        IconButton(
            onClick = onOptionsClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = c.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Long-press context bottom sheet for a cached song.
 * Shows:
 * 1. Play in iMusic player with full queue
 * 2. Share
 * 3. Copy link
 * 4. Remove from cache (free up storage)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CachedTrackOptionsSheet(
    track: CachedMusicTrack,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onRemove: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surfaceContainerLow,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            // Header: Thumbnail + Title + Artists + Size
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surfaceContainerHigh)
                ) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = c.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${track.artists} • ${MusicCacheManager.formatStorageSize(track.fileSizeBytes)}",
                        fontSize = 13.sp,
                        color = c.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action: Play
            CachedOptionItem(
                icon = Icons.Rounded.PlayArrow,
                title = "Play in iMusic",
                subtitle = "Start playback with cached songs queue",
                iconTint = c.primary,
                onClick = onPlay
            )

            // Action: Share
            CachedOptionItem(
                icon = Icons.Rounded.Share,
                title = "Share song",
                subtitle = "Share song link with friends",
                iconTint = c.onSurface,
                onClick = onShare
            )

            // Action: Copy Link
            CachedOptionItem(
                icon = Icons.Rounded.ContentCopy,
                title = "Copy link",
                subtitle = "Copy YouTube Music URL to clipboard",
                iconTint = c.onSurface,
                onClick = onCopyLink
            )

            // Action: Remove from Cache
            CachedOptionItem(
                icon = Icons.Rounded.Delete,
                title = "Remove from cache",
                subtitle = "Free up ${MusicCacheManager.formatStorageSize(track.fileSizeBytes)} of storage",
                iconTint = c.error,
                titleColor = c.error,
                onClick = onRemove
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CachedOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(c.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = titleColor)
            Text(text = subtitle, fontSize = 12.sp, color = c.onSurfaceVariant)
        }
    }
}
