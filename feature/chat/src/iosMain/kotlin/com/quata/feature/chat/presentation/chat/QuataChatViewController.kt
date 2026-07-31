package com.quata.feature.chat.presentation.chat

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
import com.quata.core.platform.ClipboardService
import com.quata.core.platform.ContactPickerService
import com.quata.core.platform.SharePayload
import com.quata.core.platform.ShareService
import com.quata.core.platform.PlatformResult
import com.quata.core.ui.components.QuataFloatingPanelContent
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.conversations.ConversationsScreenHost
import com.quata.feature.chat.presentation.conversations.ConversationsViewModel
import com.quata.feature.chat.presentation.conversations.conversationsHostStringsForLanguage
import com.quata.feature.chat.presentation.conversations.conversationsLocaleCatalogForLanguage
import com.quata.feature.chat.presentation.conversations.InviteChannelSheetContent
import com.quata.feature.chat.presentation.conversations.InviteChannelSheetStrings
import com.quata.feature.chat.presentation.conversations.InviteChannelTargetUi
import com.quata.feature.chat.domain.ChatInviteContact
import com.quata.feature.feed.presentation.IosRemoteAvatar
import com.quata.core.navigation.AppDestinations
import platform.UIKit.UIViewController
import platform.CoreFoundation.CFAbsoluteTimeGetCurrent
import platform.Foundation.NSUserDefaults
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
    /** UIKit-backed picker injected by Swift; no placeholder contact source is created here. */
    val contactPicker: ContactPickerService,
    /** Native share sheet injected by Swift for contact invitations. */
    val shareService: ShareService,
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
                    val scope = rememberCoroutineScope()
                    var inviteContacts by remember { mutableStateOf<List<ChatInviteContact>>(emptyList()) }
                    var contactsAvailable by remember { mutableStateOf(false) }
                    val conversations = remember(dependencies.repository) {
                        ConversationsViewModel(dependencies.repository, readContacts = { inviteContacts })
                    }
                    DisposableEffect(conversations) { onDispose(conversations::close) }
                    ConversationsScreenHost(
                        padding = PaddingValues(),
                        viewModel = conversations,
                        clipboardService = IosClipboardService(),
                        strings = conversationsHostStringsForLanguage(iosConversationLanguage()),
                        onOpenConversation = dependencies.onOpenConversation,
                        onOpenUserProfile = dependencies.onOpenAvatar,
                        onOpenFavorites = { dependencies.onOpenConversation(AppDestinations.FavoriteMessagesConversationId) },
                        contactsPermissionGranted = contactsAvailable,
                        onRequestInviteContactsPermission = {
                            scope.launch {
                                when (val result = dependencies.contactPicker.pickContacts()) {
                                    is PlatformResult.Success -> {
                                        inviteContacts = result.value.flatMapIndexed { index, contact ->
                                            contact.phones.mapIndexed { phoneIndex, phone ->
                                                ChatInviteContact(
                                                    id = "ios-contact-$index-$phoneIndex",
                                                    displayName = contact.displayName?.takeIf { it.isNotBlank() } ?: phone,
                                                    phone = phone,
                                                    phoneKeys = setOf(phone, phone.filter(Char::isDigit)).filter(String::isNotBlank).toSet(),
                                                )
                                            }
                                        }.distinctBy(ChatInviteContact::phone)
                                        contactsAvailable = true
                                        conversations.loadInviteContacts()
                                    }
                                    is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported -> contactsAvailable = false
                                }
                            }
                        },
                        remoteConversationAvatar = { presentation, avatarModifier -> IosRemoteAvatar(presentation.name, presentation.stableId, presentation.avatarUrl, false, null, avatarModifier) },
                        candidateAvatar = { candidate, modifier -> IosRemoteAvatar(candidate.displayName, candidate.profileId, candidate.avatarUrl, false, null, modifier) },
                        inviteAvatar = { contact, modifier -> IosRemoteAvatar(contact.displayName, contact.id, null, false, null, modifier) },
                        panelHost = { content -> QuataFloatingPanelContent(onDismiss = conversations::closeNewConversationPicker, modifier = listModifier) { panelModifier, landscape -> content(panelModifier, landscape) } },
                        inviteSheet = { contact, clipboard, dismiss -> IosInviteChannelSheet(contact, clipboard, dependencies.shareService, dismiss) },
                        nowMillisProvider = { ((CFAbsoluteTimeGetCurrent() + 978_307_200.0) * 1000.0).toLong() },
                        modifier = listModifier,
                    )
                },
                audioRecordingConfiguration = dependencies.audioRecordingConfiguration,
            )
        }
    }

@Composable
private fun IosInviteChannelSheet(contact: ChatInviteContact, clipboard: ClipboardService, shareService: ShareService, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val invitationStrings = conversationsLocaleCatalogForLanguage(iosConversationLanguage()).invitation
    InviteChannelSheetContent(
        invitationMessage = invitationStrings.message,
        targets = listOf(InviteChannelTargetUi("ios-share", invitationStrings.shareTarget)),
        strings = InviteChannelSheetStrings(invitationStrings.sheetTitle(contact.displayName), invitationStrings.copyMessage, invitationStrings.chooseAppFor(contact.displayName)),
        clipboardService = clipboard,
        onDismiss = onDismiss,
        onTargetSelected = { scope.launch { shareService.share(SharePayload(text = invitationStrings.message, title = invitationStrings.shareTitle)) }; onDismiss() },
        panelHost = { content -> QuataFloatingPanelContent(onDismiss = onDismiss) { modifier, _ -> content(modifier) } },
    )
}

/** AppleLanguages is the locale selected by the user, not a launcher fallback. */
private fun iosConversationLanguage(): String? =
    (NSUserDefaults.standardUserDefaults.objectForKey("AppleLanguages") as? List<*>)?.firstOrNull() as? String
