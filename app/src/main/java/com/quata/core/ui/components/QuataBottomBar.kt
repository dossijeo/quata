package com.quata.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MapsHomeWork
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quata.R
import com.quata.core.navigation.AppDestinations

data class BottomDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
)

val bottomDestinations = listOf(
    BottomDestination(AppDestinations.Neighborhoods.route, R.string.nav_neighborhoods, Icons.Filled.MapsHomeWork),
    BottomDestination(AppDestinations.Conversations.route, R.string.nav_chats, Icons.Filled.Forum),
    BottomDestination(AppDestinations.Official.route, R.string.nav_official, Icons.Filled.VerifiedUser),
    BottomDestination(AppDestinations.Feed.route, R.string.nav_feed, Icons.Filled.DynamicFeed),
    BottomDestination(AppDestinations.Profile.route, R.string.nav_account, Icons.Filled.AccountCircle),
)

val QuataNavigationRailWidth = 68.dp

@Composable
fun QuataBottomBar(
    currentRoute: String?,
    onDestinationClick: (String) -> Unit,
) {
    QuataBottomNavigation(
        items = bottomDestinations.map { QuataNavigationItem(it.route, stringResource(it.labelRes), it.icon) },
        selectedId = currentRoute,
        onItemClick = onDestinationClick,
    )
}

@Composable
fun QuataNavigationRail(
    currentRoute: String?,
    onDestinationClick: (String) -> Unit,
    notificationCount: Int,
    isNotificationBouncing: Boolean,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QuataNavigationRailContent(
        items = bottomDestinations.map { QuataNavigationItem(it.route, stringResource(it.labelRes), it.icon) },
        selectedId = currentRoute,
        notification = QuataNavigationRailNotification(
            label = stringResource(R.string.notifications_title),
            icon = Icons.Filled.Notifications,
            count = notificationCount,
            isEmphasized = isNotificationBouncing,
        ),
        onItemClick = onDestinationClick,
        onNotificationClick = onNotificationsClick,
        modifier = modifier,
        railWidth = QuataNavigationRailWidth,
    )
}
