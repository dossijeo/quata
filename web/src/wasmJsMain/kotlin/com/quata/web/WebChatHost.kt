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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.layout.ContentScale
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
import com.quata.feature.chat.presentation.chat.ChatScreenHost
import com.quata.feature.chat.presentation.chat.ChatMediaKind
import com.quata.feature.chat.presentation.chat.ChatSoundEvent
import com.quata.feature.chat.presentation.chat.chatDetailStringsForLanguage
import com.quata.feature.chat.presentation.conversations.ConversationsScreenHost
import com.quata.feature.chat.presentation.conversations.ConversationsViewModel
import com.quata.feature.chat.presentation.conversations.InviteChannelSheetContent
import com.quata.feature.chat.presentation.conversations.InviteChannelSheetStrings
import com.quata.feature.chat.presentation.conversations.InviteChannelTargetUi
import com.quata.feature.chat.presentation.conversations.conversationsHostStringsForLanguage
import com.quata.feature.chat.presentation.conversations.conversationsLocaleCatalogForLanguage
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
    focusedMessageId: String?,
    navigationMessage: String,
    onOpenConversation: (String) -> Unit,
    onBackToList: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenMessageConversation: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolvedAudioRecorder = remember(audioRecorder) { audioRecorder ?: BrowserAudioRecorderService() }
    val resolvedRecordingReferences = remember(audioRecorder, audioRecordingReferences) {
        audioRecordingReferences ?: (resolvedAudioRecorder as? AudioRecordingReferenceReleaser)
    }
    val scope = rememberCoroutineScope()
    var pickedInviteContacts by remember { mutableStateOf<List<ChatInviteContact>>(emptyList()) }
    var contactsAvailable by remember { mutableStateOf(false) }
    var isAppForeground by remember { mutableStateOf(browserDocumentIsVisible()) }
    val detailStrings = chatDetailStringsForLanguage(webBrowserLanguage())
    DisposableEffect(repository) {
        repository.setAppForeground(isAppForeground)
        val stopObserving = observeBrowserDocumentVisibility { foreground ->
            isAppForeground = foreground
            repository.setAppForeground(foreground)
        }
        onDispose {
            stopObserving()
            repository.setAppForeground(false)
        }
    }
    ChatScreenHost(
        repository = repository,
        audioPlayer = audioPlayer,
        audioRecorder = resolvedAudioRecorder,
        audioRecordingReferences = resolvedRecordingReferences,
        filePicker = filePicker,
        conversationId = conversationId,
        focusedMessageId = focusedMessageId,
        navigationMessage = navigationMessage,
        onOpenConversation = onOpenConversation,
        onBackToList = onBackToList,
        onOpenAttachment = { file -> scope.launch { file.openWebAttachment(documentOpener) } },
        onOpenAvatar = onOpenUserProfile,
        profileAvatar = { presentation, avatarModifier, onClick ->
            BrowserRemoteAvatar(
                presentation.name,
                presentation.profileId,
                presentation.avatarUrl,
                false,
                null,
                avatarModifier.clickable(onClick = onClick),
            )
        },
        mediaAttachment = { presentation, mediaModifier, onClick ->
            if (presentation.kind == ChatMediaKind.Image) {
                BrowserCanvasImage(
                    url = presentation.file.reference,
                    contentDescription = presentation.file.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = mediaModifier.height(180.dp).clickable(onClick = onClick),
                )
            } else {
                Surface(mediaModifier.clickable(onClick = onClick)) { Text("Reproducir vídeo") }
            }
        },
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
        onOpenMap = { value -> value.safeWebMapUrl()?.let(::openWebExternalLink) },
        onTranslateMessage = { text -> openWebExternalLink(webTranslationUrl(text)) },
        onOpenMessageConversation = onOpenMessageConversation,
        isAppForeground = isAppForeground,
        onSoundEvent = ::playWebChatSound,
        strings = detailStrings,
        messageInputOverride = { value, onChange, modifier -> WebNativeInput(value, onChange, detailStrings.message, modifier.height(56.dp), inputType = "text") },
        sendButtonOverride = { enabled, onClick, modifier ->
            WebNativeButton(detailStrings.send, enabled, onClick, modifier.width(96.dp).height(48.dp))
        },
        clipboardService = clipboardService,
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
    val invitationStrings = conversationsLocaleCatalogForLanguage(webBrowserLanguage()).invitation
    InviteChannelSheetContent(
        invitationMessage = invitationStrings.message,
        targets = listOf(InviteChannelTargetUi(id = "browser-share", label = invitationStrings.shareTarget)),
        strings = InviteChannelSheetStrings(
            shareTextTitle = invitationStrings.sheetTitle(contact.displayName),
            copyMessage = invitationStrings.copyMessage,
            chooseAppFor = invitationStrings.chooseAppFor(contact.displayName),
        ),
        clipboardService = clipboardService,
        onDismiss = onDismiss,
        onTargetSelected = {
            scope.launch { shareService.share(SharePayload(text = invitationStrings.message, title = invitationStrings.shareTitle)) }
            onDismiss()
        },
        panelHost = { content -> QuataFloatingPanelContent(onDismiss = onDismiss) { modifier, _ -> content(modifier) } },
    )
}

@JsFun("() => Date.now()")
private external fun chatBrowserNowMillisAsDouble(): Double

/** Date.now() is a JavaScript Number, not the BigInt required by a Wasm Kotlin Long. */
private fun webNowMillis(): Long = chatBrowserNowMillisAsDouble().toLong()

private fun playWebChatSound(event: ChatSoundEvent) {
    playBrowserChatTone(if (event == ChatSoundEvent.MessageSent) 880.0 else 660.0)
}

private fun playBrowserChatTone(frequency: Double): Unit = js(
    """(() => {
      try {
        const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;
        if (!AudioContext) return;
        const context = globalThis.__quataChatSoundContext || (globalThis.__quataChatSoundContext = new AudioContext());
        const oscillator = context.createOscillator();
        const gain = context.createGain();
        oscillator.frequency.value = frequency;
        gain.gain.setValueAtTime(0.0001, context.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.08, context.currentTime + 0.01);
        gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + 0.12);
        oscillator.connect(gain); gain.connect(context.destination);
        oscillator.start(); oscillator.stop(context.currentTime + 0.13);
      } catch (_) {}
    })()""",
)
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

private fun webTranslationUrl(text: String): String =
    "https://translate.google.com/?sl=auto&tl=es&text=${encodeWebChatComponent(text)}&op=translate"

private fun encodeWebChatComponent(value: String): String = js("encodeURIComponent(value)")

private fun String.safeWebAttachmentUrl(): String? = takeIf {
    startsWith("https://", ignoreCase = true) ||
        startsWith("http://", ignoreCase = true) ||
        startsWith("blob:", ignoreCase = true)
}

private fun String.safeWebMapUrl(): String? = takeIf {
    startsWith("https://", ignoreCase = true) ||
        startsWith("http://", ignoreCase = true) ||
        startsWith("geo:", ignoreCase = true)
}
