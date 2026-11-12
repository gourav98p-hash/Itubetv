package com.github.itube.ui.player

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.itube.SleepTimerMode
import com.github.itube.SleepTimerState
import com.github.itube.ui.components.TrackBlurPopup
import com.github.itube.ui.components.popupContainerColor
import com.github.itube.ui.components.popupScrimColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    isVideo: Boolean,
    onDismiss: () -> Unit,
    onSetMessage: ((String) -> Unit)? = null,
) {
    TrackBlurPopup()
    val c = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = popupContainerColor(c.surfaceContainerLow),
        scrimColor = popupScrimColor(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        SleepTimerContent(
            isVideo = isVideo,
            onDismiss = onDismiss,
            onSetMessage = onSetMessage,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
        )
    }
}

@Composable
fun SleepTimerContent(
    isVideo: Boolean,
    onDismiss: () -> Unit,
    onSetMessage: ((String) -> Unit)? = null,
    scrollable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val view = LocalView.current
    val currentMode = SleepTimerState.mode
    val isTimerActive = SleepTimerState.isTimerActive
    val mediaType = if (isVideo) "video" else "song"

    val scrollModifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(scrollModifier)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(c.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Sleep Timer",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = c.onSurface
                    )
                    Text(
                        text = if (isTimerActive) SleepTimerState.formattedRemaining() else "Turn off playback automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isTimerActive) c.primary else c.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.onSurfaceVariant)
            }
        }

        // Active Status Card (when timer is running)
        AnimatedVisibility(visible = isTimerActive, enter = fadeIn(), exit = fadeOut()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = c.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, c.primary.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (currentMode is SleepTimerMode.EndOfMedia) Icons.Rounded.HourglassBottom else Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = c.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = when (currentMode) {
                                is SleepTimerMode.EndOfMedia -> "Stops at the end of this $mediaType."
                                is SleepTimerMode.Timed -> "Stops in ${SleepTimerState.formattedRemaining()}."
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = c.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = c.error.copy(alpha = 0.14f),
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            SleepTimerState.stopTimer()
                            onSetMessage?.invoke("Sleep timer cancelled")
                        }
                    ) {
                        Text(
                            text = "Turn off",
                            color = c.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.35f))
        Spacer(Modifier.height(8.dp))

        // 1. "Till that song / video keeps playing" (End of media)
        SleepTimerOptionRow(
            icon = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.MusicNote,
            title = "End of this $mediaType",
            subtitle = "Plays until this $mediaType finishes, then pauses",
            isSelected = currentMode is SleepTimerMode.EndOfMedia,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                SleepTimerState.setEndOfMedia()
                onSetMessage?.invoke("Sleep timer: at end of $mediaType")
                onDismiss()
            }
        )

        // Preset Minute Options
        val presets = listOf(
            5 to "5 minutes",
            10 to "10 minutes",
            15 to "15 minutes",
            30 to "30 minutes",
            45 to "45 minutes",
            60 to "60 minutes (1 hour)"
        )

        presets.forEach { (minutes, label) ->
            val isSelected = currentMode is SleepTimerMode.Timed && (currentMode.totalSeconds / 60L).toInt() == minutes
            SleepTimerOptionRow(
                icon = Icons.Rounded.Timer,
                title = label,
                subtitle = "Stops playback after $minutes min",
                isSelected = isSelected,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    SleepTimerState.setTimerMinutes(minutes)
                    onSetMessage?.invoke("Sleep timer set for $minutes minutes")
                    onDismiss()
                }
            )
        }

        // Option to cancel if active
        if (isTimerActive) {
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.25f))
            Spacer(Modifier.height(4.dp))

            SleepTimerOptionRow(
                icon = Icons.Rounded.TimerOff,
                title = "Turn off timer",
                subtitle = "Cancel active sleep timer countdown",
                isSelected = false,
                isDestructive = true,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    SleepTimerState.stopTimer()
                    onSetMessage?.invoke("Sleep timer turned off")
                    onDismiss()
                }
            )
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SleepTimerOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val activeColor = if (isDestructive) c.error else c.primary
    val textColor = when {
        isSelected -> activeColor
        isDestructive -> c.error
        else -> c.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) activeColor else c.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = textColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) activeColor.copy(alpha = 0.85f) else c.onSurfaceVariant
                )
            }
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = activeColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
