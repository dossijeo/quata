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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.model.Conversation
import com.quata.core.model.User
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.QuataConfirmationDialogContent
import com.quata.feature.chat.presentation.chatDisplayTitle

data class ChatMemberPresentation(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val isModerator: Boolean,
    val isSelf: Boolean,
    val canOpenProfile: Boolean,
)

fun chatMemberPresentations(
    conversation: Conversation?,
    currentUser: User?,
): List<ChatMemberPresentation> = conversation?.participantIds.orEmpty().mapIndexed { index, id ->
    ChatMemberPresentation(
        id = id,
        name = conversation?.participantNames?.getOrNull(index)?.takeIf(String::isNotBlank) ?: id,
        avatarUrl = conversation?.participantAvatarUrls?.getOrNull(index),
        isModerator = id in conversation?.moderatorIds.orEmpty(),
        isSelf = id == currentUser?.id,
        canOpenProfile = !id.startsWith("wp:"),
    )
}.distinctBy(ChatMemberPresentation::id)

fun canManageChatMembers(conversation: Conversation?, currentUser: User?): Boolean =
    currentUser?.id != null && currentUser.id in conversation?.moderatorIds.orEmpty()

fun canInviteToChat(conversation: Conversation?, currentUser: User?): Boolean =
    canManageChatMembers(conversation, currentUser) || conversation?.canMembersInvite == true

private sealed interface ChatManagementConfirmation {
    val event: ChatUiEvent

    data object Leave : ChatManagementConfirmation { override val event = ChatUiEvent.LeaveConversation }
    data object Delete : ChatManagementConfirmation { override val event = ChatUiEvent.DeleteConversation }
    data class Promote(val userId: String) : ChatManagementConfirmation { override val event = ChatUiEvent.PromoteModerator(userId) }
    data class Demote(val userId: String) : ChatManagementConfirmation { override val event = ChatUiEvent.DemoteModerator(userId) }
    data class Block(val userId: String) : ChatManagementConfirmation { override val event = ChatUiEvent.BlockParticipant(userId) }
    data class Remove(val userId: String) : ChatManagementConfirmation { override val event = ChatUiEvent.RemoveParticipant(userId) }
}

@Composable
fun ChatGroupManagementContent(
    conversation: Conversation?,
    state: ChatUiState,
    navigationAction: @Composable () -> Unit,
    conversationAvatar: @Composable () -> Unit,
    memberAvatar: @Composable (ChatMemberPresentation) -> Unit,
    subtitle: String?,
    compact: Boolean,
    strings: ChatChromeStrings,
    trailing: @Composable RowScope.() -> Unit,
    onOpenProfile: (String) -> Unit,
    onLoadMoreParticipants: () -> Unit,
    onEvent: (ChatUiEvent) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var membersExpanded by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<ChatManagementConfirmation?>(null) }
    val isModerator = canManageChatMembers(conversation, state.currentUser)
    val canInvite = canInviteToChat(conversation, state.currentUser)

    ChatConversationTitleBarContent(
        title = conversation?.chatDisplayTitle()?.ifBlank { strings.untitledConversation } ?: strings.untitledConversation,
        subtitle = subtitle,
        expandable = conversation?.isGroup == true,
        compact = compact,
        onToggleExpanded = { membersExpanded = !membersExpanded },
        navigationAction = navigationAction,
        avatar = conversationAvatar,
        trailingActions = {
            trailing()
            CompactIconButton(onClick = { menuExpanded = true }) {
                CompactIcon(Icons.Filled.MoreVert, strings.options)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(if (conversation?.isMuted == true) strings.reactivateNotifications else strings.muteConversation) },
                    leadingIcon = {
                        CompactIcon(
                            if (conversation?.isMuted == true) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            null,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onEvent(ChatUiEvent.ConversationMutedChanged(conversation?.isMuted != true))
                    },
                )
                DropdownMenuItem(
                    text = { Text(strings.allowMemberInvites) },
                    leadingIcon = { Checkbox(checked = conversation?.canMembersInvite == true, onCheckedChange = null) },
                    enabled = isModerator,
                    onClick = {
                        menuExpanded = false
                        onEvent(ChatUiEvent.MemberInvitesChanged(conversation?.canMembersInvite != true))
                    },
                )
                DropdownMenuItem(
                    text = { Text(strings.addParticipants) },
                    leadingIcon = { CompactIcon(Icons.Filled.PersonAdd, null) },
                    enabled = canInvite,
                    onClick = {
                        menuExpanded = false
                        onEvent(ChatUiEvent.OpenAddParticipants)
                    },
                )
                DropdownMenuItem(
                    text = { Text(strings.leaveConversation) },
                    leadingIcon = { CompactIcon(Icons.Filled.PersonRemove, null) },
                    onClick = {
                        menuExpanded = false
                        confirmation = ChatManagementConfirmation.Leave
                    },
                )
                DropdownMenuItem(
                    text = { Text(strings.deleteConversation) },
                    leadingIcon = { CompactIcon(Icons.Filled.Delete, null) },
                    onClick = {
                        menuExpanded = false
                        confirmation = ChatManagementConfirmation.Delete
                    },
                )
            }
        },
    )

    if (state.isConversationActionInProgress) ChatConversationActionProgressContent()

    if (membersExpanded && conversation?.isGroup == true) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            chatMemberPresentations(conversation, state.currentUser).forEach { member ->
                var memberMenuExpanded by remember(member.id) { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    memberAvatar(member)
                    Text(
                        text = buildString {
                            append(strings.memberLabel(member.name, member.isSelf))
                            if (member.isModerator) append(" · ${strings.moderator}")
                        },
                        modifier = Modifier.weight(1f).clickable(
                            enabled = member.canOpenProfile,
                            onClick = { onOpenProfile(member.id) },
                        ).padding(8.dp),
                    )
                    if (isModerator && !member.isSelf) {
                        CompactIconButton(onClick = { memberMenuExpanded = true }) {
                            CompactIcon(Icons.Filled.MoreVert, strings.manageMember(member.name))
                        }
                        DropdownMenu(
                            expanded = memberMenuExpanded,
                            onDismissRequest = { memberMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (member.isModerator) strings.removeModerator else strings.promoteModerator) },
                                leadingIcon = { CompactIcon(Icons.Filled.Security, null) },
                                onClick = {
                                    memberMenuExpanded = false
                                    confirmation = if (member.isModerator) {
                                        ChatManagementConfirmation.Demote(member.id)
                                    } else {
                                        ChatManagementConfirmation.Promote(member.id)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(strings.blockUser) },
                                leadingIcon = { CompactIcon(Icons.Filled.Block, null) },
                                onClick = {
                                    memberMenuExpanded = false
                                    confirmation = ChatManagementConfirmation.Block(member.id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(strings.removeParticipant) },
                                leadingIcon = { CompactIcon(Icons.Filled.PersonRemove, null) },
                                onClick = {
                                    memberMenuExpanded = false
                                    confirmation = ChatManagementConfirmation.Remove(member.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.isAddParticipantsOpen) {
        ChatParticipantsPickerContent(
            state = state,
            onEvent = onEvent,
            onOpenProfile = onOpenProfile,
            onLoadMore = onLoadMoreParticipants,
            strings = strings,
        )
    }

    confirmation?.let { action ->
        val copy = action.confirmationCopy(strings)
        QuataConfirmationDialogContent(
            title = copy.first,
            message = copy.second,
            confirmLabel = strings.confirm,
            dismissLabel = strings.cancel,
            onConfirm = {
                onEvent(action.event)
                confirmation = null
            },
            onDismiss = { confirmation = null },
        )
    }
}

private fun ChatManagementConfirmation.confirmationCopy(strings: ChatChromeStrings): Pair<String, String> = when (this) {
    ChatManagementConfirmation.Leave -> strings.leaveConversation to strings.leaveConversationConfirm
    ChatManagementConfirmation.Delete -> strings.deleteConversation to strings.deleteConversationConfirm
    is ChatManagementConfirmation.Promote -> strings.promoteModerator to strings.promoteModeratorConfirm
    is ChatManagementConfirmation.Demote -> strings.removeModerator to strings.removeModeratorConfirm
    is ChatManagementConfirmation.Block -> strings.blockUser to strings.blockUserConfirm
    is ChatManagementConfirmation.Remove -> strings.removeParticipant to strings.removeParticipantConfirm
}

@Composable
private fun ChatParticipantsPickerContent(
    state: ChatUiState,
    onEvent: (ChatUiEvent) -> Unit,
    onOpenProfile: (String) -> Unit,
    onLoadMore: () -> Unit,
    strings: ChatChromeStrings,
) {
    AlertDialog(
        onDismissRequest = { onEvent(ChatUiEvent.CloseAddParticipants) },
        title = { Text(strings.addParticipants) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.participantCandidateQuery,
                    onValueChange = { onEvent(ChatUiEvent.ParticipantSearchChanged(it)) },
                    label = { Text(strings.search) },
                )
                when {
                    state.isParticipantCandidateInitialLoading -> Text(strings.searchingPeople)
                    state.participantCandidateError != null -> Text(state.participantCandidateError)
                    state.participantConversationCandidates.isEmpty() -> Text(strings.noPeopleFound)
                }
                state.participantConversationCandidates.forEach { candidate ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = candidate.profileId in state.selectedParticipantIds,
                            onCheckedChange = { onEvent(ChatUiEvent.ParticipantSelectionToggled(candidate.profileId)) },
                        )
                        Text(
                            candidate.displayName,
                            Modifier.weight(1f).clickable { onOpenProfile(candidate.profileId) }.padding(8.dp),
                        )
                    }
                }
                if (state.participantCandidateHasMore && !state.isParticipantCandidateInitialLoading) {
                    Button(onClick = onLoadMore, enabled = !state.isParticipantCandidatePageLoading) {
                        Text(if (state.isParticipantCandidatePageLoading) strings.loading else strings.loadMore)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onEvent(ChatUiEvent.AddSelectedParticipants) },
                enabled = state.selectedParticipantIds.isNotEmpty() && !state.isConversationActionInProgress,
            ) { Text(strings.add) }
        },
        dismissButton = {
            Button(onClick = { onEvent(ChatUiEvent.CloseAddParticipants) }) { Text(strings.cancel) }
        },
    )
}
