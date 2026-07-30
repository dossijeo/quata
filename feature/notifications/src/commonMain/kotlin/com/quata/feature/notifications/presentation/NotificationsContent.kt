package com.quata.feature.notifications.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.model.NotificationItem
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.QuataCard
import com.quata.core.ui.components.QuataScreen

data class NotificationsStrings(
    val title: String,
    val subtitle: String,
    val backContentDescription: String,
    val relativeTimeCatalog: RelativeTimeCatalog,
    val previewCatalog: ChatPreviewCatalog,
)

@Composable
fun NotificationsContent(
    padding: PaddingValues,
    state: NotificationsUiState,
    timestampNowMillis: Long,
    strings: NotificationsStrings,
    deliveryNotice: NotificationDeliveryNotice? = null,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onMarkRead: (NotificationItem) -> Unit,
    onDismiss: (NotificationItem) -> Unit,
) {
    QuataScreen(padding) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactIconButton(onClick = onBack) {
                    CompactIcon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.backContentDescription
                    )
                }
                Text(strings.title, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(strings.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            deliveryNotice?.let { notice -> NotificationDeliveryNoticeContent(notice) }
            Spacer(Modifier.padding(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.items, key = { it.id }) { item ->
                    DismissibleNotificationCard(
                        item = item,
                        timestampNowMillis = timestampNowMillis,
                        strings = strings,
                        onClick = {
                            onMarkRead(item)
                            onOpenConversation(item.conversationId)
                        },
                        onDismiss = { onDismiss(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationDeliveryNoticeContent(notice: NotificationDeliveryNotice) {
    QuataCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(notice.title, fontWeight = FontWeight.SemiBold)
            Text(notice.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val actionLabel = notice.actionLabel
            val onAction = notice.onAction
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) { Text(actionLabel) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleNotificationCard(
    item: NotificationItem,
    timestampNowMillis: Long,
    strings: NotificationsStrings,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
        content = {
            QuataCard(modifier = Modifier.clickable(onClick = onClick)) {
                Column(Modifier.padding(16.dp)) {
                    val createdAt = formatNotificationRelativeTime(item.createdAt, timestampNowMillis, strings.relativeTimeCatalog)
                    Text(item.title, fontWeight = FontWeight.Bold)
                    Text(
                        text = resolveChatPreview(item.body, strings.previewCatalog),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (item.unreadCount > 1) "$createdAt - ${item.unreadCount}" else createdAt,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    )
}
