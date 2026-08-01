package com.quata.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MapsHomeWork
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.navigation.AppDestinations
import com.quata.core.navigation.primaryNavigationDestinations

/** Platform-localized labels for the canonical authenticated navigation. */
data class QuataPrimaryNavigationLabels(
    val neighborhoods: String,
    val conversations: String,
    val official: String,
    val feed: String,
    val profile: String,
)

/** The composer replaces the central Official destination while retaining the five-item shell. */
sealed interface QuataPrimaryNavigationMode {
    data object Default : QuataPrimaryNavigationMode

    data class Composer(val route: String, val label: String) : QuataPrimaryNavigationMode
}

fun QuataPrimaryNavigationLabels.items(
    mode: QuataPrimaryNavigationMode = QuataPrimaryNavigationMode.Default,
): List<QuataNavigationItem> = primaryNavigationDestinations.map { destination ->
    if (destination.route == AppDestinations.Official.route && mode is QuataPrimaryNavigationMode.Composer) {
        return@map QuataNavigationItem(mode.route, mode.label, Icons.Filled.AddCircle)
    }
    when (destination.route) {
        AppDestinations.Neighborhoods.route -> QuataNavigationItem(destination.route, neighborhoods, Icons.Filled.MapsHomeWork)
        AppDestinations.Conversations.route -> QuataNavigationItem(destination.route, conversations, Icons.Filled.Forum)
        AppDestinations.Official.route -> QuataNavigationItem(destination.route, official, Icons.Filled.VerifiedUser)
        AppDestinations.Feed.route -> QuataNavigationItem(destination.route, feed, Icons.Filled.DynamicFeed)
        AppDestinations.Profile.route -> QuataNavigationItem(destination.route, profile, Icons.Filled.AccountCircle)
        else -> error("Unknown primary navigation route: ${destination.route}")
    }
}

@Composable
fun QuataPrimaryBottomNavigation(
    labels: QuataPrimaryNavigationLabels,
    selectedRoute: String?,
    onRouteSelected: (String) -> Unit,
    mode: QuataPrimaryNavigationMode = QuataPrimaryNavigationMode.Default,
    modifier: Modifier = Modifier,
) {
    QuataBottomNavigation(labels.items(mode), selectedRoute, onRouteSelected, modifier)
}

@Composable
fun QuataPrimaryNavigationRail(
    labels: QuataPrimaryNavigationLabels,
    selectedRoute: String?,
    notification: QuataNavigationRailNotification,
    onRouteSelected: (String) -> Unit,
    onNotificationClick: () -> Unit,
    mode: QuataPrimaryNavigationMode = QuataPrimaryNavigationMode.Default,
    modifier: Modifier = Modifier,
    railWidth: androidx.compose.ui.unit.Dp = 68.dp,
) {
    QuataNavigationRailContent(labels.items(mode), selectedRoute, notification, onRouteSelected, onNotificationClick, modifier, railWidth)
}
