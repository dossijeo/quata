package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.quata.core.navigation.AppDestinations
import com.quata.core.platform.AudioPlaybackState
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.AudioRecorderService
import com.quata.core.platform.AudioRecording
import com.quata.core.platform.AudioRecordingReferenceReleaser
import com.quata.core.platform.AudioRecordingOptions
import com.quata.core.platform.ClipboardService
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
import com.quata.core.ui.components.CommunityEmojiPanelContent
import com.quata.core.ui.components.communityEmojiSections
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

/** Single host-neutral Chat root mounted unchanged by Android, Web and iOS adapters. */
@Composable
fun ChatScreenHost(
    repository: ChatRepository,
    audioPlayer: AudioPlayerService,
    audioRecorder: AudioRecorderService,
    filePicker: FilePickerService,
    conversationId: String?,
    navigationMessage: String,
    onOpenConversation: (String) -> Unit,
    onBackToList: () -> Unit,
    onOpenAttachment: (PlatformFile) -> Unit,
    onOpenAvatar: (String) -> Unit = {},
    onOpenMap: (String) -> Unit = {},
    onTranslateMessage: (String) -> Unit = {},
    onOpenMessageConversation: (String, String) -> Unit = { _, _ -> },
    conversationListHost: @Composable (Modifier) -> Unit,
    focusedMessageId: String? = null,
    onFocusedMessageHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
    audioRecordingConfiguration: ChatAudioRecordingConfiguration = ChatAudioRecordingConfiguration(),
    audioRecordingReferences: AudioRecordingReferenceReleaser? = null,
    messageInputOverride: (@Composable (String, (String) -> Unit, Modifier) -> Unit)? = null,
    sendButtonOverride: (@Composable (Boolean, () -> Unit, Modifier) -> Unit)? = null,
    clipboardService: ClipboardService? = null,
) {
    if (conversationId == null) {
        conversationListHost(modifier)
    } else {
        ChatConversationScreenContent(
            repository = repository,
            audioPlayer = audioPlayer,
            audioRecorder = audioRecorder,
            audioRecordingReferences = audioRecordingReferences,
            filePicker = filePicker,
            conversationId = conversationId,
            navigationMessage = navigationMessage,
            onBackToList = onBackToList,
            onOpenAttachment = onOpenAttachment,
            onOpenAvatar = onOpenAvatar,
            onOpenMap = onOpenMap,
            onTranslateMessage = onTranslateMessage,
            onOpenMessageConversation = onOpenMessageConversation,
            focusedMessageId = focusedMessageId,
            onFocusedMessageHandled = onFocusedMessageHandled,
            audioRecordingConfiguration = audioRecordingConfiguration,
            messageInputOverride = messageInputOverride,
            sendButtonOverride = sendButtonOverride,
            clipboardService = clipboardService,
            modifier = modifier,
        )
    }
}

@Composable
private fun ChatConversationScreenContent(
    repository: ChatRepository,
    audioPlayer: AudioPlayerService,
    audioRecorder: AudioRecorderService,
    audioRecordingReferences: AudioRecordingReferenceReleaser?,
    filePicker: FilePickerService,
    conversationId: String,
    navigationMessage: String,
    onBackToList: () -> Unit,
    onOpenAttachment: (PlatformFile) -> Unit,
    onOpenAvatar: (String) -> Unit,
    onOpenMap: (String) -> Unit,
    onTranslateMessage: (String) -> Unit,
    onOpenMessageConversation: (String, String) -> Unit,
    focusedMessageId: String?,
    onFocusedMessageHandled: () -> Unit,
    audioRecordingConfiguration: ChatAudioRecordingConfiguration,
    messageInputOverride: (@Composable (String, (String) -> Unit, Modifier) -> Unit)?,
    sendButtonOverride: (@Composable (Boolean, () -> Unit, Modifier) -> Unit)?,
    clipboardService: ClipboardService?,
    modifier: Modifier,
) {
    val viewModel = remember(repository, conversationId) {
        ChatViewModel(
            conversationId = conversationId,
            repository = repository,
            isFavoritesConversation = conversationId == AppDestinations.FavoriteMessagesConversationId,
            text = { "No se pudieron cargar los mensajes." },
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
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var pendingAudioRecording by remember { mutableStateOf<AudioRecording?>(null) }
    var headerExpanded by remember(conversationId) { mutableStateOf(false) }
    var emojiPanelVisible by remember(conversationId) { mutableStateOf(false) }
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

    suspend fun pickAttachment(source: FilePickerSource) {
        when (val result = filePicker.pick(FilePickerRequest(allowMultiple = false, source = source))) {
            is PlatformResult.Success -> result.value.firstOrNull()?.let { file ->
                pendingAudioRecording?.let { recording -> audioRecordingReferences?.release(recording) }
                pendingAudioRecording = null
                attachmentError = null
                viewModel.onEvent(ChatUiEvent.AttachmentSelected(file.reference, file.displayName ?: "Adjunto", file.mimeType))
            } ?: run { attachmentError = "No se seleccionó ningún archivo." }
            is PlatformResult.Failure -> attachmentError = result.reason?.takeIf(String::isNotBlank) ?: "No se pudo adjuntar el archivo."
            PlatformResult.Cancelled -> attachmentError = null
            PlatformResult.Unsupported -> attachmentError = "Esta fuente de archivos no está disponible en la plataforma."
        }
    }

    Column(modifier.fillMaxSize()) {
        ChatPortableConversationHeader(
            state = state,
            navigationMessage = navigationMessage,
            expanded = headerExpanded,
            onToggleExpanded = { headerExpanded = !headerExpanded },
            onBack = onBackToList,
            onOpenAvatar = onOpenAvatar,
            onCopyMessage = { text -> clipboardService?.let { service -> scope.launch { service.writeText(text) } } },
            onEvent = viewModel::onEvent,
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        state.notice?.let { notice ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(notice, modifier = Modifier.weight(1f))
                Button(onClick = { viewModel.onEvent(ChatUiEvent.ClearNotice) }) { Text("Cerrar") }
            }
        }
        if (state.isConversationActionInProgress) ChatConversationActionProgressContent()
        if (deepLinkRequest is ChatMessageDeepLinkRequest.LoadFailed) {
            Button(onClick = {
                historyPageRequested = false
                pendingFocusedMessageId = null
                deepLinkRequest = retryChatMessageDeepLinkRequest(deepLinkRequest)
                viewModel.retryMessageLoading()
            }) { Text("Reintentar mensaje enlazado") }
        }
        if (state.isAddParticipantsOpen) {
            ChatPortableCandidatePanel(
                title = "Añadir participantes",
                query = state.participantCandidateQuery,
                candidates = state.participantConversationCandidates.map { it.profileId to it.displayName },
                selectedIds = emptySet(),
                error = state.participantCandidateError,
                onQueryChanged = viewModel::onParticipantCandidateQueryChanged,
                onCandidate = viewModel::addConversationCandidateParticipant,
                onLoadMore = viewModel::loadMoreParticipantCandidates,
                onConfirm = null,
                onDismiss = { viewModel.onEvent(ChatUiEvent.CloseAddParticipants) },
            )
        }
        if (state.isForwardDialogOpen) {
            ChatPortableCandidatePanel(
                title = "Reenviar mensaje",
                query = state.forwardCandidateQuery,
                candidates = state.forwardConversationCandidates.map { it.profileId to it.displayName },
                selectedIds = state.selectedForwardProfileIds.toSet(),
                error = state.forwardCandidateError,
                onQueryChanged = viewModel::onForwardCandidateQueryChanged,
                onCandidate = { viewModel.onEvent(ChatUiEvent.ForwardProfileToggled(it)) },
                onLoadMore = viewModel::loadMoreForwardConversationCandidates,
                onConfirm = { viewModel.onEvent(ChatUiEvent.SendForward) },
                onDismiss = { viewModel.onEvent(ChatUiEvent.CloseForwardDialog) },
            )
        }
        if (state.isLoading && state.messages.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChatMessageSkeletonContent(isMine = false, pulseDelayMillis = 0)
                ChatMessageSkeletonContent(isMine = true, pulseDelayMillis = 120)
            }
        }
        ChatConversationDetailContent(
            messages = state.messages,
            selectedMessageId = state.selectedMessageId,
            focusedMessageId = pendingFocusedMessageId,
            onFocusedMessageHandled = { pendingFocusedMessageId = null; onFocusedMessageHandled() },
            strings = ChatConversationDetailStrings("Editado", "Mensaje eliminado", "Reenviado"),
            showSenderAvatar = { message -> !message.isMine },
            avatar = { message ->
                if (!message.isMine && message.senderId.isNotBlank()) {
                    Button(onClick = { onOpenAvatar(message.senderId) }) { Text(message.senderName.take(1).ifBlank { "?" }) }
                }
            },
            onOpenLink = { url -> onOpenAttachment(PlatformFile(reference = url)) },
            onMessageClick = { message ->
                deepLinkRequest = cancelChatMessageDeepLinkRequest(deepLinkRequest)
                pendingFocusedMessageId = null
                if (conversationId == AppDestinations.FavoriteMessagesConversationId) {
                    onOpenMessageConversation(message.conversationId, message.id)
                } else {
                    viewModel.onEvent(ChatUiEvent.MessageSelected(message.id.takeUnless { it == state.selectedMessageId }))
                }
            },
            composer = { composerModifier ->
                if (conversationId == AppDestinations.FavoriteMessagesConversationId) {
                    Spacer(composerModifier)
                } else Surface(composerModifier) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val canSend = state.messageText.isNotBlank() || state.attachmentUri != null
                        ChatComposerInputRowContent(
                            textInput = { inputModifier ->
                                messageInputOverride?.invoke(state.messageText, { value -> viewModel.onEvent(ChatUiEvent.MessageChanged(value)) }, inputModifier.semantics { testTag = "chat.message" }) ?: OutlinedTextField(
                                    value = state.messageText,
                                    onValueChange = { value -> viewModel.onEvent(ChatUiEvent.MessageChanged(value)) },
                                    label = { Text("Mensaje") },
                                    modifier = inputModifier.semantics { testTag = "chat.message" },
                                )
                            },
                            cameraAction = { cameraModifier ->
                                Button(onClick = { scope.launch { pickAttachment(FilePickerSource.Camera) } }, modifier = cameraModifier) { Text("Cámara") }
                            },
                            primaryAction = {
                                sendButtonOverride?.invoke(canSend, { viewModel.onEvent(ChatUiEvent.Send) }, Modifier.semantics { testTag = "chat.send" })
                                    ?: ChatComposerPrimaryActionContent(
                                        onClick = { if (canSend) viewModel.onEvent(ChatUiEvent.Send) },
                                        icon = { Text("Enviar") },
                                        modifier = Modifier.semantics { testTag = "chat.send" },
                                    )
                            },
                        )
                        state.replyToMessage?.let { message ->
                            ChatComposerModeBannerContent(
                                text = "Respondiendo a ${message.senderName}: ${message.text}",
                                onClear = { viewModel.onEvent(ChatUiEvent.ClearReply) },
                            )
                        }
                        state.editingMessage?.let { message ->
                            ChatComposerModeBannerContent(
                                text = "Editando: ${message.text}",
                                onClear = { viewModel.onEvent(ChatUiEvent.CancelEdit) },
                            )
                        }
                        Button(onClick = { emojiPanelVisible = !emojiPanelVisible }) {
                            Text(if (emojiPanelVisible) "Cerrar emojis" else "Emojis")
                        }
                        if (emojiPanelVisible) {
                            CommunityEmojiPanelContent(
                                sections = communityEmojiSections(),
                                onEmojiClick = { emoji -> viewModel.onEvent(ChatUiEvent.MessageChanged(state.messageText + emoji)) },
                            )
                        }
                        state.attachmentName?.let { name ->
                            ChatPendingAttachmentOverlayContent(
                                name = name,
                                surfaceColor = MaterialTheme.colorScheme.surfaceVariant,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                onOpen = { state.attachmentUri?.let { onOpenAttachment(PlatformFile(it, name, state.attachmentMimeType)) } },
                                preview = { Text(state.attachmentMimeType.orEmpty().ifBlank { "Adjunto" }) },
                                clearAction = { clearModifier -> Button(onClick = { viewModel.onEvent(ChatUiEvent.ClearAttachment) }, modifier = clearModifier) { Text("Quitar") } },
                                modifier = Modifier.fillMaxWidth().height(180.dp),
                            )
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
                                            recordingError = "La grabación de audio no está disponible en esta plataforma."
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
                                        PlatformResult.Unsupported -> recordingError = "La grabación de audio no está disponible en esta plataforma."
                                    }
                                }
                            }) { Text("Grabar audio") }
                        }
                        recordingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        ChatAttachmentQuickPanelContent(
                            strings = ChatAttachmentQuickPanelStrings("Archivo", "Galería"),
                            onPickFile = { scope.launch { pickAttachment(FilePickerSource.Documents) } },
                            onPickGallery = { scope.launch { pickAttachment(FilePickerSource.Gallery) } },
                        )
                        attachmentError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
                    }
                }
            },
            attachment = { message, attachmentModifier ->
                Column(attachmentModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChatAttachmentContent(
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                    message.text.chatSosMapUrl()?.let { mapsUrl ->
                        ChatSosLocationContent(
                            title = "Ubicación SOS",
                            body = null,
                            locationLabel = "Ubicación compartida",
                            mapsUrl = mapsUrl,
                            age = null,
                            accuracy = null,
                            speed = null,
                            isUpdate = true,
                            isUnavailable = false,
                            unavailableLabel = "Ubicación no disponible",
                            openMapsLabel = "Abrir mapa",
                            textColor = MaterialTheme.colorScheme.onSurface,
                            accentColor = MaterialTheme.colorScheme.primary,
                            onOpenMaps = onOpenMap,
                            mapPreviewIcon = { Text("SOS") },
                        )
                    }
                }
            },
            deliveryIndicator = { message ->
                if (message.isMine) {
                    ChatMessageDeliveryIndicatorContent(
                        state = message.deliveryState,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        readTint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            favoriteMarker = { Text("★", color = MaterialTheme.colorScheme.primary) },
            typingIndicator = state.typingProfileIds.takeIf { it.isNotEmpty() }?.let { ids ->
                { ChatTypingIndicatorContent(ids.toList()) }
            },
            historyHeader = if (state.hasMoreHistory) {{
                Button(onClick = { scope.launch { viewModel.loadOlderMessages() } }, enabled = !state.isLoadingOlderMessages) {
                    Text(if (state.isLoadingOlderMessages) "Cargando historial…" else "Cargar mensajes anteriores")
                }
            }} else null,
            newMessagesLabel = "Mensajes nuevos",
            messageActions = { message, actionsModifier ->
                if (message.id == state.selectedMessageId && !message.isLocalEcho) {
                    Column(actionsModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        message.clientMessageId?.takeIf { message.deliveryState == com.quata.core.model.MessageDeliveryState.Failed }?.let { clientId ->
                            Button(onClick = { viewModel.retryPendingMessage(clientId) }) { Text("Reintentar envío") }
                        }
                        Button(onClick = { viewModel.onEvent(ChatUiEvent.StartReply) }) { Text("Responder") }
                        Button(onClick = { viewModel.onEvent(ChatUiEvent.OpenForwardDialog) }) { Text("Reenviar") }
                        Button(onClick = { viewModel.onEvent(ChatUiEvent.ToggleFavoriteSelected) }) { Text(if (message.isFavorite) "Quitar favorito" else "Favorito") }
                        if (message.isMine && !message.isDeleted) {
                            Button(onClick = { viewModel.onEvent(ChatUiEvent.StartEdit) }) { Text("Editar") }
                            Button(onClick = { viewModel.onEvent(ChatUiEvent.DeleteSelectedMessage) }) { Text("Eliminar") }
                        } else if (!message.isDeleted) {
                            Button(onClick = { viewModel.onEvent(ChatUiEvent.ReportSelectedMessage) }) { Text("Reportar") }
                        }
                        if (message.text.isNotBlank()) Button(onClick = { onTranslateMessage(message.text) }) { Text("Traducir") }
                        message.attachmentUri?.takeIf { it.startsWith("geo:") || it.contains("maps", ignoreCase = true) }?.let { map ->
                            Button(onClick = { onOpenMap(map) }) { Text("Abrir mapa") }
                        }
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChatPortableCandidatePanel(
    title: String,
    query: String,
    candidates: List<Pair<String, String>>,
    selectedIds: Set<String>,
    error: String?,
    onQueryChanged: (String) -> Unit,
    onCandidate: (String) -> Unit,
    onLoadMore: () -> Unit,
    onConfirm: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Surface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = onDismiss) { Text("Cerrar") }
            }
            OutlinedTextField(query, onQueryChanged, label = { Text("Buscar") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            candidates.take(12).forEach { (id, name) ->
                Button(onClick = { onCandidate(id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (id in selectedIds) "✓ ${name.ifBlank { "Usuario" }}" else name.ifBlank { "Usuario" })
                }
            }
            Button(onClick = onLoadMore) { Text("Cargar más") }
            onConfirm?.let { confirm -> Button(onClick = confirm, enabled = selectedIds.isNotEmpty()) { Text("Confirmar") } }
        }
    }
}

/**
 * Shared Android-equivalent header hierarchy. Permissions are derived from the authenticated
 * profile id and the server-provided moderator/member-invite fields; hosts only navigate.
 */
@Composable
private fun ChatPortableConversationHeader(
    state: ChatUiState,
    navigationMessage: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onBack: () -> Unit,
    onOpenAvatar: (String) -> Unit,
    onCopyMessage: (String) -> Unit,
    onEvent: (ChatUiEvent) -> Unit,
) {
    var pendingConversationAction by remember { mutableStateOf<String?>(null) }
    if (state.conversation?.id == AppDestinations.FavoriteMessagesConversationId) {
        FavoriteMessagesHeaderContent("Mensajes favoritos", "Volver", onBack)
        return
    }
    val selected = state.messages.firstOrNull { it.id == state.selectedMessageId }
    if (selected != null) {
        ChatSelectedMessageActionBarContent(
            compact = false,
            navigationAction = { Button(onClick = { onEvent(ChatUiEvent.MessageSelected(null)) }) { Text("Cerrar") } },
            actions = {
                Button(onClick = { onEvent(ChatUiEvent.StartReply) }) { Text("Responder") }
                if (selected.text.isNotBlank()) Button(onClick = { onCopyMessage(selected.text) }) { Text("Copiar") }
                Button(onClick = { onEvent(ChatUiEvent.OpenForwardDialog) }) { Text("Reenviar") }
                Button(onClick = { onEvent(ChatUiEvent.ToggleFavoriteSelected) }) { Text(if (selected.isFavorite) "Quitar favorito" else "Favorito") }
                if (selected.isMine && !selected.isDeleted) {
                    Button(onClick = { onEvent(ChatUiEvent.StartEdit) }) { Text("Editar") }
                    Button(onClick = { onEvent(ChatUiEvent.DeleteSelectedMessage) }) { Text("Eliminar") }
                } else if (!selected.isDeleted) {
                    Button(onClick = { onEvent(ChatUiEvent.ReportSelectedMessage) }) { Text("Reportar") }
                }
            },
        )
        return
    }
    val conversation = state.conversation
    val currentUserId = state.currentUser?.id
    val isModerator = currentUserId != null && currentUserId in conversation?.moderatorIds.orEmpty()
    val canInvite = isModerator || conversation?.canMembersInvite == true
    ChatConversationTitleBarContent(
        title = conversation?.title.orEmpty().ifBlank { "Conversación" },
        subtitle = if (conversation?.isGroup == true) "${conversation.participantIds.size} participantes" else navigationMessage,
        expandable = conversation != null,
        compact = false,
        onToggleExpanded = onToggleExpanded,
        navigationAction = { Button(onClick = onBack, modifier = Modifier.semantics { testTag = "chat.back" }) { Text("Volver") } },
        avatar = {
            ChatConversationAvatarContent(
                isGroup = conversation?.isGroup == true,
                isEmergency = conversation?.isEmergency == true,
                isMuted = conversation?.isMuted == true,
                emergencyLabel = "SOS",
                privateAvatar = {
                    Button(onClick = { conversation?.participantIds?.firstOrNull { it != currentUserId }?.let(onOpenAvatar) }) {
                        Text(conversation?.title?.take(1).orEmpty().ifBlank { "?" })
                    }
                },
                groupIcon = { Text("Grupo") },
                mutedBadge = { ChatMutedConversationBadgeContent() },
            )
        },
        trailingActions = {
            Button(onClick = { onEvent(ChatUiEvent.ConversationMutedChanged(conversation?.isMuted != true)) }) {
                Text(if (conversation?.isMuted == true) "Activar" else "Silenciar")
            }
        },
    )
    if (!expanded || conversation == null) return
    Surface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (conversation.isGroup && isModerator) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(conversation.canMembersInvite, { onEvent(ChatUiEvent.MemberInvitesChanged(it)) })
                    Text("Permitir invitaciones de miembros")
                }
            }
            if (conversation.isGroup && canInvite) {
                Button(onClick = { onEvent(ChatUiEvent.OpenAddParticipants) }) { Text("Añadir participantes") }
            }
            conversation.participantIds.forEachIndexed { index, participantId ->
                if (participantId != currentUserId) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { onOpenAvatar(participantId) }) {
                            Text(conversation.participantNames.getOrNull(index).orEmpty().ifBlank { "Participante" })
                        }
                        Spacer(Modifier.weight(1f))
                        if (isModerator) {
                            Button(onClick = { onEvent(if (participantId in conversation.moderatorIds) ChatUiEvent.DemoteModerator(participantId) else ChatUiEvent.PromoteModerator(participantId)) }) {
                                Text(if (participantId in conversation.moderatorIds) "Quitar moderador" else "Hacer moderador")
                            }
                            Button(onClick = { onEvent(ChatUiEvent.RemoveParticipant(participantId)) }) { Text("Expulsar") }
                            Button(onClick = { onEvent(ChatUiEvent.BlockParticipant(participantId)) }) { Text("Bloquear") }
                        }
                    }
                }
            }
            if (conversation.isGroup) Button(onClick = { pendingConversationAction = "leave" }) { Text("Salir del grupo") }
            Button(onClick = { pendingConversationAction = "hide" }) { Text("Ocultar conversación") }
            Button(onClick = { pendingConversationAction = "delete" }) { Text("Eliminar conversación") }
            pendingConversationAction?.let { action ->
                Text(
                    when (action) {
                        "leave" -> "¿Quieres salir de este grupo?"
                        "hide" -> "¿Quieres ocultar esta conversación de tu bandeja?"
                        else -> "¿Quieres eliminar esta conversación?"
                    },
                    color = MaterialTheme.colorScheme.error,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pendingConversationAction = null }) { Text("Cancelar") }
                    Button(onClick = {
                        when (action) {
                            "leave" -> onEvent(ChatUiEvent.LeaveConversation)
                            "hide" -> onEvent(ChatUiEvent.HideConversation)
                            else -> onEvent(ChatUiEvent.DeleteConversation)
                        }
                        pendingConversationAction = null
                    }) { Text("Confirmar") }
                }
            }
        }
    }
}

private fun Long.toDurationLabel(): String {
    val minutes = this / 60L
    val seconds = this % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun String.chatSosMapUrl(): String? {
    if (!contains("SOS", ignoreCase = true)) return null
    return Regex("(?:https?://|geo:)[^\\s]+", RegexOption.IGNORE_CASE).find(this)?.value
}

@Composable
private fun ChatAttachmentContent(
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
        ChatDocumentAttachmentContent(
            name = displayName,
            textColor = MaterialTheme.colorScheme.onSurface,
            onOpen = { onOpenAttachment(PlatformFile(reference, displayName, mimeType)) },
            icon = { Text(if (mimeType.startsWith("image/")) "Imagen" else if (mimeType.startsWith("video/")) "Vídeo" else "Archivo") },
            modifier = modifier.fillMaxWidth(),
        )
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
