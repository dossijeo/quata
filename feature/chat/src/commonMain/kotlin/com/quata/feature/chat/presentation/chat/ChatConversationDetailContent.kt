package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.Message

/** Localized labels owned by the host while the conversation structure stays portable. */
data class ChatConversationDetailStrings(
    val edited: String,
    val deletedMessage: String,
    val forwarded: String,
)

/**
 * Portable textual conversation viewport.
 *
 * System-backed concerns remain explicit slots: avatars, media/document/map attachments,
 * delivery/favorite indicators, per-message actions, composer controls and link navigation.
 */
@Composable
fun ChatConversationDetailContent(
    messages: List<Message>,
    selectedMessageId: String?,
    strings: ChatConversationDetailStrings,
    showSenderAvatar: (Message) -> Boolean,
    avatar: @Composable (Message) -> Unit,
    onOpenLink: (String) -> Unit,
    onMessageClick: (Message) -> Unit,
    composer: @Composable (Modifier) -> Unit,
    attachment: (@Composable (Message, Modifier) -> Unit)? = null,
    deliveryIndicator: (@Composable (Message) -> Unit)? = null,
    favoriteMarker: (@Composable (Message) -> Unit)? = null,
    messageActions: (@Composable (Message, Modifier) -> Unit)? = null,
    typingIndicator: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = Message::id) { message ->
                ChatConversationMessageContent(
                    message = message,
                    isSelected = message.id == selectedMessageId,
                    strings = strings,
                    showSenderAvatar = showSenderAvatar(message),
                    avatar = { avatar(message) },
                    onOpenLink = onOpenLink,
                    onClick = { onMessageClick(message) },
                    attachment = attachment?.let { slot -> { bubbleModifier -> slot(message, bubbleModifier) } },
                    deliveryIndicator = deliveryIndicator?.let { slot -> { slot(message) } },
                    favoriteMarker = favoriteMarker?.let { slot -> { slot(message) } },
                    actions = messageActions?.let { slot -> { actionsModifier -> slot(message, actionsModifier) } },
                )
            }
            typingIndicator?.let { indicator ->
                item(key = "chat-typing-indicator") { indicator() }
            }
        }
        composer(Modifier.fillMaxWidth())
    }
}

@Composable
private fun ChatConversationMessageContent(
    message: Message,
    isSelected: Boolean,
    strings: ChatConversationDetailStrings,
    showSenderAvatar: Boolean,
    avatar: @Composable () -> Unit,
    onOpenLink: (String) -> Unit,
    onClick: () -> Unit,
    attachment: (@Composable (Modifier) -> Unit)?,
    deliveryIndicator: (@Composable () -> Unit)?,
    favoriteMarker: (@Composable () -> Unit)?,
    actions: (@Composable (Modifier) -> Unit)?,
) {
    val template = quataTheme()
    val textColor = template.colors.textPrimary
    ChatMessageBubbleLayoutContent(
        isMine = message.isMine,
        isSelected = isSelected,
        showSenderAvatar = showSenderAvatar,
        avatar = avatar,
        bubbleModifier = Modifier.clickable(onClick = onClick),
    ) {
        ChatMessageBubbleContent(
            header = {
                ChatMessageHeaderContent(
                    senderName = message.senderName,
                    timestamp = message.sentAt,
                    isMine = message.isMine,
                    isEdited = message.isEdited,
                    isFavorite = message.isFavorite,
                    editedLabel = strings.edited,
                    textColor = textColor,
                    deliveryIndicator = deliveryIndicator,
                    favoriteMarker = favoriteMarker,
                )
            },
            forwardedMarker = message.forwardedFromSenderId?.let {
                { ChatForwardedMarkerContent(strings.forwarded, textColor) }
            },
            replyQuote = message.replyToMessageId?.let {
                {
                    ChatReplyQuoteContent(
                        senderName = message.replyToSenderName.orEmpty(),
                        text = message.replyToText.orEmpty(),
                        textColor = textColor,
                    )
                }
            },
            body = {
                if (message.isDeleted) {
                    androidx.compose.material3.Text(strings.deletedMessage, color = textColor.copy(alpha = 0.68f))
                } else if (message.text.isNotBlank()) {
                    ChatLinkifiedTextContent(
                        text = message.text,
                        color = textColor,
                        linkColor = template.colors.accent,
                        onOpenLink = onOpenLink,
                    )
                }
                attachment?.let { slot ->
                    if (message.text.isNotBlank() || message.isDeleted) Spacer(Modifier.padding(top = 6.dp))
                    slot(Modifier.fillMaxWidth())
                }
            },
        )
        actions?.invoke(Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}
