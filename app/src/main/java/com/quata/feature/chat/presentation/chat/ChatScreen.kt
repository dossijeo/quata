package com.quata.feature.chat.presentation.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.AudioRecorderService
import com.quata.core.platform.ClipboardService
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.PlatformFile
import com.quata.feature.chat.domain.ChatRepository

/** Android adapter for the single common Chat root. Android owns only lifecycle and intents. */
@Composable
fun ChatScreen(
    padding: PaddingValues,
    conversationId: String,
    repository: ChatRepository,
    clipboardService: ClipboardService,
    filePickerService: FilePickerService,
    audioPlayerService: AudioPlayerService,
    audioRecorderService: AudioRecorderService,
    onOpenUserProfile: (String) -> Unit = {},
    openingProfileUserId: String? = null,
    onOpenConversation: (String) -> Unit = {},
    focusedMessageId: String? = null,
    onFocusedMessageHandled: () -> Unit = {},
    onOpenMessageConversation: (String, String) -> Unit = { targetConversationId, _ -> onOpenConversation(targetConversationId) },
    onBack: () -> Unit,
    compactHeader: Boolean = false,
    appHeaderActions: (@Composable RowScope.() -> Unit)? = null,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    DisposableEffect(lifecycleOwner, repository, conversationId) {
        val initiallyForeground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        repository.setAppForeground(initiallyForeground)
        repository.setConversationVisible(conversationId, initiallyForeground)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    repository.setAppForeground(true)
                    repository.setConversationVisible(conversationId, true)
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    repository.setConversationVisible(conversationId, false)
                    repository.setAppForeground(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            repository.setConversationVisible(conversationId, false)
        }
    }
    ChatScreenHost(
        repository = repository,
        audioPlayer = audioPlayerService,
        audioRecorder = audioRecorderService,
        filePicker = filePickerService,
        conversationId = conversationId,
        focusedMessageId = focusedMessageId,
        onFocusedMessageHandled = onFocusedMessageHandled,
        navigationMessage = openingProfileUserId.orEmpty(),
        onOpenConversation = onOpenConversation,
        onBackToList = onBack,
        onOpenAttachment = { file -> context.openChatFile(file) },
        onOpenAvatar = onOpenUserProfile,
        onOpenMap = { value -> context.openChatUrl(value) },
        onTranslateMessage = { text ->
            context.openChatUrl("https://translate.google.com/?sl=auto&tl=es&text=${Uri.encode(text)}&op=translate")
        },
        onOpenMessageConversation = onOpenMessageConversation,
        clipboardService = clipboardService,
        conversationListHost = { modifier -> Spacer(modifier) },
        modifier = Modifier.fillMaxSize().padding(padding),
    )
}

private fun android.content.Context.openChatFile(file: PlatformFile) {
    runCatching {
        val uri = Uri.parse(file.reference)
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, file.mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }
}

private fun android.content.Context.openChatUrl(value: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
