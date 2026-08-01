package com.quata.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quata.R

val QuataNavigationRailWidth = 68.dp

@Composable
fun QuataBottomBar(
    currentRoute: String?,
    onDestinationClick: (String) -> Unit,
    composerRoute: String? = null,
) {
    QuataPrimaryBottomNavigation(
        labels = androidPrimaryNavigationLabels(),
        selectedRoute = currentRoute,
        onRouteSelected = onDestinationClick,
        mode = composerRoute?.let { QuataPrimaryNavigationMode.Composer(it, "Publicar") }
            ?: QuataPrimaryNavigationMode.Default,
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
    composerRoute: String? = null,
) {
    QuataPrimaryNavigationRail(
        labels = androidPrimaryNavigationLabels(),
        selectedRoute = currentRoute,
        notification = QuataNavigationRailNotification(
            label = stringResource(R.string.notifications_title),
            icon = Icons.Filled.Notifications,
            count = notificationCount,
            isEmphasized = isNotificationBouncing,
        ),
        onRouteSelected = onDestinationClick,
        onNotificationClick = onNotificationsClick,
        mode = composerRoute?.let { QuataPrimaryNavigationMode.Composer(it, "Publicar") }
            ?: QuataPrimaryNavigationMode.Default,
        modifier = modifier,
        railWidth = QuataNavigationRailWidth,
    )
}

@Composable
private fun androidPrimaryNavigationLabels() = QuataPrimaryNavigationLabels(
    neighborhoods = stringResource(R.string.nav_neighborhoods),
    conversations = stringResource(R.string.nav_chats),
    official = stringResource(R.string.nav_official),
    feed = stringResource(R.string.nav_feed),
    profile = stringResource(R.string.nav_account),
)
