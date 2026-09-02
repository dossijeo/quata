package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.Message
import com.quata.core.model.MessageDeliveryState
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
import com.quata.core.platform.DocumentViewerState
import com.quata.core.platform.documentViewerOpeningState
import com.quata.core.platform.openPlatformDocumentWithViewerState
import com.quata.core.localization.QuataLanguage
import com.quata.core.ui.components.QuataDocumentViewerStatusContent
import com.quata.core.ui.components.quataDocumentViewerStatusStrings
import com.quata.core.navigation.AppDestinations
import com.quata.core.ui.components.QuataAvatarLoadingHaloContent
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.QuataFullscreenMediaOverlayContent
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.chatDisplayTitle
import com.quata.feature.chat.presentation.conversations.ConversationAvatarContent
import com.quata.feature.chat.presentation.conversations.ConversationAvatarKind
import com.quata.feature.chat.presentation.conversations.ConversationAvatarPresentation
import com.quata.feature.chat.presentation.conversations.resolveConversationAvatarPresentation
import com.quata.feature.chat.presentation.conversations.resolveMessageAvatarPresentation
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

const val ChatProfileMemberAvatarTestTagPrefix = "chat.profile.member."
const val ChatProfileMessageAvatarTestTagPrefix = "chat.profile.message."

data class ChatDocumentAttachmentActions(
    val file: PlatformFile,
    val open: () -> Unit,
    val download: () -> Unit,
    val share: () -> Unit,
)

data class ChatMediaAttachmentActions(
    val file: PlatformFile,
    val kind: ChatAttachmentKind,
    val open: () -> Unit,
)

data class ChatAudioAttachmentActions(
    val file: PlatformFile,
    val playback: AudioPlaybackState,
    val toggle: () -> Unit,
    val seekToFraction: (Float) -> Unit,
)

data class ChatComposerActionCallbacks(
    val recordAudio: (() -> Unit)?,
    val stopRecording: (() -> Unit)?,
    val cancelRecording: (() -> Unit)?,
    val send: (() -> Unit)?,
    val messageText: String,
    val hasPendingAttachment: Boolean,
)

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

/** Shared product host for Chat. Platforms inject system adapters and the common conversations root. */
@Composable
fun ChatProductHostContent(
    repository: ChatRepository,
    audioPlayer: AudioPlayerService,
    audioRecorder: AudioRecorderService,
    filePicker: FilePickerService,
    capturePhoto: suspend () -> PlatformResult<PlatformFile>,
    conversationId: String?,
    navigationMessage: String,
    onOpenConversation: (String) -> Unit,
    onOpenMessageConversation: (String, String) -> Unit,
    onBackToList: () -> Unit,
    onOpenAttachment: suspend (PlatformFile) -> PlatformResult<Unit>,
    onDownloadAttachment: suspend (PlatformFile) -> PlatformResult<Unit> = { PlatformResult.Unsupported },
    onShareAttachment: suspend (PlatformFile) -> PlatformResult<Unit> = { PlatformResult.Unsupported },
    onOpenExternalLink: (String) -> Unit,
    onOpenMapLink: (String) -> ChatMapOpenResult = { value ->
        onOpenExternalLink(value)
        ChatMapOpenResult.Opened
    },
    onOpenUserProfile: (String) -> Unit,
    openingProfileUserId: String? = null,
    onCopyMessage: (String) -> Unit,
    remoteConversationAvatar: @Composable (ConversationAvatarPresentation, Modifier) -> Unit,
    mediaSlots: ChatMediaPlatformSlots,
    translationGateway: ChatTranslationGateway,
    translatorStrings: ChatTranslatorStrings,
    translationDirection: ChatTranslationDirection,
    languageTag: String?,
    conversationList: @Composable (Modifier) -> Unit,
    text: (ChatText) -> String,
    focusedMessageId: String? = null,
    onFocusedMessageVisible: (String) -> Unit = {},
    onFocusedMessageHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
    audioRecordingConfiguration: ChatAudioRecordingConfiguration = ChatAudioRecordingConfiguration(),
    audioRecordingReferences: AudioRecordingReferenceReleaser? = null,
    conversationModel: ChatViewModel? = null,
    compactHeader: Boolean = false,
    trailingActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    onOpenTranslator: (() -> Unit)? = null,
    groupMembersInitiallyExpanded: Boolean = false,
    messageInputOverride: (@Composable (
        String,
        (String) -> Unit,
        () -> Unit,
        Modifier,
        @Composable () -> Unit,
        @Composable () -> Unit,
    ) -> Unit)? = null,
    sendButtonOverride: (@Composable (Boolean, () -> Unit, Modifier) -> Unit)? = null,
    documentAttachmentActionsHost: (@Composable (ChatDocumentAttachmentActions) -> Unit)? = null,
    mediaAttachmentActionsHost: (@Composable (ChatMediaAttachmentActions) -> Unit)? = null,
    audioAttachmentActionsHost: (@Composable (ChatAudioAttachmentActions) -> Unit)? = null,
    composerActionsHost: (@Composable (ChatComposerActionCallbacks) -> Unit)? = null,
) {
    if (conversationId == null) {
        conversationList(modifier)
    } else {
        ChatCommonConversationHost(
            repository = repository,
            audioPlayer = audioPlayer,
            audioRecorder = audioRecorder,
            audioRecordingReferences = audioRecordingReferences,
            audioRecordingConfiguration = audioRecordingConfiguration,
            filePicker = filePicker,
            capturePhoto = capturePhoto,
            conversationId = conversationId,
            navigationMessage = navigationMessage,
            onBackToList = onBackToList,
            onOpenConversation = onOpenConversation,
            onOpenMessageConversation = onOpenMessageConversation,
            onOpenAttachment = onOpenAttachment,
            onDownloadAttachment = onDownloadAttachment,
            onShareAttachment = onShareAttachment,
            onOpenExternalLink = onOpenExternalLink,
            onOpenMapLink = onOpenMapLink,
            onOpenUserProfile = onOpenUserProfile,
            onCopyMessage = onCopyMessage,
            openingProfileUserId = openingProfileUserId,
            remoteConversationAvatar = remoteConversationAvatar,
            mediaSlots = mediaSlots,
            translationGateway = translationGateway,
            translatorStrings = translatorStrings,
            translationDirection = translationDirection,
            languageTag = languageTag,
            focusedMessageId = focusedMessageId,
            onFocusedMessageVisible = onFocusedMessageVisible,
            onFocusedMessageHandled = onFocusedMessageHandled,
            text = text,
            modifier = modifier,
            conversationModel = conversationModel,
            compactHeader = compactHeader,
            trailingActions = trailingActions,
            onOpenTranslator = onOpenTranslator,
            groupMembersInitiallyExpanded = groupMembersInitiallyExpanded,
            messageInputOverride = messageInputOverride,
            sendButtonOverride = sendButtonOverride,
            documentAttachmentActionsHost = documentAttachmentActionsHost,
            mediaAttachmentActionsHost = mediaAttachmentActionsHost,
            audioAttachmentActionsHost = audioAttachmentActionsHost,
            composerActionsHost = composerActionsHost,
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
    audioPlayer: AudioPlayerService,
    audioRecorder: AudioRecorderService,
    audioRecordingReferences: AudioRecordingReferenceReleaser?,
    audioRecordingConfiguration: ChatAudioRecordingConfiguration,
    filePicker: FilePickerService,
    capturePhoto: suspend () -> PlatformResult<PlatformFile>,
    conversationId: String,
    navigationMessage: String,
    onBackToList: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenMessageConversation: (String, String) -> Unit,
    onOpenAttachment: suspend (PlatformFile) -> PlatformResult<Unit>,
    onDownloadAttachment: suspend (PlatformFile) -> PlatformResult<Unit>,
    onShareAttachment: suspend (PlatformFile) -> PlatformResult<Unit>,
    onOpenExternalLink: (String) -> Unit,
    onOpenMapLink: (String) -> ChatMapOpenResult,
    onOpenUserProfile: (String) -> Unit,
    onCopyMessage: (String) -> Unit,
    openingProfileUserId: String?,
    remoteConversationAvatar: @Composable (ConversationAvatarPresentation, Modifier) -> Unit,
    mediaSlots: ChatMediaPlatformSlots,
    translationGateway: ChatTranslationGateway,
    translatorStrings: ChatTranslatorStrings,
    translationDirection: ChatTranslationDirection,
    languageTag: String?,
    focusedMessageId: String?,
    onFocusedMessageVisible: (String) -> Unit,
    onFocusedMessageHandled: () -> Unit,
    text: (ChatText) -> String,
    modifier: Modifier,
    conversationModel: ChatViewModel?,
    compactHeader: Boolean,
    trailingActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    onOpenTranslator: (() -> Unit)?,
    groupMembersInitiallyExpanded: Boolean,
    messageInputOverride: (@Composable (
        String,
        (String) -> Unit,
        () -> Unit,
        Modifier,
        @Composable () -> Unit,
        @Composable () -> Unit,
    ) -> Unit)?,
    sendButtonOverride: (@Composable (Boolean, () -> Unit, Modifier) -> Unit)?,
    documentAttachmentActionsHost: (@Composable (ChatDocumentAttachmentActions) -> Unit)?,
    mediaAttachmentActionsHost: (@Composable (ChatMediaAttachmentActions) -> Unit)?,
    audioAttachmentActionsHost: (@Composable (ChatAudioAttachmentActions) -> Unit)?,
    composerActionsHost: (@Composable (ChatComposerActionCallbacks) -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val template = quataTheme()
    var attachmentPickerError by remember { mutableStateOf<String?>(null) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingElapsedSeconds by remember { mutableLongStateOf(0L) }
    var recordingError by remember { mutableStateOf<String?>(null) }
    var pendingAudioRecording by remember { mutableStateOf<AudioRecording?>(null) }
    var viewedMedia by remember(conversationId) { mutableStateOf<PlatformFile?>(null) }
    var documentViewerState by remember(conversationId) { mutableStateOf<DocumentViewerState?>(null) }
    var documentOpenJob by remember(conversationId) { mutableStateOf<Job?>(null) }
    var documentOpenGeneration by remember(conversationId) { mutableLongStateOf(0L) }
    val ownsViewModel = conversationModel == null
    val viewModel = remember(repository, conversationId, conversationModel) {
        conversationModel ?: ChatViewModel(
            conversationId = conversationId,
            repository = repository,
            text = text,
            isFavoritesConversation = conversationId == AppDestinations.FavoriteMessagesConversationId,
        )
    }
    val state by viewModel.uiState.collectAsState()
    val audioController = remember(audioPlayer, conversationId) {
        ChatAudioPlaybackController(audioPlayer = audioPlayer, messages = { viewModel.uiState.value.messages })
    }
    val audioPlaybackState by audioController.state.collectAsState()
    val chromeStrings = remember(languageTag) { chatChromeStringsForLanguage(languageTag) }
    val sosStrings = remember(languageTag) { chatSosStringsForLanguage(languageTag) }
    val usersById = remember(state.participantCandidates, state.currentUser) {
        (state.participantCandidates + listOfNotNull(state.currentUser)).associateBy { it.id }
    }
    fun openAttachment(file: PlatformFile) {
        when (chatAttachmentKind(file)) {
            ChatAttachmentKind.Image, ChatAttachmentKind.Video -> viewedMedia = file
            ChatAttachmentKind.Audio -> Unit
            else -> {
                documentOpenJob?.cancel()
                val openGeneration = documentOpenGeneration + 1L
                documentOpenGeneration = openGeneration
                documentViewerState = documentViewerOpeningState(file)
                documentOpenJob = scope.launch {
                    val result = openPlatformDocumentWithViewerState(
                        file = file,
                        open = onOpenAttachment,
                        allowPlatformFallbackForUnsupportedFormat = true,
                    )
                    if (documentOpenGeneration == openGeneration) {
                        documentViewerState = result.completed
                        documentOpenJob = null
                    }
                }
            }
        }
    }
    fun handleAttachmentActionResult(result: PlatformResult<Unit>, successText: String, unsupportedText: String) {
        when (result) {
            is PlatformResult.Success -> viewModel.onEvent(ChatUiEvent.ShowNotice(successText))
            is PlatformResult.Failure -> viewModel.onEvent(ChatUiEvent.ShowError(result.reason ?: chromeStrings.attachmentActionFailed))
            PlatformResult.Cancelled -> Unit
            PlatformResult.Unsupported -> viewModel.onEvent(ChatUiEvent.ShowError(unsupportedText))
        }
    }
    fun downloadAttachment(file: PlatformFile) {
        scope.launch {
            handleAttachmentActionResult(
                onDownloadAttachment(file),
                chromeStrings.attachmentDownloadStarted,
                chromeStrings.attachmentDownloadUnsupported,
            )
        }
    }
    fun shareAttachment(file: PlatformFile) {
        scope.launch {
            val result = onShareAttachment(file)
            handleAttachmentActionResult(
                result,
                chromeStrings.attachmentShareStarted,
                chromeStrings.attachmentShareUnsupported,
            )
        }
    }
    fun openMapLink(url: String) {
        viewModel.onEvent(ChatUiEvent.ShowNotice(chromeStrings.mapOpenStarted))
        when (onOpenMapLink(url)) {
            ChatMapOpenResult.Opened -> Unit
            ChatMapOpenResult.Unsupported -> viewModel.onEvent(ChatUiEvent.ShowError(chromeStrings.mapOpenUnsupported))
            ChatMapOpenResult.Failed -> viewModel.onEvent(ChatUiEvent.ShowError(chromeStrings.mapOpenFailed))
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
        viewModel.setConversationVisible(true)
        onDispose {
            if (isRecordingAudio) scope.launch { audioRecorder.cancel() }
            documentOpenJob?.cancel()
            documentOpenGeneration += 1L
            audioController.dispose()
            viewModel.setConversationVisible(false)
            viewModel.cleanupEmptyConversationIfNeeded()
            repository.setActiveConversation(null)
            if (ownsViewModel) viewModel.close()
        }
    }
    ChatScreenHost(
        repository = repository,
        conversationId = conversationId,
        text = text,
        focusedMessageId = focusedMessageId,
        onFocusedMessageVisible = onFocusedMessageVisible,
        onFocusedMessageHandled = onFocusedMessageHandled,
        modifier = modifier,
        groupMembersInitiallyExpanded = groupMembersInitiallyExpanded,
        model = viewModel,
        slots = ChatScreenHostSlots(
            chromeStrings = chromeStrings,
            messageStrings = ChatConversationDetailStrings(chromeStrings.edited, chromeStrings.deletedMessage, chromeStrings.forwarded),
            translatorStrings = translatorStrings,
            translationGateway = translationGateway,
            translationDirection = translationDirection,
            messageTimestamp = { message -> chatMessageTimestampLabel(message, languageTag) },
            compactHeader = compactHeader,
            navigationAction = {
                CompactIconButton(onClick = onBackToList, modifier = Modifier.semantics { testTag = "chat.back" }) {
                    CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, chromeStrings.back)
                }
            },
            conversationAvatar = { conversation ->
                conversation?.let {
                    val title = it.chatDisplayTitle().ifBlank { chromeStrings.untitledConversation }
                    ConversationAvatarContent(
                        presentation = resolveConversationAvatarPresentation(
                            conversation = it,
                            currentUser = state.currentUser,
                            usersById = usersById,
                            displayTitle = title,
                            openingProfileUserId = openingProfileUserId,
                        ),
                        onOpenUserProfile = onOpenUserProfile,
                        remoteAvatar = remoteConversationAvatar,
                    )
                }
            },
            memberAvatar = { member ->
                QuataAvatarLoadingHaloContent(
                    isLoading = openingProfileUserId == member.id,
                    modifier = Modifier
                        .size(38.dp)
                        .clickable(
                            enabled = member.canOpenProfile && openingProfileUserId != member.id,
                        ) { onOpenUserProfile(member.id) }
                        .semantics { testTag = ChatProfileMemberAvatarTestTagPrefix + member.id },
                ) {
                    remoteConversationAvatar(
                        ConversationAvatarPresentation(
                            kind = ConversationAvatarKind.Private,
                            name = member.name,
                            stableId = member.id,
                            avatarUrl = member.avatarUrl,
                            profileId = member.id.takeIf { member.canOpenProfile },
                            isMuted = false,
                            isLoading = openingProfileUserId == member.id,
                        ),
                        Modifier
                            .size(34.dp),
                    )
                }
            },
            trailingActions = trailingActions,
            onOpenTranslator = onOpenTranslator,
            messageAvatar = { message ->
                QuataAvatarLoadingHaloContent(
                    isLoading = openingProfileUserId == message.senderId,
                    modifier = Modifier
                        .size(38.dp)
                        .clickable(
                            enabled = openingProfileUserId != message.senderId,
                        ) { onOpenUserProfile(message.senderId) }
                        .semantics { testTag = ChatProfileMessageAvatarTestTagPrefix + message.senderId },
                ) {
                    remoteConversationAvatar(
                        resolveMessageAvatarPresentation(
                            message = message,
                            sender = usersById[message.senderId],
                            openingProfileUserId = openingProfileUserId,
                        ),
                        Modifier
                            .size(34.dp)
                            .border(1.dp, template.colors.divider, CircleShape),
                    )
                }
            },
            onOpenLink = onOpenExternalLink,
            onBack = onBackToList,
            onCopyMessage = onCopyMessage,
            onOpenMessageConversation = onOpenMessageConversation,
            onOpenUserProfile = onOpenUserProfile,
            subtitle = { conversation, typing ->
                when {
                    typing.isNotEmpty() -> chromeStrings.typing
                    navigationMessage.isNotBlank() -> navigationMessage
                    conversation?.isGroup == true -> chromeStrings.memberCount(conversation.participantIds.size)
                    else -> null
                }
            },
            composer = { composerModifier ->
                val recordAudioAction: () -> Unit = {
                    scope.launch {
                        when (val result = audioRecorder.start(audioRecordingConfiguration.toPlatformOptions())) {
                            is PlatformResult.Success -> {
                                isRecordingAudio = true
                                recordingElapsedSeconds = 0L
                                recordingError = null
                            }
                            is PlatformResult.Failure -> recordingError = result.reason ?: chromeStrings.audioStartError
                            PlatformResult.Cancelled -> recordingError = null
                            PlatformResult.Unsupported -> recordingError = chromeStrings.audioUnsupported
                        }
                    }
                }
                val stopRecordingAction: () -> Unit = {
                    scope.launch {
                        when (val result = audioRecorder.stop()) {
                            is PlatformResult.Success -> {
                                isRecordingAudio = false
                                recordingElapsedSeconds = result.value.durationMillis / 1_000L
                                recordingError = null
                                pendingAudioRecording?.let { previous -> audioRecordingReferences?.release(previous) }
                                pendingAudioRecording = result.value
                                viewModel.onEvent(
                                    ChatUiEvent.AttachmentSelected(
                                        result.value.file.reference,
                                        result.value.file.displayName ?: chromeStrings.voiceNote,
                                        result.value.mimeType,
                                    ),
                                )
                            }
                            is PlatformResult.Failure -> {
                                isRecordingAudio = false
                                recordingError = result.reason ?: chromeStrings.audioSaveError
                            }
                            PlatformResult.Cancelled -> {
                                isRecordingAudio = false
                                recordingError = null
                            }
                            PlatformResult.Unsupported -> {
                                isRecordingAudio = false
                                recordingError = chromeStrings.audioUnsupported
                            }
                        }
                    }
                }
                val cancelRecordingAction: () -> Unit = {
                    scope.launch {
                        audioRecorder.cancel()
                        isRecordingAudio = false
                        recordingElapsedSeconds = 0L
                        recordingError = null
                    }
                }
                composerActionsHost?.invoke(
                    ChatComposerActionCallbacks(
                        recordAudio = if (!isRecordingAudio) recordAudioAction else null,
                        stopRecording = if (isRecordingAudio) stopRecordingAction else null,
                        cancelRecording = if (isRecordingAudio) cancelRecordingAction else null,
                    send = if (state.messageText.isNotBlank() || state.attachmentUri != null) {
                        { viewModel.onEvent(ChatUiEvent.Send) }
                    } else {
                        null
                    },
                    messageText = state.messageText,
                    hasPendingAttachment = state.attachmentUri != null,
                ),
            )
                ChatComposerContent(
                    state = state,
                    strings = chromeStrings,
                    onEvent = viewModel::onEvent,
                    onPickDocument = {
                        scope.launch {
                            when (val result = filePicker.pick(FilePickerRequest(source = FilePickerSource.Documents))) {
                                is PlatformResult.Success -> result.value.firstOrNull()?.let {
                                    pendingAudioRecording?.let { recording -> audioRecordingReferences?.release(recording) }
                                    pendingAudioRecording = null
                                    attachmentPickerError = null
                                    viewModel.onEvent(ChatUiEvent.AttachmentSelected(it.reference, it.displayName ?: chromeStrings.attachment, it.mimeType))
                                }
                                is PlatformResult.Failure -> attachmentPickerError = result.reason ?: chromeStrings.filePickerError
                                PlatformResult.Unsupported -> attachmentPickerError = chromeStrings.filePickerUnsupported
                                PlatformResult.Cancelled -> attachmentPickerError = null
                            }
                        }
                    },
                    onPickGallery = {
                        scope.launch {
                            when (val result = filePicker.pick(FilePickerRequest(source = FilePickerSource.Gallery))) {
                                is PlatformResult.Success -> result.value.firstOrNull()?.let {
                                    pendingAudioRecording?.let { recording -> audioRecordingReferences?.release(recording) }
                                    pendingAudioRecording = null
                                    attachmentPickerError = null
                                    viewModel.onEvent(ChatUiEvent.AttachmentSelected(it.reference, it.displayName ?: chromeStrings.attachment, it.mimeType))
                                }
                                is PlatformResult.Failure -> attachmentPickerError = result.reason ?: chromeStrings.galleryError
                                PlatformResult.Unsupported -> attachmentPickerError = chromeStrings.galleryUnsupported
                                PlatformResult.Cancelled -> attachmentPickerError = null
                            }
                        }
                    },
                    onOpenPendingAttachment = { state.attachmentUri?.let { openAttachment(PlatformFile(it, state.attachmentName, state.attachmentMimeType)) } },
                    onClearAttachment = {
                        val recording = pendingAudioRecording
                        pendingAudioRecording = null
                        viewModel.onEvent(ChatUiEvent.ClearAttachment)
                        if (recording != null) scope.launch { audioRecordingReferences?.release(recording) }
                    },
                    onCamera = {
                        scope.launch {
                            when (val result = capturePhoto()) {
                                is PlatformResult.Success -> {
                                    pendingAudioRecording?.let { recording -> audioRecordingReferences?.release(recording) }
                                    pendingAudioRecording = null
                                    attachmentPickerError = null
                                    viewModel.onEvent(
                                        ChatUiEvent.AttachmentSelected(
                                            result.value.reference,
                                            result.value.displayName ?: chromeStrings.photo,
                                            result.value.mimeType ?: "image/jpeg",
                                        ),
                                    )
                                }
                                is PlatformResult.Failure -> attachmentPickerError = result.reason ?: chromeStrings.cameraError
                                PlatformResult.Unsupported -> attachmentPickerError = chromeStrings.cameraUnsupported
                                PlatformResult.Cancelled -> attachmentPickerError = null
                            }
                        }
                    },
                    onRecordAudio = {
                        recordAudioAction()
                    },
                    isRecordingAudio = isRecordingAudio,
                    recordingElapsedLabel = recordingElapsedSeconds.toDurationLabel(),
                    recordingError = recordingError,
                    onStopRecording = {
                        stopRecordingAction()
                    },
                    onCancelRecording = {
                        cancelRecordingAction()
                    },
                    attachmentError = attachmentPickerError ?: state.error,
                    modifier = composerModifier,
                    messageInputOverride = messageInputOverride,
                    sendButtonOverride = sendButtonOverride,
                )
            },
            attachment = { message, isSelected, attachmentModifier ->
                val template = quataTheme()
                val textColor = if (message.isMine || isSelected) template.colors.accentContent else template.colors.textPrimary
                ChatBrowserAttachmentContent(
                    message = message,
                    audioState = audioPlaybackState,
                    onToggleAudio = audioController::toggle,
                    onSeekAudio = audioController::seekToFraction,
                    onOpenAttachment = ::openAttachment,
                    onDownloadAttachment = ::downloadAttachment,
                    onShareAttachment = ::shareAttachment,
                    mediaPreview = mediaSlots.preview,
                    playVideoLabel = chromeStrings.playVideo,
                    attachmentLabel = chromeStrings.attachment,
                    openAttachmentLabel = chromeStrings.openAttachment,
                    downloadAttachmentLabel = chromeStrings.downloadAttachment,
                    shareAttachmentLabel = chromeStrings.shareAttachment,
                    playAudioLabel = chromeStrings.playAudio,
                    pauseAudioLabel = chromeStrings.pauseAudio,
                    audioErrorText = chromeStrings.audioUnsupported,
                    textColor = textColor,
                    documentAttachmentActionsHost = documentAttachmentActionsHost,
                    mediaAttachmentActionsHost = mediaAttachmentActionsHost,
                    audioAttachmentActionsHost = audioAttachmentActionsHost,
                    modifier = attachmentModifier,
                )
            },
            deliveryIndicator = { message, isSelected ->
                val template = quataTheme()
                val textColor = if (message.isMine || isSelected) template.colors.accentContent else template.colors.textPrimary
                ChatMessageDeliveryIndicatorContent(
                    state = if (message.isPending) MessageDeliveryState.Pending else message.deliveryState,
                    tint = textColor.copy(alpha = 0.62f),
                    readTint = template.colors.accent,
                )
            },
            favoriteMarker = { message, isSelected ->
                val template = quataTheme()
                val textColor = if (message.isMine || isSelected) template.colors.accentContent else template.colors.textPrimary
                CompactIcon(
                    imageVector = Icons.Filled.StarBorder,
                    contentDescription = chromeStrings.favoriteMarker,
                    tint = textColor.copy(alpha = 0.62f),
                    modifier = Modifier.size(15.dp),
                )
            },
            specialMessageBody = { message, isSelected ->
                val sos = remember(message.text, sosStrings) { resolveChatSosPresentation(message.text, sosStrings) }
                if (sos == null) {
                    false
                } else {
                    val template = quataTheme()
                    val textColor = if (message.isMine || isSelected) template.colors.accentContent else template.colors.textPrimary
                    val accentColor = if (message.isMine || isSelected) template.colors.accentContent else template.colors.accent
                    ChatSosLocationContent(
                        title = sos.title,
                        body = sos.body,
                        locationLabel = sos.locationLabel,
                        mapsUrl = sos.mapsUrl,
                        age = sos.age,
                        accuracy = sos.accuracy,
                        speed = sos.speed,
                        isUpdate = sos.isUpdate,
                        isUnavailable = sos.isUnavailable,
                        unavailableLabel = sos.unavailableLabel,
                        openMapsLabel = sosStrings.openMaps,
                        textColor = textColor,
                        accentColor = accentColor,
                        onOpenMaps = ::openMapLink,
                        mapPreviewIcon = {
                            CompactIcon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(42.dp),
                            )
                        },
                    )
                    true
                }
            },
            typingIndicator = { typing ->
                if (typing.isEmpty()) null else { { Text(chromeStrings.typing, Modifier.padding(14.dp)) } }
            },
        ),
    )
    viewedMedia?.let { file ->
        val kind = chatAttachmentKind(file)
        QuataFullscreenMediaOverlayContent(
            title = file.displayName ?: chromeStrings.attachment,
            onDismiss = { viewedMedia = null },
            showCommonMediaClose = mediaSlots.showCommonMediaClose,
            nativeClose = { dismiss -> mediaSlots.nativeClose(this, dismiss) },
        ) { mediaModifier ->
            mediaSlots.viewer(file, kind, mediaModifier)
        }
    }
    QuataDocumentViewerStatusContent(
        state = documentViewerState,
        strings = quataDocumentViewerStatusStrings(chatDocumentViewerLanguage(languageTag)),
        onDismiss = {
            documentOpenJob?.cancel()
            documentOpenJob = null
            documentOpenGeneration += 1L
            documentViewerState = null
        },
    )
}

private fun chatDocumentViewerLanguage(languageTag: String?): QuataLanguage {
    val normalized = languageTag.orEmpty().substringBefore('-').substringBefore('_').lowercase()
    return QuataLanguage.entries.firstOrNull { it.tag == normalized } ?: QuataLanguage.Spanish
}


private fun Long.toDurationLabel(): String {
    val minutes = this / 60L
    val seconds = this % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun ChatBrowserAttachmentContent(
    message: Message,
    audioState: ChatAudioPlaybackUiState,
    onToggleAudio: (Message, PlatformFile) -> Unit,
    onSeekAudio: (String, Float) -> Unit,
    onOpenAttachment: (PlatformFile) -> Unit,
    mediaPreview: @Composable (PlatformFile, ChatAttachmentKind, Modifier) -> Unit,
    playVideoLabel: String = "Play video",
    attachmentLabel: String = "Attachment",
    openAttachmentLabel: String = "Open attachment",
    downloadAttachmentLabel: String = "Download attachment",
    shareAttachmentLabel: String = "Share attachment",
    onDownloadAttachment: (PlatformFile) -> Unit,
    onShareAttachment: (PlatformFile) -> Unit,
    playAudioLabel: String = "Play audio",
    pauseAudioLabel: String = "Pause audio",
    audioErrorText: String = "Audio not available",
    textColor: androidx.compose.ui.graphics.Color,
    documentAttachmentActionsHost: (@Composable (ChatDocumentAttachmentActions) -> Unit)? = null,
    mediaAttachmentActionsHost: (@Composable (ChatMediaAttachmentActions) -> Unit)? = null,
    audioAttachmentActionsHost: (@Composable (ChatAudioAttachmentActions) -> Unit)? = null,
    modifier: Modifier,
) {
    val reference = message.attachmentUri.orEmpty()
    if (reference.isBlank()) return
    val mimeType = message.attachmentMimeType.orEmpty()
    val displayName = message.attachmentName?.takeIf { it.isNotBlank() } ?: attachmentLabel
    val file = PlatformFile(reference, displayName, mimeType)
    val kind = chatAttachmentKind(file)
    if (kind == ChatAttachmentKind.Image || kind == ChatAttachmentKind.Video) {
        mediaAttachmentActionsHost?.invoke(ChatMediaAttachmentActions(file, kind) { onOpenAttachment(file) })
        ChatMediaAttachmentContent(
            file = file,
            kind = kind,
            media = mediaPreview,
            onOpen = { onOpenAttachment(file) },
            playVideoLabel = playVideoLabel,
            modifier = modifier,
        )
        return
    }
    if (kind != ChatAttachmentKind.Audio) {
        val open = { onOpenAttachment(file) }
        val download = { onDownloadAttachment(file) }
        val share = { onShareAttachment(file) }
        documentAttachmentActionsHost?.invoke(ChatDocumentAttachmentActions(file, open, download, share))
        ChatDocumentAttachmentContent(
            name = displayName,
            textColor = textColor,
            onOpen = open,
            openLabel = openAttachmentLabel,
            downloadLabel = downloadAttachmentLabel,
            shareLabel = shareAttachmentLabel,
            onDownload = download,
            onShare = share,
            icon = {
                CompactIcon(
                    if (kind == ChatAttachmentKind.Document) Icons.Filled.AttachFile else Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = textColor,
                )
            },
            modifier = modifier,
        )
        return
    }
    val isActive = audioState.activeReference == reference
    val visiblePlayback = if (isActive) audioState.playback else AudioPlaybackState()
    val togglePlayback = { onToggleAudio(message, PlatformFile(reference, displayName, mimeType)) }
    audioAttachmentActionsHost?.invoke(
        ChatAudioAttachmentActions(
            file = file,
            playback = visiblePlayback,
            toggle = togglePlayback,
            seekToFraction = { fraction -> onSeekAudio(reference, fraction) },
        ),
    )
    ChatAudioAttachmentPlayerContent(
        isPlaying = visiblePlayback.isPlaying,
        hasError = isActive && audioState.failed,
        isLoading = isActive && audioState.operationInFlight,
        progress = if (visiblePlayback.durationMillis > 0L) {
            visiblePlayback.positionMillis.toFloat() / visiblePlayback.durationMillis.toFloat()
        } else 0f,
        displayText = displayName,
        errorText = audioErrorText,
        textColor = textColor,
        playPauseDescription = if (visiblePlayback.isPlaying) pauseAudioLabel else playAudioLabel,
        onTogglePlayback = togglePlayback,
        onSeekToFraction = { fraction -> onSeekAudio(reference, fraction) },
        modifier = modifier,
    )
}
