package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.model.Conversation
import com.quata.core.model.User
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.QuataConfirmationDialogContent

data class ChatMemberPresentation(val id:String,val name:String,val avatarUrl:String?,val isModerator:Boolean,val isSelf:Boolean)
fun chatMemberPresentations(conversation: Conversation?, currentUser: User?): List<ChatMemberPresentation> = conversation?.participantIds?.mapIndexed { i,id -> ChatMemberPresentation(id, conversation.participantNames.getOrNull(i) ?: id, conversation.participantAvatarUrls.getOrNull(i), id in conversation.moderatorIds, id==currentUser?.id) }.orEmpty()
fun canManageChatMembers(conversation:Conversation?, currentUser:User?) = currentUser?.id in conversation?.moderatorIds.orEmpty()
fun canInviteToChat(conversation:Conversation?, currentUser:User?) = canManageChatMembers(conversation,currentUser) || conversation?.canMembersInvite == true

@Composable fun ChatGroupManagementContent(conversation:Conversation?, state:ChatUiState, navigationAction:@Composable () -> Unit, conversationAvatar:@Composable () -> Unit, subtitle:String?, compact:Boolean, trailing:@Composable RowScope.() -> Unit, onOpenProfile:(String)->Unit, onEvent:(ChatUiEvent)->Unit) {
 var menu by remember{mutableStateOf(false)}; var expanded by remember{mutableStateOf(false)}; var confirm by remember{mutableStateOf<ChatUiEvent?>(null)}
 val moderator=canManageChatMembers(conversation,state.currentUser); val canInvite=canInviteToChat(conversation,state.currentUser)
 ChatConversationTitleBarContent(conversation?.title?.ifBlank{"Conversación"}?:"Conversación", subtitle, conversation?.isGroup==true, compact,{expanded=!expanded}, navigationAction, conversationAvatar, {
  trailing(); CompactIconButton({menu=true}){CompactIcon(Icons.Filled.MoreVert,"Opciones")}; DropdownMenu(menu,{menu=false}){
   DropdownMenuItem({Text(if(conversation?.isMuted==true)"Reactivar avisos" else "Silenciar")},{CompactIcon(if(conversation?.isMuted==true)Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,null)},{menu=false;onEvent(ChatUiEvent.ConversationMutedChanged(conversation?.isMuted!=true))})
   if(conversation?.isGroup==true){ DropdownMenuItem({Text("Permitir invitaciones")},{Checkbox(conversation.canMembersInvite,null)},enabled=moderator,onClick={menu=false;onEvent(ChatUiEvent.MemberInvitesChanged(!conversation.canMembersInvite))}); DropdownMenuItem({Text("Añadir participantes")},{CompactIcon(Icons.Filled.PersonAdd,null)},enabled=canInvite,onClick={menu=false;onEvent(ChatUiEvent.OpenAddParticipants)}) }
   DropdownMenuItem({Text("Salir")},onClick={menu=false;confirm=ChatUiEvent.LeaveConversation}); DropdownMenuItem({Text("Ocultar conversación")},{CompactIcon(Icons.Filled.Delete,null)},onClick={menu=false;confirm=ChatUiEvent.HideConversation})
  }
 })
 if(expanded&&conversation?.isGroup==true) Column(Modifier.fillMaxWidth().padding(12.dp)){ chatMemberPresentations(conversation,state.currentUser).forEach{m-> var mm by remember(m.id){mutableStateOf(false)}; Row(Modifier.fillMaxWidth().clickable{onOpenProfile(m.id)}.padding(8.dp)){Text(if(m.isModerator)"${m.name} · moderador" else m.name,Modifier.weight(1f)); if(moderator&&!m.isSelf){CompactIconButton({mm=true}){CompactIcon(Icons.Filled.MoreVert,"Gestionar")}; DropdownMenu(mm,{mm=false}){DropdownMenuItem({Text(if(m.isModerator)"Quitar moderador" else "Nombrar moderador")},{CompactIcon(Icons.Filled.Security,null)},{mm=false;confirm=if(m.isModerator)ChatUiEvent.DemoteModerator(m.id)else ChatUiEvent.PromoteModerator(m.id)});DropdownMenuItem({Text("Bloquear")},{CompactIcon(Icons.Filled.Block,null)},{mm=false;confirm=ChatUiEvent.BlockParticipant(m.id)});DropdownMenuItem({Text("Expulsar")},{CompactIcon(Icons.Filled.PersonRemove,null)},{mm=false;confirm=ChatUiEvent.RemoveParticipant(m.id)})}}}}
 }
 if(state.isAddParticipantsOpen) Participants(state,onEvent,onOpenProfile)
 confirm?.let{e->QuataConfirmationDialogContent("Confirmar","¿Quieres continuar?","Confirmar","Cancelar",{onEvent(e);confirm=null},{confirm=null})}
}
@Composable private fun Participants(s:ChatUiState,e:(ChatUiEvent)->Unit,open:(String)->Unit)=AlertDialog({e(ChatUiEvent.CloseAddParticipants)},{Text("Añadir participantes")},{Column{OutlinedTextField(s.participantCandidateQuery,{e(ChatUiEvent.ParticipantSearchChanged(it))},label={Text("Buscar")}); if(s.isParticipantCandidateInitialLoading)Text("Cargando…"); if(s.participantCandidateError!=null)Text(s.participantCandidateError); if(!s.isParticipantCandidateInitialLoading&&s.participantConversationCandidates.isEmpty())Text("No hay contactos"); s.participantConversationCandidates.forEach{c->Row(Modifier.fillMaxWidth().padding(6.dp)){Checkbox(c.profileId in s.selectedParticipantIds,{e(ChatUiEvent.ParticipantSelectionToggled(c.profileId))});Text(c.displayName,Modifier.weight(1f).clickable{open(c.profileId)})}};if(s.participantCandidateHasMore)Button({/* model slot required in next extraction */},enabled=!s.isParticipantCandidatePageLoading){Text("Cargar más")}}},{Button({e(ChatUiEvent.AddSelectedParticipants)},enabled=s.selectedParticipantIds.isNotEmpty()&&!s.isConversationActionInProgress){Text("Añadir")}},{Button({e(ChatUiEvent.CloseAddParticipants)}){Text("Cancelar")}})
