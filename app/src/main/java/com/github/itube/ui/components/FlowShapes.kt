package com.github.itube.ui.components

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import kotlin.math.cos
import kotlin.math.sin

/**
 * 9-sided scalloped Cookie shape for artist portraits, matching Flow.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun flowArtistShape(): Shape {
    return runCatching {
        MaterialShapes.Cookie9Sided.toShape()
    }.getOrElse {
        CookieShape(lobes = 9)
    }
}

/**
 * 12-sided scalloped Cookie shape for action buttons, matching Flow.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun flowActionShape(): Shape {
    return runCatching {
        MaterialShapes.Cookie12Sided.toShape()
    }.getOrElse {
        CookieShape(lobes = 12)
    }
}

/**
 * Procedural cookie/scalloped flower shape with [lobes] petals.
 */
fun CookieShape(lobes: Int = 9): Shape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outerR = minOf(cx, cy)
    val innerR = outerR * 0.84f
    val step = (2.0 * Math.PI) / (lobes * 2)

    val points = mutableListOf<Pair<Float, Float>>()
    for (i in 0 until (lobes * 2)) {
        val angle = i * step - Math.PI / 2.0
        val r = if (i % 2 == 0) outerR else innerR
        val x = (cx + r * cos(angle)).toFloat()
        val y = (cy + r * sin(angle)).toFloat()
        points.add(x to y)
    }

    if (points.isNotEmpty()) {
        moveTo(points[0].first, points[0].second)
        for (i in 0 until points.size) {
            val p0 = points[i]
            val p1 = points[(i + 1) % points.size]
            val midX = (p0.first + p1.first) / 2f
            val midY = (p0.second + p1.second) / 2f
            quadraticTo(p0.first, p0.second, midX, midY)
        }
        close()
    }
}
