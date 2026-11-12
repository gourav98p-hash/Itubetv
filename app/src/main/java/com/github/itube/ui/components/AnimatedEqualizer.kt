package com.github.itube.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated 5-bar equalizer matching Material GraphicEq geometry.
 *
 * When [isPlaying] is true, the bars dynamically bounce up and down with out-of-phase
 * easing curves, simulating real-time audio playback.
 * When [isPlaying] is false (paused), the bars stay static at resting heights without
 * any animation running.
 */
@Composable
fun AnimatedEqualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFF0000),
    size: Dp = 24.dp,
) {
    if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "equalizer_bars")
        val h1 by transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(420, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "eq_bar1"
        )
        val h2 by transition.animateFloat(
            initialValue = 0.85f,
            targetValue = 0.30f,
            animationSpec = infiniteRepeatable(
                animation = tween(330, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "eq_bar2"
        )
        val h3 by transition.animateFloat(
            initialValue = 0.40f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(510, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "eq_bar3"
        )
        val h4 by transition.animateFloat(
            initialValue = 0.90f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(360, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "eq_bar4"
        )
        val h5 by transition.animateFloat(
            initialValue = 0.30f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "eq_bar5"
        )

        EqualizerCanvas(
            fractions = listOf(h1, h2, h3, h4, h5),
            modifier = modifier,
            color = color,
            size = size
        )
    } else {
        // Paused state: static GraphicEq resting bar heights
        EqualizerCanvas(
            fractions = listOf(0.35f, 0.65f, 1.0f, 0.65f, 0.35f),
            modifier = modifier,
            color = color,
            size = size
        )
    }
}

@Composable
private fun EqualizerCanvas(
    fractions: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFF0000),
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val totalW = this.size.width
        val totalH = this.size.height
        val barWidth = totalW * 0.11f
        val gap = totalW * 0.08f
        val totalBarsW = 5 * barWidth + 4 * gap
        val startX = (totalW - totalBarsW) / 2f
        val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)

        for (i in 0 until 5) {
            val bx = startX + i * (barWidth + gap)
            val bh = (totalH * 0.85f * fractions[i]).coerceAtLeast(barWidth)
            val by = (totalH - bh) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(bx, by),
                size = Size(barWidth, bh),
                cornerRadius = cornerRadius
            )
        }
    }
}
