package com.github.itube

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * The single motion vocabulary (shared with Castii). Tuning dials:
 *  - [Emphasized] ease-out for page changes and small reveals.
 *  - [Morph] the slow-start, single-soft-overshoot curve for the player morph.
 */
object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)
    val Morph = CubicBezierEasing(0.35f, 0f, 0.15f, 1.12f)
    /** Emphasized accelerate: things leaving (cleared queue cards sliding away). */
    val Exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    /** A spring-like pop with one overshoot, for things driven by a progress (no time of their own). */
    val Pop = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    const val PageEnterMs = 220
    const val PageRiseMs = 280
    const val PageExitMs = 90
    /** Tap-initiated open: slow start so the eye sees where the video grows from. */
    const val MorphMs = 480
    /** Tap/back-initiated collapse. Gesture releases use PlayerMorph's spring instead. */
    const val CollapseMs = 380
    /** Overlay controls hide after this long without a touch while playing. */
    const val ControlsHideMs = 2_000L
}

/** True when animations are off system-wide; all motion collapses to instant switches. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) == 0f
    }
}

/**
 * Tap target with the app's press & D-pad remote focus feedback:
 * - A small scale-down while held and a quick spring back on touch.
 * - On TV / D-pad remote: smooth scale-up, shadow pop, and high-visibility focus outline.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    focusScale: Float = 1.05f,
    focusBorderWidth: Dp = 2.5.dp,
    focusShape: Shape? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val reduced = rememberReducedMotion()
    val targetScale = when {
        pressed && !reduced -> pressedScale
        focused && !reduced -> focusScale
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 1300f),
        label = "pressScale",
    )
    val c = MaterialTheme.colorScheme
    val strokeColor = c.primary
    val shape = focusShape ?: RoundedCornerShape(18.dp)

    val layer = this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            if (focused) shadowElevation = 16f
        }
        .then(
            if (focused) {
                Modifier.border(focusBorderWidth, strokeColor, shape)
            } else Modifier
        )
    if (!enabled) return@composed layer
    layer
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick?.let { long -> { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); long() } },
            onClick = onClick,
        )
}
