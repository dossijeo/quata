package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.quata.core.model.Message
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.CommunityEmojiPanelContent
import com.quata.core.ui.components.QuataConfirmationDialogContent
import com.quata.core.ui.components.communityEmojiSections
import com.quata.core.ui.components.insertAtSelection

/** Common selection action chrome backed by [ChatUiEvent]; confirmation stays in commonMain. */
@Composable
fun ChatSelectedMessageActionsContent(
    message: Message,
    compact: Boolean,
    strings: ChatChromeStrings,
    onCopy: (String) -> Unit,
    onEvent: (ChatUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmation by remember(message.id) { mutableStateOf<ChatConfirmation?>(null) }
    ChatSelectedMessageActionBarContent(
        compact = compact,
        navigationAction = { CompactIconButton(onClick = { onEvent(ChatUiEvent.MessageSelected(null)) }) { CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, strings.closeSelection) } },
        actions = {
            if (!message.isDeleted) {
                CompactIconButton(
                    onClick = { onCopy(message.text); onEvent(ChatUiEvent.MessageSelected(null)) },
                    modifier = Modifier.semantics { testTag = "chat.action.copy" },
                ) { CompactIcon(Icons.Filled.ContentCopy, strings.copyMessage) }
                CompactIconButton(
                    onClick = { onEvent(ChatUiEvent.StartReply) },
                    modifier = Modifier.semantics { testTag = "chat.action.reply" },
                ) { CompactIcon(Icons.AutoMirrored.Filled.Reply, strings.replyMessage) }
                CompactIconButton(
                    onClick = { onEvent(ChatUiEvent.OpenForwardDialog) },
                    modifier = Modifier.semantics { testTag = "chat.action.forward" },
                ) { CompactIcon(Icons.AutoMirrored.Filled.Forward, strings.forwardMessage) }
            }
            if (message.isMine && !message.isDeleted) CompactIconButton(
                onClick = { onEvent(ChatUiEvent.StartEdit) },
                modifier = Modifier.semantics { testTag = "chat.action.edit" },
            ) { CompactIcon(Icons.Filled.Edit, strings.editMessage) }
            if (!message.isMine && !message.isDeleted) CompactIconButton(
                onClick = { confirmation = ChatConfirmation.Report },
                modifier = Modifier.semantics { testTag = "chat.action.report" },
            ) { CompactIcon(Icons.Filled.Flag, strings.reportMessage) }
            if (!message.isDeleted) CompactIconButton(
                onClick = { onEvent(ChatUiEvent.ToggleFavoriteSelected) },
                modifier = Modifier.semantics { testTag = "chat.action.favorite" },
            ) { CompactIcon(if (message.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, strings.favoriteMessage) }
            if (message.isMine && !message.isDeleted) CompactIconButton(
                onClick = { confirmation = ChatConfirmation.Delete },
                modifier = Modifier.semantics { testTag = "chat.action.delete" },
            ) { CompactIcon(Icons.Filled.Delete, strings.deleteMessage) }
        },
        modifier = modifier,
    )
    confirmation?.let { action ->
        QuataConfirmationDialogContent(
            title = if (action == ChatConfirmation.Delete) strings.deleteMessage else strings.reportMessage,
            message = if (action == ChatConfirmation.Delete) strings.deleteMessageConfirm else strings.reportMessageConfirm,
            confirmLabel = strings.confirm, dismissLabel = strings.cancel,
            onDismiss = { confirmation = null },
            onConfirm = {
                onEvent(if (action == ChatConfirmation.Delete) ChatUiEvent.DeleteSelectedMessage else ChatUiEvent.ReportSelectedMessage)
                confirmation = null
            },
        )
    }
}

private enum class ChatConfirmation { Delete, Report }

/** Real shared forward picker: candidate selection and mutation use ChatViewModel events. */
@Composable
fun ChatForwardPickerContent(
    state: ChatUiState,
    strings: ChatChromeStrings,
    onEvent: (ChatUiEvent) -> Unit,
    onQueryChanged: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(ChatUiEvent.CloseForwardDialog) },
        title = { Text(strings.forwardTitle) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.forwardCandidateQuery,
                    onValueChange = onQueryChanged,
                    label = { Text(strings.search) },
                )
                if (state.isForwardCandidateInitialLoading) {
                    Text(strings.searchingPeople)
                } else if (state.forwardConversationCandidates.isEmpty() && state.forwardCandidateError == null) {
                    Text(strings.noPeopleFound)
                }
                state.forwardConversationCandidates.forEach { candidate ->
                    Button(onClick = { onEvent(ChatUiEvent.ForwardProfileToggled(candidate.profileId)) }) {
                        Text(if (candidate.profileId in state.selectedForwardProfileIds) "✓ ${candidate.displayName}" else candidate.displayName)
                    }
                }
                state.forwardCandidateError?.let { Text(it) }
                if (state.forwardCandidateHasMore && !state.isForwardCandidateInitialLoading) {
                    Button(
                        onClick = onLoadMore,
                        enabled = !state.isForwardCandidatePageLoading,
                    ) {
                        Text(if (state.isForwardCandidatePageLoading) strings.loading else strings.loadMore)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onEvent(ChatUiEvent.SendForward) },
                enabled = state.selectedForwardProfileIds.isNotEmpty() && !state.isConversationActionInProgress,
            ) { Text(strings.forwardMessage) }
        },
        dismissButton = { Button(onClick = { onEvent(ChatUiEvent.CloseForwardDialog) }) { Text(strings.cancel) } },
    )
}

/** Shared visual composer; platform services are explicit callbacks, never product UI replacements. */
@Composable
fun ChatComposerContent(
    state: ChatUiState,
    strings: ChatChromeStrings,
    onEvent: (ChatUiEvent) -> Unit,
    onPickDocument: () -> Unit,
    onPickGallery: () -> Unit,
    onOpenPendingAttachment: () -> Unit,
    onClearAttachment: () -> Unit,
    onCamera: (() -> Unit)?,
    onRecordAudio: (() -> Unit)?,
    isRecordingAudio: Boolean = false,
    recordingElapsedLabel: String? = null,
    recordingError: String? = null,
    onStopRecording: (() -> Unit)? = null,
    onCancelRecording: (() -> Unit)? = null,
    attachmentError: String? = null,
    modifier: Modifier = Modifier,
    messageInputOverride: (@Composable (
        String,
        (String) -> Unit,
        Modifier,
        @Composable () -> Unit,
        @Composable () -> Unit,
    ) -> Unit)? = null,
    sendButtonOverride: (@Composable (Boolean, () -> Unit, Modifier) -> Unit)? = null,
) {
    var emojiVisible by remember { mutableStateOf(false) }
    var attachmentsVisible by remember { mutableStateOf(false) }
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(state.messageText, TextRange(state.messageText.length)))
    }
    LaunchedEffect(state.messageText) {
        if (fieldValue.text != state.messageText) {
            fieldValue = TextFieldValue(state.messageText, TextRange(state.messageText.length))
        }
    }
    Column(modifier.fillMaxWidth()) {
        state.editingMessage?.let { ChatComposerModeBannerContent(strings.editingMessage, onClear = { onEvent(ChatUiEvent.CancelEdit) }) }
        state.replyToMessage?.let { ChatComposerModeBannerContent(strings.replyingTo(it.senderName), onClear = { onEvent(ChatUiEvent.ClearReply) }) }
        state.attachmentName?.let { name ->
            ChatPendingAttachmentOverlayContent(
                name = name,
                surfaceColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                textColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                onOpen = onOpenPendingAttachment,
                preview = { Text(strings.attachmentReady) },
                clearAction = { clearModifier -> CompactIconButton(onClick = onClearAttachment, modifier = clearModifier) { CompactIcon(Icons.Filled.Delete, strings.removeAttachment) } },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
        if (isRecordingAudio) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(strings.recording(recordingElapsedLabel.orEmpty()))
                androidx.compose.foundation.layout.Row {
                    onStopRecording?.let { stop ->
                        Button(onClick = stop) {
                            CompactIcon(Icons.Filled.Stop, strings.stopRecording)
                            Text(strings.stopAndAttach)
                        }
                    }
                    onCancelRecording?.let { cancel ->
                        Button(onClick = cancel) { Text(strings.cancel) }
                    }
                }
            }
        }
        recordingError?.let {
            Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp))
        }
        if (emojiVisible) CommunityEmojiPanelContent(
            sections = communityEmojiSections(),
            onEmojiClick = { emoji ->
                fieldValue = fieldValue.insertAtSelection(emoji)
                onEvent(ChatUiEvent.MessageChanged(fieldValue.text))
            },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        if (attachmentsVisible) ChatAttachmentQuickPanelContent(
            strings = ChatAttachmentQuickPanelStrings(strings.chooseFile, strings.chooseGallery),
            onPickFile = { attachmentsVisible = false; onPickDocument() },
            onPickGallery = { attachmentsVisible = false; onPickGallery() },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        attachmentError?.let {
            Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp))
        }
        val cameraAction: (@Composable (Modifier) -> Unit)? = if (onCamera != null) {
            { cameraModifier ->
                CompactIconButton(onClick = onCamera, modifier = cameraModifier) {
                    CompactIcon(Icons.Filled.PhotoCamera, strings.openCamera)
                }
            }
        } else null
        val primaryAction: (@Composable () -> Unit)? = when {
            state.messageText.isNotBlank() || state.attachmentUri != null -> {
                {
                    CompactIconButton(onClick = { onEvent(ChatUiEvent.Send) }) {
                        CompactIcon(Icons.AutoMirrored.Filled.Send, strings.send)
                    }
                }
            }
            !isRecordingAudio && onRecordAudio != null -> {
                {
                    CompactIconButton(onClick = onRecordAudio) {
                        CompactIcon(Icons.Filled.Mic, strings.recordAudio)
                    }
                }
            }
            else -> null
        }
        ChatComposerInputRowContent(
            textInput = { inputModifier ->
                val leadingIcon: @Composable () -> Unit = {
                    CompactIconButton(onClick = { emojiVisible = !emojiVisible; attachmentsVisible = false }) {
                        CompactIcon(Icons.Filled.InsertEmoticon, strings.emoji)
                    }
                }
                val trailingIcon: @Composable () -> Unit = {
                    CompactIconButton(onClick = { attachmentsVisible = !attachmentsVisible; emojiVisible = false }) {
                        CompactIcon(Icons.Filled.AttachFile, strings.attach)
                    }
                }
                messageInputOverride?.invoke(
                    fieldValue.text,
                    { value ->
                        fieldValue = TextFieldValue(value, TextRange(value.length))
                        onEvent(ChatUiEvent.MessageChanged(value))
                    },
                    inputModifier,
                    leadingIcon,
                    trailingIcon,
                ) ?: OutlinedTextField(
                    value = fieldValue,
                    onValueChange = {
                        fieldValue = it
                        onEvent(ChatUiEvent.MessageChanged(it.text))
                    },
                    placeholder = { Text(strings.message) },
                    modifier = inputModifier,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                )
            },
            cameraAction = cameraAction,
            primaryAction = if (state.messageText.isNotBlank() || state.attachmentUri != null) {
                sendButtonOverride?.let { sendButton ->
                    {
                        sendButton(
                            true,
                            { onEvent(ChatUiEvent.Send) },
                            Modifier,
                        )
                    }
                } ?: primaryAction
            } else {
                primaryAction
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
