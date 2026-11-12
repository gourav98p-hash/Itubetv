package com.github.itube.ui.components

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.itube.backend.EqualizerManager
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(onDismiss: () -> Unit) {
    TrackBlurPopup()
    val c = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val view = LocalView.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = popupContainerColor(c.surfaceContainer),
        contentColor = c.onSurface,
        scrimColor = popupScrimColor(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Top App Bar / Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (EqualizerManager.isEnabled) c.primary.copy(alpha = 0.16f) else c.surfaceContainerHigh,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Rounded.Equalizer,
                            contentDescription = null,
                            tint = if (EqualizerManager.isEnabled) c.primary else c.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Equalizer",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = c.onSurface
                    )
                    Text(
                        text = if (EqualizerManager.isEnabled) "Active: ${EqualizerManager.currentPresetName}" else "Disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (EqualizerManager.isEnabled) c.primary else c.onSurfaceVariant
                    )
                }

                // Master Toggle Switch
                Switch(
                    checked = EqualizerManager.isEnabled,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        EqualizerManager.setEqualizerEnabled(it)
                    },
                    thumbContent = {
                        Icon(
                            imageVector = if (EqualizerManager.isEnabled) Icons.Rounded.Check else Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                )

                Spacer(Modifier.width(8.dp))

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.35f))
            Spacer(Modifier.height(14.dp))

            // ── Preset Chips Row ──
            Text(
                text = "Presets",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqualizerManager.builtInPresets.forEach { preset ->
                    val isSelected = EqualizerManager.currentPresetName == preset.name
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (!EqualizerManager.isEnabled) EqualizerManager.setEqualizerEnabled(true)
                            EqualizerManager.setPreset(preset)
                        },
                        label = {
                            Text(
                                text = preset.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = c.surfaceContainerHigh,
                            labelColor = c.onSurface,
                            selectedContainerColor = c.primary,
                            selectedLabelColor = c.onPrimary
                        ),
                        border = null,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Frequency Bands Card ──
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = c.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Frequency Bands",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = c.onSurface
                        )
                        TextButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                EqualizerManager.resetBands()
                            },
                            enabled = EqualizerManager.isEnabled
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reset", fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    val freqs = EqualizerManager.centerFrequencies
                    val minMb = EqualizerManager.minBandLevelMb
                    val maxMb = EqualizerManager.maxBandLevelMb

                    freqs.forEachIndexed { index, freqHz ->
                        val currentLevelMb = EqualizerManager.bandLevels.getOrNull(index) ?: 0
                        var sliderValue by remember(currentLevelMb) {
                            mutableFloatStateOf(currentLevelMb.toFloat())
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            // Frequency label (e.g., 60 Hz, 14 kHz)
                            Text(
                                text = formatEqFrequency(freqHz),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (EqualizerManager.isEnabled) c.onSurface else c.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.width(62.dp)
                            )

                            // Band Level Slider
                            Slider(
                                value = sliderValue.coerceIn(minMb.toFloat(), maxMb.toFloat()),
                                onValueChange = { newVal ->
                                    sliderValue = newVal
                                    EqualizerManager.setBandLevel(index, newVal.roundToInt())
                                },
                                onValueChangeFinished = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                },
                                valueRange = minMb.toFloat()..maxMb.toFloat(),
                                enabled = EqualizerManager.isEnabled,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = c.primary,
                                    inactiveTrackColor = c.surfaceContainerHighest,
                                    disabledActiveTrackColor = c.outlineVariant.copy(alpha = 0.4f),
                                    disabledInactiveTrackColor = c.surfaceContainerHighest.copy(alpha = 0.4f),
                                    thumbColor = c.primary
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            // Gain dB readout
                            Text(
                                text = formatEqDb(sliderValue / 100f),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (EqualizerManager.isEnabled) c.primary else c.onSurfaceVariant.copy(alpha = 0.5f),
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(62.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Audio Enhancements Card (Bass Boost, Virtualizer, Volume Boost) ──
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = c.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Audio Enhancements",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = c.onSurface,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // 1. Bass Boost
                    EnhancementSliderRow(
                        title = "Bass Boost",
                        icon = Icons.Rounded.GraphicEq,
                        enabled = EqualizerManager.isEnabled && EqualizerManager.bassBoostEnabled,
                        checked = EqualizerManager.bassBoostEnabled,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            EqualizerManager.setBassBoost(it)
                        },
                        value = EqualizerManager.bassBoostStrength,
                        valueRange = 0..1000,
                        onValueChange = { EqualizerManager.setBassBoost(EqualizerManager.bassBoostEnabled, it) },
                        formatValue = { "${it / 10}%" },
                        masterEnabled = EqualizerManager.isEnabled
                    )

                    HorizontalDivider(
                        color = c.outlineVariant.copy(alpha = 0.25f),
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    // 2. Virtualizer (3D Surround)
                    EnhancementSliderRow(
                        title = "Surround (Virtualizer)",
                        icon = Icons.Rounded.SurroundSound,
                        enabled = EqualizerManager.isEnabled && EqualizerManager.virtualizerEnabled,
                        checked = EqualizerManager.virtualizerEnabled,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            EqualizerManager.setVirtualizer(it)
                        },
                        value = EqualizerManager.virtualizerStrength,
                        valueRange = 0..1000,
                        onValueChange = { EqualizerManager.setVirtualizer(EqualizerManager.virtualizerEnabled, it) },
                        formatValue = { "${it / 10}%" },
                        masterEnabled = EqualizerManager.isEnabled
                    )

                    HorizontalDivider(
                        color = c.outlineVariant.copy(alpha = 0.25f),
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    // 3. Volume / Loudness Boost
                    EnhancementSliderRow(
                        title = "Loudness Boost",
                        icon = Icons.Rounded.VolumeUp,
                        enabled = EqualizerManager.isEnabled && EqualizerManager.loudnessEnabled,
                        checked = EqualizerManager.loudnessEnabled,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            EqualizerManager.setLoudness(it)
                        },
                        value = EqualizerManager.loudnessGainMb,
                        valueRange = 0..1000,
                        onValueChange = { EqualizerManager.setLoudness(EqualizerManager.loudnessEnabled, it) },
                        formatValue = { "+${String.format(Locale.US, "%.1f", it / 100f)} dB" },
                        masterEnabled = EqualizerManager.isEnabled
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── System Equalizer Launcher Button ──
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = c.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        val launched = EqualizerManager.openSystemEqualizer(context)
                        if (!launched) {
                            Toast.makeText(context, "No system equalizer found on this device", Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = c.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "System Equalizer",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = c.onSurface
                            )
                            Text(
                                text = "Use device default audio panel (Dolby Atmos, etc.)",
                                fontSize = 12.sp,
                                color = c.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun EnhancementSliderRow(
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    formatValue: (Int) -> String,
    masterEnabled: Boolean
) {
    val c = MaterialTheme.colorScheme
    var localVal by remember(value) { mutableFloatStateOf(value.toFloat()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) c.primary else c.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (masterEnabled) c.onSurface else c.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = masterEnabled,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = localVal.coerceIn(valueRange.first.toFloat(), valueRange.last.toFloat()),
                onValueChange = { newVal ->
                    localVal = newVal
                    onValueChange(newVal.roundToInt())
                },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                enabled = enabled,
                colors = SliderDefaults.colors(
                    activeTrackColor = c.primary,
                    inactiveTrackColor = c.surfaceContainerHighest,
                    disabledActiveTrackColor = c.outlineVariant.copy(alpha = 0.4f),
                    disabledInactiveTrackColor = c.surfaceContainerHighest.copy(alpha = 0.4f),
                    thumbColor = c.primary
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text = formatValue(localVal.roundToInt()),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (enabled) c.primary else c.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.End,
                modifier = Modifier.width(60.dp)
            )
        }
    }
}

private fun formatEqFrequency(hz: Int): String {
    if (hz <= 0) return ""
    return if (hz >= 1000) {
        val k = hz / 1000f
        if (k % 1f == 0f) "${k.toInt()} kHz" else String.format(Locale.US, "%.1f kHz", k)
    } else {
        "$hz Hz"
    }
}

private fun formatEqDb(db: Float): String {
    val rounded = (db * 10f).roundToInt() / 10f
    return "${if (rounded > 0f) "+" else ""}${String.format(Locale.US, "%.1f", rounded)} dB"
}
