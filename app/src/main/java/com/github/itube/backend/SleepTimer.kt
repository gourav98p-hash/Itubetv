package com.github.itube

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed class SleepTimerMode {
    object Off : SleepTimerMode()
    object EndOfMedia : SleepTimerMode() // "Till that song / video keeps playing"
    data class Timed(val totalSeconds: Long) : SleepTimerMode()
}

/**
 * Global sleep timer state machine for both Video Player and iMusic Player.
 */
object SleepTimerState {
    var mode by mutableStateOf<SleepTimerMode>(SleepTimerMode.Off)
    var remainingSeconds by mutableLongStateOf(0L)
    var isTimerActive by mutableStateOf(false)

    private var targetEpochMs: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var tickerRunnable: Runnable? = null

    /**
     * Optional callback for pausing video player controller or showing confirmation messages.
     */
    var onTimerExpired: (() -> Unit)? = null

    fun labelForVideo(): String = when (mode) {
        is SleepTimerMode.Off -> "Off"
        is SleepTimerMode.EndOfMedia -> "At end of video"
        is SleepTimerMode.Timed -> formatRemaining(remainingSeconds)
    }

    fun labelForMusic(): String = when (mode) {
        is SleepTimerMode.Off -> "Off"
        is SleepTimerMode.EndOfMedia -> "At end of song"
        is SleepTimerMode.Timed -> formatRemaining(remainingSeconds)
    }

    fun formattedRemaining(): String = when (mode) {
        is SleepTimerMode.Off -> "Off"
        is SleepTimerMode.EndOfMedia -> "End of playback"
        is SleepTimerMode.Timed -> formatRemaining(remainingSeconds)
    }

    private fun formatRemaining(sec: Long): String {
        val s = sec.coerceAtLeast(0L)
        val hrs = s / 3600
        val mins = (s % 3600) / 60
        val secs = s % 60
        return when {
            hrs > 0 -> "${hrs}h ${mins}m"
            mins > 0 -> "${mins}m ${secs}s"
            else -> "${secs}s"
        }
    }

    fun setEndOfMedia() {
        cancelTicker()
        mode = SleepTimerMode.EndOfMedia
        remainingSeconds = 0L
        isTimerActive = true
    }

    fun setTimerMinutes(minutes: Int) {
        setTimerSeconds(minutes * 60L)
    }

    fun setTimerSeconds(seconds: Long) {
        cancelTicker()
        if (seconds <= 0) {
            stopTimer()
            return
        }
        mode = SleepTimerMode.Timed(seconds)
        remainingSeconds = seconds
        isTimerActive = true
        targetEpochMs = SystemClock.elapsedRealtime() + seconds * 1000L

        val runnable = object : Runnable {
            override fun run() {
                val now = SystemClock.elapsedRealtime()
                val left = (targetEpochMs - now) / 1000L
                if (left <= 0) {
                    remainingSeconds = 0L
                    triggerPlaybackStop()
                } else {
                    remainingSeconds = left
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        tickerRunnable = runnable
        handler.postDelayed(runnable, 1000L)
    }

    fun stopTimer() {
        cancelTicker()
        mode = SleepTimerMode.Off
        remainingSeconds = 0L
        isTimerActive = false
    }

    fun shouldStopAtEnd(): Boolean = mode is SleepTimerMode.EndOfMedia

    fun onEndedTriggered() {
        stopTimer()
    }

    fun triggerPlaybackStop() {
        stopTimer()
        try {
            MusicPlayerState.pause()
            MusicPlayerState.companionVideoPlayer?.pause()
        } catch (_: Exception) {}
        onTimerExpired?.invoke()
    }

    private fun cancelTicker() {
        tickerRunnable?.let { handler.removeCallbacks(it) }
        tickerRunnable = null
    }
}
