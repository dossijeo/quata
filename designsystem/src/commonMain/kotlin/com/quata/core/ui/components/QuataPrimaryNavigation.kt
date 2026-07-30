package com.quata.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
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

fun QuataPrimaryNavigationLabels.items(): List<QuataNavigationItem> = primaryNavigationDestinations.map { destination ->
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
    modifier: Modifier = Modifier,
) {
    QuataBottomNavigation(labels.items(), selectedRoute, onRouteSelected, modifier)
}

@Composable
fun QuataPrimaryNavigationRail(
    labels: QuataPrimaryNavigationLabels,
    selectedRoute: String?,
    notification: QuataNavigationRailNotification,
    onRouteSelected: (String) -> Unit,
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier,
    railWidth: androidx.compose.ui.unit.Dp = 68.dp,
) {
    QuataNavigationRailContent(labels.items(), selectedRoute, notification, onRouteSelected, onNotificationClick, modifier, railWidth)
}
