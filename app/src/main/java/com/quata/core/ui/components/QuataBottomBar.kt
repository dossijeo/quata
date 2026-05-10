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
import androidx.compose.ui.graphics.vector.ImageVector
import com.quata.core.designsystem.theme.QuataBackground
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.navigation.AppDestinations

data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val bottomDestinations = listOf(
    BottomDestination(AppDestinations.Neighborhoods.route, "Barrios", Icons.Filled.MapsHomeWork),
    BottomDestination(AppDestinations.Conversations.route, "Chats", Icons.Filled.Forum),
    BottomDestination(AppDestinations.CreatePost.route, "Publicar", Icons.Filled.AddCircle),
    BottomDestination(AppDestinations.Feed.route, "Feed", Icons.Filled.DynamicFeed),
    BottomDestination(AppDestinations.Profile.route, "Cuenta", Icons.Filled.AccountCircle)
)

@Composable
fun QuataBottomBar(
    currentRoute: String?,
    onDestinationClick: (String) -> Unit
) {
    NavigationBar(containerColor = QuataBackground) {
        bottomDestinations.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onDestinationClick(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
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
