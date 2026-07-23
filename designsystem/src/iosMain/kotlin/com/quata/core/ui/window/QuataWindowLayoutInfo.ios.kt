package com.quata.core.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberQuataWindowLayoutInfo(): QuataWindowLayoutInfo = remember {
    QuataWindowLayoutInfo(0, 0, false, "ios:unmeasured")
}
