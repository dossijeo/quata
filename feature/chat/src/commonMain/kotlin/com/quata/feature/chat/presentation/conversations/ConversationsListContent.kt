package com.quata.feature.chat.presentation.conversations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.model.Conversation

/**
 * Host-neutral conversation-list structure. Hosts localize row text and own platform avatar,
 * navigation and contextual actions; this layer only arranges the shared list components.
 */
data class ConversationListRow(
    val conversation: Conversation,
    val title: String,
    val preview: String,
    val updatedAt: String,
)

@Composable
fun ConversationsListContent(
    rows: List<ConversationListRow>,
    isLoading: Boolean,
    avatar: @Composable (ConversationListRow) -> Unit,
    onOpenConversation: (ConversationListRow) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    emptyContent: @Composable () -> Unit = {},
    rowActions: @Composable RowScope.(ConversationListRow) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "conversation-list-header") { header() }
        if (isLoading && rows.isEmpty()) {
            items(6, key = { index -> "conversation-list-skeleton-$index" }) { index ->
                ConversationListLoadingSkeletonContent(pulseDelayMillis = index * 85)
            }
        } else if (rows.isEmpty()) {
            item(key = "conversation-list-empty") { emptyContent() }
        } else {
            items(rows, key = { row -> row.conversation.id }) { row ->
                ConversationListItemContent(
                    title = row.title,
                    preview = row.preview,
                    updatedAt = row.updatedAt,
                    unreadCount = row.conversation.unreadCount,
                    showUnreadBadge = !row.conversation.isMuted,
                    avatar = { avatar(row) },
                    onOpen = { onOpenConversation(row) },
                    trailingAction = { rowActions(row) },
                )
            }
        }
    }
}
