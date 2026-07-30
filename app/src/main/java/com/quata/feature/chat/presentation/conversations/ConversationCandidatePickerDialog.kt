package com.quata.feature.chat.presentation.conversations

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.quata.R
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.platform.ClipboardService
import com.quata.core.ui.components.QuataAvatarFallback
import com.quata.core.ui.components.QuataStandardFloatingPanel
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatInviteContact

/** Compatibility adapter used by the Android conversation detail composer. */
@Composable
fun ConversationCandidatePickerDialog(
    state: ConversationsUiState, clipboardService: ClipboardService, onSearchChange: (String) -> Unit,
    onLoadMore: () -> Unit, onOpenCandidate: (ChatConversationCandidate) -> Unit, onDismiss: () -> Unit,
    inviteContactsEnabled: Boolean = false, onRequestInviteContactsPermission: (() -> Unit)? = null,
    onInviteContact: ((ChatInviteContact) -> Unit)? = null, title: String = stringResource(R.string.conversations_new_chat_title),
    actionIcon: ImageVector = Icons.Filled.ChatBubble, actionContentDescription: String = stringResource(R.string.common_chat),
    excludedProfileIds: Set<String> = emptySet(), selectedCandidateIds: Set<String> = emptySet(),
    onToggleCandidate: ((ChatConversationCandidate) -> Unit)? = null, onConfirmSelection: (() -> Unit)? = null,
    confirmEnabled: Boolean = selectedCandidateIds.isNotEmpty(), selectionSummary: String = "",
    confirmIcon: ImageVector = Icons.AutoMirrored.Filled.Send, confirmContentDescription: String = stringResource(R.string.common_send),
) = ConversationCandidatePickerDialogContent(
    state, clipboardService, androidCandidateStrings(), onSearchChange, onLoadMore, onOpenCandidate, onDismiss,
    panelHost = { content -> QuataStandardFloatingPanel(onDismiss = onDismiss, template = quataTheme()) { modifier, landscape -> content(modifier, landscape) } },
    candidateAvatar = { candidate, modifier -> if (candidate.avatarUrl.isNullOrBlank()) QuataAvatarFallback(candidate.displayName, candidate.profileId, modifier) else AsyncImage(candidate.avatarUrl, candidate.displayName, modifier = modifier) },
    inviteAvatar = { contact, modifier -> QuataAvatarFallback(contact.displayName, contact.id, modifier) },
    inviteContactsEnabled = inviteContactsEnabled, onRequestInviteContactsPermission = onRequestInviteContactsPermission,
    title = title, actionIcon = actionIcon, actionContentDescription = actionContentDescription, excludedProfileIds = excludedProfileIds,
    selectedCandidateIds = selectedCandidateIds, onToggleCandidate = onToggleCandidate, onConfirmSelection = onConfirmSelection,
    confirmEnabled = confirmEnabled, selectionSummary = selectionSummary, confirmIcon = confirmIcon, confirmContentDescription = confirmContentDescription,
)

@Composable private fun androidCandidateStrings() = ConversationCandidatePickerStrings(
    stringResource(R.string.conversations_new_chat_search_placeholder), stringResource(R.string.conversations_new_chat_no_results), stringResource(R.string.common_cancel), stringResource(R.string.conversations_new_chat_contacts), stringResource(R.string.conversations_new_chat_following), stringResource(R.string.conversations_new_chat_followers), stringResource(R.string.share_to_quata_recent_conversations), stringResource(R.string.conversations_new_chat_other_neighborhoods), stringResource(R.string.conversations_new_chat_unknown_neighborhood), stringResource(R.string.conversations_invite_to_quata), stringResource(R.string.conversations_invite_contacts_permission), stringResource(R.string.conversations_invite_allow), stringResource(R.string.conversations_invite_action), stringResource(R.string.conversation_forward_none_selected),
)
