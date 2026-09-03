package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.Message
import com.quata.core.platform.PlatformFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val FocusedMessageHighlightMillis = 8_000L
private const val FocusedMessageViewportInsetFraction = 0.18f
private val ChatConversationMessagesBottomPadding = 96.dp
private val ChatConversationFocusedMessagesTopPadding = 96.dp
private val ChatConversationMessagesTopPadding = 12.dp
const val ChatConversationMessagesListTestTag = "chat.messages.list"

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
    attachment: (@Composable (Message, Boolean, Modifier) -> Unit)? = null,
    deliveryIndicator: (@Composable (Message, Boolean) -> Unit)? = null,
    favoriteMarker: (@Composable (Message, Boolean) -> Unit)? = null,
    specialMessageBody: (@Composable (Message, Boolean) -> Boolean)? = null,
    messageActions: (@Composable (Message, Modifier) -> Unit)? = null,
    typingIndicator: (@Composable () -> Unit)? = null,
    /** Shown inside the history viewport while the first backend snapshot is pending. */
    initialContent: (@Composable () -> Unit)? = null,
    /** Shown inside the history viewport after a successful empty snapshot. */
    emptyContent: (@Composable () -> Unit)? = null,
    /** Real repository pagination; the root never manufactures history locally. */
    onLoadOlderMessages: () -> Boolean = { false },
    isLoadingOlderMessages: Boolean = false,
    /** A host-provided message target. It is ignored safely until it is present in [messages]. */
    focusedMessageId: String? = null,
    onFocusedMessageVisible: (String) -> Unit = {},
    onFocusedMessageHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    var initialPositionReady by remember { mutableStateOf(false) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    var userHasDetachedFromBottom by remember { mutableStateOf(false) }
    var previousMessageLayout by remember { mutableStateOf(emptyList<ChatMessageLayoutKey>()) }
    val density = LocalDensity.current
    val focusedIndex = remember(focusedMessageId, messages) {
        focusedMessageId?.let { target -> messages.indexOfFirst { it.id == target }.takeIf { it >= 0 } }
    }
    val focusedMessageIsMedia = remember(focusedMessageId, messages) {
        focusedMessageId
            ?.let { target -> messages.firstOrNull { it.id == target } }
            ?.mediaAttachmentKind()
            ?.let { it == ChatAttachmentKind.Image || it == ChatAttachmentKind.Video } == true
    }
    val focusedViewportOffset = remember(focusedMessageId, messages) {
        when {
            focusedMessageIsMedia -> ChatConversationFocusedMessagesTopPadding
            focusedMessageId != null -> ChatConversationFocusedMessagesTopPadding
            else -> ChatConversationMessagesTopPadding
        }
    }
    val focusedViewportOffsetPx = with(density) { focusedViewportOffset.roundToPx() }
    val focusedScrollOffsetPx = -focusedViewportOffsetPx
    LaunchedEffect(focusedIndex, focusedScrollOffsetPx) {
        focusedIndex?.let { index ->
            val focusedMessage = messages.getOrNull(index) ?: return@let
            highlightedMessageId = focusedMessage.id
            listState.scrollToItem(index, scrollOffset = focusedScrollOffsetPx)
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.any { item -> item.key == focusedMessage.composeKey() }
            }.first { it }
            val focusInset = listState.layoutInfo.viewportSize.height * FocusedMessageViewportInsetFraction
            val focusedItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                item.key == focusedMessage.composeKey()
            }
            if (focusInset > 0f && focusedItem != null) {
                val desiredTop = maxOf(0, listState.layoutInfo.viewportStartOffset) + focusInset
                val desiredBottom = listState.layoutInfo.viewportEndOffset - focusInset
                val desiredHeight = desiredBottom - desiredTop
                val itemTop = focusedItem.offset.toFloat()
                val itemBottom = itemTop + focusedItem.size
                val itemCenter = itemTop + focusedItem.size / 2f
                val desiredCenter = desiredTop + desiredHeight / 2f
                val scrollDelta = when {
                    focusedItem.size > desiredHeight -> itemCenter - desiredCenter
                    itemTop < desiredTop -> itemTop - desiredTop
                    itemBottom > desiredBottom && focusedItem.size <= desiredHeight -> itemBottom - desiredBottom
                    else -> 0f
                }
                if (scrollDelta != 0f) {
                    listState.scrollBy(scrollDelta)
                }
            }
            onFocusedMessageVisible(focusedMessage.id)
            delay(FocusedMessageHighlightMillis)
            onFocusedMessageHandled()
            highlightedMessageId = null
        }
    }
    val currentMessageLayout = remember(messages) { messages.map(Message::chatLayoutKey) }
    LaunchedEffect(currentMessageLayout, focusedMessageId) {
        if (currentMessageLayout.isEmpty()) {
            previousMessageLayout = emptyList()
            return@LaunchedEffect
        }
        val shouldFollowUpdate = shouldFollowChatLayoutUpdate(
            previous = previousMessageLayout,
            current = currentMessageLayout,
            userHasDetachedFromBottom = userHasDetachedFromBottom,
        )
        if (shouldFollowUpdate && focusedMessageId == null) {
            userHasDetachedFromBottom = false
            listState.scrollToItem(currentMessageLayout.lastIndex, scrollOffset = Int.MAX_VALUE)
        }
        previousMessageLayout = currentMessageLayout
        if (focusedMessageId == null) {
            initialPositionReady = true
        }
    }
    LaunchedEffect(listState, isUserDragging) {
        if (isUserDragging) {
            snapshotFlow { !listState.canScrollForward }
                .collect { isAtBottom -> userHasDetachedFromBottom = !isAtBottom }
        } else if (!listState.canScrollForward) {
            userHasDetachedFromBottom = false
        }
    }
    LaunchedEffect(typingIndicator != null, messages.size, isLoadingOlderMessages) {
        if (typingIndicator != null && !userHasDetachedFromBottom) {
            delay(80L)
            val typingItemIndex = messages.size + if (isLoadingOlderMessages) 1 else 0
            listState.animateScrollToItem(typingItemIndex)
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
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .semantics { testTag = ChatConversationMessagesListTestTag },
            contentPadding = PaddingValues(
                start = 14.dp,
                top = focusedViewportOffset,
                end = 14.dp,
                bottom = ChatConversationMessagesBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (initialContent != null) {
                item(key = "chat-initial-loading") { initialContent() }
            } else if (messages.isEmpty() && emptyContent != null) {
                item(key = "chat-empty") { emptyContent() }
            }
            if (isLoadingOlderMessages) {
                item(key = "chat-history-loading") {
                    ChatMessageSkeletonContent(isMine = false, pulseDelayMillis = 80)
                }
            }
            items(messages, key = Message::composeKey) { message ->
                val isMessageSelected =
                    message.id == selectedMessageId ||
                        message.id == highlightedMessageId ||
                        message.id == focusedMessageId
                ChatConversationMessageContent(
                    message = message,
                    isSelected = isMessageSelected,
                    strings = strings,
                    showSenderAvatar = showSenderAvatar(message),
                    avatar = { avatar(message) },
                    onOpenLink = onOpenLink,
                    onClick = { onMessageClick(message) },
                    timestamp = messageTimestamp(message),
                    translatableTextModifier = translatableTextModifier,
                    attachment = attachment?.let { slot ->
                        { bubbleModifier -> slot(message, isMessageSelected, bubbleModifier) }
                    },
                    deliveryIndicator = deliveryIndicator?.let { slot -> { slot(message, isMessageSelected) } },
                    favoriteMarker = favoriteMarker?.let { slot -> { slot(message, isMessageSelected) } },
                    specialMessageBody = specialMessageBody?.let { slot -> { slot(message, isMessageSelected) } },
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
    val textColor = if (message.isMine || isSelected) template.colors.accentContent else template.colors.textPrimary
    val bubbleSemantics = Modifier.semantics {
        testTag = if (isSelected) "chat.message.${message.id}.selected" else "chat.message.${message.id}"
        role = Role.Button
        contentDescription = message.accessibleActionLabel()
        selected = isSelected
        stateDescription = if (isSelected) "selected" else "not selected"
    }
    val mediaAttachmentOwnsTap = message.mediaAttachmentKind()?.let {
        it == ChatAttachmentKind.Image || it == ChatAttachmentKind.Video
    } == true
    ChatMessageBubbleLayoutContent(
        isMine = message.isMine,
        isSelected = isSelected,
        showSenderAvatar = showSenderAvatar,
        avatar = avatar,
        bubbleModifier = translatableTextModifier(
            message,
            if (mediaAttachmentOwnsTap) bubbleSemantics else bubbleSemantics.clickable(onClick = onClick),
        ),
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
                        linkColor = if (message.isMine || isSelected) template.colors.accentContent else template.colors.accent,
                        onOpenLink = onOpenLink,
                    )
                }
                attachment?.let { slot ->
                    if ((!specialBodyRendered && message.text.isNotBlank()) || message.isDeleted) Spacer(Modifier.padding(top = 6.dp))
                    slot(Modifier.fillMaxWidth())
                }
            },
        )
        if (isSelected) {
            Box(
                Modifier
                    .size(1.dp)
                    .semantics {
                        testTag = "chat.message.${message.id}.selected"
                        stateDescription = "selected"
                    },
            )
        }
        if (message.isPending) {
            Box(
                Modifier
                    .size(1.dp)
                    .semantics {
                        testTag = "chat.message.${message.id}.pending"
                        stateDescription = "pending"
                    },
            )
        }
        actions?.invoke(Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

private fun Message.accessibleActionLabel(): String {
    val body = text.takeIf { it.isNotBlank() } ?: replyToText?.takeIf { it.isNotBlank() } ?: id
    return "${senderName.ifBlank { "Mensaje" }}: $body"
}

private fun Message.mediaAttachmentKind(): ChatAttachmentKind? {
    val reference = attachmentUri?.takeIf { it.isNotBlank() } ?: return null
    return chatAttachmentKind(PlatformFile(reference, attachmentName, attachmentMimeType))
}
