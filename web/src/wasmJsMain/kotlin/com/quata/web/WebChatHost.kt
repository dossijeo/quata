@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.AudioRecorderService
import com.quata.core.platform.AudioRecordingReferenceReleaser
import com.quata.core.platform.BrowserAudioRecorderService
import com.quata.core.platform.DocumentPreviewKind
import com.quata.core.platform.DocumentSupport
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.ClipboardService
import com.quata.core.platform.ContactPickerService
import com.quata.core.platform.SharePayload
import com.quata.core.platform.ShareService
import com.quata.core.platform.PlatformResult
import com.quata.core.ui.components.QuataFloatingPanelContent
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.chat.ChatBrowserHostContent
import com.quata.feature.chat.presentation.conversations.ConversationsScreenHost
import com.quata.feature.chat.presentation.conversations.ConversationsViewModel
import com.quata.feature.chat.presentation.conversations.InviteChannelSheetContent
import com.quata.feature.chat.presentation.conversations.InviteChannelSheetStrings
import com.quata.feature.chat.presentation.conversations.InviteChannelTargetUi
import com.quata.feature.chat.presentation.conversations.conversationsHostStringsForLanguage
import com.quata.feature.chat.domain.ChatInviteContact
import com.quata.core.navigation.AppDestinations
import kotlinx.coroutines.launch

/** Browser adapter: hash navigation and safe URL opening stay at the platform boundary. */
@Composable
fun WebChatHost(
    repository: ChatRepository,
    audioPlayer: AudioPlayerService,
    audioRecorder: AudioRecorderService? = null,
    audioRecordingReferences: AudioRecordingReferenceReleaser? = null,
    filePicker: FilePickerService,
    documentOpener: DocumentOpenService,
    clipboardService: ClipboardService,
    shareService: ShareService,
    contactPicker: ContactPickerService,
    conversationId: String?,
    navigationMessage: String,
    onOpenConversation: (String) -> Unit,
    onBackToList: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolvedAudioRecorder = remember(audioRecorder) { audioRecorder ?: BrowserAudioRecorderService() }
    val resolvedRecordingReferences = remember(audioRecorder, audioRecordingReferences) {
        audioRecordingReferences ?: (resolvedAudioRecorder as? AudioRecordingReferenceReleaser)
    }
    val scope = rememberCoroutineScope()
    var pickedInviteContacts by remember { mutableStateOf<List<ChatInviteContact>>(emptyList()) }
    var contactsAvailable by remember { mutableStateOf(false) }
    DisposableEffect(repository) {
        repository.setAppForeground(browserDocumentIsVisible())
        val stopObserving = observeBrowserDocumentVisibility(repository::setAppForeground)
        onDispose {
            stopObserving()
            repository.setAppForeground(false)
        }
    }
    ChatBrowserHostContent(
        repository = repository,
        audioPlayer = audioPlayer,
        audioRecorder = resolvedAudioRecorder,
        audioRecordingReferences = resolvedRecordingReferences,
        filePicker = filePicker,
        conversationId = conversationId,
        navigationMessage = navigationMessage,
        onOpenConversation = onOpenConversation,
        onBackToList = onBackToList,
        onOpenAttachment = { file -> scope.launch { file.openWebAttachment(documentOpener) } },
        onOpenAvatar = onOpenUserProfile,
        conversationListHost = { listModifier ->
            val conversations = remember(repository) { ConversationsViewModel(repository, readContacts = { pickedInviteContacts }) }
            DisposableEffect(conversations) { onDispose(conversations::close) }
            ConversationsScreenHost(
                padding = androidx.compose.foundation.layout.PaddingValues(),
                viewModel = conversations,
                clipboardService = clipboardService,
                strings = conversationsHostStringsForLanguage(webBrowserLanguage()),
                onOpenConversation = onOpenConversation,
                onOpenUserProfile = onOpenUserProfile,
                onOpenFavorites = { onOpenConversation(AppDestinations.FavoriteMessagesConversationId) },
                contactsPermissionGranted = contactsAvailable,
                onRequestInviteContactsPermission = {
                    scope.launch {
                        when (val result = contactPicker.pickContacts()) {
                            is PlatformResult.Success -> {
                                pickedInviteContacts = result.value.flatMapIndexed { index, contact ->
                                    contact.phones.mapIndexed { phoneIndex, phone ->
                                        val digits = phone.filter(Char::isDigit)
                                        ChatInviteContact(
                                            id = "web-contact-$index-$phoneIndex",
                                            displayName = contact.displayName?.takeIf { it.isNotBlank() } ?: phone,
                                            phone = phone,
                                            phoneKeys = setOf(phone, digits).filter(String::isNotBlank).toSet(),
                                        )
                                    }
                                }.distinctBy { it.phone }
                                contactsAvailable = true
                                conversations.loadInviteContacts()
                            }
                            is PlatformResult.Failure -> contactsAvailable = false
                            PlatformResult.Cancelled, PlatformResult.Unsupported -> contactsAvailable = false
                        }
                    }
                },
                remoteConversationAvatar = { presentation, avatarModifier -> BrowserRemoteAvatar(presentation.name, presentation.stableId, presentation.avatarUrl, false, null, avatarModifier) },
                candidateAvatar = { candidate, avatarModifier -> BrowserRemoteAvatar(candidate.displayName, candidate.profileId, candidate.avatarUrl, false, null, avatarModifier) },
                inviteAvatar = { contact, avatarModifier -> BrowserRemoteAvatar(contact.displayName, contact.id, null, false, null, avatarModifier) },
                panelHost = { content -> QuataFloatingPanelContent(onDismiss = conversations::closeNewConversationPicker, modifier = listModifier) { panelModifier, landscape -> content(panelModifier, landscape) } },
                inviteSheet = { contact, clipboard, dismiss ->
                    WebInviteChannelSheet(contact, clipboard, shareService, dismiss)
                },
                nowMillisProvider = ::webNowMillis,
                modifier = listModifier,
            )
        },
        messageInputOverride = { value, onChange, modifier -> WebNativeInput(value, onChange, "Mensaje", modifier.height(56.dp), inputType = "text") },
        sendButtonOverride = { enabled, onClick, modifier -> WebNativeButton("Enviar", enabled, onClick, modifier.height(48.dp)) },
        modifier = modifier,
    )
}

@Composable
private fun WebInviteChannelSheet(
    contact: ChatInviteContact,
    clipboardService: ClipboardService,
    shareService: ShareService,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val invitation = "Únete a Qüata: conecta, publica y conversa con tu comunidad."
    InviteChannelSheetContent(
        invitationMessage = invitation,
        targets = listOf(InviteChannelTargetUi(id = "browser-share", label = "Compartir")),
        strings = InviteChannelSheetStrings(
            shareTextTitle = "Invitar a ${contact.displayName}",
            copyMessage = "Copiar invitación",
            chooseAppFor = "Elige cómo enviar la invitación",
        ),
        clipboardService = clipboardService,
        onDismiss = onDismiss,
        onTargetSelected = {
            scope.launch { shareService.share(SharePayload(text = invitation, title = "Qüata")) }
            onDismiss()
        },
        panelHost = { content -> QuataFloatingPanelContent(onDismiss = onDismiss) { modifier, _ -> content(modifier) } },
    )
}

private fun webNowMillis(): Long = js("Date.now()")
private fun webBrowserLanguage(): String? = js("globalThis.navigator?.language || null")

private fun browserDocumentIsVisible(): Boolean = js(
    "globalThis.document?.visibilityState !== 'hidden'",
)

/** Pauses the repository polling loops on background tabs and unregisters with the host. */
private fun observeBrowserDocumentVisibility(onChanged: (Boolean) -> Unit): () -> Unit = js(
    """
    (() => {
    const document = globalThis.document;
    if (!document?.addEventListener) return () => {};
    const listener = () => onChanged(document.visibilityState !== 'hidden');
    document.addEventListener('visibilitychange', listener);
    return () => document.removeEventListener('visibilitychange', listener);
    })()
    """,
)

private suspend fun PlatformFile.openWebAttachment(documentOpener: DocumentOpenService) {
    when (DocumentSupport.describe(reference, displayName, mimeType).kind) {
        DocumentPreviewKind.Pdf,
        DocumentPreviewKind.RichText,
        DocumentPreviewKind.Office -> documentOpener.open(this)
        else -> reference.safeWebAttachmentUrl()?.let(::openWebExternalLink)
    }
}

private fun openWebExternalLink(url: String): Unit = js("globalThis.open(url, '_blank', 'noopener,noreferrer')")

private fun String.safeWebAttachmentUrl(): String? = takeIf {
    startsWith("https://", ignoreCase = true) ||
        startsWith("http://", ignoreCase = true) ||
        startsWith("blob:", ignoreCase = true)
}
