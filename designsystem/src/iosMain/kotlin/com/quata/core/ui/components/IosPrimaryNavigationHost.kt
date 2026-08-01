package com.quata.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import platform.UIKit.UIViewController

/** Swift-facing Compose host for the authenticated primary navigation chrome. */
class IosPrimaryNavigationHost(
    initialSelectedRoute: String,
    private val onRouteSelected: (String) -> Unit,
) {
    private var selectedRoute by mutableStateOf(initialSelectedRoute)
    private var isComposerMode by mutableStateOf(false)

    fun updateSelectedRoute(route: String) {
        selectedRoute = route
    }

    fun updateComposerMode(isComposer: Boolean) {
        isComposerMode = isComposer
    }

    fun viewController(): UIViewController = ComposeUIViewController {
        QuataTheme {
            QuataPrimaryBottomNavigation(
                labels = IosPrimaryNavigationLabels,
                selectedRoute = selectedRoute,
                onRouteSelected = onRouteSelected,
                mode = if (isComposerMode) {
                    QuataPrimaryNavigationMode.Composer(route = "composer", label = "Publicar")
                } else {
                    QuataPrimaryNavigationMode.Default
                },
            )
        }
    }
}

private val IosPrimaryNavigationLabels = QuataPrimaryNavigationLabels(
    neighborhoods = "Qüata",
    conversations = "Chats",
    official = "Oficial",
    feed = "Feed",
    profile = "Cuenta",
)
