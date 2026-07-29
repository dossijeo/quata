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

    fun updateSelectedRoute(route: String) {
        selectedRoute = route
    }

    fun viewController(): UIViewController = ComposeUIViewController {
        QuataTheme {
            QuataPrimaryBottomNavigation(
                labels = IosPrimaryNavigationLabels,
                selectedRoute = selectedRoute,
                onRouteSelected = onRouteSelected,
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
