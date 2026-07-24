package com.quata.feature.externalshare

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import com.quata.core.platform.ClipboardService
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.conversations.ConversationCandidatePickerDialog
import com.quata.feature.chat.presentation.conversations.ConversationsUiState

@Composable
fun ShareToQuataDialog(
    payload: ExternalSharePayload,
    repository: ChatRepository,
    clipboardService: ClipboardService,
    onDismiss: () -> Unit,
    onSent: (String?) -> Unit
) {
    val context = LocalContext.current
    val viewModel: ShareToQuataAndroidViewModel = viewModel(
        key = "share-to-quata-${payload.id}",
        factory = ShareToQuataAndroidViewModel.factory(repository, payload, context)
    )
    val state by viewModel.uiState.collectAsState()
    val displayedCandidates = ((state.recentCandidates.takeIf { state.candidateQuery.isBlank() }.orEmpty()) + state.candidates)
        .distinctBy { it.profileId }
    val selectedNames = displayedCandidates
        .filter { it.profileId in state.selectedProfileIds }
        .joinToString(", ") { it.displayName }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onSent(state.completedConversationId)
    }

    if (payload.directConversationId != null) {
        ExternalShareSendingStateContent(
            message = stringResource(R.string.share_to_quata_sending),
            isSending = state.isSending,
            error = state.error,
            closeLabel = stringResource(R.string.common_close),
            onDismiss = onDismiss
        )
        return
    }

    ConversationCandidatePickerDialog(
        state = ConversationsUiState(
            currentUser = state.currentUser,
            candidateQuery = state.candidateQuery,
            conversationCandidates = displayedCandidates,
            isCandidateInitialLoading = state.isInitialLoading,
            isCandidatePageLoading = state.isPageLoading,
            candidateHasMore = state.hasMore,
            candidateNextOffset = state.nextOffset,
            candidateActorNeighborhood = state.actorNeighborhood,
            candidateError = state.error
        ),
        clipboardService = clipboardService,
        onSearchChange = viewModel::onQueryChanged,
        onLoadMore = viewModel::loadMore,
        onOpenCandidate = { viewModel.toggle(it.profileId) },
        onDismiss = onDismiss,
        title = stringResource(R.string.share_with_quata),
        selectedCandidateIds = state.selectedProfileIds,
        onToggleCandidate = { viewModel.toggle(it.profileId) },
        onConfirmSelection = viewModel::send,
        confirmEnabled = state.selectedProfileIds.isNotEmpty() && !state.isSending,
        selectionSummary = if (state.isSending) stringResource(R.string.share_to_quata_sending) else selectedNames,
        confirmIcon = Icons.AutoMirrored.Filled.Send,
        confirmContentDescription = stringResource(R.string.common_send)
    )
}
