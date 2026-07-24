package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.AudioRecorderService
import com.quata.core.platform.BrowserAudioRecorderService
import com.quata.core.platform.FilePickerService
import com.quata.feature.chat.presentation.chat.ChatBrowserHostContent

/** Browser adapter: hash navigation and safe URL opening stay at the platform boundary. */
@Composable
fun WebChatHost(
    repository: WebChatRepository,
    audioPlayer: AudioPlayerService,
    audioRecorder: AudioRecorderService? = null,
    filePicker: FilePickerService,
    conversationId: String?,
    navigationMessage: String,
    onOpenConversation: (String) -> Unit,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolvedAudioRecorder = remember(audioRecorder) { audioRecorder ?: BrowserAudioRecorderService() }
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
        filePicker = filePicker,
        conversationId = conversationId,
        navigationMessage = navigationMessage,
        onOpenConversation = onOpenConversation,
        onBackToList = onBackToList,
        onOpenAttachment = { url -> url.safeWebAttachmentUrl()?.let(::openWebExternalLink) },
        modifier = modifier,
    )
}

private fun browserDocumentIsVisible(): Boolean = js(
    "globalThis.document?.visibilityState !== 'hidden'",
)

/** Pauses the repository polling loops on background tabs and unregisters with the host. */
private fun observeBrowserDocumentVisibility(onChanged: (Boolean) -> Unit): () -> Unit = js(
    """
    const document = globalThis.document;
    if (!document?.addEventListener) return () => {};
    const listener = () => onChanged(document.visibilityState !== 'hidden');
    document.addEventListener('visibilitychange', listener);
    return () => document.removeEventListener('visibilitychange', listener);
    """,
)

private fun openWebExternalLink(url: String): Unit = js("globalThis.open(url, '_blank', 'noopener,noreferrer')")

private fun String.safeWebAttachmentUrl(): String? = takeIf {
    startsWith("https://", ignoreCase = true) ||
        startsWith("http://", ignoreCase = true) ||
        startsWith("blob:", ignoreCase = true)
}
