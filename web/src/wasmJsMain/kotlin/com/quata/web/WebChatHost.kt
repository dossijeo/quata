@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
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
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.chat.ChatBrowserHostContent
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
    navigationMessage: String,
    onOpenConversation: (String) -> Unit,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolvedAudioRecorder = remember(audioRecorder) { audioRecorder ?: BrowserAudioRecorderService() }
    val resolvedRecordingReferences = remember(audioRecorder, audioRecordingReferences) {
        audioRecordingReferences ?: (resolvedAudioRecorder as? AudioRecordingReferenceReleaser)
    }
    val scope = rememberCoroutineScope()
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
        messageInputOverride = { value, onChange, modifier -> WebNativeInput(value, onChange, "Mensaje", modifier.height(56.dp), inputType = "text") },
        sendButtonOverride = { enabled, onClick, modifier -> WebNativeButton("Enviar", enabled, onClick, modifier.height(48.dp)) },
        modifier = modifier,
    )
}

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
        else -> reference.safeWebAttachmentUrl()?.let(::openWebExternalLink)
    }
}

private fun openWebExternalLink(url: String): Unit = js("globalThis.open(url, '_blank', 'noopener,noreferrer')")

private fun String.safeWebAttachmentUrl(): String? = takeIf {
    startsWith("https://", ignoreCase = true) ||
        startsWith("http://", ignoreCase = true) ||
        startsWith("blob:", ignoreCase = true)
}
