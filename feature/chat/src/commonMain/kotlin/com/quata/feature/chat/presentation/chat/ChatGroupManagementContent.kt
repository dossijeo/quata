package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.model.Conversation
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.QuataConfirmationDialogContent

/** Shared group/chat menu and member panel. All writes dispatch real [ChatUiEvent]s. */
@Composable
fun ChatGroupManagementContent(
    conversation: Conversation?, state: ChatUiState, onEvent: (ChatUiEvent) -> Unit,
    onOpenProfile: (String) -> Unit, onBack: () -> Unit, trailing: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    var menu by remember { mutableStateOf(false) }; var expanded by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<ChatUiEvent?>(null) }
    ChatConversationTitleBarContent(
        title = conversation?.title?.ifBlank { "Conversación" } ?: "Conversación",
        subtitle = if (conversation?.isGroup == true) "${conversation.participantIds.size} miembros" else null,
        expandable = conversation?.isGroup == true, onToggleExpanded = { expanded = !expanded }, compact = false,
        navigationAction = { CompactIconButton(onClick = onBack) { } }, avatar = {},
        trailingActions = {
            trailing(); CompactIconButton(onClick = { menu = true }) { CompactIcon(Icons.Filled.MoreVert, "Opciones") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (conversation?.isMuted == true) "Reactivar avisos" else "Silenciar") }, leadingIcon = { CompactIcon(if (conversation?.isMuted == true) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, null) }, onClick = { menu=false; onEvent(ChatUiEvent.ConversationMutedChanged(conversation?.isMuted != true)) })
                DropdownMenuItem(text = { Text("Permitir invitaciones") }, onClick = { menu=false; onEvent(ChatUiEvent.MemberInvitesChanged(conversation?.canMembersInvite != true)) })
                DropdownMenuItem(text = { Text("Añadir participantes") }, leadingIcon = { CompactIcon(Icons.Filled.PersonAdd, null) }, onClick = { menu=false; onEvent(ChatUiEvent.OpenAddParticipants) })
                DropdownMenuItem(text = { Text("Salir") }, onClick = { menu=false; confirm=ChatUiEvent.LeaveConversation })
                DropdownMenuItem(text = { Text("Ocultar conversación") }, leadingIcon = { CompactIcon(Icons.Filled.Delete,null) }, onClick = { menu=false; confirm=ChatUiEvent.HideConversation })
            }
        }
    )
    if (expanded && conversation != null) Column(Modifier.fillMaxWidth().padding(12.dp)) {
        conversation.participantIds.forEach { id -> Text(id, Modifier.fillMaxWidth().clickable { onOpenProfile(id) }.padding(8.dp)) }
    }
    if (state.isAddParticipantsOpen) ChatParticipantsPickerContent(state, onEvent, onOpenProfile)
    confirm?.let { action -> QuataConfirmationDialogContent("Confirmar", "¿Quieres continuar?", "Confirmar", "Cancelar", onConfirm = { onEvent(action); confirm=null }, onDismiss = { confirm=null }) }
}

@Composable private fun ChatParticipantsPickerContent(state: ChatUiState, onEvent: (ChatUiEvent)->Unit, onOpenProfile:(String)->Unit) {
    androidx.compose.material3.AlertDialog(onDismissRequest={onEvent(ChatUiEvent.CloseAddParticipants)}, title={Text("Añadir participantes")}, text={ Column {
        androidx.compose.material3.OutlinedTextField(state.participantCandidateQuery, { onEvent(ChatUiEvent.ParticipantSearchChanged(it)) }, label={Text("Buscar")})
        state.participantConversationCandidates.forEach { c -> Text(c.displayName, Modifier.clickable { onOpenProfile(c.profileId) }.padding(8.dp)) }
    }}, confirmButton={ androidx.compose.material3.Button(onClick={onEvent(ChatUiEvent.AddSelectedParticipants)}) { Text("Añadir") } }, dismissButton={ androidx.compose.material3.Button(onClick={onEvent(ChatUiEvent.CloseAddParticipants)}) { Text("Cancelar") } })
}
