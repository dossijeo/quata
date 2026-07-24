package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.FilePickerService
import com.quata.feature.chat.presentation.chat.ChatBrowserHostContent

/** Browser adapter: hash navigation and safe URL opening stay at the platform boundary. */
@Composable
fun WebChatHost(
    repository: WebChatRepository,
    audioPlayer: AudioPlayerService,
    filePicker: FilePickerService,
    conversationId: String?,
    navigationMessage: String,
    onOpenConversation: (String) -> Unit,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier,
) = ChatBrowserHostContent(
    repository = repository,
    audioPlayer = audioPlayer,
    filePicker = filePicker,
    conversationId = conversationId,
    navigationMessage = navigationMessage,
    onOpenConversation = onOpenConversation,
    onBackToList = onBackToList,
    onOpenAttachment = { url -> url.safeWebAttachmentUrl()?.let(::openWebExternalLink) },
    modifier = modifier,
)

private fun openWebExternalLink(url: String): Unit = js("globalThis.open(url, '_blank', 'noopener,noreferrer')")

private fun String.safeWebAttachmentUrl(): String? = takeIf {
    startsWith("https://", ignoreCase = true) ||
        startsWith("http://", ignoreCase = true) ||
        startsWith("blob:", ignoreCase = true)
}
