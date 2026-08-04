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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.Message
import kotlinx.coroutines.delay

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
    messageTimestamp: (Message) -> String = { it.sentAt },
    translatableTextModifier: (Message, Modifier) -> Modifier = { _, value -> value },
    composer: @Composable (Modifier) -> Unit,
    attachment: (@Composable (Message, Modifier) -> Unit)? = null,
    deliveryIndicator: (@Composable (Message) -> Unit)? = null,
    favoriteMarker: (@Composable (Message) -> Unit)? = null,
    specialMessageBody: (@Composable (Message) -> Boolean)? = null,
    messageActions: (@Composable (Message, Modifier) -> Unit)? = null,
    typingIndicator: (@Composable () -> Unit)? = null,
    /** Shown inside the history viewport while the first backend snapshot is pending. */
    initialContent: (@Composable () -> Unit)? = null,
    /** Real repository pagination; the root never manufactures history locally. */
    onLoadOlderMessages: () -> Boolean = { false },
    isLoadingOlderMessages: Boolean = false,
    /** A host-provided message target. It is ignored safely until it is present in [messages]. */
    focusedMessageId: String? = null,
    onFocusedMessageHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var initialPositionReady by remember { mutableStateOf(false) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    val focusedIndex = remember(focusedMessageId, messages) {
        focusedMessageId?.let { target -> messages.indexOfFirst { it.id == target }.takeIf { it >= 0 } }
    }
    LaunchedEffect(focusedIndex) {
        focusedIndex?.let { index ->
            listState.scrollToItem(index)
            highlightedMessageId = messages[index].id
            delay(720L)
            highlightedMessageId = null
            onFocusedMessageHandled()
        }
    }
    LaunchedEffect(messages, focusedMessageId, initialPositionReady) {
        if (!initialPositionReady && focusedMessageId == null && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
            initialPositionReady = true
        }
    }
    LaunchedEffect(listState, isLoadingOlderMessages, initialPositionReady, focusedMessageId) {
        if (!initialPositionReady || focusedMessageId != null) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress to listState.firstVisibleItemIndex }
            .collect { (isScrolling, firstVisible) ->
                if (isScrolling && firstVisible <= 2 && !isLoadingOlderMessages) onLoadOlderMessages()
            }
    }
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (initialContent != null) {
                item(key = "chat-initial-loading") { initialContent() }
            }
            if (isLoadingOlderMessages) {
                item(key = "chat-history-loading") {
                    ChatMessageSkeletonContent(isMine = false, pulseDelayMillis = 80)
                }
            }
            items(messages, key = Message::composeKey) { message ->
                ChatConversationMessageContent(
                    message = message,
                    isSelected = message.id == selectedMessageId || message.id == highlightedMessageId,
                    strings = strings,
                    showSenderAvatar = showSenderAvatar(message),
                    avatar = { avatar(message) },
                    onOpenLink = onOpenLink,
                    onClick = { onMessageClick(message) },
                    timestamp = messageTimestamp(message),
                    translatableTextModifier = translatableTextModifier,
                    attachment = attachment?.let { slot -> { bubbleModifier -> slot(message, bubbleModifier) } },
                    deliveryIndicator = deliveryIndicator?.let { slot -> { slot(message) } },
                    favoriteMarker = favoriteMarker?.let { slot -> { slot(message) } },
                    specialMessageBody = specialMessageBody?.let { slot -> { slot(message) } },
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
    timestamp: String,
    translatableTextModifier: (Message, Modifier) -> Modifier,
    attachment: (@Composable (Modifier) -> Unit)?,
    deliveryIndicator: (@Composable () -> Unit)?,
    favoriteMarker: (@Composable () -> Unit)?,
    specialMessageBody: (@Composable () -> Boolean)?,
    actions: (@Composable (Modifier) -> Unit)?,
) {
    val template = quataTheme()
    val textColor = if (message.isMine) template.colors.accentContent else template.colors.textPrimary
    ChatMessageBubbleLayoutContent(
        isMine = message.isMine,
        isSelected = isSelected,
        showSenderAvatar = showSenderAvatar,
        avatar = avatar,
        bubbleModifier = translatableTextModifier(message, Modifier.clickable(onClick = onClick)),
    ) {
        ChatMessageBubbleContent(
            header = {
                ChatMessageHeaderContent(
                    senderName = message.senderName,
                    timestamp = timestamp,
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
                val specialBodyRendered = specialMessageBody?.invoke() == true
                if (message.isDeleted) {
                    androidx.compose.material3.Text(strings.deletedMessage, color = textColor.copy(alpha = 0.68f))
                } else if (!specialBodyRendered && message.text.isNotBlank()) {
                    ChatLinkifiedTextContent(
                        text = message.text,
                        color = textColor,
                        linkColor = template.colors.accent,
                        onOpenLink = onOpenLink,
                    )
                }
                attachment?.let { slot ->
                    if ((!specialBodyRendered && message.text.isNotBlank()) || message.isDeleted) Spacer(Modifier.padding(top = 6.dp))
                    slot(Modifier.fillMaxWidth())
                }
            },
        )
        actions?.invoke(Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}
