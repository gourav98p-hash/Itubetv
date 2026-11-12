package com.github.itube.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Expressive Wavy Progress Slider.
 * Matches Android 13/14 and AirBeats expressive squiggly seekbar.
 * Dynamically ripples during active playback and smoothly settles to a flat bar on pause or scrub.
 */
@Composable
fun WavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    isPlaying: Boolean = true,
    enabled: Boolean = true,
    activeTrackColor: Color = Color.White,
    inactiveTrackColor: Color = Color.White.copy(alpha = 0.25f),
    thumbColor: Color = Color.White,
    strokeWidth: Dp = 4.dp,
    thumbRadius: Dp = 7.dp,
    waveAmplitude: Dp = 3.5.dp,
    wavelength: Dp = 32.dp,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val baseThumbRadiusPx = with(density) { thumbRadius.toPx() }
    val waveAmpPx = with(density) { waveAmplitude.toPx() }
    val wavelengthPx = with(density) { wavelength.toPx() }

    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val normalizedValue = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(normalizedValue) }

    val currentProgress = if (isDragging) dragProgress else normalizedValue

    // Infinite wave progression when active
    val infiniteTransition = rememberInfiniteTransition(label = "wavySeekbarLoop")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavySeekbarPhase"
    )

    // Smoothly animate amplitude to 0 when paused or while dragging
    val animatedAmplitude by animateDpAsState(
        targetValue = if (isPlaying && !isDragging) waveAmplitude else 0.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
        label = "wavySeekbarAmplitude"
    )
    val animatedAmpPx = with(density) { animatedAmplitude.toPx() }

    // Thumb scale up slightly when scrubbing
    val animatedThumbRadius by animateDpAsState(
        targetValue = if (isDragging) thumbRadius + 2.5.dp else thumbRadius,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "thumbRadiusAnim"
    )
    val animatedThumbRadiusPx = with(density) { animatedThumbRadius.toPx() }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val containerHeight = (thumbRadius * 2 + 16.dp).coerceAtLeast(32.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight)
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val mapped = valueRange.start + frac * rangeSpan
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnValueChange(mapped)
                    currentOnValueChangeFinished?.invoke()
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        dragProgress = frac
                        val mapped = valueRange.start + frac * rangeSpan
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnValueChange(mapped)
                    },
                    onDragEnd = {
                        isDragging = false
                        currentOnValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        isDragging = false
                        currentOnValueChangeFinished?.invoke()
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val delta = dragAmount / size.width.toFloat()
                        dragProgress = (dragProgress + delta).coerceIn(0f, 1f)
                        val mapped = valueRange.start + dragProgress * rangeSpan
                        currentOnValueChange(mapped)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val centerY = size.height / 2f
            val thumbX = (width * currentProgress).coerceIn(0f, width)

            // 1. Inactive Track (straight smooth line from thumbX to end)
            if (thumbX < width) {
                drawLine(
                    color = inactiveTrackColor,
                    start = Offset(thumbX, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round
                )
            }

            // 2. Active Track (animated sine wave from start to thumbX)
            if (thumbX > 0f) {
                val path = Path()
                path.moveTo(0f, centerY)

                val step = 2.5f
                var currX = 0f
                val dampenDist = with(density) { 16.dp.toPx() }

                while (currX <= thumbX) {
                    // Dampen amplitude at start and end so it connects smoothly to (0, centerY) and (thumbX, centerY)
                    val startDamp = (currX / dampenDist).coerceIn(0f, 1f)
                    val endDamp = ((thumbX - currX) / dampenDist).coerceIn(0f, 1f)
                    val effAmp = animatedAmpPx * startDamp * endDamp

                    val waveY = if (effAmp > 0.05f) {
                        centerY + effAmp * sin((2 * PI * (currX / wavelengthPx) - wavePhase).toFloat())
                    } else {
                        centerY
                    }

                    path.lineTo(currX, waveY)
                    currX += step
                }

                // Ensure path closes right at thumb center
                path.lineTo(thumbX, centerY)

                drawPath(
                    path = path,
                    color = activeTrackColor,
                    style = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round
                    )
                )
            }

            // 3. Thumb (sleek solid circle)
            drawCircle(
                color = thumbColor,
                radius = animatedThumbRadiusPx,
                center = Offset(thumbX, centerY)
            )
        }
    }
}
