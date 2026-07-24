package com.quata.feature.externalshare

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.platform.ClipboardService
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatInviteContact
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.conversations.ConversationCandidatePickerDialogContent
import com.quata.feature.chat.presentation.conversations.ConversationCandidatePickerStrings
import com.quata.feature.chat.presentation.conversations.ConversationsUiState

data class ExternalShareDestinationStrings(
    val title: String,
    val sending: String,
    val close: String,
    val payloadTextLabel: String,
    val attachmentsLabel: (Int) -> String,
    val picker: ConversationCandidatePickerStrings,
    val sendContentDescription: String,
)

/**
 * Shared external-share destination flow. It owns the common ViewModel lifecycle, loading,
 * candidate selection and sending state. Hosts inject panel/navigation/avatar/attachment UI only.
 */
@Composable
fun ExternalShareDestinationHostContent(
    payload: ExternalSharePayload,
    repository: ChatRepository,
    clipboardService: ClipboardService,
    strings: ExternalShareDestinationStrings,
    onDismiss: () -> Unit,
    onSent: (String?) -> Unit,
    panelHost: @Composable (@Composable (Modifier, Boolean) -> Unit) -> Unit,
    candidateAvatar: @Composable (ChatConversationCandidate, Modifier) -> Unit,
    attachmentContent: @Composable (ExternalShareAttachment, Modifier, () -> Unit) -> Unit,
    onOpenAttachment: (ExternalShareAttachment) -> Unit,
    modifier: Modifier = Modifier,
    inviteAvatar: @Composable (ChatInviteContact, Modifier) -> Unit = { _, _ -> },
) {
    val viewModel = remember(payload.id, repository) { ShareToQuataViewModel(repository, payload) }
    val state by viewModel.uiState.collectAsState()
    DisposableEffect(viewModel) { onDispose(viewModel::close) }
    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onSent(state.completedConversationId)
    }

    val preview: @Composable () -> Unit = {
        ExternalSharePayloadPreviewContent(
            payload = payload,
            textLabel = strings.payloadTextLabel,
            attachmentsLabel = strings.attachmentsLabel,
            attachmentContent = attachmentContent,
            onOpenAttachment = onOpenAttachment,
        )
    }
    if (payload.directConversationId != null) {
        ExternalShareSendingStateContent(
            message = strings.sending,
            isSending = state.isSending,
            error = state.error,
            closeLabel = strings.close,
            onDismiss = onDismiss,
            payloadPreview = preview,
        )
        return
    }

    val displayedCandidates = (state.recentCandidates.takeIf { state.candidateQuery.isBlank() }.orEmpty() + state.candidates)
        .distinctBy { it.profileId }
    val selectedNames = displayedCandidates
        .filter { it.profileId in state.selectedProfileIds }
        .joinToString(", ") { it.displayName }
    ConversationCandidatePickerDialogContent(
        state = ConversationsUiState(
            currentUser = state.currentUser,
            candidateQuery = state.candidateQuery,
            conversationCandidates = displayedCandidates,
            isCandidateInitialLoading = state.isInitialLoading,
            isCandidatePageLoading = state.isPageLoading,
            candidateHasMore = state.hasMore,
            candidateNextOffset = state.nextOffset,
            candidateActorNeighborhood = state.actorNeighborhood,
            candidateError = state.error,
        ),
        clipboardService = clipboardService,
        strings = strings.picker,
        onSearchChange = viewModel::onQueryChanged,
        onLoadMore = viewModel::loadMore,
        onOpenCandidate = { viewModel.toggle(it.profileId) },
        onDismiss = onDismiss,
        panelHost = { picker ->
            panelHost { panelModifier, isLandscape ->
                Column(panelModifier.then(modifier)) {
                    preview()
                    Spacer(Modifier.height(12.dp))
                    picker(Modifier.weight(1f), isLandscape)
                }
            }
        },
        candidateAvatar = candidateAvatar,
        inviteAvatar = inviteAvatar,
        title = strings.title,
        actionIcon = Icons.AutoMirrored.Filled.Send,
        actionContentDescription = strings.sendContentDescription,
        selectedCandidateIds = state.selectedProfileIds,
        onToggleCandidate = { viewModel.toggle(it.profileId) },
        onConfirmSelection = viewModel::send,
        confirmEnabled = state.selectedProfileIds.isNotEmpty() && !state.isSending,
        selectionSummary = if (state.isSending) strings.sending else selectedNames,
        confirmIcon = Icons.AutoMirrored.Filled.Send,
        confirmContentDescription = strings.sendContentDescription,
    )
}

@Composable
fun ExternalSharePayloadPreviewContent(
    payload: ExternalSharePayload,
    textLabel: String,
    attachmentsLabel: (Int) -> String,
    attachmentContent: @Composable (ExternalShareAttachment, Modifier, () -> Unit) -> Unit,
    onOpenAttachment: (ExternalShareAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        payload.text.takeIf { it.isNotBlank() }?.let { text ->
            Text(textLabel, fontWeight = FontWeight.Bold)
            Text(text, modifier = Modifier.padding(top = 2.dp))
        }
        if (payload.attachments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(attachmentsLabel(payload.attachments.size), fontWeight = FontWeight.Bold)
            payload.attachments.forEach { attachment ->
                attachmentContent(attachment, Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    onOpenAttachment(attachment)
                }
            }
        }
    }
}
