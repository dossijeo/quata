package com.quata.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

data class QuataNavigationItem(val id: String, val label: String, val icon: ImageVector)

/** Shared bottom-navigation renderer; platform navigation owns route interpretation. */
@Composable
fun QuataBottomNavigation(
    items: List<QuataNavigationItem>,
    selectedId: String?,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    NavigationBar(
        containerColor = template.colors.background,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .height(92.dp),
        windowInsets = WindowInsets(0.dp),
    ) {
        items.forEach { item ->
            QuataBottomNavigationItem(item, selected = selectedId == item.id) { onItemClick(item.id) }
        }
    }
}

@Composable
private fun RowScope.QuataBottomNavigationItem(item: QuataNavigationItem, selected: Boolean, onClick: () -> Unit) {
    val template = quataTheme()
    val contentColor = if (selected) template.colors.textPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = if (selected) template.colors.selectedSurface else template.colors.surfaceAlt,
        contentColor = contentColor,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (selected) template.colors.selectedBorder else template.colors.divider),
        modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 5.dp, vertical = 10.dp).clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            Icon(item.icon, item.label, tint = contentColor, modifier = Modifier.size(24.dp))
            Text(item.label, color = contentColor, fontSize = template.textSizes.tiny, lineHeight = template.textSizes.caption, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
