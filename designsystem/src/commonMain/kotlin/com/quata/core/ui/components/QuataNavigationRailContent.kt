package com.quata.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme

data class QuataNavigationRailNotification(
    val label: String,
    val icon: ImageVector,
    val count: Int,
    val isEmphasized: Boolean = false,
)

/** Shared compact navigation rail; the host owns routes, localization and destination actions. */
@Composable
fun QuataNavigationRailContent(
    items: List<QuataNavigationItem>,
    selectedId: String?,
    notification: QuataNavigationRailNotification,
    onItemClick: (String) -> Unit,
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier,
    railWidth: Dp = 68.dp,
) {
    val template = quataTheme()
    val layoutDirection = LocalLayoutDirection.current
    val safeStartPadding = WindowInsets.safeDrawing.asPaddingValues().calculateStartPadding(layoutDirection)
    BoxWithConstraints(
        modifier = modifier
            .width(safeStartPadding + railWidth)
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom)),
    ) {
        val isShortRail = maxHeight < 420.dp
        val itemHeight = if (isShortRail) 46.dp else 52.dp
        val itemSpacing = if (isShortRail) 4.dp else 5.dp
        val spacerHeight = if (isShortRail) 4.dp else 8.dp
        val verticalPadding = if (isShortRail) 4.dp else 6.dp
        val horizontalPadding = if (isShortRail) 4.dp else 5.dp
        val navigationIconSize = if (isShortRail) 17.dp else 19.dp
        val notificationIconSize = if (isShortRail) {
            if (notification.isEmphasized) 20.dp else 17.dp
        } else {
            if (notification.isEmphasized) 22.dp else 19.dp
        }
        val labelSize = if (isShortRail) 8.sp else 9.sp
        Surface(
            color = template.colors.background,
            contentColor = template.colors.textPrimary,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .padding(start = safeStartPadding)
                        .width(railWidth)
                        .fillMaxHeight()
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(itemSpacing, Alignment.Top),
                ) {
                    QuataCompactNotificationRailItem(
                        notification = notification,
                        iconSize = notificationIconSize,
                        labelSize = labelSize,
                        itemHeight = itemHeight,
                        onClick = onNotificationClick,
                    )
                    Spacer(Modifier.height(spacerHeight))
                    items.forEach { item ->
                        QuataCompactNavigationRailItem(
                            item = item,
                            selected = selectedId == item.id,
                            iconSize = navigationIconSize,
                            labelSize = labelSize,
                            itemHeight = itemHeight,
                            onClick = { onItemClick(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuataCompactNotificationRailItem(
    notification: QuataNavigationRailNotification,
    iconSize: Dp,
    labelSize: TextUnit,
    itemHeight: Dp,
    onClick: () -> Unit,
) {
    val template = quataTheme()
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = template.colors.surfaceAlt,
        contentColor = contentColor,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(width = 1.dp, color = template.colors.divider),
        modifier = Modifier.fillMaxWidth().height(itemHeight).clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BadgedBox(
                badge = {
                    if (notification.count > 0) {
                        Badge(containerColor = template.colors.sos) {
                            Text(notification.count.coerceAtMost(99).toString(), color = Color.White, fontSize = 9.sp)
                        }
                    }
                },
            ) {
                Icon(notification.icon, notification.label, tint = contentColor, modifier = Modifier.size(iconSize))
            }
            Spacer(Modifier.height(2.dp))
            Text(notification.label, color = contentColor, fontSize = labelSize, lineHeight = labelSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
private fun QuataCompactNavigationRailItem(
    item: QuataNavigationItem,
    selected: Boolean,
    iconSize: Dp,
    labelSize: TextUnit,
    itemHeight: Dp,
    onClick: () -> Unit,
) {
    val template = quataTheme()
    val contentColor = if (selected) template.colors.textPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = if (selected) template.colors.selectedSurface else template.colors.surfaceAlt,
        contentColor = contentColor,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(width = 1.dp, color = if (selected) template.colors.selectedBorder else template.colors.divider),
        modifier = Modifier.fillMaxWidth().height(itemHeight).clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(item.icon, item.label, tint = contentColor, modifier = Modifier.size(iconSize))
            Spacer(Modifier.height(2.dp))
            Text(item.label, color = contentColor, fontSize = labelSize, lineHeight = labelSize, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
