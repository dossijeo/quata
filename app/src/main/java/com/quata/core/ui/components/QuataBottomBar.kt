package com.quata.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MapsHomeWork
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.quata.R
import com.quata.core.designsystem.theme.QuataBackground
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.navigation.AppDestinations

data class BottomDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

val bottomDestinations = listOf(
    BottomDestination(AppDestinations.Neighborhoods.route, R.string.nav_neighborhoods, Icons.Filled.MapsHomeWork),
    BottomDestination(AppDestinations.Conversations.route, R.string.nav_chats, Icons.Filled.Forum),
    BottomDestination(AppDestinations.CreatePost.route, R.string.nav_publish, Icons.Filled.AddCircle),
    BottomDestination(AppDestinations.Feed.route, R.string.nav_feed, Icons.Filled.DynamicFeed),
    BottomDestination(AppDestinations.Profile.route, R.string.nav_account, Icons.Filled.AccountCircle)
)

@Composable
fun QuataBottomBar(
    currentRoute: String?,
    onDestinationClick: (String) -> Unit
) {
    NavigationBar(containerColor = QuataBackground) {
        bottomDestinations.forEach { item ->
            val selected = currentRoute == item.route
            val label = stringResource(item.labelRes)
            NavigationBarItem(
                selected = selected,
                onClick = { onDestinationClick(item.route) },
                icon = { Icon(item.icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = QuataOrange,
                    indicatorColor = QuataOrange,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
