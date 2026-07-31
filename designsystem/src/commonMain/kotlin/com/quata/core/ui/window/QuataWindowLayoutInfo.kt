package com.quata.core.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

@Immutable
data class QuataWindowLayoutInfo(
    val widthPx: Int,
    val heightPx: Int,
    val isLandscape: Boolean,
    val viewportKey: String,
)

internal fun measuredQuataWindowLayoutInfo(
    platform: String,
    widthPx: Int,
    heightPx: Int,
): QuataWindowLayoutInfo {
    val width = widthPx.coerceAtLeast(0)
    val height = heightPx.coerceAtLeast(0)
    return QuataWindowLayoutInfo(
        widthPx = width,
        heightPx = height,
        isLandscape = width > height,
        viewportKey = "$platform:${width}x${height}",
    )
}

@Composable
expect fun rememberQuataWindowLayoutInfo(): QuataWindowLayoutInfo
