package com.quata.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import platform.UIKit.UIViewController

/**
 * Thin UIKit containment bridge for the shared authenticated top chrome.
 * UIKit owns route factories only; it must not draw a competing header.
 */
class IosAuthenticatedTopChromeHost(
    private val onLogoClick: () -> Unit,
    private val onNotificationsClick: () -> Unit,
    private val onSosClick: () -> Unit,
) {
    private var notificationCount by mutableStateOf(0)

    fun updateNotificationCount(count: Int) {
        notificationCount = count
    }

    fun viewController(): UIViewController = ComposeUIViewController {
        QuataTheme {
            QuataAuthenticatedShellChrome(
                notificationCount = notificationCount,
                isNotificationBouncing = false,
                isOnline = true,
                strings = IosAuthenticatedChromeStrings,
                onLogoClick = onLogoClick,
                onNotificationsClick = onNotificationsClick,
                onSosClick = onSosClick,
                isSosSending = false,
                bottomNavigation = {},
                content = {},
            )
        }
    }
}

/** Escapes are deliberate: this source crosses Xcode/Kotlin encoding boundaries. */
internal val IosAuthenticatedChromeStrings = QuataAuthenticatedChromeSpanish
