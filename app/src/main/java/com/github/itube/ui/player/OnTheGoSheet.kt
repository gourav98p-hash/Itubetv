package com.github.itube

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.itube.ui.components.MaterialLoader
import kotlinx.coroutines.launch

/**
 * Pill shown above the player timestamp to summon the On-the-go premium playback popup.
 */
@Composable
fun OnTheGoPill(
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.68f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .padding(start = 10.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.pressable(pressedScale = 0.95f, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Tune,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "On-the-go",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
            )
        }
        Spacer(Modifier.width(7.dp))
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Dismiss",
                tint = Color.White,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

/**
 * YouTube Premium-style "On-the-go" panel inside PlayerScreen.
 * Exactly like Description and Comments:
 * - NO dim scrim: video remains visible and un-dimmed above the panel.
 * - Tapping outside (on the video) interacts with the video and does NOT close the panel.
 * - Panel container background is plain white in light mode, surface in dark mode (exact match to Description).
 * - Compact mode rests at partialTop (~70% screen height, just under the video).
 * - Audio-only mode smoothly expands to 100% full screen with 16:9 thumbnail.
 */
@Composable
fun OnTheGoPanel(
    state: PlayerState,
    partialTop: Dp,
    onClose: () -> Unit,
    onDownload: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()

    var isAudioMode by remember { mutableStateOf(state.onTheGoAudioMode) }
    var showSpeed by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }

    val hasPrevious = state.positionMs > 3_000L || state.controller?.hasPreviousMediaItem() == true
    val hasNext = state.queue.isNotEmpty() || state.controller?.hasNextMediaItem() == true

    val containerColor = if (isDark) c.surface else Color.White
    val contentColor = if (isDark) c.onSurface else Color(0xFF1F1F1F)
    val subtitleColor = if (isDark) c.onSurfaceVariant else Color(0xFF5F6368)
    val playButtonBg = if (isDark) c.surfaceContainerHighest else Color(0xFFF1F3F4)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        val fullPx = with(density) { (maxHeight - statusTop - 4.dp).toPx() }
        val partialPx = with(density) { (maxHeight - partialTop).toPx() }.coerceIn(fullPx * 0.35f, fullPx)

        val height = remember { Animatable(if (isAudioMode) fullPx else partialPx) }
        val slide = remember { Animatable(1f) }

        LaunchedEffect(Unit) {
            if (reduced) slide.snapTo(0f) else slide.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 550f))
        }

        fun close() = scope.launch {
            if (!reduced) slide.animateTo(1f, tween(240, easing = Motion.Emphasized))
            onClose()
        }

        BackHandler { close() }

        fun dragBy(dy: Float): Float {
            val before = height.value
            val after = (before - dy).coerceIn(partialPx * 0.25f, fullPx)
            scope.launch { height.snapTo(after) }
            return before - after
        }

        fun settle(velocity: Float) {
            val h = height.value
            when {
                // Dragged down past 30%, or flicked down hard: close
                h < partialPx * 0.7f || velocity > 1200f && h < partialPx - 1f || velocity > 1800f && h <= partialPx + 1f -> close()
                velocity < -1200f -> {
                    isAudioMode = true
                    state.onTheGoAudioMode = true
                    scope.launch { height.animateTo(fullPx, spring(dampingRatio = 0.86f, stiffness = 550f)) }
                }
                velocity > 1200f -> {
                    isAudioMode = false
                    state.onTheGoAudioMode = false
                    scope.launch { height.animateTo(partialPx, spring(dampingRatio = 0.86f, stiffness = 550f)) }
                }
                else -> {
                    val target = if (isAudioMode) fullPx else if (h > (partialPx + fullPx) / 2f) fullPx else partialPx
                    scope.launch { height.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 550f)) }
                }
            }
        }

        val topRadius by animateDpAsState(
            targetValue = if (isAudioMode) 0.dp else 28.dp,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
            label = "topRadius",
        )
        val safeRadius = topRadius.coerceAtLeast(0.dp)
        val shape = RoundedCornerShape(topStart = safeRadius, topEnd = safeRadius)

        // Column aligned at bottom. Notice: NO DIM BACKGROUND SCRIM at all!
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(density) { height.value.toDp() })
                .graphicsLayer { translationY = slide.value * size.height }
                .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
                .clip(shape)
                .background(containerColor)
                // Swallow taps inside the panel so nothing underneath reacts through it
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                }
                .imePadding()
                .padding(bottom = navBottom.coerceAtLeast(8.dp)),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Drag handle & Header Row (the draggable area)
            Column(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = rememberDraggableState { dragBy(it) },
                        orientation = Orientation.Vertical,
                        onDragStopped = { v -> settle(v) },
                    )
            ) {
                if (isAudioMode) {
                    Spacer(Modifier.height(statusTop.coerceAtLeast(6.dp)))
                }

                // Drag handle pill
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 4.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (isDark) c.outlineVariant.copy(alpha = 0.5f) else Color(0xFFD3D3D3))
                )

                // Header Row: Red "P" badge, "On-the-go" title, Close "X" button
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = if (isAudioMode) 6.dp else 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF0000)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "P",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                fontFamily = RoundedHeavy,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "On-the-go",
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = contentColor,
                            letterSpacing = (-0.4).sp,
                        )
                    }
                    IconButton(
                        onClick = { close() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = contentColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            val activeItem = state.details?.item ?: state.current

            // In Audio Mode: Display large 16:9 Thumbnail with rounded corners
            if (isAudioMode) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isDark) c.surfaceContainerHigh else Color(0xFFE8EAED)),
                ) {
                    Thumbnail(activeItem?.thumbnail, Modifier.fillMaxSize(), radius = 0)
                }
            }

            // Video Details (Title & Channel)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = if (isAudioMode) 4.dp else 8.dp)
            ) {
                Text(
                    text = activeItem?.title.orEmpty(),
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isAudioMode) 20.sp else 19.sp,
                    lineHeight = 25.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = activeItem?.channel.orEmpty(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.weight(1f))

            // Timestamp and Progress Scrubber — themed custom seek bar matching the video player
            Column(Modifier.fillMaxWidth()) {
                var draggingFrac by remember { mutableStateOf<Float?>(null) }
                var isDragging by remember { mutableStateOf(false) }
                val totalDuration = if (state.durationMs > 0) state.durationMs else 1L
                val liveFrac = (state.positionMs.toFloat() / totalDuration).coerceIn(0f, 1f)
                val displayFrac = draggingFrac ?: liveFrac
                val displayMs = (displayFrac * totalDuration).toLong()

                // Timestamp row
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatDuration(displayMs),
                        color = subtitleColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatDuration(state.durationMs),
                        color = subtitleColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Custom themed seek bar
                val trackColor = c.onSurface.copy(alpha = 0.18f)
                val progressColor = c.primary
                val thumbRadius = if (isDragging) 9.dp else 7.dp
                val thumbRadiusPx = with(LocalDensity.current) { thumbRadius.toPx() }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(28.dp)
                        .pointerInput(totalDuration) {
                            detectHorizontalDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = {
                                    isDragging = false
                                    draggingFrac?.let { state.controller?.seekTo((it * totalDuration).toLong()) }
                                    draggingFrac = null
                                },
                                onDragCancel = { isDragging = false; draggingFrac = null },
                                onHorizontalDrag = { change, _ ->
                                    val w = size.width.toFloat()
                                    draggingFrac = (change.position.x / w).coerceIn(0f, 1f)
                                }
                            )
                        }
                        .pointerInput(totalDuration) {
                            detectTapGestures { offset ->
                                val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                state.controller?.seekTo((frac * totalDuration).toLong())
                            }
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                        val trackY = size.height / 2f
                        val trackH = 3.dp.toPx()
                        val trackStart = thumbRadiusPx
                        val trackEnd = size.width - thumbRadiusPx
                        val trackWidth = trackEnd - trackStart
                        // Inactive track
                        drawRoundRect(
                            color = trackColor,
                            topLeft = androidx.compose.ui.geometry.Offset(trackStart, trackY - trackH / 2),
                            size = androidx.compose.ui.geometry.Size(trackWidth, trackH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2),
                        )
                        // Active track
                        val filled = (trackWidth * displayFrac).coerceAtLeast(0f)
                        if (filled > 0f) {
                            drawRoundRect(
                                color = progressColor,
                                topLeft = androidx.compose.ui.geometry.Offset(trackStart, trackY - trackH / 2),
                                size = androidx.compose.ui.geometry.Size(filled, trackH),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2),
                            )
                        }
                        // Thumb
                        val thumbX = trackStart + trackWidth * displayFrac
                        drawCircle(
                            color = progressColor,
                            radius = thumbRadiusPx,
                            center = androidx.compose.ui.geometry.Offset(thumbX, trackY),
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Primary Playback Controls Row
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (isAudioMode) 6.dp else 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { state.playPrevious() },
                    enabled = hasPrevious,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = if (hasPrevious) contentColor else contentColor.copy(alpha = 0.35f),
                        modifier = Modifier.size(34.dp),
                    )
                }

                IconButton(
                    onClick = {
                        val target = (state.positionMs - 10_000L).coerceAtLeast(0L)
                        state.controller?.seekTo(target)
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.Replay10,
                        contentDescription = "Rewind 10 seconds",
                        tint = contentColor,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(playButtonBg)
                        .pressable(pressedScale = 0.93f) { state.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBuffering || (state.loading && state.details == null)) {
                        MaterialLoader(color = contentColor, size = 32.dp)
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = contentColor,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val maxTime = if (state.durationMs > 0) state.durationMs else Long.MAX_VALUE
                        val target = (state.positionMs + 10_000L).coerceAtMost(maxTime)
                        state.controller?.seekTo(target)
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.Forward10,
                        contentDescription = "Forward 10 seconds",
                        tint = contentColor,
                        modifier = Modifier.size(32.dp),
                    )
                }

                IconButton(
                    onClick = { state.playNextUp() },
                    enabled = hasNext,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = if (hasNext) contentColor else contentColor.copy(alpha = 0.35f),
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Bottom Action Bar: Download, Mode Toggle, Speed, Queue
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 4.dp, bottom = if (isAudioMode) 14.dp else 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (onDownload != null) onDownload() else showDownload = true
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = "Download", tint = contentColor, modifier = Modifier.size(24.dp))
                }
                IconButton(
                    onClick = {
                        isAudioMode = !isAudioMode
                        state.onTheGoAudioMode = isAudioMode
                        scope.launch {
                            val target = if (isAudioMode) fullPx else partialPx
                            height.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 550f))
                        }
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        if (isAudioMode) Icons.Rounded.SmartDisplay else Icons.Rounded.Headphones,
                        contentDescription = if (isAudioMode) "Switch to video view" else "Switch to audio mode",
                        tint = contentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = { showSpeed = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Speed, contentDescription = "Speed", tint = contentColor, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { showQueue = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = contentColor, modifier = Modifier.size(24.dp))
                }
            }
        }
    }

    if (showDownload) {
        DownloadSheet(state, onDismiss = { showDownload = false })
    }
    if (showSpeed) {
        SpeedSheet(state, onDismiss = { showSpeed = false })
    }
    if (showQueue) {
        QueueSheet(state, onDismiss = { showQueue = false })
    }
}

/**
 * Top-level sheet wrapper if summoned from places outside PlayerScreen.
 */
@Composable
fun OnTheGoSheet(
    state: PlayerState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit = {},
) {
    val partialTop = panelRestTop(state)
    OnTheGoPanel(
        state = state,
        partialTop = partialTop,
        onClose = onDismiss,
        onDownload = onDownload,
    )
}
