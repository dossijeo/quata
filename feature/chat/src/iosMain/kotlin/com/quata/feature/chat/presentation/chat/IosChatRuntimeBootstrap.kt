package com.quata.feature.chat.presentation.chat

import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.session.IosSupabaseAuthRuntimeConfiguration
import com.quata.core.session.IosSupabaseAuthSessionRefresher
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.AudioRecorderService
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.CameraCaptureService
import com.quata.core.platform.PlatformFile
import com.quata.feature.chat.data.IosChatAttachmentUploader
import com.quata.feature.chat.data.IosChatAuthenticatedUserProvider
import com.quata.feature.chat.data.IosChatPostgrestTransport
import com.quata.feature.chat.data.IosChatRuntimeConfiguration
import com.quata.feature.chat.data.IosChatRealtimeGateway
import com.quata.feature.chat.data.PostgrestChatRepository
import com.quata.feature.chat.domain.ChatRepository
import com.quata.core.ui.components.IosMemberProfileOpeningState

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
            transport = IosChatPostgrestTransport(configuration, authSession),
            authenticatedUser = IosChatAuthenticatedUserProvider(authSession),
            attachmentUploader = IosChatAttachmentUploader(configuration, authSession),
            realtimeGateway = IosChatRealtimeGateway(configuration, authSession),
        )
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
        conversationId: String?,
        focusedMessageId: String?,
        languageTag: String,
        onOpenConversation: (String) -> Unit,
        onBackToList: () -> Unit,
        onOpenAttachment: (PlatformFile) -> Unit,
        onOpenMap: (String) -> Unit,
        onOpenAvatar: (String) -> Unit,
        profileOpeningState: IosMemberProfileOpeningState,
    ): IosChatHostDependencies = IosChatHostDependencies(
        repository = repository(),
        audioPlayer = audioPlayer,
        audioRecorder = audioRecorder,
        filePicker = filePicker,
        cameraCapture = cameraCapture,
        conversationId = conversationId,
        focusedMessageId = focusedMessageId,
        languageTag = languageTag,
        onOpenConversation = onOpenConversation,
        onBackToList = onBackToList,
        onOpenAttachment = onOpenAttachment,
        onOpenMap = onOpenMap,
        onOpenAvatar = onOpenAvatar,
        profileOpeningState = profileOpeningState,
    )
}

/** Swift-facing factory avoiding Kotlin default-argument export ambiguity. */
fun createIosChatRuntimeBootstrap(
    configuration: IosChatRuntimeConfiguration,
    authSession: IosRenewableAuthSession,
): IosChatRuntimeBootstrap = IosChatRuntimeBootstrap(configuration, authSession)
