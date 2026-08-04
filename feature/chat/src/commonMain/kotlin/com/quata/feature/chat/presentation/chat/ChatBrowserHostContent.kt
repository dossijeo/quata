package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.quata.core.model.Message
import com.quata.core.platform.AudioPlaybackState
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.AudioRecorderService
import com.quata.core.platform.AudioRecording
import com.quata.core.platform.AudioRecordingReferenceReleaser
import com.quata.core.platform.AudioRecordingOptions
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.FilePickerSource
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.ui.components.QuataAvatarFallback
import com.quata.core.ui.components.QuataAvatarLoadingHaloContent
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.conversations.ConversationListRow
import com.quata.feature.chat.presentation.conversations.ConversationsListContent
import com.quata.feature.chat.presentation.conversations.ConversationsUiEvent
import com.quata.feature.chat.presentation.conversations.ConversationsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Recording format selected by a platform launcher for the shared chat composer.
 *
 * The browser keeps WebM as its default because it is the MediaRecorder-compatible format used
 * by its recorder adapter. iOS injects AAC-in-MP4 from its UIKit host instead of teaching common
 * UI about AVFoundation.
 */
data class ChatAudioRecordingConfiguration(
    val mimeType: String = WEB_MIME_TYPE,
) {
    fun toPlatformOptions(): AudioRecordingOptions = AudioRecordingOptions(mimeType = mimeType)

    companion object {
        const val WEB_MIME_TYPE = "audio/webm"
        const val IOS_MIME_TYPE = "audio/mp4"
    }
}

/** Host-neutral browser-style Chat viewport. Navigation and external opening are injected. */
@Composable
fun ChatBrowserHostContent(
    repository: ChatRepository,
    audioPlayer: AudioPlayerService,
    audioRecorder: AudioRecorderService,
    filePicker: FilePickerService,
    conversationId: String?,
    navigationMessage: String,
    onOpenConversation: (String) -> Unit,
    onBackToList: () -> Unit,
    onOpenAttachment: (PlatformFile) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    openingProfileUserId: String? = null,
    conversationList: @Composable (Modifier) -> Unit,
    text: (ChatText) -> String,
    focusedMessageId: String? = null,
    modifier: Modifier = Modifier,
    audioRecordingConfiguration: ChatAudioRecordingConfiguration = ChatAudioRecordingConfiguration(),
    audioRecordingReferences: AudioRecordingReferenceReleaser? = null,
    messageInputOverride: (@Composable (String, (String) -> Unit, Modifier) -> Unit)? = null,
    sendButtonOverride: (@Composable (Boolean, () -> Unit, Modifier) -> Unit)? = null,
) {
    if (conversationId == null) {
        conversationList(modifier)
    } else {
        ChatCommonConversationHost(
            repository = repository,
            conversationId = conversationId,
            navigationMessage = navigationMessage,
            onBackToList = onBackToList,
            onOpenAttachment = onOpenAttachment,
            onOpenUserProfile = onOpenUserProfile,
            openingProfileUserId = openingProfileUserId,
            focusedMessageId = focusedMessageId,
            text = text,
            modifier = modifier,
        )
    }
}

/**
 * CommonMain consumer of [ChatScreenHost] used by Wasm and iOS. Media/system adapters remain
 * intentionally outside this first read-root unit; composer/action parity is tracked by unit 2.
 */
@Composable
private fun ChatCommonConversationHost(
    repository: ChatRepository,
    conversationId: String,
    navigationMessage: String,
    onBackToList: () -> Unit,
    onOpenAttachment: (PlatformFile) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    openingProfileUserId: String?,
    focusedMessageId: String?,
    text: (ChatText) -> String,
    modifier: Modifier,
) {
    val viewModel = remember(repository, conversationId) {
        ChatViewModel(conversationId = conversationId, repository = repository, text = text)
    }
    val state by viewModel.uiState.collectAsState()
    DisposableEffect(viewModel) {
        repository.setActiveConversation(conversationId)
        onDispose {
            repository.setActiveConversation(null)
            viewModel.close()
        }
    }
    ChatScreenHost(
        repository = repository,
        conversationId = conversationId,
        text = text,
        focusedMessageId = focusedMessageId,
        modifier = modifier,
        model = viewModel,
        slots = ChatScreenHostSlots(
            strings = ChatScreenHostStrings("ConversaciÃ³n", "Reintentar mensajes"),
            messageStrings = ChatConversationDetailStrings("Editado", "Mensaje eliminado", "Reenviado"),
            compactHeader = false,
            navigationAction = {
                Button(onClick = onBackToList, modifier = Modifier.semantics { testTag = "chat.back" }) {
                    Text("Volver a conversaciones")
                }
            },
            conversationAvatar = { _ -> },
            trailingActions = {},
            messageAvatar = { message ->
                QuataAvatarLoadingHaloContent(
                    isLoading = openingProfileUserId == message.senderId,
                    modifier = Modifier.size(38.dp),
                ) {
                    QuataAvatarFallback(
                        name = message.senderName,
                        stableId = message.senderId,
                        modifier = Modifier.size(34.dp).clickable(
                            enabled = openingProfileUserId != message.senderId,
                        ) { onOpenUserProfile(message.senderId) },
                    )
                }
            },
            onOpenLink = { url -> onOpenAttachment(PlatformFile(reference = url)) },
            onBack = onBackToList,
            onFocusedMessageHandled = {},
            subtitle = { _, typing -> if (typing.isNotEmpty()) "Escribiendoâ€¦" else navigationMessage },
            composer = { composerModifier ->
                Surface(composerModifier) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.messageText,
                            onValueChange = { viewModel.onEvent(ChatUiEvent.MessageChanged(it)) },
                            label = { Text("Mensaje") },
                            modifier = Modifier.fillMaxWidth().semantics { testTag = "chat.message" },
                        )
                        Button(
                            onClick = { viewModel.onEvent(ChatUiEvent.Send) },
                            enabled = state.messageText.isNotBlank(),
                            modifier = Modifier.semantics { testTag = "chat.send" },
                        ) { Text("Enviar") }
                    }
                }
            },
            attachment = { message, attachmentModifier ->
                message.attachmentUri?.takeIf { it.isNotBlank() }?.let { reference ->
                    Button(
                        onClick = {
                            onOpenAttachment(PlatformFile(reference, message.attachmentName, message.attachmentMimeType))
                        },
                        modifier = attachmentModifier,
                    ) { Text(message.attachmentName ?: "Abrir adjunto") }
                }
            },
            messageActions = { message, actionsModifier ->
                if (message.id == state.selectedMessageId && !message.isLocalEcho) {
                    Button(onClick = { viewModel.onEvent(ChatUiEvent.StartReply) }, modifier = actionsModifier) {
                        Text("Responder")
                    }
                }
            },
            typingIndicator = { typing ->
                if (typing.isEmpty()) null else { { Text("Escribiendoâ€¦", Modifier.padding(14.dp)) } }
            },
        ),
    )
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
                Button(
                    onClick = { viewModel.onEvent(ConversationsUiEvent.Refresh) },
                    modifier = Modifier.semantics { testTag = "chat.refresh" },
                ) { Text("Actualizar") }
                Button(onClick = {
                    isGroupComposerOpen = false
                    viewModel.openNewConversationPicker()
                }, modifier = Modifier.semantics { testTag = "chat.new-conversation" }) { Text("Nuevo chat") }
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
    audioRecorder: AudioRecorderService,
    audioRecordingReferences: AudioRecordingReferenceReleaser?,
    filePicker: FilePickerService,
    conversationId: String,
    navigationMessage: String,
    onBackToList: () -> Unit,
    onOpenAttachment: (PlatformFile) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    openingProfileUserId: String?,
    focusedMessageId: String?,
    audioRecordingConfiguration: ChatAudioRecordingConfiguration,
    messageInputOverride: (@Composable (String, (String) -> Unit, Modifier) -> Unit)?,
    sendButtonOverride: (@Composable (Boolean, () -> Unit, Modifier) -> Unit)?,
    text: (ChatText) -> String,
    modifier: Modifier,
) {
    val viewModel = remember(repository, conversationId) {
        ChatViewModel(
            conversationId = conversationId,
            repository = repository,
            text = text,
        )
    }
    val state by viewModel.uiState.collectAsState()
    var deepLinkRequest by remember(conversationId, focusedMessageId) {
        mutableStateOf(chatMessageDeepLinkRequest(focusedMessageId))
    }
    var historyPageRequested by remember(conversationId, focusedMessageId) { mutableStateOf(false) }
    var pendingFocusedMessageId by remember(conversationId, focusedMessageId) { mutableStateOf<String?>(null) }
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
    val scope = rememberCoroutineScope()
    val audioLifecycle = remember(audioPlayer) { ChatAudioPlaybackLifecycleOwner(audioPlayer) }
    var activeAudioReference by remember { mutableStateOf<String?>(null) }
    var audioPlayback by remember { mutableStateOf(AudioPlaybackState()) }
    var audioFailed by remember { mutableStateOf(false) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingElapsedSeconds by remember { mutableLongStateOf(0L) }
    var recordingError by remember { mutableStateOf<String?>(null) }
    var pendingAudioRecording by remember { mutableStateOf<AudioRecording?>(null) }
    LaunchedEffect(deepLinkRequest) {
        val focused = deepLinkRequest as? ChatMessageDeepLinkRequest.Focused ?: return@LaunchedEffect
        viewModel.onEvent(ChatUiEvent.MessageSelected(focused.messageId))
        pendingFocusedMessageId = focused.messageId
    }
    LaunchedEffect(deepLinkRequest, state.isLoadingOlderMessages, historyPageRequested) {
        if (deepLinkRequest !is ChatMessageDeepLinkRequest.LoadingOlder) return@LaunchedEffect
        if (!historyPageRequested) {
            historyPageRequested = viewModel.loadOlderMessages()
        } else if (!state.isLoadingOlderMessages) {
            historyPageRequested = false
            deepLinkRequest = resumeChatMessageDeepLinkRequest(deepLinkRequest)
        }
    }
    LaunchedEffect(isRecordingAudio) {
        if (!isRecordingAudio) return@LaunchedEffect
        while (isRecordingAudio) {
            delay(1_000L)
            recordingElapsedSeconds += 1L
        }
    }
    DisposableEffect(viewModel, audioRecorder, audioPlayer) {
        repository.setActiveConversation(conversationId)
        onDispose {
            if (isRecordingAudio) {
                scope.launch { audioRecorder.cancel() }
            }
            // The platform player owns temporary attachment files. Always request its terminal
            // cleanup when this conversation leaves composition; failures remain fail-closed in
            // the iOS wrapper and never trigger a replacement download.
            audioLifecycle.dispose()
            repository.setActiveConversation(null)
            viewModel.close()
        }
    }

    Column(modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onBackToList,
                    modifier = Modifier.semantics { testTag = "chat.back" },
                ) { Text("Volver a conversaciones") }
                Text(state.conversation?.title ?: "Conversación", style = MaterialTheme.typography.titleLarge)
                Text(navigationMessage, style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (deepLinkRequest is ChatMessageDeepLinkRequest.LoadFailed) {
                    Button(onClick = {
                        historyPageRequested = false
                        pendingFocusedMessageId = null
                        deepLinkRequest = retryChatMessageDeepLinkRequest(deepLinkRequest)
                        viewModel.retryMessageLoading()
                    }) { Text("Reintentar mensaje enlazado") }
                }
            }
        }
        ChatConversationDetailContent(
            messages = state.messages,
            selectedMessageId = state.selectedMessageId,
            focusedMessageId = pendingFocusedMessageId,
            onFocusedMessageHandled = { pendingFocusedMessageId = null },
            strings = ChatConversationDetailStrings("Editado", "Mensaje eliminado", "Reenviado"),
            showSenderAvatar = { message -> !message.isMine },
            avatar = { message ->
                QuataAvatarLoadingHaloContent(
                    isLoading = openingProfileUserId == message.senderId,
                    modifier = Modifier.size(38.dp),
                ) {
                    QuataAvatarFallback(
                        name = message.senderName,
                        stableId = message.senderId,
                        modifier = Modifier.size(34.dp).clickable(
                            enabled = openingProfileUserId != message.senderId,
                        ) { onOpenUserProfile(message.senderId) },
                    )
                }
            },
            onOpenLink = { url -> onOpenAttachment(PlatformFile(reference = url)) },
            onMessageClick = { message ->
                deepLinkRequest = cancelChatMessageDeepLinkRequest(deepLinkRequest)
                pendingFocusedMessageId = null
                viewModel.onEvent(
                    ChatUiEvent.MessageSelected(
                        message.id.takeUnless { it == state.selectedMessageId },
                    ),
                )
            },
            composer = { composerModifier ->
                Surface(composerModifier) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        messageInputOverride?.invoke(state.messageText, { value -> viewModel.onEvent(ChatUiEvent.MessageChanged(value)) }, Modifier.fillMaxWidth().semantics { testTag = "chat.message" }) ?: OutlinedTextField(
                            value = state.messageText,
                            onValueChange = { value -> viewModel.onEvent(ChatUiEvent.MessageChanged(value)) },
                            label = { Text("Mensaje") },
                            modifier = Modifier.fillMaxWidth().semantics { testTag = "chat.message" },
                        )
                        state.replyToMessage?.let { message ->
                            Text(
                                "Respondiendo a ${message.senderName}: ${message.text}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = { viewModel.onEvent(ChatUiEvent.ClearReply) }) {
                                Text("Cancelar respuesta")
                            }
                        }
                        state.attachmentName?.let { name ->
                            Text("Adjunto: $name", style = MaterialTheme.typography.bodySmall)
                        }
                        if (isRecordingAudio) {
                            Text(
                                "Grabando ${recordingElapsedSeconds.toDurationLabel()}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = {
                                scope.launch {
                                    when (val result = audioRecorder.stop()) {
                                        is PlatformResult.Success -> {
                                            isRecordingAudio = false
                                            recordingElapsedSeconds = result.value.durationMillis / 1_000L
                                            recordingError = null
                                            pendingAudioRecording?.let { previous ->
                                                audioRecordingReferences?.release(previous)
                                            }
                                            pendingAudioRecording = result.value
                                            viewModel.onEvent(
                                                ChatUiEvent.AttachmentSelected(
                                                    uri = result.value.file.reference,
                                                    name = result.value.file.displayName ?: "Nota de voz",
                                                    mimeType = result.value.mimeType,
                                                ),
                                            )
                                        }
                                        is PlatformResult.Failure -> {
                                            isRecordingAudio = false
                                            recordingError = "No se pudo guardar la grabacion: ${result.reason.orEmpty()}"
                                        }
                                        PlatformResult.Cancelled -> {
                                            isRecordingAudio = false
                                            recordingError = null
                                        }
                                        PlatformResult.Unsupported -> {
                                            isRecordingAudio = false
                                            recordingError = "La grabacion de audio no esta disponible en este navegador."
                                        }
                                    }
                                }
                            }) { Text("Detener y adjuntar") }
                            Button(onClick = {
                                scope.launch {
                                    audioRecorder.cancel()
                                    isRecordingAudio = false
                                    recordingElapsedSeconds = 0L
                                    recordingError = null
                                }
                            }) { Text("Cancelar grabacion") }
                        } else {
                            Button(onClick = {
                                scope.launch {
                                    when (val result = audioRecorder.start(audioRecordingConfiguration.toPlatformOptions())) {
                                        is PlatformResult.Success -> {
                                            isRecordingAudio = true
                                            recordingElapsedSeconds = 0L
                                            recordingError = null
                                        }
                                        is PlatformResult.Failure -> recordingError = "No se pudo iniciar la grabacion: ${result.reason.orEmpty()}"
                                        PlatformResult.Cancelled -> recordingError = null
                                        PlatformResult.Unsupported -> recordingError = "La grabacion de audio no esta disponible en este navegador."
                                    }
                                }
                            }) { Text("Grabar audio") }
                        }
                        recordingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
                                            pendingAudioRecording?.let { recording ->
                                                audioRecordingReferences?.release(recording)
                                            }
                                            pendingAudioRecording = null
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
                            Button(onClick = {
                                val recording = pendingAudioRecording
                                pendingAudioRecording = null
                                viewModel.onEvent(ChatUiEvent.ClearAttachment)
                                if (recording != null) {
                                    scope.launch { audioRecordingReferences?.release(recording) }
                                }
                            }) {
                                Text("Quitar adjunto")
                            }
                        }
                        sendButtonOverride?.invoke(
                            state.messageText.isNotBlank() || state.attachmentUri != null,
                            { viewModel.onEvent(ChatUiEvent.Send) },
                            Modifier.semantics { testTag = "chat.send" },
                        ) ?: Button(
                            onClick = { viewModel.onEvent(ChatUiEvent.Send) },
                            enabled = state.messageText.isNotBlank() || state.attachmentUri != null,
                            modifier = Modifier.semantics { testTag = "chat.send" },
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
                    launch = audioLifecycle::launch,
                    modifier = attachmentModifier,
                )
            },
            messageActions = { message, actionsModifier ->
                if (message.id == state.selectedMessageId && !message.isLocalEcho) {
                    Button(
                        onClick = { viewModel.onEvent(ChatUiEvent.StartReply) },
                        modifier = actionsModifier,
                    ) { Text("Responder") }
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

private fun Long.toDurationLabel(): String {
    val minutes = this / 60L
    val seconds = this % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun ChatBrowserAttachmentContent(
    message: Message,
    audioPlayer: AudioPlayerService,
    activeAudioReference: String?,
    playback: AudioPlaybackState,
    failed: Boolean,
    onPlaybackChanged: (String?, AudioPlaybackState, Boolean) -> Unit,
    onOpenAttachment: (PlatformFile) -> Unit,
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
                Button(onClick = { onOpenAttachment(PlatformFile(reference, displayName, mimeType)) }) { Text("Abrir adjunto") }
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
