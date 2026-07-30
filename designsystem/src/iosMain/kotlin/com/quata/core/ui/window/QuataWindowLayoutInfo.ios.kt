package com.quata.core.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo

@Composable
actual fun rememberQuataWindowLayoutInfo(): QuataWindowLayoutInfo {
    val size = LocalWindowInfo.current.containerSize
    return remember(size) {
        measuredQuataWindowLayoutInfo("ios", size.width, size.height)
    }
}
