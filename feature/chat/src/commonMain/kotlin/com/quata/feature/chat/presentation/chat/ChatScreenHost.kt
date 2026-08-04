package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.navigation.AppDestinations
import com.quata.designsystem.translation.FangTranslatorTriggerContent
import com.quata.designsystem.translation.LocalQuataTranslatableTextRegistry
import com.quata.designsystem.translation.QuataTranslatableTextRegistry
import com.quata.designsystem.translation.quataTranslatableText
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.conversations.shouldShowMessageSenderAvatar

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
    val translatorRegistry = remember(conversationId) { QuataTranslatableTextRegistry() }
    var translatorActive by remember(conversationId) { mutableStateOf(false) }
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

    val isFavoritesConversation = conversationId == AppDestinations.FavoriteMessagesConversationId
    ChatProductScaffold(
        conversationName = conversationId.takeUnless { isFavoritesConversation },
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize()) {
            val selectedMessage = state.messages.firstOrNull { it.id == state.selectedMessageId }
            if (isFavoritesConversation) {
                FavoriteMessagesHeaderContent(slots.chromeStrings.favoriteMessages, slots.chromeStrings.back, slots.onBack)
            } else if (selectedMessage != null) {
                ChatSelectedMessageActionsContent(
                    message = selectedMessage,
                    compact = slots.compactHeader,
                    strings = slots.chromeStrings,
                    onCopy = slots.onCopyMessage,
                    onEvent = model::onEvent,
                )
            } else {
                ChatGroupManagementContent(
                    conversation = state.conversation, state = state, navigationAction = slots.navigationAction,
                    conversationAvatar = { slots.conversationAvatar(state.conversation) }, subtitle = slots.subtitle(state.conversation, state.typingProfileIds), compact = slots.compactHeader,
                    strings = slots.chromeStrings,
                    memberAvatar = slots.memberAvatar,
                    trailing = trailing@{
                        slots.trailingActions.invoke(this@trailing)
                        FangTranslatorTriggerContent(
                            contentDescription = slots.translatorStrings.contentDescription,
                            onClick = { translatorActive = true },
                            enabled = state.messages.any { !it.isDeleted && it.text.isNotBlank() },
                        )
                    }, onOpenProfile = slots.onOpenUserProfile,
                    onLoadMoreParticipants = model::loadMoreParticipantCandidates,
                    onEvent = model::onEvent,
                )
            }
            state.notice?.let { notice ->
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(notice, modifier = Modifier.weight(1f))
                        Button(onClick = { model.onEvent(ChatUiEvent.ClearNotice) }) { Text(slots.chromeStrings.close) }
                    }
                }
            }
            state.error?.let { error ->
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        Button(onClick = { model.onEvent(ChatUiEvent.ClearError) }) { Text(slots.chromeStrings.close) }
                    }
                }
            }
            val focusedLoadFailure = deepLinkRequest as? ChatMessageDeepLinkRequest.LoadFailed
            if (state.messageLoadFailure != null || focusedLoadFailure != null) {
                ChatReadFailureContent(
                    message = focusedLoadFailure?.error ?: state.messageLoadFailure ?: text(ChatText.LoadMessages),
                    retryLabel = slots.chromeStrings.retryMessages,
                    onRetry = {
                        if (focusedLoadFailure != null) {
                            historyPageRequested = false
                            deepLinkRequest = retryChatMessageDeepLinkRequest(deepLinkRequest)
                        }
                        model.retryMessageLoading()
                    },
                )
            }
            CompositionLocalProvider(LocalQuataTranslatableTextRegistry provides translatorRegistry) {
                ChatConversationDetailContent(
                messages = state.messages,
                selectedMessageId = state.selectedMessageId,
                strings = slots.messageStrings,
                showSenderAvatar = { message -> shouldShowMessageSenderAvatar(state.conversation, message) },
                avatar = slots.messageAvatar,
                onOpenLink = slots.onOpenLink,
                messageTimestamp = slots.messageTimestamp,
                onMessageClick = { message ->
                    if (conversationId == AppDestinations.FavoriteMessagesConversationId) {
                        slots.onOpenMessageConversation(message.conversationId, message.id)
                    } else if (message.isLocalEcho) {
                        if (message.deliveryState == com.quata.core.model.MessageDeliveryState.Failed) {
                            message.clientMessageId?.let(model::retryPendingMessage)
                        }
                    } else {
                        model.onEvent(ChatUiEvent.MessageSelected(message.id.takeUnless { it == state.selectedMessageId }))
                    }
                },
                translatableTextModifier = { message, value ->
                    if (message.isDeleted || message.text.isBlank()) value else value.quataTranslatableText(
                        id = "chat-message:${message.composeKey()}",
                        text = message.text,
                        displayText = buildString {
                            append(if (message.isMine) "mine" else "other")
                            append(" | ")
                            append(message.senderName)
                            append(" | ")
                            appendLine(slots.messageTimestamp(message))
                            append(message.text)
                        },
                    )
                },
                composer = if (isFavoritesConversation) ({}) else slots.composer,
                attachment = slots.attachment,
                deliveryIndicator = slots.deliveryIndicator,
                favoriteMarker = slots.favoriteMarker,
                specialMessageBody = slots.specialMessageBody,
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
        }
        if (state.isForwardDialogOpen) {
            ChatForwardPickerContent(
                state = state,
                strings = slots.chromeStrings,
                onEvent = model::onEvent,
                onQueryChanged = model::onForwardCandidateQueryChanged,
                onLoadMore = model::loadMoreForwardConversationCandidates,
            )
        }
        if (translatorActive) {
            ChatTranslatorOverlayContent(
                registry = translatorRegistry,
                gateway = slots.translationGateway,
                initialDirection = slots.translationDirection,
                strings = slots.translatorStrings,
                onDismiss = { translatorActive = false },
            )
        }
    }
}

/** Platform boundaries for `ChatScreenHost`; none of these are product-owned parallel UIs. */
data class ChatScreenHostSlots(
    val chromeStrings: ChatChromeStrings,
    val messageStrings: ChatConversationDetailStrings,
    val translatorStrings: ChatTranslatorStrings,
    val translationGateway: ChatTranslationGateway,
    val translationDirection: ChatTranslationDirection,
    val messageTimestamp: (Message) -> String,
    val compactHeader: Boolean,
    val navigationAction: @Composable () -> Unit,
    val conversationAvatar: @Composable (Conversation?) -> Unit,
    val memberAvatar: @Composable (ChatMemberPresentation) -> Unit,
    val trailingActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    val messageAvatar: @Composable (Message) -> Unit,
    val onOpenLink: (String) -> Unit,
    val onCopyMessage: (String) -> Unit,
    val onOpenMessageConversation: (String, String) -> Unit,
    val onOpenUserProfile: (String) -> Unit,
    val onBack: () -> Unit,
    val subtitle: (Conversation?, Set<String>) -> String?,
    val composer: @Composable (Modifier) -> Unit,
    val attachment: (@Composable (Message, Modifier) -> Unit)? = null,
    val deliveryIndicator: (@Composable (Message) -> Unit)? = null,
    val favoriteMarker: (@Composable (Message) -> Unit)? = null,
    val specialMessageBody: (@Composable (Message) -> Boolean)? = null,
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
