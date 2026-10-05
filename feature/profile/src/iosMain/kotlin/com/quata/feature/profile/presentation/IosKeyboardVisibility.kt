package com.quata.feature.profile.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIKeyboardDidHideNotification
import platform.UIKit.UIKeyboardDidShowNotification

/** Observes the UIKit keyboard because Compose window insets are not exposed by every iOS host. */
@Composable
internal fun rememberIosKeyboardVisible(): Boolean {
    var isVisible by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val showObserver = center.addObserverForName(
            name = UIKeyboardDidShowNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { isVisible = true }
        val hideObserver = center.addObserverForName(
            name = UIKeyboardDidHideNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { isVisible = false }
        onDispose {
            center.removeObserver(showObserver)
            center.removeObserver(hideObserver)
        }
    }
    return isVisible
}
