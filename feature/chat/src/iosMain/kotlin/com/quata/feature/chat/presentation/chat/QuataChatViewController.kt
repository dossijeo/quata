package com.quata.feature.chat.presentation.chat

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.navigation.AppDestinations
import com.quata.core.platform.IosClipboardService
import com.quata.core.language.FangTranslationService
import com.quata.core.language.IosTranslationHttpTransport
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.AudioRecorderService
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.CameraCaptureService
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerSource
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.SharePayload
import com.quata.core.platform.ShareService
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.data.IosChatAttachmentDownloader
import com.quata.feature.chat.presentation.conversations.ConversationAvatarKind
import com.quata.feature.chat.presentation.conversations.ConversationAvatarPresentation
import com.quata.feature.chat.presentation.conversations.ConversationsScreenHost
import com.quata.feature.chat.presentation.conversations.ConversationsViewModel
import com.quata.feature.chat.presentation.conversations.conversationsHostStringsForLanguage
import com.quata.core.ui.components.IosRemoteAvatar
import com.quata.core.ui.components.QuataAvatarFallback
import com.quata.core.ui.components.QuataStandardFloatingPanelContent
import com.quata.core.ui.components.IosMemberProfileOpeningState
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.UIKit.UIViewController
import kotlinx.coroutines.launch

/**
 * iOS composition input for the shared chat list, bubble stream and composer.
 *
 * Realtime, player/recorder lifecycle, URI resolution and attachment caching stay in the iOS
 * launcher adapters. The common host only receives the contracts it needs to render and dispatch
 * UI events.
 */
class IosChatHostDependencies(
    val repository: ChatRepository,
    val audioPlayer: AudioPlayerService,
    val audioRecorder: AudioRecorderService,
    val filePicker: FilePickerService,
    val cameraCapture: CameraCaptureService,
    val attachmentDownloader: IosChatAttachmentDownloader,
    val shareService: ShareService,
    val mediaViewerFactory: IosChatMediaViewerFactory,
    val conversationId: String? = null,
    /** Optional deep-link target; common UI resolves it only against messages already present. */
    val focusedMessageId: String? = null,
    /** Platform router callback that clears the one-shot deep-link target after common UI consumes it. */
    val onFocusedMessageHandled: () -> Unit,
    val languageTag: String,
    val navigationMessage: String = "Quata para iOS",
    /** AVFoundation records AAC in an MP4 container; Web stays on the common WebM default. */
    val audioRecordingConfiguration: ChatAudioRecordingConfiguration = ChatAudioRecordingConfiguration(
        mimeType = ChatAudioRecordingConfiguration.IOS_MIME_TYPE,
    ),
    val onOpenConversation: (String) -> Unit,
    val onOpenMessageConversation: (String, String) -> Unit,
    val onBackToList: () -> Unit,
    /** Host slot for image/document/audio/map URIs selected from a shared bubble. */
    val onOpenAttachment: suspend (PlatformFile) -> PlatformResult<Unit>,
    /** Host slot reserved for a platform avatar/profile destination. */
    val onOpenAvatar: (String) -> Unit = {},
    /** Host slot for validated HTTP(S) links, including map/location destinations. */
    val onOpenExternalLink: (String) -> Unit,
    val onOpenMapLink: (String) -> ChatMapOpenResult = { value ->
        onOpenExternalLink(value)
        ChatMapOpenResult.Opened
    },
    val profileOpeningState: IosMemberProfileOpeningState,
)

/**
 * Stable UIKit/Compose entry point. The injected contracts deliberately prevent this iOS source
 * from creating a fake repository, manual Realtime client, player, cache or URI implementation.
 */
fun QuataChatViewController(dependencies: IosChatHostDependencies): UIViewController =
    ComposeUIViewController {
        QuataTheme {
            val languageTag = dependencies.languageTag
            val chatText = remember(languageTag) { { value: ChatText -> chatTextForLanguage(value, languageTag) } }
            val conversationsModel = remember(dependencies.repository, languageTag) {
                ConversationsViewModel(
                    repository = dependencies.repository,
                    text = chatText,
                )
            }
            val clipboard = remember { IosClipboardService() }
            val translationGateway = remember {
                FangChatTranslationGateway(FangTranslationService(transport = IosTranslationHttpTransport()))
            }
            val evidenceFilePicker = remember(dependencies.filePicker) {
                IosChatEvidenceFilePicker.wrapIfRequested(dependencies.filePicker)
            }
            val scope = rememberCoroutineScope()
            val openingProfileUserId by dependencies.profileOpeningState.profileId.collectAsState()
            DisposableEffect(conversationsModel) { onDispose(conversationsModel::close) }
            DisposableEffect(dependencies.repository) {
                dependencies.repository.setAppForeground(true)
                onDispose { dependencies.repository.setAppForeground(false) }
            }
            ChatProductHostContent(
                repository = dependencies.repository,
                audioPlayer = dependencies.audioPlayer,
                audioRecorder = dependencies.audioRecorder,
                filePicker = evidenceFilePicker,
                capturePhoto = {
                    iosChatEvidenceCameraCapturePhoto() ?: dependencies.cameraCapture.capturePhoto()
                },
                conversationId = dependencies.conversationId,
                focusedMessageId = dependencies.focusedMessageId,
                onFocusedMessageHandled = dependencies.onFocusedMessageHandled,
                navigationMessage = dependencies.navigationMessage,
                onOpenConversation = dependencies.onOpenConversation,
                onOpenMessageConversation = dependencies.onOpenMessageConversation,
                onBackToList = dependencies.onBackToList,
                onOpenAttachment = dependencies.onOpenAttachment,
                onDownloadAttachment = { file -> dependencies.shareDownloadedAttachment(file) },
                onShareAttachment = { file -> dependencies.shareDownloadedAttachment(file) },
                onOpenExternalLink = dependencies.onOpenExternalLink,
                onOpenMapLink = dependencies.onOpenMapLink,
                onOpenUserProfile = dependencies.onOpenAvatar,
                openingProfileUserId = openingProfileUserId,
                onCopyMessage = { value -> scope.launch { clipboard.writeText(value) } },
                audioPlaybackProgressRefreshIntervalMillis = iosChatAudioPlaybackProgressRefreshIntervalMillis(),
                remoteConversationAvatar = { presentation, avatarModifier ->
                    IosRemoteAvatar(
                        name = presentation.name,
                        stableId = presentation.stableId,
                        avatarUrl = presentation.avatarUrl,
                        modifier = avatarModifier,
                    )
                },
                mediaSlots = iosChatMediaPlatformSlots(
                    downloader = dependencies.attachmentDownloader,
                    viewerFactory = dependencies.mediaViewerFactory,
                    retryLabel = chatChromeStringsForLanguage(languageTag).retry,
                ),
                translationGateway = translationGateway,
                translatorStrings = chatTranslatorStringsForLanguage(languageTag),
                translationDirection = chatTranslationDirectionForLanguage(languageTag),
                languageTag = languageTag,
                text = chatText,
                conversationList = { listModifier ->
                    ConversationsScreenHost(
                        padding = PaddingValues(),
                        model = conversationsModel,
                        clipboardService = clipboard,
                        strings = conversationsHostStringsForLanguage(languageTag),
                        onOpenConversation = dependencies.onOpenConversation,
                        onOpenFavorites = { dependencies.onOpenConversation(AppDestinations.FavoriteMessagesConversationId) },
                        onOpenUserProfile = dependencies.onOpenAvatar,
                        openingProfileUserId = openingProfileUserId,
                        remoteConversationAvatar = { presentation, avatarModifier ->
                            IosRemoteAvatar(
                                name = presentation.name,
                                stableId = presentation.stableId,
                                avatarUrl = presentation.avatarUrl,
                                modifier = avatarModifier,
                            )
                        },
                        candidateAvatar = { candidate, avatarModifier ->
                            IosRemoteAvatar(
                                name = candidate.displayName,
                                stableId = candidate.profileId,
                                avatarUrl = candidate.avatarUrl,
                                modifier = avatarModifier,
                            )
                        },
                        inviteAvatar = { contact, avatarModifier ->
                            QuataAvatarFallback(contact.displayName, contact.id, avatarModifier)
                        },
                        panelHost = { content ->
                            QuataStandardFloatingPanelContent(onDismiss = conversationsModel::closeNewConversationPicker) { panelModifier, landscape ->
                                content(panelModifier, landscape)
                            }
                        },
                        nowMillisProvider = ::iosChatNowMillis,
                        modifier = listModifier,
                    )
                },
                audioRecordingConfiguration = dependencies.audioRecordingConfiguration,
            )
        }
    }

private fun iosChatAudioPlaybackProgressRefreshIntervalMillis(): Long =
    if (NSProcessInfo.processInfo.environment["QUATA_IOS_CHAT_ATTACHMENTS_AUDIO_UI_E2E"] == "1") 0L else 1_000L

private suspend fun IosChatHostDependencies.shareDownloadedAttachment(file: PlatformFile): PlatformResult<Unit> {
    val downloaded = attachmentDownloader.download(file.reference, file.displayName)
    val localFile = when (downloaded) {
        is PlatformResult.Success -> downloaded.value
        is PlatformResult.Failure -> return PlatformResult.Failure(downloaded.reason)
        PlatformResult.Cancelled -> return PlatformResult.Cancelled
        PlatformResult.Unsupported -> return PlatformResult.Unsupported
    }
    return try {
        shareService.share(
            SharePayload(
                title = localFile.displayName ?: file.displayName ?: "QÜATA",
                files = listOf(localFile),
            ),
        )
    } finally {
        attachmentDownloader.discard(localFile)
    }
}

private const val ChatMediaFixtureOptIn = "I_ACCEPT_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE"

private class IosChatEvidenceFilePicker(
    private val delegate: FilePickerService,
) : FilePickerService {
    override suspend fun pickFiles(
        acceptedMimeTypes: List<String>,
        allowMultiple: Boolean,
    ): PlatformResult<List<PlatformFile>> = pick(
        FilePickerRequest(acceptedMimeTypes, allowMultiple, FilePickerSource.Documents),
    )

    override suspend fun pick(request: FilePickerRequest): PlatformResult<List<PlatformFile>> {
        iosChatEvidencePickerOutcome(request.source)?.let { return it }
        iosChatEvidencePickedFile(request.source)?.let { return PlatformResult.Success(listOf(it)) }
        return delegate.pick(request)
    }

    companion object {
        fun wrapIfRequested(delegate: FilePickerService): FilePickerService =
            if (iosChatEvidenceFixtureOptedIn()) IosChatEvidenceFilePicker(delegate) else delegate
    }
}

private fun iosChatEvidenceCameraCapturePhoto(): PlatformResult<PlatformFile>? =
    iosChatEvidencePickerOutcome(FilePickerSource.Camera)
        ?.let {
            when (it) {
                is PlatformResult.Success -> it.value.firstOrNull()?.let { file -> PlatformResult.Success(file) }
                    ?: PlatformResult.Failure("camera_capture_empty")
                is PlatformResult.Failure -> it
                PlatformResult.Cancelled -> PlatformResult.Cancelled
                PlatformResult.Unsupported -> PlatformResult.Unsupported
            }
        }
        ?: iosChatEvidencePickedFile(FilePickerSource.Camera)?.let { PlatformResult.Success(it) }

private fun iosChatEvidencePickerOutcome(source: FilePickerSource): PlatformResult<List<PlatformFile>>? {
    val environment = NSProcessInfo.processInfo.environment
    if (!iosChatEvidenceFixtureOptedIn(environment)) return null
    if (environment.iosChatFixtureValue("QUATA_IOS_CHAT_ATTACHMENT_PICKER_SOURCE")?.lowercase() != source.iosChatEvidenceSourceName()) {
        return null
    }
    return when (environment.iosChatFixtureValue("QUATA_IOS_CHAT_ATTACHMENT_PICKER_OUTCOME")?.lowercase()) {
        "cancelled" -> PlatformResult.Cancelled
        "failure" -> PlatformResult.Failure(
            environment.iosChatFixtureValue("QUATA_IOS_CHAT_ATTACHMENT_PICKER_REASON")
                ?: "attachment_picker_e2e_failure",
        )
        "unsupported" -> PlatformResult.Unsupported
        else -> null
    }
}

private fun iosChatEvidencePickedFile(source: FilePickerSource): PlatformFile? {
    val environment = NSProcessInfo.processInfo.environment
    if (!iosChatEvidenceFixtureOptedIn(environment)) return null
    if (environment.iosChatFixtureValue("QUATA_IOS_CHAT_ATTACHMENT_PICKER_SOURCE")?.lowercase() != source.iosChatEvidenceSourceName()) {
        return null
    }
    val path = environment.iosChatFixtureValue("QUATA_IOS_CHAT_ATTACHMENT_PICKER_PATH")
        ?.takeIf(String::isNotBlank)
        ?: return null
    val reference = if (path.startsWith("file://")) path else NSURL.fileURLWithPath(path).absoluteString ?: path
    val name = environment.iosChatFixtureValue("QUATA_IOS_CHAT_ATTACHMENT_PICKER_NAME")
        ?: path.substringAfterLast('/').ifBlank { "chat-attachment-fixture" }
    val mimeType = environment.iosChatFixtureValue("QUATA_IOS_CHAT_ATTACHMENT_PICKER_MIME")
        ?: when (source) {
            FilePickerSource.Documents -> "application/pdf"
            FilePickerSource.Gallery,
            FilePickerSource.Camera -> "image/png"
        }
    return PlatformFile(reference = reference, displayName = name, mimeType = mimeType)
}

private fun FilePickerSource.iosChatEvidenceSourceName(): String = when (this) {
    FilePickerSource.Documents -> "document"
    FilePickerSource.Gallery -> "gallery"
    FilePickerSource.Camera -> "camera"
}

private fun iosChatEvidenceFixtureOptedIn(
    environment: Map<Any?, *> = NSProcessInfo.processInfo.environment,
): Boolean =
    environment.iosChatFixtureValue("QUATA_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE_OPT_IN") == ChatMediaFixtureOptIn

private fun Map<Any?, *>.iosChatFixtureValue(key: String): String? =
    this[key]?.toString()?.takeIf(String::isNotBlank)

private fun iosChatNowMillis(): Long = currentEpochMillis()
