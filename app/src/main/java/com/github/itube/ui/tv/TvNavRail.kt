package com.github.itube.ui.tv

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.itube.Account
import com.github.itube.Tab

val TvRailWidth = 76.dp
val TvRailExpandedWidth = 200.dp

@Composable
fun TvNavRail(
    selectedTab: Tab,
    onSelectTab: (Tab) -> Unit,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    var railHasFocus by remember { mutableStateOf(false) }

    val railWidth by animateDpAsState(
        targetValue = if (railHasFocus) TvRailExpandedWidth else TvRailWidth,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 900f),
        label = "tvRailWidth",
    )

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(railWidth)
            .shadow(if (railHasFocus) 24.dp else 8.dp)
            .onFocusChanged { railHasFocus = it.hasFocus },
        color = c.surfaceContainerLow.copy(alpha = 0.94f),
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 18.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header / Brand Mark
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = if (railHasFocus) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE50914)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "iTube TV",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    if (railHasFocus) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "iTube",
                            color = c.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Navigation Tabs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = if (railHasFocus) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                val tabs = listOf(
                    Tab.Home,
                    Tab.Music,
                    Tab.Subscriptions,
                    Tab.Shorts,
                    Tab.Search,
                    Tab.Library,
                )

                tabs.forEach { tab ->
                    TvNavRailItem(
                        tab = tab,
                        isSelected = tab == selectedTab,
                        expanded = railHasFocus,
                        onClick = { onSelectTab(tab) },
                    )
                }
            }

            // Account & Settings Button at Bottom
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = if (railHasFocus) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                val interaction = remember { MutableInteractionSource() }
                val isFocused by interaction.collectIsFocusedAsState()
                val view = LocalView.current

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isFocused) c.primary.copy(alpha = 0.22f)
                            else Color.Transparent
                        )
                        .then(
                            if (isFocused) Modifier.border(2.dp, c.primary, RoundedCornerShape(14.dp))
                            else Modifier
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onAccount()
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (railHasFocus) Arrangement.Start else Arrangement.Center,
                ) {
                    Icon(
                        imageVector = if (Account.signedIn) Icons.Rounded.AccountCircle else Icons.Rounded.Settings,
                        contentDescription = "Settings and Account",
                        tint = if (isFocused) c.primary else c.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    if (railHasFocus) {
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = if (Account.signedIn) "Account" else "Settings",
                            color = if (isFocused) c.primary else c.onSurface,
                            fontSize = 14.sp,
                            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvNavRailItem(
    tab: Tab,
    isSelected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    val view = LocalView.current

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 1200f),
        label = "tvRailItemScale",
    )

    val itemBgColor = when {
        isFocused -> c.primary
        isSelected -> c.primaryContainer
        else -> Color.Transparent
    }

    val itemContentColor = when {
        isFocused -> c.onPrimary
        isSelected -> c.onPrimaryContainer
        else -> c.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(itemBgColor)
            .then(
                if (isFocused) {
                    Modifier.border(2.5.dp, Color.White, RoundedCornerShape(16.dp))
                } else Modifier
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Icon(
            imageVector = if (isSelected || isFocused) tab.selectedIcon else tab.icon,
            contentDescription = tab.label,
            tint = itemContentColor,
            modifier = Modifier.size(24.dp),
        )
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                text = tab.label,
                color = itemContentColor,
                fontSize = 14.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
