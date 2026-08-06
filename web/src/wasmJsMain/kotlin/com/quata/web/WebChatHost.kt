@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.quata.core.platform.BrowserClipboardService
import com.quata.core.language.BrowserTranslationHttpTransport
import com.quata.core.language.FangTranslationService
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.AudioRecorderService
import com.quata.core.platform.AudioRecordingReferenceReleaser
import com.quata.core.platform.BrowserAudioRecorderService
import com.quata.core.platform.DocumentPreviewKind
import com.quata.core.platform.DocumentSupport
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerSource
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.navigation.AppDestinations
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.chat.ChatProductHostContent
import com.quata.feature.chat.presentation.chat.ChatMediaPlatformSlots
import com.quata.feature.chat.presentation.chat.FangChatTranslationGateway
import com.quata.feature.chat.presentation.chat.chatTranslationDirectionForLanguage
import com.quata.feature.chat.presentation.chat.chatTranslatorStringsForLanguage
import com.quata.feature.chat.presentation.chat.chatTextForLanguage
import com.quata.feature.chat.presentation.conversations.ConversationAvatarPresentation
import com.quata.feature.chat.presentation.conversations.ConversationsScreenHost
import com.quata.feature.chat.presentation.conversations.ConversationsViewModel
import com.quata.feature.chat.presentation.conversations.conversationsHostStringsForLanguage
import com.quata.core.ui.components.QuataAvatarFallback
import com.quata.core.ui.components.QuataStandardFloatingPanelContent
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
    conversationId: String?,
    focusedMessageId: String? = null,
    onFocusedMessageHandled: () -> Unit = {},
    navigationMessage: String,
    onOpenConversation: (String) -> Unit,
    onOpenMessageConversation: (String, String) -> Unit,
    onBackToList: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    openingProfileUserId: String? = null,
    modifier: Modifier = Modifier,
) {
    val resolvedAudioRecorder = remember(audioRecorder) { audioRecorder ?: BrowserAudioRecorderService() }
    val resolvedRecordingReferences = remember(audioRecorder, audioRecordingReferences) {
        audioRecordingReferences ?: (resolvedAudioRecorder as? AudioRecordingReferenceReleaser)
    }
    val scope = rememberCoroutineScope()
    val languageTag = browserChatLanguageTag()
    val chatText = remember(languageTag) { { value: com.quata.feature.chat.presentation.chat.ChatText -> chatTextForLanguage(value, languageTag) } }
    val conversationsModel = remember(repository, languageTag) {
        ConversationsViewModel(repository = repository, text = chatText)
    }
    val clipboard = remember { BrowserClipboardService() }
    val translationGateway = remember {
        FangChatTranslationGateway(FangTranslationService(transport = BrowserTranslationHttpTransport()))
    }
    DisposableEffect(conversationsModel) { onDispose(conversationsModel::close) }
    DisposableEffect(repository) {
        repository.setAppForeground(chatBrowserDocumentIsVisible())
        val stopObserving = observeChatBrowserDocumentVisibility(repository::setAppForeground)
        onDispose {
            stopObserving()
            repository.setAppForeground(false)
        }
    }
    ChatProductHostContent(
        repository = repository,
        audioPlayer = audioPlayer,
        audioRecorder = resolvedAudioRecorder,
        audioRecordingReferences = resolvedRecordingReferences,
        filePicker = filePicker,
        capturePhoto = {
            when (val result = filePicker.pick(FilePickerRequest(source = FilePickerSource.Camera))) {
                is PlatformResult.Success -> result.value.firstOrNull()?.let { PlatformResult.Success(it) }
                    ?: PlatformResult.Failure("camera_capture_empty")
                is PlatformResult.Failure -> result
                PlatformResult.Cancelled -> PlatformResult.Cancelled
                PlatformResult.Unsupported -> PlatformResult.Unsupported
            }
        },
        conversationId = conversationId,
        focusedMessageId = focusedMessageId,
        onFocusedMessageHandled = onFocusedMessageHandled,
        navigationMessage = navigationMessage,
        onOpenConversation = onOpenConversation,
        onOpenMessageConversation = onOpenMessageConversation,
        onBackToList = onBackToList,
        onOpenAttachment = { file -> scope.launch { file.openWebAttachment(documentOpener) } },
        onOpenExternalLink = ::openWebExternalLink,
        onOpenUserProfile = onOpenUserProfile,
        openingProfileUserId = openingProfileUserId,
        onCopyMessage = { value -> scope.launch { clipboard.writeText(value) } },
        remoteConversationAvatar = { presentation, avatarModifier ->
            WebConversationAvatar(presentation, avatarModifier)
        },
        mediaSlots = ChatMediaPlatformSlots(
            preview = { file, kind, mediaModifier ->
                BrowserChatMediaContent(file, kind, viewer = false, modifier = mediaModifier)
            },
            viewer = { file, kind, mediaModifier ->
                BrowserChatMediaContent(file, kind, viewer = true, modifier = mediaModifier)
            },
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
                onOpenConversation = onOpenConversation,
                onOpenFavorites = { onOpenConversation(AppDestinations.FavoriteMessagesConversationId) },
                onOpenUserProfile = onOpenUserProfile,
                openingProfileUserId = openingProfileUserId,
                remoteConversationAvatar = { presentation, avatarModifier ->
                    WebConversationAvatar(presentation, avatarModifier)
                },
                candidateAvatar = { candidate, avatarModifier ->
                    WebConversationAvatar(
                        ConversationAvatarPresentation(
                            kind = com.quata.feature.chat.presentation.conversations.ConversationAvatarKind.Private,
                            name = candidate.displayName,
                            stableId = candidate.profileId,
                            avatarUrl = candidate.avatarUrl,
                            profileId = candidate.profileId,
                            isMuted = false,
                            isLoading = false,
                        ),
                        avatarModifier,
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
                nowMillisProvider = ::webChatNowMillis,
                modifier = listModifier,
            )
        },
        messageInputOverride = { value, onChange, modifier, leadingIcon, trailingIcon ->
            WebNativeInput(
                value = value,
                onValueChange = onChange,
                name = "Mensaje",
                modifier = modifier.height(62.dp),
                inputType = "text",
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
            )
        },
        sendButtonOverride = { enabled, onClick, modifier ->
            WebNativeButton("Enviar", enabled, onClick, modifier.width(56.dp).height(48.dp))
        },
        modifier = modifier,
    )
}

@Composable
private fun WebConversationAvatar(presentation: ConversationAvatarPresentation, modifier: Modifier) {
    Box(modifier.clip(CircleShape)) {
        QuataAvatarFallback(presentation.name, presentation.stableId, Modifier.fillMaxSize())
        presentation.avatarUrl?.takeIf(::isSafeWebAvatarUrl)?.let { url ->
            BrowserCanvasImage(url, presentation.name, ContentScale.Crop, Modifier.fillMaxSize())
        }
    }
}

private fun isSafeWebAvatarUrl(value: String): Boolean =
    value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)

@JsFun("() => globalThis.navigator?.language || globalThis.document?.documentElement?.lang || 'en'")
private external fun browserChatLanguageTag(): String

@JsFun("() => Date.now()")
private external fun webChatNowMillisAsDouble(): Double

private fun webChatNowMillis(): Long = webChatNowMillisAsDouble().toLong()

internal fun chatBrowserDocumentIsVisible(): Boolean = js(
    "globalThis.document?.visibilityState !== 'hidden'",
)

/** Pauses the repository polling loops on background tabs and unregisters with the host. */
internal fun observeChatBrowserDocumentVisibility(onChanged: (Boolean) -> Unit): () -> Unit = js(
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
        else -> reference.safeBrowserChatMediaUrl()?.let(::openWebExternalLink)
    }
}

private fun openWebExternalLink(url: String): Unit = js("globalThis.open(url, '_blank', 'noopener,noreferrer')")
