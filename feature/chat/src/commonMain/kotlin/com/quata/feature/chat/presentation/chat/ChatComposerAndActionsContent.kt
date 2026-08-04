package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.model.Message
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.CommunityEmojiPanelContent
import com.quata.core.ui.components.QuataConfirmationDialogContent
import com.quata.core.ui.components.communityEmojiSections

/** Common selection action chrome backed by [ChatUiEvent]; confirmation stays in commonMain. */
@Composable
fun ChatSelectedMessageActionsContent(
    message: Message,
    compact: Boolean,
    onCopy: (String) -> Unit,
    onEvent: (ChatUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmation by remember(message.id) { mutableStateOf<ChatConfirmation?>(null) }
    ChatSelectedMessageActionBarContent(
        compact = compact,
        navigationAction = { CompactIconButton(onClick = { onEvent(ChatUiEvent.MessageSelected(null)) }) { CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar selección") } },
        actions = {
            CompactIconButton(onClick = { onCopy(message.text); onEvent(ChatUiEvent.MessageSelected(null)) }) { CompactIcon(Icons.Filled.ContentCopy, "Copiar") }
            CompactIconButton(onClick = { onEvent(ChatUiEvent.StartReply) }) { CompactIcon(Icons.AutoMirrored.Filled.Reply, "Responder") }
            CompactIconButton(onClick = { onEvent(ChatUiEvent.OpenForwardDialog) }) { CompactIcon(Icons.AutoMirrored.Filled.Forward, "Reenviar") }
            if (message.isMine && !message.isDeleted) CompactIconButton(onClick = { onEvent(ChatUiEvent.StartEdit) }) { CompactIcon(Icons.Filled.Edit, "Editar") }
            if (!message.isMine && !message.isDeleted) CompactIconButton(onClick = { confirmation = ChatConfirmation.Report }) { CompactIcon(Icons.Filled.Flag, "Reportar") }
            CompactIconButton(onClick = { onEvent(ChatUiEvent.ToggleFavoriteSelected) }) { CompactIcon(if (message.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, "Favorito") }
            if (message.isMine && !message.isDeleted) CompactIconButton(onClick = { confirmation = ChatConfirmation.Delete }) { CompactIcon(Icons.Filled.Delete, "Borrar") }
        },
        modifier = modifier,
    )
    confirmation?.let { action ->
        QuataConfirmationDialogContent(
            title = if (action == ChatConfirmation.Delete) "Eliminar mensaje" else "Reportar mensaje",
            message = if (action == ChatConfirmation.Delete) "Esta acción eliminará el mensaje." else "¿Quieres reportar este mensaje?",
            confirmLabel = "Confirmar", dismissLabel = "Cancelar",
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
    onEvent: (ChatUiEvent) -> Unit,
    onQueryChanged: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(ChatUiEvent.CloseForwardDialog) },
        title = { Text("Reenviar mensaje") },
        text = {
            Column {
                OutlinedTextField(
                    value = state.forwardCandidateQuery,
                    onValueChange = onQueryChanged,
                    label = { Text("Buscar") },
                )
                state.forwardConversationCandidates.forEach { candidate ->
                    Button(onClick = { onEvent(ChatUiEvent.ForwardProfileToggled(candidate.profileId)) }) {
                        Text(if (candidate.profileId in state.selectedForwardProfileIds) "✓ ${candidate.displayName}" else candidate.displayName)
                    }
                }
                state.forwardCandidateError?.let { Text(it) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onEvent(ChatUiEvent.SendForward) },
                enabled = state.selectedForwardProfileIds.isNotEmpty() && !state.isConversationActionInProgress,
            ) { Text("Reenviar") }
        },
        dismissButton = { Button(onClick = { onEvent(ChatUiEvent.CloseForwardDialog) }) { Text("Cancelar") } },
    )
}

/** Shared visual composer; platform services are explicit callbacks, never product UI replacements. */
@Composable
fun ChatComposerContent(
    state: ChatUiState,
    onEvent: (ChatUiEvent) -> Unit,
    onPickDocument: () -> Unit,
    onPickGallery: () -> Unit,
    onOpenPendingAttachment: () -> Unit,
    onCamera: (() -> Unit)?,
    onRecordAudio: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var emojiVisible by remember { mutableStateOf(false) }
    var attachmentsVisible by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        state.editingMessage?.let { ChatComposerModeBannerContent("Editando mensaje", onClear = { onEvent(ChatUiEvent.CancelEdit) }) }
        state.replyToMessage?.let { ChatComposerModeBannerContent("Respondiendo a ${it.senderName}", onClear = { onEvent(ChatUiEvent.ClearReply) }) }
        state.attachmentName?.let { name ->
            ChatPendingAttachmentOverlayContent(
                name = name,
                surfaceColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                textColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                onOpen = onOpenPendingAttachment,
                preview = { Text("Adjunto preparado") },
                clearAction = { clearModifier -> CompactIconButton(onClick = { onEvent(ChatUiEvent.ClearAttachment) }, modifier = clearModifier) { CompactIcon(Icons.Filled.Delete, "Quitar adjunto") } },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
        if (emojiVisible) CommunityEmojiPanelContent(
            sections = communityEmojiSections(),
            onEmojiClick = { onEvent(ChatUiEvent.MessageChanged(state.messageText + it)) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        if (attachmentsVisible) ChatAttachmentQuickPanelContent(
            strings = ChatAttachmentQuickPanelStrings("Archivo", "Galería"),
            onPickFile = { attachmentsVisible = false; onPickDocument() },
            onPickGallery = { attachmentsVisible = false; onPickGallery() },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        ChatComposerInputRowContent(
            textInput = { inputModifier -> OutlinedTextField(
                value = state.messageText, onValueChange = { onEvent(ChatUiEvent.MessageChanged(it)) },
                placeholder = { Text("Mensaje") }, modifier = inputModifier,
                leadingIcon = { CompactIconButton(onClick = { emojiVisible = !emojiVisible; attachmentsVisible = false }) { CompactIcon(Icons.Filled.InsertEmoticon, "Emoji") } },
                trailingIcon = { CompactIconButton(onClick = { attachmentsVisible = !attachmentsVisible; emojiVisible = false }) { CompactIcon(Icons.Filled.AttachFile, "Adjuntar") } },
            ) },
            cameraAction = { cameraModifier ->
                if (onCamera != null) CompactIconButton(onClick = onCamera, modifier = cameraModifier) { CompactIcon(Icons.Filled.PhotoCamera, "Cámara") }
            },
            primaryAction = {
                val send = state.messageText.isNotBlank() || state.attachmentUri != null
                CompactIconButton(onClick = { if (send) onEvent(ChatUiEvent.Send) else onRecordAudio?.invoke() }) {
                    CompactIcon(if (send) Icons.Filled.Send else Icons.Filled.Mic, if (send) "Enviar" else "Grabar audio")
                }
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
