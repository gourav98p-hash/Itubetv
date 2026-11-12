package com.github.itube.ui.components

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.View
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

object BlurSetting {
    private const val KEY = "blur_effect_enabled"
    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean(KEY, false) ?: false
    }

    fun set(context: Context, on: Boolean) {
        if (on == enabled) return
        enabled = on
        prefs?.edit()?.putBoolean(KEY, on)?.apply()
    }
}

object BlurPopupState {
    var activePopupCount by mutableIntStateOf(0)

    val isBlurActive: Boolean
        get() = BlurSetting.enabled && activePopupCount > 0

    fun push() { activePopupCount++ }
    fun pop() { if (activePopupCount > 0) activePopupCount-- }
}

@Composable
fun TrackBlurPopup(active: Boolean = true) {
    if (!BlurSetting.enabled) return
    DisposableEffect(active) {
        if (active) {
            BlurPopupState.push()
            onDispose { BlurPopupState.pop() }
        } else {
            onDispose { }
        }
    }
    ApplyWindowBlur()
}

@Composable
fun ApplyWindowBlur() {
    if (!BlurSetting.enabled) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = findWindow(view)
            if (window != null) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                try {
                    window.attributes.blurBehindRadius = 40
                } catch (_: Throwable) {}
            }
            onDispose { }
        }
    }
}

private fun findWindow(view: View): Window? {
    var v: Any? = view
    while (v != null) {
        if (v is DialogWindowProvider) return v.window
        if (v is View) {
            v = v.parent
        } else if (v is ViewParent) {
            v = v.parent
        } else {
            break
        }
    }
    return (view.context as? Activity)?.window
}

@Composable
fun popupContainerColor(fallback: Color = MaterialTheme.colorScheme.surfaceContainerLow): Color {
    if (!BlurSetting.enabled) return fallback
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // Frosted glass tint matching Shorts nav bar
    return if (dark) Color(0xFF202024).copy(alpha = 0.72f) else Color(0xFFF2F2F6).copy(alpha = 0.80f)
}

@Composable
fun popupScrimColor(fallback: Color = Color.Black.copy(alpha = 0.32f)): Color {
    if (!BlurSetting.enabled) return fallback
    return Color.Black.copy(alpha = 0.18f)
}
