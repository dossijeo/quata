package com.quata.feature.feed.presentation

import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.platform.IosPreferenceStore
import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/** Narrow iOS composition root for dependencies consumed by the shared Feed UI. */
class IosFeedHostDependencies(
    val preferences: PreferenceStore,
)

fun createIosFeedHostDependencies(): IosFeedHostDependencies =
    IosFeedHostDependencies(preferences = IosPreferenceStore())

/** Stable Swift entry point that composes real iOS-backed dependencies. */
fun QuataFeedViewController(): UIViewController =
    QuataFeedViewController(createIosFeedHostDependencies())

/** iOS adapter: the screen and its design system remain in commonMain. */
fun QuataFeedViewController(dependencies: IosFeedHostDependencies): UIViewController = ComposeUIViewController {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    QuataTheme {
        FeedStatusContent(
            message = "Quata para iOS se está preparando.",
            retryLabel = "Reintentar",
            onRetry = {
                scope.launch {
                    dependencies.preferences.putString("ios.feed.retry", "true")
                }
            }
        )
    }
}
