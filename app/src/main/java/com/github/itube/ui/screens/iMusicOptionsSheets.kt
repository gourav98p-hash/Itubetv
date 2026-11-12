package com.github.itube.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.github.itube.MusicCacheManager
import com.github.itube.RoundedHeavy
import com.github.itube.MusicArtist
import com.github.itube.MusicBackend
import com.github.itube.MusicPlayerState
import com.github.itube.MusicPlaylistRef
import com.github.itube.MusicTrack
import com.github.itube.pressable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Premium iMusic Song/Track Options Bottom Sheet.
 * Provides Play Next, Add to Queue, Play Last, Download/Cache Offline, Start Radio,
 * Go to Artist, Copy Link, Share, and Remove options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicTrackOptionsSheet(
    track: MusicTrack,
    onDismiss: () -> Unit,
    onGoToArtist: ((String) -> Unit)? = null,
    isCachedView: Boolean = false,
    onRemoveFromCache: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isCached = remember(track.id) { MusicCacheManager.isTrackCached(track.id) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surfaceContainerLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // Header: Thumbnail + Title + Artists
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(6.dp, RoundedCornerShape(12.dp))
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
                        text = track.artists.ifBlank { "Song" },
                        fontSize = 13.sp,
                        color = c.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = c.outlineVariant.copy(alpha = 0.35f)
            )
            Spacer(Modifier.height(8.dp))

            // Action: Play Next
            SheetOptionRow(
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                title = "Play next",
                subtitle = "Insert directly after current song",
                iconTint = c.primary,
                onClick = {
                    MusicPlayerState.playNext(track)
                    Toast.makeText(context, "Playing next: ${track.title}", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            // Action: Add to Queue
            SheetOptionRow(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = "Add to queue",
                subtitle = "Append to the end of playing queue",
                iconTint = c.primary,
                onClick = {
                    MusicPlayerState.addToQueue(track)
                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            // Action: Play Last
            SheetOptionRow(
                icon = Icons.Rounded.LowPriority,
                title = "Play last",
                subtitle = "Queue at the very end of list",
                iconTint = c.secondary,
                onClick = {
                    MusicPlayerState.playLast(track)
                    Toast.makeText(context, "Queued to play last", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            // Action: Download / Cache Offline
            SheetOptionRow(
                icon = if (isCached) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                title = if (isCached) "Saved offline" else "Download / Cache offline",
                subtitle = if (isCached) "Already saved for offline playback" else "Save audio & full metadata to offline storage",
                iconTint = if (isCached) Color(0xFF4CAF50) else c.primary,
                onClick = {
                    if (isCached) {
                        Toast.makeText(context, "Already available offline in Library", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Caching \"${track.title}\" offline...", Toast.LENGTH_SHORT).show()
                        MusicCacheManager.cacheTrackOnDemand(track) { success ->
                            if (success) {
                                Toast.makeText(context, "Cached \"${track.title}\" offline", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    onDismiss()
                }
            )

            // Action: Start Radio
            SheetOptionRow(
                icon = Icons.Rounded.Radio,
                title = "Start radio",
                subtitle = "Queue similar songs and recommendations",
                iconTint = c.primary,
                onClick = {
                    Toast.makeText(context, "Starting radio from ${track.title}...", Toast.LENGTH_SHORT).show()
                    MusicPlayerState.playRadio(track)
                    onDismiss()
                }
            )

            // Action: Go to Artist (if available)
            if (onGoToArtist != null && !track.artistId.isNullOrBlank()) {
                SheetOptionRow(
                    icon = Icons.Rounded.Person,
                    title = "Go to artist",
                    subtitle = track.artists,
                    iconTint = c.onSurface,
                    onClick = {
                        onGoToArtist(track.artistId)
                        onDismiss()
                    }
                )
            }

            // Action: Copy Link
            SheetOptionRow(
                icon = Icons.Rounded.ContentCopy,
                title = "Copy link",
                subtitle = "https://music.youtube.com/watch?v=${track.id}",
                iconTint = c.onSurface,
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Song Link", "https://music.youtube.com/watch?v=${track.id}")
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Song link copied to clipboard", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            // Action: Share
            SheetOptionRow(
                icon = Icons.Rounded.Share,
                title = "Share",
                subtitle = "Share song with friends",
                iconTint = c.onSurface,
                onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, track.title)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Listen to \"${track.title}\" by ${track.artists}\nhttps://music.youtube.com/watch?v=${track.id}"
                        )
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share song"))
                    onDismiss()
                }
            )

            // Action: Remove from Cache (if in cached view or track is cached)
            if (isCachedView || isCached) {
                SheetOptionRow(
                    icon = Icons.Rounded.Delete,
                    title = "Remove from cache",
                    subtitle = "Free up device storage",
                    iconTint = c.error,
                    titleColor = c.error,
                    onClick = {
                        MusicCacheManager.removeCachedTrack(track.id)
                        onRemoveFromCache?.invoke()
                        Toast.makeText(context, "Removed from cached songs", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                )
            }
        }
    }
}

/**
 * Premium iMusic Album / Playlist Options Bottom Sheet.
 * Provides Play Next, Add to Queue, Download Album, Copy Link, Share, View Album,
 * and Go to Artist options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicAlbumOptionsSheet(
    playlist: MusicPlaylistRef,
    onDismiss: () -> Unit,
    onViewAlbum: (() -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surfaceContainerLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // Header: Thumbnail + Title + Author/Type
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(6.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surfaceContainerHigh)
                ) {
                    AsyncImage(
                        model = playlist.thumbnailUrl,
                        contentDescription = playlist.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.title,
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = c.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = listOfNotNull(
                            playlist.author.takeIf { it.isNotBlank() },
                            playlist.trackCount?.let { "$it tracks" }
                        ).joinToString(" • ").ifBlank { "Album" },
                        fontSize = 13.sp,
                        color = c.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = c.outlineVariant.copy(alpha = 0.35f)
            )
            Spacer(Modifier.height(8.dp))

            // Action: Play Next
            SheetOptionRow(
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                title = "Play next",
                subtitle = "Queue album tracks directly after current song",
                iconTint = c.primary,
                onClick = {
                    Toast.makeText(context, "Queuing album next...", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        val details = MusicBackend.fetchPlaylistDetails(playlist.id)
                        if (details != null && details.tracks.isNotEmpty()) {
                            MusicPlayerState.playNext(details.tracks)
                            Toast.makeText(context, "Playing next: ${playlist.title}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Couldn't load album tracks", Toast.LENGTH_SHORT).show()
                        }
                    }
                    onDismiss()
                }
            )

            // Action: Add to Queue
            SheetOptionRow(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = "Add to queue",
                subtitle = "Append all tracks to the queue",
                iconTint = c.primary,
                onClick = {
                    Toast.makeText(context, "Adding album to queue...", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        val details = MusicBackend.fetchPlaylistDetails(playlist.id)
                        if (details != null && details.tracks.isNotEmpty()) {
                            MusicPlayerState.addToQueue(details.tracks)
                            Toast.makeText(context, "Added ${details.tracks.size} tracks to queue", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Couldn't load album tracks", Toast.LENGTH_SHORT).show()
                        }
                    }
                    onDismiss()
                }
            )

            // Action: Download Album
            SheetOptionRow(
                icon = Icons.Rounded.Download,
                title = "Download album",
                subtitle = "Save all album tracks to offline storage",
                iconTint = c.primary,
                onClick = {
                    Toast.makeText(context, "Downloading album: ${playlist.title}...", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        val details = MusicBackend.fetchPlaylistDetails(playlist.id)
                        if (details != null && details.tracks.isNotEmpty()) {
                            details.tracks.forEach { track ->
                                MusicCacheManager.cacheTrackOnDemand(track)
                            }
                            Toast.makeText(context, "Caching ${details.tracks.size} tracks for offline playback", Toast.LENGTH_SHORT).show()
                        }
                    }
                    onDismiss()
                }
            )

            // Action: View Album
            if (onViewAlbum != null) {
                SheetOptionRow(
                    icon = Icons.Rounded.Album,
                    title = "View album",
                    subtitle = "Open full album tracklist",
                    iconTint = c.onSurface,
                    onClick = {
                        onViewAlbum()
                        onDismiss()
                    }
                )
            }

            // Action: Copy Link
            SheetOptionRow(
                icon = Icons.Rounded.ContentCopy,
                title = "Copy link",
                subtitle = "https://music.youtube.com/playlist?list=${playlist.id}",
                iconTint = c.onSurface,
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Album Link", "https://music.youtube.com/playlist?list=${playlist.id}")
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Album link copied to clipboard", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            // Action: Share
            SheetOptionRow(
                icon = Icons.Rounded.Share,
                title = "Share",
                subtitle = "Share album with friends",
                iconTint = c.onSurface,
                onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, playlist.title)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Listen to \"${playlist.title}\" on iMusic\nhttps://music.youtube.com/playlist?list=${playlist.id}"
                        )
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share album"))
                    onDismiss()
                }
            )
        }
    }
}

/**
 * Premium iMusic Artist Options Bottom Sheet.
 * Provides View Artist, Play Top Songs / Radio, Copy Link, and Share options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicArtistOptionsSheet(
    artist: MusicArtist,
    onDismiss: () -> Unit,
    onViewArtist: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surfaceContainerLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // Header: Artist Avatar + Name + Subtitle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(c.surfaceContainerHigh)
                ) {
                    AsyncImage(
                        model = artist.thumbnailUrl,
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artist.name,
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = c.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Artist",
                        fontSize = 13.sp,
                        color = c.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = c.outlineVariant.copy(alpha = 0.35f)
            )
            Spacer(Modifier.height(8.dp))

            // Action: View Artist
            if (onViewArtist != null) {
                SheetOptionRow(
                    icon = Icons.Rounded.Person,
                    title = "View artist",
                    subtitle = "Open artist profile, top tracks & albums",
                    iconTint = c.primary,
                    onClick = {
                        onViewArtist()
                        onDismiss()
                    }
                )
            }

            // Action: Play Top Songs / Radio
            SheetOptionRow(
                icon = Icons.Rounded.PlayArrow,
                title = "Play top songs",
                subtitle = "Start queue with ${artist.name}'s greatest tracks",
                iconTint = c.primary,
                onClick = {
                    Toast.makeText(context, "Loading ${artist.name} top songs...", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        val details = MusicBackend.fetchArtistDetails(artist.id)
                        val top = details?.topTracks
                        if (!top.isNullOrEmpty()) {
                            MusicPlayerState.play(top.first(), top)
                            Toast.makeText(context, "Playing ${artist.name}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No tracks available for ${artist.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    onDismiss()
                }
            )

            // Action: Copy Link
            SheetOptionRow(
                icon = Icons.Rounded.ContentCopy,
                title = "Copy link",
                subtitle = "https://music.youtube.com/channel/${artist.id}",
                iconTint = c.onSurface,
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Artist Link", "https://music.youtube.com/channel/${artist.id}")
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Artist link copied to clipboard", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            // Action: Share
            SheetOptionRow(
                icon = Icons.Rounded.Share,
                title = "Share",
                subtitle = "Share artist with friends",
                iconTint = c.onSurface,
                onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, artist.name)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Check out ${artist.name} on iMusic\nhttps://music.youtube.com/channel/${artist.id}"
                        )
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share artist"))
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun SheetOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = titleColor
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
