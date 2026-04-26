package com.quata.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
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
    BottomDestination(AppDestinations.Feed.route, "Feed", Icons.Filled.Home),
    BottomDestination(AppDestinations.CreatePost.route, "Crear", Icons.Filled.AddCircle),
    BottomDestination(AppDestinations.Conversations.route, "Chat", Icons.Filled.ChatBubble),
    BottomDestination(AppDestinations.Notifications.route, "Avisos", Icons.Filled.Notifications),
    BottomDestination(AppDestinations.Profile.route, "Perfil", Icons.Filled.Person)
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
