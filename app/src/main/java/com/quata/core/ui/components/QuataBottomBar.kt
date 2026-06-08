package com.quata.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MapsHomeWork
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.R
import com.quata.core.designsystem.theme.QuataBackground
import com.quata.core.designsystem.theme.QuataDivider
import com.quata.core.designsystem.theme.QuataSurfaceAlt
import com.quata.core.designsystem.theme.quataTheme
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
    val template = quataTheme()
    NavigationBar(
        containerColor = template.colors.background,
        modifier = Modifier
            .navigationBarsPadding()
            .height(92.dp),
        windowInsets = WindowInsets(0.dp)
    ) {
        bottomDestinations.forEach { item ->
            val selected = currentRoute == item.route
            val label = stringResource(item.labelRes)
            CompactBottomBarItem(
                selected = selected,
                label = label,
                icon = item.icon,
                onClick = { onDestinationClick(item.route) },
            )
        }
    }
}

@Composable
private fun RowScope.CompactBottomBarItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val template = quataTheme()
    val selectedContentColor = template.colors.textPrimary
    val unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val contentColor = if (selected) selectedContentColor else unselectedContentColor
    Surface(
        color = if (selected) template.colors.selectedSurface else template.colors.surfaceAlt,
        contentColor = contentColor,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) template.colors.selectedBorder else template.colors.divider
        ),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 5.dp, vertical = 10.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = template.textSizes.tiny,
                lineHeight = template.textSizes.caption,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
