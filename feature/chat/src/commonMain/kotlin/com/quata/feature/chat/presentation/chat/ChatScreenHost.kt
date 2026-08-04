package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.navigation.AppDestinations
import com.quata.designsystem.chat.ProceduralChatBackgroundCanvas
import com.quata.designsystem.chat.proceduralChatBackgroundSpec
import com.quata.feature.chat.domain.ChatRepository

/**
 * Product root shared by Android, Wasm and iOS for the read side of `SCR-CHAT`.
 *
 * This deliberately owns the background, title hierarchy, loading/error/history/typing states,
 * message selection and focused-message consumption. System-backed rendering and the composer are
 * explicit slots; the later chat units replace their platform-specific implementations without
 * moving product navigation or read state back out of commonMain.
 *
 * IDs: SCR-CHAT, CHAT-MESSAGES, CHAT-FOCUSED-MESSAGE, CHAT-PROFILE.
 */
@Composable
fun ChatScreenHost(
    repository: ChatRepository,
    conversationId: String,
    text: (ChatText) -> String,
    slots: ChatScreenHostSlots,
    focusedMessageId: String? = null,
    modifier: Modifier = Modifier,
    model: ChatViewModel = remember(repository, conversationId) {
        ChatViewModel(conversationId = conversationId, repository = repository, text = text)
    },
) {
    val state by model.uiState.collectAsState()
    val template = quataTheme()
    var deepLinkRequest by remember(conversationId, focusedMessageId) {
        mutableStateOf(chatMessageDeepLinkRequest(focusedMessageId))
    }
    var historyPageRequested by remember(conversationId, focusedMessageId) { mutableStateOf(false) }
    val resolvedDeepLinkRequest = resolveChatMessageDeepLinkRequest(
        request = deepLinkRequest,
        hasReceivedMessageSnapshot = state.hasReceivedMessageSnapshot,
        messages = state.messages,
        hasMoreHistory = state.hasMoreHistory,
        messageLoadFailure = state.messageLoadFailure,
    )
    LaunchedEffect(resolvedDeepLinkRequest) {
        if (resolvedDeepLinkRequest != deepLinkRequest) deepLinkRequest = resolvedDeepLinkRequest
    }
    LaunchedEffect(deepLinkRequest, state.isLoadingOlderMessages, historyPageRequested) {
        if (deepLinkRequest !is ChatMessageDeepLinkRequest.LoadingOlder) return@LaunchedEffect
        if (!historyPageRequested) {
            historyPageRequested = model.loadOlderMessages()
        } else if (!state.isLoadingOlderMessages) {
            historyPageRequested = false
            deepLinkRequest = resumeChatMessageDeepLinkRequest(deepLinkRequest)
        }
    }
    val focusedMessage = (deepLinkRequest as? ChatMessageDeepLinkRequest.Focused)
        ?.let { focused -> state.messages.firstOrNull { it.id == focused.messageId } }

    LaunchedEffect(state.shouldCloseConversation) {
        if (state.shouldCloseConversation) slots.onBack()
    }

    Box(modifier.fillMaxSize().background(template.colors.background)) {
        ProceduralChatBackgroundCanvas(
            spec = proceduralChatBackgroundSpec(
                conversationName = conversationId,
                templateId = "${template.id}-clouds-v3",
                paletteCount = template.colors.chatBackgroundPalettes.size,
            ),
            palettes = template.colors.chatBackgroundPalettes,
        )
        Box(Modifier.fillMaxSize().background(template.colors.scrim))
        Column(Modifier.fillMaxSize()) {
            val selectedMessage = state.messages.firstOrNull { it.id == state.selectedMessageId }
            if (selectedMessage != null) {
                ChatSelectedMessageActionsContent(
                    message = selectedMessage,
                    compact = slots.compactHeader,
                    onCopy = slots.onCopyMessage,
                    onEvent = model::onEvent,
                )
            } else {
                ChatConversationTitleBarContent(
                    title = state.conversation?.title ?: slots.strings.untitledConversation,
                    subtitle = slots.subtitle(state.conversation, state.typingProfileIds),
                    expandable = false,
                    compact = slots.compactHeader,
                    onToggleExpanded = {},
                    navigationAction = slots.navigationAction,
                    avatar = { slots.conversationAvatar(state.conversation) },
                    trailingActions = slots.trailingActions,
                )
            }
            val focusedLoadFailure = deepLinkRequest as? ChatMessageDeepLinkRequest.LoadFailed
            if (state.messageLoadFailure != null || focusedLoadFailure != null) {
                ChatReadFailureContent(
                    message = focusedLoadFailure?.error ?: state.messageLoadFailure ?: text(ChatText.LoadMessages),
                    retryLabel = slots.strings.retryMessages,
                    onRetry = {
                        if (focusedLoadFailure != null) {
                            historyPageRequested = false
                            deepLinkRequest = retryChatMessageDeepLinkRequest(deepLinkRequest)
                        }
                        model.retryMessageLoading()
                    },
                )
            }
            ChatConversationDetailContent(
                messages = state.messages,
                selectedMessageId = state.selectedMessageId,
                strings = slots.messageStrings,
                showSenderAvatar = { message -> !message.isMine },
                avatar = slots.messageAvatar,
                onOpenLink = slots.onOpenLink,
                onMessageClick = { message ->
                    if (conversationId == AppDestinations.FavoriteMessagesConversationId) {
                        slots.onOpenMessageConversation(message.conversationId, message.id)
                    } else model.onEvent(ChatUiEvent.MessageSelected(message.id.takeUnless { it == state.selectedMessageId }))
                },
                composer = slots.composer,
                attachment = slots.attachment,
                deliveryIndicator = slots.deliveryIndicator,
                favoriteMarker = slots.favoriteMarker,
                messageActions = slots.messageActions,
                typingIndicator = slots.typingIndicator(state.typingProfileIds),
                initialContent = if (state.isLoading && state.messages.isEmpty()) slots.loadingContent else null,
                onLoadOlderMessages = model::loadOlderMessages,
                isLoadingOlderMessages = state.isLoadingOlderMessages,
                focusedMessageId = focusedMessage?.id,
                onFocusedMessageHandled = { deepLinkRequest = ChatMessageDeepLinkRequest.NoTarget },
                modifier = Modifier.weight(1f),
            )
        }
        if (state.isForwardDialogOpen) {
            ChatForwardPickerContent(
                state = state,
                onEvent = model::onEvent,
                onQueryChanged = model::onForwardCandidateQueryChanged,
            )
        }
    }
}

data class ChatScreenHostStrings(
    val untitledConversation: String,
    val retryMessages: String,
)

/** Platform boundaries for `ChatScreenHost`; none of these are product-owned parallel UIs. */
data class ChatScreenHostSlots(
    val strings: ChatScreenHostStrings,
    val messageStrings: ChatConversationDetailStrings,
    val compactHeader: Boolean,
    val navigationAction: @Composable () -> Unit,
    val conversationAvatar: @Composable (Conversation?) -> Unit,
    val trailingActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    val messageAvatar: @Composable (Message) -> Unit,
    val onOpenLink: (String) -> Unit,
    val onCopyMessage: (String) -> Unit,
    val onOpenMessageConversation: (String, String) -> Unit,
    val onBack: () -> Unit,
    val subtitle: (Conversation?, Set<String>) -> String?,
    val composer: @Composable (Modifier) -> Unit,
    val attachment: (@Composable (Message, Modifier) -> Unit)? = null,
    val deliveryIndicator: (@Composable (Message) -> Unit)? = null,
    val favoriteMarker: (@Composable (Message) -> Unit)? = null,
    val messageActions: (@Composable (Message, Modifier) -> Unit)? = null,
    val typingIndicator: @Composable (Set<String>) -> (@Composable () -> Unit)?,
    val loadingContent: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            ChatMessageSkeletonContent(isMine = false, pulseDelayMillis = 0)
            ChatMessageSkeletonContent(isMine = true, pulseDelayMillis = 120)
            ChatMessageSkeletonContent(isMine = false, pulseDelayMillis = 240)
        }
    },
)

@Composable
private fun ChatReadFailureContent(message: String, retryLabel: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text(retryLabel) }
    }
}
