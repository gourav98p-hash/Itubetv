package com.github.itube

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.github.itube.ui.components.MaterialLoader
import com.github.itube.ui.components.TrackBlurPopup
import com.github.itube.ui.components.popupContainerColor
import com.github.itube.ui.components.popupScrimColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(state: PlayerState, onDismiss: () -> Unit) {
    val video = state.current ?: return
    DownloadSheet(video = video, description = state.details?.description.orEmpty(), onDismiss = onDismiss)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(video: VideoItem, description: String = "", onDismiss: () -> Unit) {
    TrackBlurPopup()
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme

    var options by remember(video.id) { mutableStateOf<List<DownloadOption>?>(null) }
    var loading by remember(video.id) { mutableStateOf(true) }
    var loadError by remember(video.id) { mutableStateOf<String?>(null) }
    var pendingOption by remember { mutableStateOf<DownloadOption?>(null) }
    var resolvedDescription by remember(video.id) { mutableStateOf(description) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val opt = pendingOption
        if (opt != null) {
            VideoDownloader.download(context, video, resolvedDescription, opt)
            Toast.makeText(context, "Download started… check notification", Toast.LENGTH_SHORT).show()
            pendingOption = null
            onDismiss()
        }
    }

    fun startDownload(opt: DownloadOption) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingOption = opt
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            VideoDownloader.download(context, video, resolvedDescription, opt)
            Toast.makeText(context, "Download started… check notification", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    LaunchedEffect(video.id) {
        loading = true
        loadError = null
        val info = try {
            withContext(Dispatchers.IO) {
                StreamCache.get(video.id) ?: StreamCache.getOrLoad(video.id)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            loadError = error.message?.takeIf { it.isNotBlank() } ?: "Couldn't read download streams for this video."
            null
        }
        if (info != null) {
            try {
                options = VideoDownloader.extractOptions(info)
                if (options.isNullOrEmpty()) loadError = "YouTube didn't provide downloadable qualities for this video."
                if (resolvedDescription.isBlank()) {
                    resolvedDescription = info.description?.let { description ->
                        if (description.type == org.schabi.newpipe.extractor.stream.Description.HTML) {
                            androidx.core.text.HtmlCompat.fromHtml(description.content, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                        } else description.content.trim()
                    }.orEmpty()
                }
            } catch (error: Exception) {
                options = emptyList()
                loadError = error.message?.takeIf { it.isNotBlank() } ?: "Couldn't read download qualities for this video."
            }
        }
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = popupContainerColor(c.surfaceContainerLow),
        scrimColor = popupScrimColor(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // Header: Title and Video snippet
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Thumbnail(video.thumbnail, Modifier.matchParentSize())
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = video.channel,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Download",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
            )

            Spacer(Modifier.height(12.dp))

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialLoader(color = c.primary, size = 38.dp)
                }
            } else {
                val opts = options.orEmpty()
                val videoOptions = opts.filterIsInstance<DownloadOption.Video>()
                val audioOptions = opts.filterIsInstance<DownloadOption.Audio>()

                if (opts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            loadError ?: "No download streams available for this video",
                            color = c.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    if (videoOptions.isNotEmpty()) {
                        Text(
                            "VIDEO",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = c.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        videoOptions.forEach { opt ->
                            DownloadOptionCard(
                                title = "${opt.height}p",
                                subtitle = "${opt.formatLabel}${opt.approxSize?.let { " · ~$it" } ?: ""}",
                                isHd = opt.height >= 720,
                                isVideo = true,
                                onClick = { startDownload(opt) }
                            )
                        }
                    }

                    if (audioOptions.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "AUDIO ONLY",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = c.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        audioOptions.forEach { opt ->
                            DownloadOptionCard(
                                title = opt.label,
                                subtitle = "${opt.formatLabel}${opt.approxSize?.let { " · ~$it" } ?: ""}",
                                isHd = opt.bitrate >= 160,
                                isVideo = false,
                                onClick = { startDownload(opt) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DownloadOptionCard(
    title: String,
    subtitle: String,
    isHd: Boolean,
    isVideo: Boolean,
    onClick: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(c.surfaceContainer)
            .border(1.dp, c.outlineVariant.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isVideo) c.primaryContainer else c.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isVideo) {
                    if (isHd) Icons.Rounded.HighQuality else Icons.Rounded.Videocam
                } else {
                    if (isHd) Icons.Rounded.Headphones else Icons.Rounded.MusicNote
                },
                contentDescription = null,
                tint = if (isVideo) c.onPrimaryContainer else c.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = c.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = c.onSurfaceVariant
            )
        }

        Icon(
            painter = painterResource(R.drawable.ic_download),
            contentDescription = "Download",
            tint = c.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}
