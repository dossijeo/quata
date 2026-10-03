package com.quata.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.designsystem.effects.fluidTouchEffect
import platform.UIKit.UIViewController

/** One process-wide value observed by every iOS Compose root. Swift updates it on the main thread. */
private var iosTouchFlowEnabled by mutableStateOf(false)

/** Activates the common Touch Flow renderer only for the currently validated profile. */
fun setIosTouchFlowEnabled(enabled: Boolean) {
    iosTouchFlowEnabled = enabled
}

/** The only opaque iOS Compose factory used by product hosts. */
fun QuataComposeUIViewController(content: @Composable () -> Unit): UIViewController =
    ComposeUIViewController {
        IosTouchFlowSurface(content)
    }

/** Transparent counterpart for dialogs presented over an existing product route. */
@OptIn(ExperimentalComposeUiApi::class)
fun QuataTransparentComposeUIViewController(content: @Composable () -> Unit): UIViewController =
    ComposeUIViewController(configure = { opaque = false }) {
        IosTouchFlowSurface(content)
    }

@Composable
private fun IosTouchFlowSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .fluidTouchEffect(enabled = iosTouchFlowEnabled),
    ) {
        content()
    }
}
