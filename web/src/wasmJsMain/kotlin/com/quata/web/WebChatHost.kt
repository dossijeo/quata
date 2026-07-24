package com.quata.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.platform.AudioPlaybackState
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.feature.chat.presentation.chat.ChatAudioAttachmentPlayerContent
import com.quata.feature.chat.presentation.chat.ChatConversationDetailContent
import com.quata.feature.chat.presentation.chat.ChatConversationDetailStrings
import com.quata.feature.chat.presentation.chat.ChatViewModel
import com.quata.feature.chat.presentation.conversations.ConversationListRow
import com.quata.feature.chat.presentation.conversations.ConversationsListContent
import com.quata.feature.chat.presentation.conversations.ConversationsUiEvent
import com.quata.feature.chat.presentation.conversations.ConversationsViewModel
import kotlinx.coroutines.launch

/** Read-only browser chat host backed by RPC polling; it intentionally exposes no composer actions. */
@Composable
fun WebChatHost(
    repository: WebChatRepository,
    audioPlayer: AudioPlayerService,
    conversationId: String?,
    navigationMessage: String,
    onOpenConversation: (String) -> Unit,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (conversationId == null) {
        WebChatConversationList(
            repository = repository,
            navigationMessage = navigationMessage,
            onOpenConversation = onOpenConversation,
            modifier = modifier,
        )
    } else {
        WebChatConversationDetail(
            repository = repository,
            audioPlayer = audioPlayer,
            conversationId = conversationId,
            navigationMessage = navigationMessage,
            onBackToList = onBackToList,
            modifier = modifier,
        )
    }
}

@Composable
private fun WebChatConversationList(
    repository: WebChatRepository,
    navigationMessage: String,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier,
) {
    val viewModel = remember(repository) {
        ConversationsViewModel(repository = repository, text = { "No se pudieron cargar los chats." })
    }
    val state by viewModel.uiState.collectAsState()
    DisposableEffect(viewModel) { onDispose(viewModel::close) }

    val rows = state.conversations.map { conversation ->
        ConversationListRow(
            conversation = conversation,
            title = conversation.webChatTitle(),
            preview = conversation.lastMessagePreview.ifBlank { "Sin mensajes" },
            updatedAt = conversation.updatedAt,
        )
    }
    Column(modifier.fillMaxSize()) {
        state.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
        ConversationsListContent(
            rows = rows,
            isLoading = state.isLoading,
            avatar = { row -> WebChatAvatar(row.conversation) },
            onOpenConversation = { row -> onOpenConversation(row.conversation.id) },
            header = {
                WebChatListHeader(
                    message = navigationMessage,
                    onRefresh = { viewModel.onEvent(ConversationsUiEvent.Refresh) },
                )
            },
            emptyContent = { WebChatEmptyContent() },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WebChatConversationDetail(
    repository: WebChatRepository,
    audioPlayer: AudioPlayerService,
    conversationId: String,
    navigationMessage: String,
    onBackToList: () -> Unit,
    modifier: Modifier,
) {
    val viewModel = remember(repository, conversationId) {
        ChatViewModel(conversationId = conversationId, repository = repository, text = { "No se pudieron cargar los mensajes." })
    }
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var activeAudioReference by remember { mutableStateOf<String?>(null) }
    var audioPlayback by remember { mutableStateOf(AudioPlaybackState()) }
    var audioError by remember { mutableStateOf(false) }
    DisposableEffect(viewModel) {
        repository.setActiveConversation(conversationId)
        onDispose {
            repository.setActiveConversation(null)
            viewModel.close()
        }
    }
    Column(modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onBackToList) { Text("Volver a conversaciones") }
                Text(state.conversation?.webChatTitle() ?: "Conversación", style = MaterialTheme.typography.titleLarge)
                Text(navigationMessage, style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        ChatConversationDetailContent(
            messages = state.messages,
            selectedMessageId = null,
            strings = ChatConversationDetailStrings(
                edited = "Editado",
                deletedMessage = "Mensaje eliminado",
                forwarded = "Reenviado",
            ),
            showSenderAvatar = { message -> !message.isMine },
            avatar = { message -> WebChatMessageAvatar(message) },
            onOpenLink = ::openWebExternalLink,
            onMessageClick = {},
            composer = { composerModifier ->
                Surface(modifier = composerModifier) {
                    Text(
                        "Chat de solo lectura en Quata Web.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            attachment = { message, attachmentModifier ->
                WebChatAttachment(
                    message = message,
                    audioPlayer = audioPlayer,
                    activeAudioReference = activeAudioReference,
                    playback = audioPlayback,
                    hasError = audioError,
                    onPlaybackChanged = { reference, updatedState, hasFailure ->
                        activeAudioReference = reference
                        audioPlayback = updatedState
                        audioError = hasFailure
                    },
                    launch = { block -> scope.launch { block() } },
                    modifier = attachmentModifier,
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WebChatAttachment(
    message: Message,
    audioPlayer: AudioPlayerService,
    activeAudioReference: String?,
    playback: AudioPlaybackState,
    hasError: Boolean,
    onPlaybackChanged: (String?, AudioPlaybackState, Boolean) -> Unit,
    launch: ((suspend () -> Unit) -> Unit),
    modifier: Modifier,
) {
    val reference = message.attachmentUri ?: return
    val mimeType = message.attachmentMimeType.orEmpty()
    val displayName = message.attachmentName?.takeIf { it.isNotBlank() } ?: "Adjunto"
    if (mimeType.startsWith("audio/", ignoreCase = true)) {
        val isActive = activeAudioReference == reference
        val displayedState = if (isActive) playback else AudioPlaybackState()
        ChatAudioAttachmentPlayerContent(
            isPlaying = displayedState.isPlaying,
            hasError = isActive && hasError,
            progress = if (displayedState.durationMillis > 0L) {
                displayedState.positionMillis.toFloat() / displayedState.durationMillis.toFloat()
            } else 0f,
            displayText = displayName,
            textColor = MaterialTheme.colorScheme.onSurface,
            playPauseDescription = if (displayedState.isPlaying) "Pausar audio" else "Reproducir audio",
            onTogglePlayback = {
                launch {
                    val result = if (!isActive) {
                        when (val loaded = audioPlayer.load(PlatformFile(reference, displayName, mimeType))) {
                            is PlatformResult.Success -> audioPlayer.play()
                            is PlatformResult.Failure -> loaded
                            PlatformResult.Cancelled -> PlatformResult.Cancelled
                            PlatformResult.Unsupported -> PlatformResult.Unsupported
                        }
                    } else if (displayedState.isPlaying) {
                        audioPlayer.pause()
                    } else {
                        audioPlayer.play()
                    }
                    when (result) {
                        is PlatformResult.Success -> onPlaybackChanged(reference, result.value, false)
                        is PlatformResult.Failure,
                        PlatformResult.Cancelled,
                        PlatformResult.Unsupported -> onPlaybackChanged(reference, displayedState, true)
                    }
                }
            },
            onSeekToFraction = { fraction ->
                if (isActive && displayedState.durationMillis > 0L) {
                    launch {
                        when (val result = audioPlayer.seekTo((displayedState.durationMillis * fraction).toLong())) {
                            is PlatformResult.Success -> onPlaybackChanged(reference, result.value, false)
                            is PlatformResult.Failure,
                            PlatformResult.Cancelled,
                            PlatformResult.Unsupported -> onPlaybackChanged(reference, displayedState, true)
                        }
                    }
                }
            },
            modifier = modifier,
        )
    } else {
        val safeUrl = reference.safeWebAttachmentUrl()
        Surface(modifier = modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(displayName, style = MaterialTheme.typography.bodyMedium)
                if (safeUrl != null) {
                    Button(onClick = { openWebExternalLink(safeUrl) }) { Text("Abrir adjunto") }
                } else {
                    Text("Adjunto no disponible en este navegador.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun WebChatListHeader(message: String, onRefresh: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Conversaciones", style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRefresh) { Text("Actualizar") }
        }
    }
}

@Composable
private fun WebChatAvatar(conversation: Conversation) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.size(46.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(conversation.webChatTitle().firstOrNull()?.uppercase() ?: "C")
        }
    }
}

@Composable
private fun WebChatMessageAvatar(message: Message) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.size(34.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(message.senderName.firstOrNull()?.uppercase() ?: "U")
        }
    }
}

@Composable
private fun WebChatEmptyContent() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text("No hay conversaciones disponibles.")
    }
}

private fun Conversation.webChatTitle(): String = title.ifBlank { "Conversación" }

private fun String.safeWebAttachmentUrl(): String? = takeIf {
    startsWith("https://", ignoreCase = true) ||
        startsWith("http://", ignoreCase = true) ||
        startsWith("blob:", ignoreCase = true)
}

private fun openWebExternalLink(url: String): Unit = js("globalThis.open(url, '_blank', 'noopener,noreferrer')")
