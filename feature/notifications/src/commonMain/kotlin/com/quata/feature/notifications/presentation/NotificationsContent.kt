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
import androidx.compose.material3.CircularProgressIndicator
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
    val loadingLabel: String,
    val emptyTitle: String,
    val emptyMessage: String,
    val errorTitle: String,
    val relativeTime: (createdAt: String, nowMillis: Long) -> String,
    val localizedBody: (String) -> String,
    val photoPreview: String,
    val videoPreview: String,
    val documentPreview: String,
    val voiceNotePreview: String,
    val filePreview: String,
)

/**
 * Shared counterpart of Android's `localizedChatPreview` marker handling.
 *
 * The platform callback still owns platform-only preview conventions (such as SOS), while the
 * portable attachment markers are decoded here so Web and iOS do not expose storage tokens.
 */
fun NotificationsStrings.localizedNotificationBody(raw: String): String = when (raw.trim()) {
    "[QUATA_ATTACHMENT:photo]" -> photoPreview
    "[QUATA_ATTACHMENT:video]" -> videoPreview
    "[QUATA_ATTACHMENT:document]" -> documentPreview
    "[QUATA_ATTACHMENT:voice_note]",
    "[QUATA_NOTIFICATION:chat_voice_note]" -> voiceNotePreview
    "[QUATA_ATTACHMENT:file]",
    "[QUATA_NOTIFICATION:chat_attachment]" -> filePreview
    else -> localizedBody(raw)
}

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
    canMutate: Boolean = true,
    onAuthenticationRequired: (NotificationItem) -> Unit = {},
    onDismissAuthenticationRequired: (NotificationItem) -> Unit = onAuthenticationRequired,
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
                when {
                    state.isLoading -> item("notifications-loading") {
                        NotificationStatusCard(strings.loadingLabel) {
                            CircularProgressIndicator()
                        }
                    }
                    state.error != null -> item("notifications-error") {
                        NotificationStatusCard(strings.errorTitle) {
                            Text(state.error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    state.items.isEmpty() -> item("notifications-empty") {
                        NotificationStatusCard(strings.emptyTitle) {
                            Text(strings.emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> items(state.items, key = { it.id }) { item ->
                        DismissibleNotificationCard(
                            item = item,
                            timestampNowMillis = timestampNowMillis,
                            strings = strings,
                            onClick = {
                                handleNotificationClick(
                                    item = item,
                                    canMutate = canMutate,
                                    onMarkRead = onMarkRead,
                                    onOpenConversation = onOpenConversation,
                                    onAuthenticationRequired = onAuthenticationRequired,
                                )
                            },
                            canDismiss = canMutate,
                            onDismiss = { onDismiss(item) },
                            onDismissBlocked = { onDismissAuthenticationRequired(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationStatusCard(
    title: String,
    content: @Composable () -> Unit,
) {
    QuataCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            content()
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
    canDismiss: Boolean,
    onDismiss: () -> Unit,
    onDismissBlocked: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                handleNotificationDismissAttempt(
                    canDismiss = canDismiss,
                    onDismiss = onDismiss,
                    onDismissBlocked = onDismissBlocked,
                )
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
                    val createdAt = strings.relativeTime(item.createdAt, timestampNowMillis)
                    Text(item.title, fontWeight = FontWeight.Bold)
                    Text(
                        text = strings.localizedNotificationBody(item.body),
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

internal fun handleNotificationClick(
    item: NotificationItem,
    canMutate: Boolean,
    onMarkRead: (NotificationItem) -> Unit,
    onOpenConversation: (String) -> Unit,
    onAuthenticationRequired: (NotificationItem) -> Unit,
) {
    if (canMutate) {
        onMarkRead(item)
        onOpenConversation(item.conversationId)
    } else {
        onAuthenticationRequired(item)
    }
}

internal fun handleNotificationDismissAttempt(
    canDismiss: Boolean,
    onDismiss: () -> Unit,
    onDismissBlocked: () -> Unit,
): Boolean {
    if (!canDismiss) {
        onDismissBlocked()
        return false
    }
    onDismiss()
    return true
}
