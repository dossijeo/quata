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
import com.quata.feature.chat.presentation.chat.ChatDocumentAttachmentActions
import com.quata.feature.chat.presentation.chat.ChatMediaAttachmentActions
import com.quata.feature.chat.presentation.chat.ChatComposerActionCallbacks
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
        documentAttachmentActionsHost = { actions ->
            WebChatDocumentAttachmentE2eBridge(actions)
        },
        mediaAttachmentActionsHost = { actions ->
            WebChatMediaAttachmentE2eBridge(actions)
        },
        composerActionsHost = { actions ->
            WebChatComposerActionsE2eBridge(actions)
        },
        modifier = modifier,
    )
}

@Composable
private fun WebChatComposerActionsE2eBridge(actions: ChatComposerActionCallbacks) {
    DisposableEffect(
        actions.recordAudio,
        actions.stopRecording,
        actions.cancelRecording,
        actions.send,
        actions.messageText,
        actions.hasPendingAttachment,
    ) {
        val dispose = installWebChatComposerActionsE2eBridge(
            recordAudio = actions.recordAudio,
            stopRecording = actions.stopRecording,
            cancelRecording = actions.cancelRecording,
            send = actions.send,
            messageText = actions.messageText,
            hasPendingAttachment = actions.hasPendingAttachment,
        )
        onDispose(dispose)
    }
}

@Composable
private fun WebChatDocumentAttachmentE2eBridge(actions: ChatDocumentAttachmentActions) {
    DisposableEffect(actions.file.reference, actions.file.displayName, actions.file.mimeType, actions.open, actions.download, actions.share) {
        val dispose = installWebChatDocumentAttachmentE2eBridge(
            reference = actions.file.reference,
            name = actions.file.displayName.orEmpty(),
            mimeType = actions.file.mimeType.orEmpty(),
            open = actions.open,
            download = actions.download,
            share = actions.share,
        )
        onDispose(dispose)
    }
}

@Composable
private fun WebChatMediaAttachmentE2eBridge(actions: ChatMediaAttachmentActions) {
    DisposableEffect(actions.file.reference, actions.file.displayName, actions.file.mimeType, actions.kind, actions.open) {
        val dispose = installWebChatMediaAttachmentE2eBridge(
            reference = actions.file.reference,
            name = actions.file.displayName.orEmpty(),
            mimeType = actions.file.mimeType.orEmpty(),
            kind = actions.kind.name,
            open = actions.open,
        )
        onDispose(dispose)
    }
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

@JsFun(
    """(recordAudio, stopRecording, cancelRecording, send, messageText, hasPendingAttachment) => {
      const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
      const params = new URLSearchParams(location?.search || '');
      const optedIn = params.get('quata-chat-composer-e2e') === '1' ||
        globalThis.sessionStorage?.getItem('quata.chat_composer.e2e') === '1';
      if (!local || !optedIn) return () => {};
      const bridge = Object.freeze({
        version: 1,
        available: () => ({
          recordAudio: typeof recordAudio === 'function',
          stopRecording: typeof stopRecording === 'function',
          cancelRecording: typeof cancelRecording === 'function',
          send: typeof send === 'function',
          messageText: String(messageText || ''),
          pendingAttachment: Boolean(hasPendingAttachment),
        }),
        recordAudio: () => {
          if (typeof recordAudio !== 'function') throw Error('chat_composer_record_audio_unavailable');
          recordAudio();
          return { action: 'chat.composer.recordAudio' };
        },
        stopRecording: () => {
          if (typeof stopRecording !== 'function') throw Error('chat_composer_recording_stop_unavailable');
          stopRecording();
          return { action: 'chat.composer.recording.stop' };
        },
        cancelRecording: () => {
          if (typeof cancelRecording !== 'function') throw Error('chat_composer_recording_cancel_unavailable');
          cancelRecording();
          return { action: 'chat.composer.recording.cancel' };
        },
        send: () => {
          if (typeof send !== 'function') throw Error('chat_composer_send_unavailable');
          send();
          return { action: 'chat.composer.send' };
        },
      });
      globalThis.__quataChatComposerE2eProduct = bridge;
      globalThis.document?.documentElement?.setAttribute('data-quata-chat-composer-e2e', 'ready');
      return () => {
        if (globalThis.__quataChatComposerE2eProduct === bridge) {
          delete globalThis.__quataChatComposerE2eProduct;
          globalThis.document?.documentElement?.removeAttribute('data-quata-chat-composer-e2e');
        }
      };
    }""",
)
private external fun installWebChatComposerActionsE2eBridge(
    recordAudio: (() -> Unit)?,
    stopRecording: (() -> Unit)?,
    cancelRecording: (() -> Unit)?,
    send: (() -> Unit)?,
    messageText: String,
    hasPendingAttachment: Boolean,
): () -> Unit

@JsFun(
    """(reference, name, mimeType, open, download, share) => {
      const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
      const params = new URLSearchParams(location?.search || '');
      const optedIn = params.get('quata-chat-document-attachment-e2e') === '1' ||
        globalThis.sessionStorage?.getItem('quata.chat_document_attachment.e2e') === '1';
      if (!local || !optedIn) return () => {};
      const store = globalThis.__quataChatDocumentAttachmentE2eActions || new Map();
      globalThis.__quataChatDocumentAttachmentE2eActions = store;
      const key = String(reference ?? '') + '\n' + String(name ?? '') + '\n' + String(mimeType ?? '');
      const entry = Object.freeze({ reference, name, mimeType, open, download, share });
      store.set(key, entry);
      const find = (needle) => {
        const entries = Array.from(store.values());
        if (entries.length === 0) return null;
        const query = String(needle ?? '').trim().toLowerCase();
        if (!query) return entries.at(-1);
        return entries.find((candidate) =>
          String(candidate.name ?? '').toLowerCase().includes(query) ||
          String(candidate.reference ?? '').toLowerCase().includes(query)
        ) || entries.at(-1);
      };
      const bridge = Object.freeze({
        version: 1,
        list: () => Array.from(store.values()).map((candidate) => ({
          name: candidate.name,
          mimeType: candidate.mimeType,
          referenceSuffix: String(candidate.reference ?? '').slice(-48),
        })),
        open: (needle) => {
          const target = find(needle);
          if (!target) throw Error('chat_document_attachment_bridge_target_missing');
          target.open();
          return { action: 'open', name: target.name };
        },
        download: (needle) => {
          const target = find(needle);
          if (!target) throw Error('chat_document_attachment_bridge_target_missing');
          target.download();
          return { action: 'download', name: target.name };
        },
        share: (needle) => {
          const target = find(needle);
          if (!target) throw Error('chat_document_attachment_bridge_target_missing');
          target.share();
          return { action: 'share', name: target.name };
        },
      });
      globalThis.__quataChatDocumentAttachmentE2eProduct = bridge;
      globalThis.document?.documentElement?.setAttribute('data-quata-chat-document-attachment-e2e', 'ready');
      return () => {
        store.delete(key);
        if (store.size === 0 && globalThis.__quataChatDocumentAttachmentE2eProduct === bridge) {
          delete globalThis.__quataChatDocumentAttachmentE2eProduct;
          delete globalThis.__quataChatDocumentAttachmentE2eActions;
          globalThis.document?.documentElement?.removeAttribute('data-quata-chat-document-attachment-e2e');
        }
      };
    }""",
)
private external fun installWebChatDocumentAttachmentE2eBridge(
    reference: String,
    name: String,
    mimeType: String,
    open: () -> Unit,
    download: () -> Unit,
    share: () -> Unit,
): () -> Unit

@JsFun(
    """(reference, name, mimeType, kind, open) => {
      const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
      const params = new URLSearchParams(location?.search || '');
      const optedIn = params.get('quata-chat-media-attachment-e2e') === '1' ||
        globalThis.sessionStorage?.getItem('quata.chat_media_attachment.e2e') === '1';
      if (!local || !optedIn) return () => {};
      const store = globalThis.__quataChatMediaAttachmentE2eActions || new Map();
      globalThis.__quataChatMediaAttachmentE2eActions = store;
      const key = String(reference ?? '') + '\n' + String(name ?? '') + '\n' + String(mimeType ?? '') + '\n' + String(kind ?? '');
      const entry = Object.freeze({ reference, name, mimeType, kind, open });
      store.set(key, entry);
      const find = (needle, expectedKind) => {
        const entries = Array.from(store.values());
        if (entries.length === 0) return null;
        const query = String(needle ?? '').trim().toLowerCase();
        const normalizedKind = String(expectedKind ?? '').trim().toLowerCase();
        const sameKind = (candidate) => !normalizedKind || String(candidate.kind ?? '').toLowerCase() === normalizedKind;
        const candidates = entries.filter(sameKind);
        const scoped = candidates.length ? candidates : entries;
        if (!query) return scoped.at(-1);
        return scoped.find((candidate) =>
          String(candidate.name ?? '').toLowerCase().includes(query) ||
          String(candidate.reference ?? '').toLowerCase().includes(query)
        ) || scoped.at(-1);
      };
      const bridge = Object.freeze({
        version: 1,
        list: () => Array.from(store.values()).map((candidate) => ({
          name: candidate.name,
          mimeType: candidate.mimeType,
          kind: candidate.kind,
          referenceSuffix: String(candidate.reference ?? '').slice(-48),
        })),
        open: (needle, expectedKind) => {
          const target = find(needle, expectedKind);
          if (!target) throw Error('chat_media_attachment_bridge_target_missing');
          target.open();
          return { action: 'open', name: target.name, kind: target.kind };
        },
      });
      globalThis.__quataChatMediaAttachmentE2eProduct = bridge;
      globalThis.document?.documentElement?.setAttribute('data-quata-chat-media-attachment-e2e', 'ready');
      return () => {
        store.delete(key);
        if (store.size === 0 && globalThis.__quataChatMediaAttachmentE2eProduct === bridge) {
          delete globalThis.__quataChatMediaAttachmentE2eProduct;
          delete globalThis.__quataChatMediaAttachmentE2eActions;
          globalThis.document?.documentElement?.removeAttribute('data-quata-chat-media-attachment-e2e');
        }
      };
    }""",
)
private external fun installWebChatMediaAttachmentE2eBridge(
    reference: String,
    name: String,
    mimeType: String,
    kind: String,
    open: () -> Unit,
): () -> Unit

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
