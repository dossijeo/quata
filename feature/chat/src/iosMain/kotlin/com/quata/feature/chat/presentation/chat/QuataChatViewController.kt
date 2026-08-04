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
import com.quata.core.platform.IosClipboardService
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.AudioRecorderService
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.CameraCaptureService
import com.quata.core.platform.PlatformFile
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
    val mediaViewerFactory: IosChatMediaViewerFactory,
    val conversationId: String? = null,
    /** Optional deep-link target; common UI resolves it only against messages already present. */
    val focusedMessageId: String? = null,
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
    val onOpenAttachment: (PlatformFile) -> Unit,
    /** Host slot reserved for a platform avatar/profile destination. */
    val onOpenAvatar: (String) -> Unit = {},
    /** Host slot for map/location attachment navigation. */
    val onOpenMap: (String) -> Unit,
    /** Host slot for translation UI owned by the launcher. */
    val onTranslateMessage: (String) -> Unit = {},
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
            val scope = rememberCoroutineScope()
            val openingProfileUserId by dependencies.profileOpeningState.profileId.collectAsState()
            DisposableEffect(conversationsModel) { onDispose(conversationsModel::close) }
            DisposableEffect(dependencies.repository) {
                dependencies.repository.setAppForeground(true)
                onDispose { dependencies.repository.setAppForeground(false) }
            }
            ChatBrowserHostContent(
                repository = dependencies.repository,
                audioPlayer = dependencies.audioPlayer,
                audioRecorder = dependencies.audioRecorder,
                filePicker = dependencies.filePicker,
                capturePhoto = { dependencies.cameraCapture.capturePhoto() },
                conversationId = dependencies.conversationId,
                focusedMessageId = dependencies.focusedMessageId,
                navigationMessage = dependencies.navigationMessage,
                onOpenConversation = dependencies.onOpenConversation,
                onOpenMessageConversation = dependencies.onOpenMessageConversation,
                onBackToList = dependencies.onBackToList,
                onOpenAttachment = dependencies.onOpenAttachment,
                onOpenMap = dependencies.onOpenMap,
                onOpenUserProfile = dependencies.onOpenAvatar,
                openingProfileUserId = openingProfileUserId,
                onCopyMessage = { value -> scope.launch { clipboard.writeText(value) } },
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
                ),
                text = chatText,
                conversationList = { listModifier ->
                    ConversationsScreenHost(
                        padding = PaddingValues(),
                        model = conversationsModel,
                        clipboardService = clipboard,
                        strings = conversationsHostStringsForLanguage(languageTag),
                        onOpenConversation = dependencies.onOpenConversation,
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

private fun iosChatNowMillis(): Long = currentEpochMillis()
