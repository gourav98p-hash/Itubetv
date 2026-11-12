package com.github.itube.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import com.github.itube.ui.components.BlurSetting
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.github.itube.MusicPlayerState
import com.github.itube.highQualityThumbnail
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dedicated Music Mini-Player (AirBeats New Classic Nav Only Mini Player).
 * Floats immediately above the bottom navigation bar. Completely independent from video player.
 */
@Composable
fun NewClassicMiniPlayer(
    modifier: Modifier = Modifier,
    onExpand: () -> Unit = { MusicPlayerState.expanded = true },
) {
    val track = MusicPlayerState.currentTrack ?: return
    val isPlaying = MusicPlayerState.isPlaying
    val position = MusicPlayerState.positionMs
    val duration = MusicPlayerState.durationMs
    val coroutineScope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current

    val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    val offsetXAnimatable = remember { Animatable(0f) }
    var dragStartTime by remember { mutableLongStateOf(0L) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }

    // Rotating cookie play/pause button animation (smooth rotation while playing)
    val cookieRotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                val current = cookieRotation.value % 360f
                cookieRotation.snapTo(current)
                cookieRotation.animateTo(
                    targetValue = current + 360f,
                    animationSpec = tween(durationMillis = 6000, easing = LinearEasing)
                )
            }
        }
    }

    val animationSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .padding(horizontal = 14.dp)
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragStartTime = System.currentTimeMillis()
                        totalDragDistance = 0f
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            offsetXAnimatable.animateTo(targetValue = 0f, animationSpec = animationSpec)
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val adjusted = if (layoutDirection == LayoutDirection.Rtl) -dragAmount else dragAmount
                        totalDragDistance += abs(adjusted)
                        coroutineScope.launch {
                            offsetXAnimatable.snapTo(offsetXAnimatable.value + adjusted)
                        }
                    },
                    onDragEnd = {
                        val dragDuration = System.currentTimeMillis() - dragStartTime
                        val velocity = if (dragDuration > 0) totalDragDistance / dragDuration else 0f
                        val currentOffset = offsetXAnimatable.value

                        val shouldChangeSong = (abs(currentOffset) > 60f && velocity > 0.5f) || abs(currentOffset) > 160f
                        if (shouldChangeSong) {
                            if (currentOffset > 0) {
                                MusicPlayerState.skipPrevious()
                            } else {
                                MusicPlayerState.skipNext()
                            }
                        }

                        coroutineScope.launch {
                            offsetXAnimatable.animateTo(targetValue = 0f, animationSpec = animationSpec)
                        }
                    }
                )
            }
    ) {
        val isBlurEnabled = BlurSetting.enabled
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val miniPlayerBgColor = if (isBlurEnabled) {
            if (isDark) Color(0xFF252525).copy(alpha = 0.68f) else Color(0xFFF0F0F3).copy(alpha = 0.78f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
        }
        val miniPlayerBorderColor = if (isBlurEnabled) {
            if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        }

        // Inner sliding container
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetXAnimatable.value.roundToInt(), 0) }
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(33.dp), spotColor = Color.Black.copy(alpha = 0.35f))
                .border(
                    width = 1.dp,
                    color = miniPlayerBorderColor,
                    shape = RoundedCornerShape(33.dp)
                )
                .clickable { onExpand() },
            shape = RoundedCornerShape(33.dp),
            color = miniPlayerBgColor,
            tonalElevation = if (isBlurEnabled) 0.dp else 6.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                // 1. Artwork with progress ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFFFF0000), // Iconic red progress accent matching iMusic tag
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                        strokeWidth = 2.5.dp
                    )

                    AsyncImage(
                        model = track.thumbnailUrl?.highQualityThumbnail(),
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // 2. Info (Title & Artist with basicMarquee)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artists,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                }

                // 3. Like Heart Button with Spring Animation
                val isLiked = track.liked
                IconButton(
                    onClick = { MusicPlayerState.toggleLike() },
                    modifier = Modifier.size(36.dp)
                ) {
                    val heartScale by animateFloatAsState(
                        targetValue = if (isLiked) 1.25f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "heartScale"
                    )
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFFFF0000) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                scaleX = heartScale
                                scaleY = heartScale
                            }
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 4. AirBeats Play/Pause Button (Smooth rotating cookie shape)
                FilledIconButton(
                    onClick = { MusicPlayerState.togglePlayPause() },
                    modifier = Modifier
                        .size(46.dp)
                        .graphicsLayer {
                            rotationZ = cookieRotation.value
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFFF0000), // Vibrant Red matching iMusic theme
                        contentColor = Color.White,
                    )
                ) {
                    Box(
                        modifier = Modifier.graphicsLayer {
                            rotationZ = -cookieRotation.value
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isPlaying,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(150)) +
                                        scaleIn(
                                            initialScale = 0.4f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )).togetherWith(
                                        fadeOut(animationSpec = tween(100)) +
                                                scaleOut(targetScale = 1.6f, animationSpec = tween(100))
                                    )
                            },
                            label = "playPauseIcon"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
