package com.quata.feature.chat.presentation.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.ui.components.QuataCard

/** Shared conversation-row structure; the host supplies avatar/profile navigation as a slot. */
@Composable
fun ConversationListItemContent(
    title: String,
    preview: String,
    updatedAt: String,
    unreadCount: Int,
    showUnreadBadge: Boolean,
    avatar: @Composable () -> Unit,
    onOpen: () -> Unit,
    trailingAction: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    QuataCard(modifier = modifier.clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            avatar()
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    preview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(updatedAt, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    if (showUnreadBadge && unreadCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        ) {
                            Text(unreadCount.toString(), fontSize = 9.sp)
                        }
                    }
                }
                trailingAction()
            }
        }
    }
}
