package com.quata.core.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import platform.UIKit.UIViewController

/** Swift-facing host for the shared Compose Multiplatform splash screen. */
fun QuataSplashViewController(onFinished: () -> Unit): UIViewController = ComposeUIViewController {
    QuataTheme {
        QuataSplashScreen(
            onFinished = onFinished,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
