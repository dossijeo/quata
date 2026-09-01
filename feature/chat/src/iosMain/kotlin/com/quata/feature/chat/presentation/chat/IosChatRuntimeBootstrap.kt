package com.quata.feature.chat.presentation.chat

import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.session.IosSupabaseAuthRuntimeConfiguration
import com.quata.core.session.IosSupabaseAuthSessionRefresher
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.AudioRecorderService
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.CameraCaptureService
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.ShareService
import com.quata.feature.chat.data.IosChatAttachmentUploader
import com.quata.feature.chat.data.IosChatAttachmentDownloader
import com.quata.feature.chat.data.IosChatAttachmentPreviewService
import com.quata.feature.chat.data.IosChatAuthenticatedUserProvider
import com.quata.feature.chat.data.ChatPostgrestResponse
import com.quata.feature.chat.data.ChatPostgrestTransport
import com.quata.feature.chat.data.IosChatPostgrestTransport
import com.quata.feature.chat.data.IosChatRuntimeConfiguration
import com.quata.feature.chat.data.IosChatRealtimeGateway
import com.quata.feature.chat.data.PostgrestChatRepository
import com.quata.feature.chat.domain.ChatRepository
import com.quata.core.ui.components.IosMemberProfileOpeningState
import platform.Foundation.NSProcessInfo

/**
 * Authenticated runtime composition for Chat. It shares the iOS Keychain owner with Auth/Feed
 * when injected by the launcher, and does not create a placeholder repository when no session
 * exists. A future UIKit router can request [repository] only after its current route requires it.
 */
class IosChatRuntimeBootstrap(
    private val configuration: IosChatRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession = IosRenewableAuthSession(
        IosSupabaseAuthSessionRefresher(
            IosSupabaseAuthRuntimeConfiguration(
                supabaseUrl = configuration.supabaseUrl,
                supabasePublishableKey = configuration.supabasePublishableKey,
            ),
        ),
    ),
) {
    private val chatRepository: ChatRepository by lazy {
        PostgrestChatRepository(
            transport = iosChatEvidenceFaultingTransportIfRequested(IosChatPostgrestTransport(configuration, authSession)),
            authenticatedUser = IosChatAuthenticatedUserProvider(authSession),
            attachmentUploader = IosChatAttachmentUploader(configuration, authSession),
            realtimeGateway = IosChatRealtimeGateway(configuration, authSession),
        )
    }
    private val attachmentDownloader: IosChatAttachmentDownloader by lazy {
        IosChatAttachmentDownloader(configuration, authSession)
    }

    /** One repository instance preserves the common polling/state flows across route transitions. */
    fun repository(): ChatRepository = chatRepository

    fun authSessionForInteractiveLogin(): IosRenewableAuthSession = authSession

    /**
     * Creates the exported Compose host input from the one authenticated repository and the
     * platform adapters owned by the iOS launcher. Keeping this hand-off here prevents Swift
     * from constructing a second Chat repository or an unrelated Keychain session per route.
     */
    fun hostDependencies(
        audioPlayer: AudioPlayerService,
        audioRecorder: AudioRecorderService,
        filePicker: FilePickerService,
        cameraCapture: CameraCaptureService,
        shareService: ShareService,
        mediaViewerFactory: IosChatMediaViewerFactory,
        conversationId: String?,
        focusedMessageId: String?,
        onFocusedMessageHandled: () -> Unit,
        languageTag: String,
        onOpenConversation: (String) -> Unit,
        onOpenMessageConversation: (String, String) -> Unit,
        onBackToList: () -> Unit,
        attachmentPreviewService: IosChatAttachmentPreviewService?,
        onOpenExternalLink: (String) -> Unit,
        onOpenMapLink: (String) -> ChatMapOpenResult = { value ->
            onOpenExternalLink(value)
            ChatMapOpenResult.Opened
        },
        onOpenAvatar: (String) -> Unit,
        profileOpeningState: IosMemberProfileOpeningState,
    ): IosChatHostDependencies = IosChatHostDependencies(
        repository = repository(),
        audioPlayer = audioPlayer,
        audioRecorder = audioRecorder,
        filePicker = filePicker,
        cameraCapture = cameraCapture,
        attachmentDownloader = attachmentDownloader,
        shareService = shareService,
        mediaViewerFactory = mediaViewerFactory,
        conversationId = conversationId,
        focusedMessageId = focusedMessageId,
        onFocusedMessageHandled = onFocusedMessageHandled,
        languageTag = languageTag,
        onOpenConversation = onOpenConversation,
        onOpenMessageConversation = onOpenMessageConversation,
        onBackToList = onBackToList,
        onOpenAttachment = { attachment ->
            attachmentPreviewService?.openRemoteAttachment(attachment) ?: PlatformResult.Unsupported
        },
        onOpenExternalLink = onOpenExternalLink,
        onOpenMapLink = onOpenMapLink,
        onOpenAvatar = onOpenAvatar,
        profileOpeningState = profileOpeningState,
    )
}

private fun iosChatEvidenceFaultingTransportIfRequested(
    delegate: ChatPostgrestTransport,
): ChatPostgrestTransport = if (iosChatRegisterFailureFixtureOptedIn()) {
    object : ChatPostgrestTransport {
        override suspend fun post(functionName: String, body: String): ChatPostgrestResponse {
            return if (functionName == "quata_chat_register_attachment") {
                ChatPostgrestResponse.Failure(IllegalStateException("chat_attachment_register_e2e_failure"))
            } else {
                delegate.post(functionName, body)
            }
        }
    }
} else {
    delegate
}

private fun iosChatRegisterFailureFixtureOptedIn(): Boolean {
    val environment = NSProcessInfo.processInfo.environment
    return environment["QUATA_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE_OPT_IN"]?.toString() == "I_ACCEPT_IOS_CHAT_ATTACHMENT_PICKER_FIXTURE" &&
        environment["QUATA_IOS_CHAT_ATTACHMENT_PICKER_OUTCOME"]?.toString()?.lowercase() == "register-failure"
}

/** Swift-facing factory avoiding Kotlin default-argument export ambiguity. */
fun createIosChatRuntimeBootstrap(
    configuration: IosChatRuntimeConfiguration,
    authSession: IosRenewableAuthSession,
): IosChatRuntimeBootstrap = IosChatRuntimeBootstrap(configuration, authSession)
