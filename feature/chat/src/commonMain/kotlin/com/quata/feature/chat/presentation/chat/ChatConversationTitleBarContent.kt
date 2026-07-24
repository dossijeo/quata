package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

/**
 * Portable conversation title bar. Platform hosts provide navigation, avatar and action controls
 * so image loading, system integrations and localized actions remain adapters.
 */
@Composable
fun ChatConversationTitleBarContent(
    title: String,
    subtitle: String?,
    expandable: Boolean,
    compact: Boolean,
    onToggleExpanded: () -> Unit,
    navigationAction: @Composable () -> Unit,
    avatar: @Composable () -> Unit,
    trailingActions: @Composable RowScope.() -> Unit,
    showSurface: Boolean = true,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    val verticalPadding = if (compact) 6.dp else 10.dp
    @Composable
    fun TitleBar(titleBarModifier: Modifier) {
        Row(
            modifier = titleBarModifier
                .fillMaxWidth()
                .clickable(enabled = expandable, onClick = onToggleExpanded)
                .padding(horizontal = 8.dp, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigationAction()
            avatar()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            trailingActions()
            if (compact) {
                Spacer(Modifier.width(120.dp))
            }
        }
    }
    if (showSurface) {
        Surface(color = template.colors.surface.copy(alpha = 0.92f), modifier = modifier.fillMaxWidth()) {
            TitleBar(Modifier)
        }
    } else {
        TitleBar(modifier)
    }
}
