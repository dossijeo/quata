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
import com.quata.core.platform.SharePayload
import com.quata.core.platform.ShareService
import com.quata.core.navigation.AppDestinations
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.chat.ChatMediaPlatformSlots
import com.quata.feature.chat.presentation.chat.ChatMapOpenResult
import com.quata.feature.chat.presentation.chat.ChatProductHostContent
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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
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
    shareService: ShareService,
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
    val groupMembersInitiallyExpanded = remember(conversationId) { browserChatMembersExpandedE2eEnabled() }
    val chatText = remember(languageTag) { { value: com.quata.feature.chat.presentation.chat.ChatText -> chatTextForLanguage(value, languageTag) } }
    val conversationsModel = remember(repository, languageTag) {
        ConversationsViewModel(repository = repository, text = chatText)
    }
    val clipboard = remember { BrowserClipboardService() }
    val translationGateway = remember {
        FangChatTranslationGateway(FangTranslationService(transport = BrowserTranslationHttpTransport()))
    }
    val openUserProfile = remember(onOpenUserProfile) {
        { userId: String ->
            blurWebChatActiveElement()
            onOpenUserProfile(userId)
        }
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
        onFocusedMessageVisible = ::setWebChatFocusedMessageSelected,
        onFocusedMessageHandled = {
            clearWebChatFocusedMessageSelected()
            onFocusedMessageHandled()
        },
        navigationMessage = navigationMessage,
        onOpenConversation = onOpenConversation,
        onOpenMessageConversation = onOpenMessageConversation,
        onBackToList = onBackToList,
        onOpenAttachment = { file -> file.openWebAttachment(documentOpener) },
        onDownloadAttachment = { file -> file.downloadWebAttachment() },
        onShareAttachment = { file -> file.shareWebAttachment(shareService) },
        onOpenExternalLink = ::openWebExternalLink,
        onOpenMapLink = ::openWebMapLink,
        onOpenUserProfile = openUserProfile,
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
        groupMembersInitiallyExpanded = groupMembersInitiallyExpanded,
        conversationList = { listModifier ->
            ConversationsScreenHost(
                padding = PaddingValues(),
                model = conversationsModel,
                clipboardService = clipboard,
                strings = conversationsHostStringsForLanguage(languageTag),
                onOpenConversation = onOpenConversation,
                onOpenFavorites = { onOpenConversation(AppDestinations.FavoriteMessagesConversationId) },
                onOpenUserProfile = openUserProfile,
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
        messageInputOverride = { value, onChange, onSubmit, modifier, leadingIcon, trailingIcon ->
            if (openingProfileUserId == null) {
                WebNativeInput(
                    value = value,
                    onValueChange = onChange,
                    onSubmit = onSubmit,
                    name = "Mensaje",
                    modifier = modifier.height(62.dp),
                    inputType = "text",
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                )
            } else {
                Box(modifier.height(62.dp))
            }
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

@JsFun("() => new URLSearchParams(globalThis.location?.search || '').get('quata-chat-members-expanded-e2e') === '1'")
private external fun browserChatMembersExpandedE2eEnabled(): Boolean

@JsFun("() => Date.now()")
private external fun webChatNowMillisAsDouble(): Double

private fun webChatNowMillis(): Long = webChatNowMillisAsDouble().toLong()

@JsFun("(messageId) => { globalThis.document?.documentElement?.setAttribute('data-quata-chat-focused-message-selected', String(messageId)); }")
private external fun setWebChatFocusedMessageSelected(messageId: String)

@JsFun("() => { globalThis.document?.documentElement?.removeAttribute('data-quata-chat-focused-message-selected'); }")
private external fun clearWebChatFocusedMessageSelected()

@JsFun("() => { const active = globalThis.document?.activeElement; if (active && typeof active.blur === 'function') active.blur(); }")
private external fun blurWebChatActiveElement()

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

private suspend fun PlatformFile.openWebAttachment(documentOpener: DocumentOpenService): PlatformResult<Unit> =
    when (DocumentSupport.describe(reference, displayName, mimeType).kind) {
        DocumentPreviewKind.Pdf,
        DocumentPreviewKind.RichText,
        DocumentPreviewKind.Office -> documentOpener.open(this)
        else -> reference.safeBrowserChatMediaUrl()
            ?.let {
                when (openWebExternalLinkResult(it)) {
                    "opened" -> PlatformResult.Success(Unit)
                    "unsupported" -> PlatformResult.Unsupported
                    else -> PlatformResult.Failure("web_chat_attachment_popup_blocked")
                }
            }
            ?: PlatformResult.Unsupported
    }

private suspend fun PlatformFile.downloadWebAttachment(): PlatformResult<Unit> {
    recordWebAttachmentActionEvent("download", "start", displayName)
    val url = reference.safeBrowserChatMediaUrl() ?: return PlatformResult.Unsupported
    return suspendCoroutine { continuation ->
        downloadWebAttachment(url, displayName ?: "quata-attachment") { state, reason ->
            recordWebAttachmentActionEvent("download", state, reason)
            continuation.resume(
                when (state) {
                    "success" -> PlatformResult.Success(Unit)
                    "unsupported" -> PlatformResult.Unsupported
                    else -> PlatformResult.Failure(reason)
                },
            )
        }
    }
}

private suspend fun PlatformFile.shareWebAttachment(shareService: ShareService): PlatformResult<Unit> {
    recordWebAttachmentActionEvent("share", "start", displayName)
    if (reference.startsWith("blob:", ignoreCase = true)) {
        return shareService.share(SharePayload(title = displayName ?: "QÜATA", files = listOf(this))).also {
            recordWebAttachmentActionEvent("share", "blob-result", it.webAttachmentResultName())
        }
    }
    val url = reference.safeBrowserChatMediaUrl() ?: return PlatformResult.Unsupported
    val local = when (val result = materializeWebAttachment(url, displayName, mimeType)) {
        is PlatformResult.Success -> {
            recordWebAttachmentActionEvent("share", "materialized", result.value.displayName)
            result.value
        }
        is PlatformResult.Failure -> {
            recordWebAttachmentActionEvent("share", "materialize-failure", result.reason)
            return result
        }
        PlatformResult.Cancelled -> {
            recordWebAttachmentActionEvent("share", "materialize-cancelled", null)
            return PlatformResult.Cancelled
        }
        PlatformResult.Unsupported -> {
            recordWebAttachmentActionEvent("share", "materialize-unsupported", null)
            return shareService.share(SharePayload(title = displayName ?: "QÜATA", text = url)).also {
                recordWebAttachmentActionEvent("share", "url-result", it.webAttachmentResultName())
            }
        }
    }
    return try {
        shareService.share(SharePayload(title = local.displayName ?: displayName ?: "QÜATA", files = listOf(local))).also {
            recordWebAttachmentActionEvent("share", "file-result", it.webAttachmentResultName())
        }
    } finally {
        revokeWebAttachmentObjectUrl(local.reference)
    }
}

private suspend fun materializeWebAttachment(
    url: String,
    displayName: String?,
    mimeType: String?,
): PlatformResult<PlatformFile> = suspendCoroutine { continuation ->
    materializeWebAttachment(url, displayName ?: "quata-attachment", mimeType) { state, reference, resolvedMimeType, size ->
        continuation.resume(
            when (state) {
                "success" -> reference?.let {
                    PlatformResult.Success(
                        PlatformFile(
                            reference = it,
                            displayName = displayName ?: "quata-attachment",
                            mimeType = resolvedMimeType ?: mimeType,
                            sizeBytes = size.takeIf { value -> value >= 0 }?.toLong(),
                        ),
                    )
                } ?: PlatformResult.Failure("web_chat_attachment_share_blob_missing")
                "unsupported" -> PlatformResult.Unsupported
                else -> PlatformResult.Failure(reference)
            },
        )
    }
}

private fun PlatformResult<Unit>.webAttachmentResultName(): String = when (this) {
    is PlatformResult.Success -> "success"
    is PlatformResult.Failure -> reason ?: "failure"
    PlatformResult.Cancelled -> "cancelled"
    PlatformResult.Unsupported -> "unsupported"
}

private fun openWebExternalLink(url: String) {
    openWebExternalLinkResult(url)
}

private fun openWebMapLink(url: String): ChatMapOpenResult = when (openWebExternalLinkResult(url)) {
    "opened" -> ChatMapOpenResult.Opened
    "unsupported" -> ChatMapOpenResult.Unsupported
    else -> ChatMapOpenResult.Failed
}

private fun openWebExternalLinkResult(url: String): String = js(
    """
    (() => {
      try {
        if (!globalThis.open) return 'unsupported';
        const opened = globalThis.open(url, '_blank', 'noopener,noreferrer');
        return opened == null ? 'failed' : 'opened';
      } catch (_) {
        return 'failed';
      }
    })()
    """,
)

private fun downloadWebAttachment(url: String, name: String, onResult: (String, String?) -> Unit): Unit = js(
    """
    (async () => {
      const document = globalThis.document;
      if (!document?.body || typeof document.createElement !== 'function') {
        onResult('unsupported', null);
        return;
      }
      if (typeof globalThis.fetch !== 'function' || !globalThis.URL?.createObjectURL) {
        onResult('unsupported', null);
        return;
      }
      const response = await globalThis.fetch(url, { credentials: 'omit', cache: 'no-store' });
      if (!response.ok) {
        onResult('failure', `web_chat_attachment_download_http_${'$'}{response.status}`);
        return;
      }
      const blob = await response.blob();
      if (!blob) {
        onResult('failure', 'web_chat_attachment_download_empty');
        return;
      }
      const objectUrl = globalThis.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = (name || 'quata-attachment').replace(/[\\/:*?"<>|]+/g, '_').slice(0, 128);
      anchor.rel = 'noopener noreferrer';
      anchor.style.display = 'none';
      document.body.appendChild(anchor);
      anchor.click();
      globalThis.setTimeout(() => {
        anchor.remove();
        globalThis.URL.revokeObjectURL(objectUrl);
      }, 1000);
      onResult('success', null);
    })().catch((error) => onResult('failure', error?.message ?? error?.name ?? 'web_chat_attachment_download_failed'))
    """,
)

private fun materializeWebAttachment(
    url: String,
    name: String,
    mimeType: String?,
    onResult: (String, String?, String?, Double) -> Unit,
): Unit = js(
    """
    (async () => {
      if (typeof globalThis.fetch !== 'function' || !globalThis.URL?.createObjectURL) {
        onResult('unsupported', null, null, -1);
        return;
      }
      const response = await globalThis.fetch(url, { credentials: 'omit', cache: 'no-store' });
      if (!response.ok) {
        onResult('failure', `web_chat_attachment_share_http_${'$'}{response.status}`, null, -1);
        return;
      }
      const sourceBlob = await response.blob();
      if (!sourceBlob) {
        onResult('failure', 'web_chat_attachment_share_empty', null, -1);
        return;
      }
      const blob = mimeType && sourceBlob.type !== mimeType ? new Blob([sourceBlob], { type: mimeType }) : sourceBlob;
      onResult('success', globalThis.URL.createObjectURL(blob), blob.type || mimeType || null, blob.size ?? -1);
    })().catch((error) => onResult('failure', error?.message ?? error?.name ?? 'web_chat_attachment_share_failed', null, -1))
    """,
)

private fun revokeWebAttachmentObjectUrl(reference: String): Unit = js(
    """
    (() => {
      if (reference?.startsWith?.('blob:')) globalThis.URL?.revokeObjectURL?.(reference);
    })()
    """,
)

private fun recordWebAttachmentActionEvent(action: String, state: String, detail: String?): Unit = js(
    """
    (() => {
      const root = globalThis;
      const events = root.__quataAttachmentActionEvents;
      if (!Array.isArray(events)) return;
      events.push({ action, state, detail: detail ?? null, time: Date.now() });
      if (events.length > 60) events.shift();
    })()
    """,
)
