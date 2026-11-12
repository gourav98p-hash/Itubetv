package com.github.itube.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalIsTv = compositionLocalOf { false }

object TvHelper {
    /** True if running on an Android TV device (Leanback / TV ui mode). */
    fun isTvDevice(context: Context): Boolean {
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        ) {
            return true
        }
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
}

/**
 * Returns true if running on Android TV or in landscape mode with TV optimization enabled.
 */
@Composable
fun rememberIsTv(): Boolean {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    return remember(config.orientation, config.screenWidthDp) {
        TvHelper.isTvDevice(context) ||
            config.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            config.screenWidthDp >= 600
    }
}

/**
 * Adds high-visibility D-pad focus feedback (scale up, elevation, luminous focus border).
 */
fun Modifier.tvFocusRing(
    shape: Shape = RoundedCornerShape(16.dp),
    focusScale: Float = 1.05f,
    borderWidth: Dp = 2.5.dp,
    borderColor: Color? = null,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) focusScale else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 1200f),
        label = "tvFocusScale",
    )
    val c = MaterialTheme.colorScheme
    val strokeColor = borderColor ?: c.primary

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            if (focused) shadowElevation = 16f
        }
        .then(
            if (focused) {
                Modifier.border(borderWidth, strokeColor, shape)
            } else Modifier
        )
}
