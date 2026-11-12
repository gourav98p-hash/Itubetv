package com.github.itube.backend

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

data class EqPreset(
    val name: String,
    val bandLevelsMb: List<Int>,
)

/**
 * Manages audio equalization, presets, bass boost, virtualizer, and loudness enhancement
 * directly on the active ExoPlayer audio session for iTube.
 */
object EqualizerManager {
    private const val TAG = "EqualizerManager"
    private const val PREFS_NAME = "itube_equalizer_settings"
    private const val KEY_ENABLED = "eq_enabled"
    private const val KEY_PRESET_NAME = "eq_preset_name"
    private const val KEY_BAND_LEVELS = "eq_band_levels"
    private const val KEY_BASS_ENABLED = "eq_bass_enabled"
    private const val KEY_BASS_STRENGTH = "eq_bass_strength"
    private const val KEY_VIRT_ENABLED = "eq_virt_enabled"
    private const val KEY_VIRT_STRENGTH = "eq_virt_strength"
    private const val KEY_LOUD_ENABLED = "eq_loud_enabled"
    private const val KEY_LOUD_GAIN = "eq_loud_gain"

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    // Native AudioFX handles
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0

    // Built-in Presets
    val builtInPresets = listOf(
        EqPreset("Flat", listOf(0, 0, 0, 0, 0)),
        EqPreset("Bass Boost", listOf(600, 400, 150, 0, 0)),
        EqPreset("Treble Boost", listOf(0, 0, 150, 400, 600)),
        EqPreset("Rock", listOf(450, 250, -100, 250, 400)),
        EqPreset("Pop", listOf(-150, 200, 400, 200, -150)),
        EqPreset("Jazz", listOf(300, 150, 200, 200, 300)),
        EqPreset("Classical", listOf(400, 250, -150, 250, 350)),
        EqPreset("Hip Hop", listOf(500, 300, 0, 150, 300)),
        EqPreset("Electronic", listOf(400, 250, 0, 200, 400)),
        EqPreset("Vocal", listOf(-200, 100, 500, 300, -100))
    )

    // Reactive Compose states for UI
    var isEnabled by mutableStateOf(false)
        private set
    var currentPresetName by mutableStateOf("Flat")
        private set
    var bandLevels by mutableStateOf<List<Int>>(listOf(0, 0, 0, 0, 0))
        private set
    var centerFrequencies by mutableStateOf<List<Int>>(listOf(60, 230, 910, 3600, 14000))
        private set
    var minBandLevelMb by mutableIntStateOf(-1500)
        private set
    var maxBandLevelMb by mutableIntStateOf(1500)
        private set

    var bassBoostEnabled by mutableStateOf(false)
        private set
    var bassBoostStrength by mutableIntStateOf(0) // 0..1000
        private set

    var virtualizerEnabled by mutableStateOf(false)
        private set
    var virtualizerStrength by mutableIntStateOf(0) // 0..1000
        private set

    var loudnessEnabled by mutableStateOf(false)
        private set
    var loudnessGainMb by mutableIntStateOf(0) // 0..1000
        private set

    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSavedSettings()
    }

    /**
     * Binds audio effects to the active ExoPlayer audio session.
     */
    fun bindAudioSession(sessionId: Int) {
        if (sessionId <= 0) return
        if (currentSessionId == sessionId && equalizer != null) return

        releaseEffects(notifySession = true)
        currentSessionId = sessionId

        try {
            val eq = Equalizer(0, sessionId)
            equalizer = eq

            val bands = eq.numberOfBands.toInt()
            val range = runCatching { eq.bandLevelRange }.getOrNull()
            if (range != null && range.size >= 2) {
                minBandLevelMb = range[0].toInt()
                maxBandLevelMb = range[1].toInt()
            }
            val freqs = mutableListOf<Int>()
            for (i in 0 until bands) {
                val centerMilliHz = runCatching { eq.getCenterFreq(i.toShort()) }.getOrNull() ?: 0
                freqs.add(centerMilliHz / 1000)
            }
            if (freqs.isNotEmpty()) {
                centerFrequencies = freqs
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed creating Equalizer for session $sessionId: ${e.message}")
            equalizer = null
        }

        bassBoost = runCatching { BassBoost(0, sessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, sessionId) }.getOrNull()
        loudnessEnhancer = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()

        applyAllSettingsToHardware()

        appContext?.let { ctx ->
            try {
                ctx.sendBroadcast(
                    Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, ctx.packageName)
                        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed sending OPEN session broadcast: ${e.message}")
            }
        }
    }

    fun releaseEffects(notifySession: Boolean = true) {
        val oldSession = currentSessionId
        if (oldSession > 0 && notifySession) {
            appContext?.let { ctx ->
                try {
                    ctx.sendBroadcast(
                        Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, oldSession)
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, ctx.packageName)
                        }
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed sending CLOSE session broadcast: ${e.message}")
                }
            }
        }
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        currentSessionId = 0
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        isEnabled = enabled
        saveSetting(KEY_ENABLED, enabled)
        applyAllSettingsToHardware()
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        val current = bandLevels.toMutableList()
        while (current.size <= bandIndex) current.add(0)
        current[bandIndex] = levelMb.coerceIn(minBandLevelMb, maxBandLevelMb)
        bandLevels = current
        currentPresetName = "Custom"
        saveSetting(KEY_PRESET_NAME, "Custom")
        saveBandLevels()
        applyBandToHardware(bandIndex, current[bandIndex])
    }

    fun setPreset(preset: EqPreset) {
        currentPresetName = preset.name
        saveSetting(KEY_PRESET_NAME, preset.name)
        val resampled = resampleLevels(preset.bandLevelsMb, centerFrequencies.size)
        bandLevels = resampled
        saveBandLevels()
        applyAllSettingsToHardware()
    }

    fun resetBands() {
        val flat = List(centerFrequencies.size) { 0 }
        bandLevels = flat
        currentPresetName = "Flat"
        saveSetting(KEY_PRESET_NAME, "Flat")
        saveBandLevels()
        applyAllSettingsToHardware()
    }

    fun setBassBoost(enabled: Boolean, strength: Int = bassBoostStrength) {
        bassBoostEnabled = enabled
        bassBoostStrength = strength.coerceIn(0, 1000)
        saveSetting(KEY_BASS_ENABLED, enabled)
        saveSetting(KEY_BASS_STRENGTH, bassBoostStrength)
        applyBassBoostToHardware()
    }

    fun setVirtualizer(enabled: Boolean, strength: Int = virtualizerStrength) {
        virtualizerEnabled = enabled
        virtualizerStrength = strength.coerceIn(0, 1000)
        saveSetting(KEY_VIRT_ENABLED, enabled)
        saveSetting(KEY_VIRT_STRENGTH, virtualizerStrength)
        applyVirtualizerToHardware()
    }

    fun setLoudness(enabled: Boolean, gainMb: Int = loudnessGainMb) {
        loudnessEnabled = enabled
        loudnessGainMb = gainMb.coerceIn(0, 1000)
        saveSetting(KEY_LOUD_ENABLED, enabled)
        saveSetting(KEY_LOUD_GAIN, loudnessGainMb)
        applyLoudnessToHardware()
    }

    fun openSystemEqualizer(context: Context): Boolean {
        val sessionId = if (currentSessionId > 0) currentSessionId else 0
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }
        return try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to launch system equalizer: ${e.message}")
            false
        }
    }

    private fun applyAllSettingsToHardware() {
        val eq = equalizer
        if (eq != null) {
            runCatching { eq.enabled = isEnabled }
            if (isEnabled) {
                val bands = eq.numberOfBands.toInt()
                val resampled = resampleLevels(bandLevels, bands)
                for (i in 0 until bands) {
                    val level = resampled.getOrNull(i)?.coerceIn(minBandLevelMb, maxBandLevelMb) ?: 0
                    runCatching { eq.setBandLevel(i.toShort(), level.toShort()) }
                }
            }
        }
        applyBassBoostToHardware()
        applyVirtualizerToHardware()
        applyLoudnessToHardware()
    }

    private fun applyBandToHardware(band: Int, levelMb: Int) {
        if (!isEnabled) return
        val eq = equalizer ?: return
        runCatching {
            eq.setBandLevel(band.toShort(), levelMb.toShort())
        }
    }

    private fun applyBassBoostToHardware() {
        val bb = bassBoost ?: return
        val shouldEnable = isEnabled && bassBoostEnabled
        runCatching { bb.enabled = shouldEnable }
        if (shouldEnable) {
            runCatching { bb.setStrength(bassBoostStrength.toShort()) }
        }
    }

    private fun applyVirtualizerToHardware() {
        val v = virtualizer ?: return
        val shouldEnable = isEnabled && virtualizerEnabled
        runCatching { v.enabled = shouldEnable }
        if (shouldEnable) {
            runCatching { v.setStrength(virtualizerStrength.toShort()) }
        }
    }

    private fun applyLoudnessToHardware() {
        val le = loudnessEnhancer ?: return
        val shouldEnable = isEnabled && loudnessEnabled
        runCatching { le.enabled = shouldEnable }
        if (shouldEnable) {
            runCatching { le.setTargetGain(loudnessGainMb) }
        }
    }

    private fun resampleLevels(source: List<Int>, targetCount: Int): List<Int> {
        if (targetCount <= 0) return emptyList()
        if (source.isEmpty()) return List(targetCount) { 0 }
        if (source.size == targetCount) return source
        if (source.size == 1) return List(targetCount) { source[0] }
        return List(targetCount) { i ->
            val pos = i.toFloat() * (source.size - 1) / (targetCount - 1).coerceAtLeast(1)
            val lo = pos.toInt().coerceIn(0, source.size - 1)
            val hi = (lo + 1).coerceIn(0, source.size - 1)
            val t = pos - lo
            (source[lo] + (source[hi] - source[lo]) * t).roundToInt()
        }
    }

    private fun loadSavedSettings() {
        val p = prefs ?: return
        isEnabled = p.getBoolean(KEY_ENABLED, false)
        currentPresetName = p.getString(KEY_PRESET_NAME, "Flat") ?: "Flat"
        val rawBands = p.getString(KEY_BAND_LEVELS, null)
        if (!rawBands.isNullOrBlank()) {
            val parsed = rawBands.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (parsed.isNotEmpty()) {
                bandLevels = parsed
            }
        }
        bassBoostEnabled = p.getBoolean(KEY_BASS_ENABLED, false)
        bassBoostStrength = p.getInt(KEY_BASS_STRENGTH, 0)
        virtualizerEnabled = p.getBoolean(KEY_VIRT_ENABLED, false)
        virtualizerStrength = p.getInt(KEY_VIRT_STRENGTH, 0)
        loudnessEnabled = p.getBoolean(KEY_LOUD_ENABLED, false)
        loudnessGainMb = p.getInt(KEY_LOUD_GAIN, 0)
    }

    private fun saveSetting(key: String, value: Any) {
        val editor = prefs?.edit() ?: return
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is String -> editor.putString(key, value)
        }
        editor.apply()
    }

    private fun saveBandLevels() {
        saveSetting(KEY_BAND_LEVELS, bandLevels.joinToString(","))
    }
}
