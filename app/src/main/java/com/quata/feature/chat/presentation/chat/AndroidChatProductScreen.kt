package com.quata.feature.chat.presentation.chat

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import com.quata.core.designsystem.theme.QuataResolvedTheme
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.localization.QuataLanguageManager
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.AudioRecorderService
import com.quata.core.platform.CameraCaptureService
import com.quata.core.platform.ClipboardService
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.PlatformFile
import com.quata.core.translation.QuataCachedTranslator
import com.quata.core.ui.components.AttachmentFullscreenMediaContent
import com.quata.core.ui.components.AttachmentPreview
import com.quata.core.ui.components.AttachmentThumbnail
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.openAttachmentWithDocumentReaderOrChooser
import com.quata.feature.chat.domain.ChatRepository
import kotlinx.coroutines.launch

/** Android system adapters for the same [ChatProductHostContent] mounted by Wasm and iOS. */
@Composable
fun AndroidChatProductScreen(
    padding: PaddingValues,
    conversationId: String,
    repository: ChatRepository,
    clipboardService: ClipboardService,
    filePickerService: FilePickerService,
    cameraCaptureService: CameraCaptureService,
    audioRecorderService: AudioRecorderService,
    audioPlayerService: AudioPlayerService,
    onOpenUserProfile: (String) -> Unit,
    openingProfileUserId: String?,
    onOpenConversation: (String) -> Unit,
    focusedMessageId: String?,
    onFocusedMessageHandled: () -> Unit,
    onOpenMessageConversation: (String, String) -> Unit,
    onBack: () -> Unit,
    compactHeader: Boolean,
    appHeaderActions: (@Composable RowScope.() -> Unit)? = null,
    viewModel: ChatAndroidViewModel = viewModel(
        key = "chat_$conversationId",
        factory = ChatAndroidViewModel.factory(
            conversationId = conversationId,
            repository = repository,
            context = androidx.compose.ui.platform.LocalContext.current,
        ),
    ),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val languageTag = QuataLanguageManager.currentLanguage.tag
    val template = quataTheme()
    val attachmentFallbackName = stringResource(R.string.common_file)
    val translationGateway = remember(context) {
        FangChatTranslationGateway(QuataCachedTranslator.get(context))
    }
    var focusedMessageVisible by remember(conversationId) { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().padding(padding)) {
        ChatProductHostContent(
            repository = repository,
            audioPlayer = audioPlayerService,
            audioRecorder = audioRecorderService,
            filePicker = filePickerService,
            capturePhoto = { cameraCaptureService.capturePhoto() },
            conversationId = conversationId,
            navigationMessage = "",
            onOpenConversation = onOpenConversation,
            onOpenMessageConversation = onOpenMessageConversation,
            onBackToList = onBack,
            onOpenAttachment = { file ->
                context.openAttachmentWithDocumentReaderOrChooser(
                    attachment = file.toAttachmentPreview(attachmentFallbackName),
                    isDarkMode = template.resolvedTheme != QuataResolvedTheme.Light,
                )
            },
            onOpenExternalLink = context::openSafeChatExternalLink,
            onOpenUserProfile = onOpenUserProfile,
            openingProfileUserId = openingProfileUserId,
            onCopyMessage = { value -> scope.launch { clipboardService.writeText(value) } },
            remoteConversationAvatar = { presentation, avatarModifier ->
                AvatarImage(
                    name = presentation.name,
                    avatarUrl = presentation.avatarUrl,
                    profileId = presentation.profileId,
                    modifier = avatarModifier,
                )
            },
            mediaSlots = ChatMediaPlatformSlots(
                preview = { file, _, mediaModifier ->
                    AttachmentThumbnail(file.toAttachmentPreview(attachmentFallbackName), mediaModifier)
                },
                viewer = { file, _, mediaModifier ->
                    AttachmentFullscreenMediaContent(file.toAttachmentPreview(attachmentFallbackName), mediaModifier)
                },
            ),
            translationGateway = translationGateway,
            translatorStrings = chatTranslatorStringsForLanguage(languageTag),
            translationDirection = chatTranslationDirectionForLanguage(languageTag),
            languageTag = languageTag,
            conversationList = {},
            text = context::androidChatText,
            focusedMessageId = focusedMessageId,
            onFocusedMessageVisible = { messageId -> focusedMessageVisible = messageId },
            onFocusedMessageHandled = {
                focusedMessageVisible = null
                onFocusedMessageHandled()
            },
            modifier = Modifier.fillMaxSize(),
            audioRecordingConfiguration = ChatAudioRecordingConfiguration(mimeType = "audio/mp4"),
            conversationModel = viewModel.commonModel,
            compactHeader = compactHeader,
            trailingActions = { appHeaderActions?.invoke(this) },
        )
        focusedMessageVisible?.let { messageId ->
            val focusedMessageVisibleTag = "chat.focused-message.visible.$messageId"
            Box(
                Modifier
                    .size(1.dp)
                    .semantics {
                        testTag = focusedMessageVisibleTag
                        contentDescription = focusedMessageVisibleTag
                        selected = true
                    },
            )
        }
    }
}

private fun PlatformFile.toAttachmentPreview(fallbackName: String): AttachmentPreview = AttachmentPreview(
    name = displayName?.takeIf(String::isNotBlank) ?: fallbackName,
    uri = reference,
    mimeType = mimeType,
)

private fun Context.openSafeChatExternalLink(value: String) {
    val uri = runCatching { value.toUri() }.getOrNull() ?: return
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}
