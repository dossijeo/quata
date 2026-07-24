package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.quata.core.model.Message
import com.quata.core.platform.AudioPlaybackState
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.FilePickerSource
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.conversations.ConversationListRow
import com.quata.feature.chat.presentation.conversations.ConversationsListContent
import com.quata.feature.chat.presentation.conversations.ConversationsUiEvent
import com.quata.feature.chat.presentation.conversations.ConversationsViewModel
import kotlinx.coroutines.launch

/** Host-neutral browser-style Chat viewport. Navigation and external opening are injected. */
@Composable
fun ChatBrowserHostContent(
    repository: ChatRepository,
    audioPlayer: AudioPlayerService,
    filePicker: FilePickerService,
    conversationId: String?,
    navigationMessage: String,
    onOpenConversation: (String) -> Unit,
    onBackToList: () -> Unit,
    onOpenAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (conversationId == null) {
        ChatBrowserConversationList(
            repository = repository,
            navigationMessage = navigationMessage,
            onOpenConversation = onOpenConversation,
            modifier = modifier,
        )
    } else {
        ChatBrowserConversationDetail(
            repository = repository,
            audioPlayer = audioPlayer,
            filePicker = filePicker,
            conversationId = conversationId,
            navigationMessage = navigationMessage,
            onBackToList = onBackToList,
            onOpenAttachment = onOpenAttachment,
            modifier = modifier,
        )
    }
}

@Composable
private fun ChatBrowserConversationList(
    repository: ChatRepository,
    navigationMessage: String,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier,
) {
    val viewModel = remember(repository) {
        ConversationsViewModel(repository = repository, text = { "No se pudieron cargar los chats." })
    }
    val state by viewModel.uiState.collectAsState()
    var isGroupComposerOpen by remember { mutableStateOf(false) }
    DisposableEffect(viewModel) { onDispose(viewModel::close) }

    Column(modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Conversaciones", style = MaterialTheme.typography.titleLarge)
                Text(navigationMessage, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { viewModel.onEvent(ConversationsUiEvent.Refresh) }) { Text("Actualizar") }
                Button(onClick = {
                    isGroupComposerOpen = false
                    viewModel.openNewConversationPicker()
                }) { Text("Nuevo chat") }
            }
        }
        if (state.isNewConversationPickerOpen) {
            ChatConversationCreationContent(
                state = state,
                isGroupComposerOpen = isGroupComposerOpen,
                onGroupComposerOpenChanged = { isGroupComposerOpen = it },
                onQueryChanged = viewModel::onCandidateQueryChanged,
                onOpenPrivate = { candidate -> viewModel.openCandidateConversation(candidate, onOpenConversation) },
                onToggleGroupCandidate = viewModel::toggleNewConversationCandidate,
                onGroupTitleChanged = viewModel::onNewGroupTitleChanged,
                onCreateGroup = { viewModel.openSelectedGroupConversation(onOpenConversation) },
                onLoadMore = viewModel::loadMoreConversationCandidates,
                onDismiss = viewModel::closeNewConversationPicker,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ConversationsListContent(
            rows = state.conversations.map { conversation ->
                ConversationListRow(
                    conversation = conversation,
                    title = conversation.title.ifBlank { "Conversación" },
                    preview = conversation.lastMessagePreview.ifBlank { "Sin mensajes" },
                    updatedAt = conversation.updatedAt,
                )
            },
            isLoading = state.isLoading,
            avatar = {},
            onOpenConversation = { row -> onOpenConversation(row.conversation.id) },
            emptyContent = {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No hay conversaciones disponibles.")
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/** Common, host-neutral new private/group chat flow backed by [ConversationsViewModel]. */
@Composable
private fun ChatConversationCreationContent(
    state: com.quata.feature.chat.presentation.conversations.ConversationsUiState,
    isGroupComposerOpen: Boolean,
    onGroupComposerOpenChanged: (Boolean) -> Unit,
    onQueryChanged: (String) -> Unit,
    onOpenPrivate: (com.quata.feature.chat.domain.ChatConversationCandidate) -> Unit,
    onToggleGroupCandidate: (com.quata.feature.chat.domain.ChatConversationCandidate) -> Unit,
    onGroupTitleChanged: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (isGroupComposerOpen) "Nuevo grupo" else "Nueva conversación", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.candidateQuery,
                onValueChange = onQueryChanged,
                label = { Text("Buscar personas") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isGroupComposerOpen) {
                OutlinedTextField(
                    value = state.newGroupTitle,
                    onValueChange = onGroupTitleChanged,
                    label = { Text("Nombre del grupo (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Selecciona al menos dos personas.", style = MaterialTheme.typography.bodySmall)
            }
            state.candidateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when {
                state.isCandidateInitialLoading -> Text("Buscando personas…", style = MaterialTheme.typography.bodySmall)
                state.conversationCandidates.isEmpty() -> Text("No se encontraron personas.", style = MaterialTheme.typography.bodySmall)
                else -> state.conversationCandidates.forEach { candidate ->
                    Surface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(candidate.displayName, style = MaterialTheme.typography.titleSmall)
                            candidate.neighborhood.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                            if (isGroupComposerOpen) {
                                val selected = candidate.profileId in state.selectedNewConversationProfileIds
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { onToggleGroupCandidate(candidate) },
                                )
                            } else {
                                Button(
                                    onClick = { onOpenPrivate(candidate) },
                                    enabled = state.openingCandidateProfileId == null,
                                ) {
                                    Text(if (state.openingCandidateProfileId == candidate.profileId) "Abriendo…" else "Abrir chat")
                                }
                            }
                        }
                    }
                }
            }
            if (state.candidateHasMore && !state.isCandidateInitialLoading) {
                Button(onClick = onLoadMore, enabled = !state.isCandidatePageLoading) {
                    Text(if (state.isCandidatePageLoading) "Cargando…" else "Cargar más")
                }
            }
            if (isGroupComposerOpen) {
                Button(
                    onClick = onCreateGroup,
                    enabled = state.selectedNewConversationProfileIds.size >= 2 && !state.isOpeningGroupConversation,
                ) { Text(if (state.isOpeningGroupConversation) "Creando…" else "Crear grupo") }
            }
            Button(onClick = { onGroupComposerOpenChanged(!isGroupComposerOpen) }) {
                Text(if (isGroupComposerOpen) "Crear chat privado" else "Crear grupo")
            }
            Button(onClick = onDismiss) { Text("Cancelar") }
        }
    }
}

@Composable
private fun ChatBrowserConversationDetail(
    repository: ChatRepository,
    audioPlayer: AudioPlayerService,
    filePicker: FilePickerService,
    conversationId: String,
    navigationMessage: String,
    onBackToList: () -> Unit,
    onOpenAttachment: (String) -> Unit,
    modifier: Modifier,
) {
    val viewModel = remember(repository, conversationId) {
        ChatViewModel(
            conversationId = conversationId,
            repository = repository,
            text = { "No se pudieron cargar los mensajes." },
        )
    }
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var activeAudioReference by remember { mutableStateOf<String?>(null) }
    var audioPlayback by remember { mutableStateOf(AudioPlaybackState()) }
    var audioFailed by remember { mutableStateOf(false) }
    DisposableEffect(viewModel) {
        repository.setActiveConversation(conversationId)
        onDispose {
            repository.setActiveConversation(null)
            viewModel.close()
        }
    }

    Column(modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onBackToList) { Text("Volver a conversaciones") }
                Text(state.conversation?.title ?: "Conversación", style = MaterialTheme.typography.titleLarge)
                Text(navigationMessage, style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        ChatConversationDetailContent(
            messages = state.messages,
            selectedMessageId = null,
            strings = ChatConversationDetailStrings("Editado", "Mensaje eliminado", "Reenviado"),
            showSenderAvatar = { message -> !message.isMine },
            avatar = {},
            onOpenLink = onOpenAttachment,
            onMessageClick = {},
            composer = { composerModifier ->
                Surface(composerModifier) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.messageText,
                            onValueChange = { value -> viewModel.onEvent(ChatUiEvent.MessageChanged(value)) },
                            label = { Text("Mensaje") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        state.attachmentName?.let { name ->
                            Text("Adjunto: $name", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    when (val result = filePicker.pick(
                                        FilePickerRequest(
                                            allowMultiple = false,
                                            source = FilePickerSource.Documents,
                                        ),
                                    )) {
                                        is PlatformResult.Success -> result.value.firstOrNull()?.let { file ->
                                            viewModel.onEvent(
                                                ChatUiEvent.AttachmentSelected(
                                                    uri = file.reference,
                                                    name = file.displayName ?: "Adjunto",
                                                    mimeType = file.mimeType,
                                                ),
                                            )
                                        }
                                        is PlatformResult.Failure -> Unit
                                        PlatformResult.Cancelled,
                                        PlatformResult.Unsupported -> Unit
                                    }
                                }
                            },
                        ) { Text("Adjuntar archivo") }
                        if (state.attachmentUri != null) {
                            Button(onClick = { viewModel.onEvent(ChatUiEvent.ClearAttachment) }) {
                                Text("Quitar adjunto")
                            }
                        }
                        Button(
                            onClick = { viewModel.onEvent(ChatUiEvent.Send) },
                            enabled = state.messageText.isNotBlank() || state.attachmentUri != null,
                        ) { Text("Enviar") }
                    }
                }
            },
            attachment = { message, attachmentModifier ->
                ChatBrowserAttachmentContent(
                    message = message,
                    audioPlayer = audioPlayer,
                    activeAudioReference = activeAudioReference,
                    playback = audioPlayback,
                    failed = audioFailed,
                    onPlaybackChanged = { reference, updatedPlayback, failed ->
                        activeAudioReference = reference
                        audioPlayback = updatedPlayback
                        audioFailed = failed
                    },
                    onOpenAttachment = onOpenAttachment,
                    launch = { action -> scope.launch { action() } },
                    modifier = attachmentModifier,
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChatBrowserAttachmentContent(
    message: Message,
    audioPlayer: AudioPlayerService,
    activeAudioReference: String?,
    playback: AudioPlaybackState,
    failed: Boolean,
    onPlaybackChanged: (String?, AudioPlaybackState, Boolean) -> Unit,
    onOpenAttachment: (String) -> Unit,
    launch: ((suspend () -> Unit) -> Unit),
    modifier: Modifier,
) {
    val reference = message.attachmentUri.orEmpty()
    if (reference.isBlank()) return
    val mimeType = message.attachmentMimeType.orEmpty()
    val displayName = message.attachmentName?.takeIf { it.isNotBlank() } ?: "Adjunto"
    if (!mimeType.startsWith("audio/", ignoreCase = true)) {
        Surface(modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(displayName)
                Button(onClick = { onOpenAttachment(reference) }) { Text("Abrir adjunto") }
            }
        }
        return
    }
    val isActive = activeAudioReference == reference
    val visiblePlayback = if (isActive) playback else AudioPlaybackState()
    ChatAudioAttachmentPlayerContent(
        isPlaying = visiblePlayback.isPlaying,
        hasError = isActive && failed,
        progress = if (visiblePlayback.durationMillis > 0L) {
            visiblePlayback.positionMillis.toFloat() / visiblePlayback.durationMillis.toFloat()
        } else 0f,
        displayText = displayName,
        textColor = MaterialTheme.colorScheme.onSurface,
        playPauseDescription = if (visiblePlayback.isPlaying) "Pausar audio" else "Reproducir audio",
        onTogglePlayback = {
            launch {
                val result = when {
                    !isActive -> when (val loaded = audioPlayer.load(PlatformFile(reference, displayName, mimeType))) {
                        is PlatformResult.Success -> audioPlayer.play()
                        is PlatformResult.Failure -> loaded
                        PlatformResult.Cancelled -> PlatformResult.Cancelled
                        PlatformResult.Unsupported -> PlatformResult.Unsupported
                    }
                    visiblePlayback.isPlaying -> audioPlayer.pause()
                    else -> audioPlayer.play()
                }
                when (result) {
                    is PlatformResult.Success -> onPlaybackChanged(reference, result.value, false)
                    is PlatformResult.Failure,
                    PlatformResult.Cancelled,
                    PlatformResult.Unsupported -> onPlaybackChanged(reference, visiblePlayback, true)
                }
            }
        },
        onSeekToFraction = { fraction ->
            if (isActive && visiblePlayback.durationMillis > 0L) {
                launch {
                    when (val result = audioPlayer.seekTo((visiblePlayback.durationMillis * fraction).toLong())) {
                        is PlatformResult.Success -> onPlaybackChanged(reference, result.value, false)
                        is PlatformResult.Failure,
                        PlatformResult.Cancelled,
                        PlatformResult.Unsupported -> onPlaybackChanged(reference, visiblePlayback, true)
                    }
                }
            }
        },
        modifier = modifier,
    )
}
