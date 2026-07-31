package com.quata.feature.chat.presentation.chat

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.AudioRecorderService
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.IosClipboardService
import com.quata.core.ui.components.QuataAvatarFallback
import com.quata.core.ui.components.QuataFloatingPanelContent
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.conversations.ConversationsScreenHost
import com.quata.feature.chat.presentation.conversations.ConversationsViewModel
import com.quata.feature.chat.presentation.conversations.spanishConversationsHostStrings
import platform.UIKit.UIViewController
import platform.Foundation.NSDate

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
    val conversationId: String? = null,
    /** Optional deep-link target; common UI resolves it only against messages already present. */
    val focusedMessageId: String? = null,
    val navigationMessage: String = "Quata para iOS",
    /** AVFoundation records AAC in an MP4 container; Web stays on the common WebM default. */
    val audioRecordingConfiguration: ChatAudioRecordingConfiguration = ChatAudioRecordingConfiguration(
        mimeType = ChatAudioRecordingConfiguration.IOS_MIME_TYPE,
    ),
    val onOpenConversation: (String) -> Unit,
    val onBackToList: () -> Unit,
    /** Host slot for image/document/audio/map URIs selected from a shared bubble. */
    val onOpenAttachment: (PlatformFile) -> Unit,
    /** Host slot reserved for a platform avatar/profile destination. */
    val onOpenAvatar: (String) -> Unit = {},
    /** Host slot for map/location attachment navigation. */
    val onOpenMap: (String) -> Unit = {},
    /** Host slot for translation UI owned by the launcher. */
    val onTranslateMessage: (String) -> Unit = {},
)

/**
 * Stable UIKit/Compose entry point. The injected contracts deliberately prevent this iOS source
 * from creating a fake repository, manual Realtime client, player, cache or URI implementation.
 */
fun QuataChatViewController(dependencies: IosChatHostDependencies): UIViewController =
    ComposeUIViewController {
        QuataTheme {
            ChatBrowserHostContent(
                repository = dependencies.repository,
                audioPlayer = dependencies.audioPlayer,
                audioRecorder = dependencies.audioRecorder,
                filePicker = dependencies.filePicker,
                conversationId = dependencies.conversationId,
                focusedMessageId = dependencies.focusedMessageId,
                navigationMessage = dependencies.navigationMessage,
                onOpenConversation = dependencies.onOpenConversation,
                onBackToList = dependencies.onBackToList,
                onOpenAttachment = dependencies.onOpenAttachment,
                conversationListHost = { listModifier ->
                    val conversations = remember(dependencies.repository) { ConversationsViewModel(dependencies.repository) }
                    DisposableEffect(conversations) { onDispose(conversations::close) }
                    ConversationsScreenHost(
                        padding = PaddingValues(),
                        viewModel = conversations,
                        clipboardService = IosClipboardService(),
                        strings = spanishConversationsHostStrings(),
                        onOpenConversation = dependencies.onOpenConversation,
                        onOpenUserProfile = dependencies.onOpenAvatar,
                        conversationAvatar = { conversation, _ -> QuataAvatarFallback(conversation.title.ifBlank { "Conversación" }, conversation.id, Modifier.size(46.dp)) },
                        candidateAvatar = { candidate, modifier -> QuataAvatarFallback(candidate.displayName, candidate.profileId, modifier) },
                        inviteAvatar = { contact, modifier -> QuataAvatarFallback(contact.displayName, contact.id, modifier) },
                        panelHost = { content -> QuataFloatingPanelContent(onDismiss = conversations::closeNewConversationPicker, modifier = listModifier) { panelModifier, landscape -> content(panelModifier, landscape) } },
                        nowMillisProvider = { (NSDate().timeIntervalSince1970 * 1000.0).toLong() },
                        modifier = listModifier,
                    )
                },
                audioRecordingConfiguration = dependencies.audioRecordingConfiguration,
            )
        }
    }
