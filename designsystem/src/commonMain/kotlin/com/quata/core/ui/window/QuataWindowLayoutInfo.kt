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

@Composable
expect fun rememberQuataWindowLayoutInfo(): QuataWindowLayoutInfo
