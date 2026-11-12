package com.github.itube.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AirBeats-styled Material 3 Expressive Loading Indicator.
 * Replaces the old circular spinner everywhere across the app.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 38.dp,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(
            modifier = Modifier.fillMaxSize(),
            color = color,
        )
    }
}

/** Centered full-width loader placeholder for feeds and lists. */
@Composable
fun CenteredMaterialLoader(
    modifier: Modifier = Modifier,
    padding: Dp = 32.dp,
    size: Dp = 38.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(padding),
        contentAlignment = Alignment.Center
    ) {
        MaterialLoader(size = size, color = color)
    }
}
