package com.quata.feature.externalshare

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
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
    val attachmentsLabel: @Composable (Int) -> String,
    val picker: ConversationCandidatePickerStrings,
    val sendContentDescription: String,
)

const val ExternalShareRootTestTag = "external-share.root"
const val ExternalShareSearchTestTag = "external-share.search"
const val ExternalShareConfirmTestTag = "external-share.confirm"
const val ExternalShareDismissTestTag = "external-share.dismiss"
const val ExternalShareCandidateTestTagPrefix = "external-share.candidate."
const val ExternalShareCandidateActionTestTagPrefix = "external-share.candidate.action."
const val ExternalSharePayloadTextTestTag = "external-share.payload.text"
const val ExternalShareAttachmentTestTagPrefix = "external-share.attachment."

fun externalShareAttachmentTestTag(index: Int): String =
    ExternalShareAttachmentTestTagPrefix + index

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
    panelHost: @Composable (dismissEnabled: Boolean, content: @Composable (Modifier, Boolean) -> Unit) -> Unit,
    candidateAvatar: @Composable (ChatConversationCandidate, Modifier) -> Unit,
    attachmentContent: @Composable (ExternalShareAttachment, Modifier, () -> Unit) -> Unit,
    onOpenAttachment: (ExternalShareAttachment) -> Unit,
    modifier: Modifier = Modifier,
    inviteAvatar: @Composable (ChatInviteContact, Modifier) -> Unit = { _, _ -> },
    viewModelFactory: (ExternalSharePayload, ChatRepository) -> ShareToQuataViewModel = { sharePayload, chatRepository ->
        ShareToQuataViewModel(chatRepository, sharePayload)
    },
) {
    // The host may supply localized presentation transforms while the lifecycle and state remain
    // portable. This keeps Context and platform ViewModel types outside commonMain.
    val viewModel = remember(payload.id, repository) { viewModelFactory(payload, repository) }
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
    val dismissEnabled = !state.isSending
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
            panelHost(dismissEnabled) { panelModifier, isLandscape ->
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
        rootTestTag = ExternalShareRootTestTag,
        searchTestTag = ExternalShareSearchTestTag,
        candidateTestTagPrefix = ExternalShareCandidateTestTagPrefix,
        candidateActionTestTagPrefix = ExternalShareCandidateActionTestTagPrefix,
        confirmTestTag = ExternalShareConfirmTestTag,
        dismissTestTag = ExternalShareDismissTestTag,
        dismissEnabled = dismissEnabled,
    )
}

@Composable
fun ExternalSharePayloadPreviewContent(
    payload: ExternalSharePayload,
    textLabel: String,
    attachmentsLabel: @Composable (Int) -> String,
    attachmentContent: @Composable (ExternalShareAttachment, Modifier, () -> Unit) -> Unit,
    onOpenAttachment: (ExternalShareAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = 180.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        payload.text.takeIf { it.isNotBlank() }?.let { text ->
            Text(textLabel, fontWeight = FontWeight.Bold)
            Text(
                text,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .semantics { testTag = ExternalSharePayloadTextTestTag },
            )
        }
        if (payload.attachments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(attachmentsLabel(payload.attachments.size), fontWeight = FontWeight.Bold)
            payload.attachments.forEachIndexed { index, attachment ->
                attachmentContent(
                    attachment,
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .semantics { testTag = externalShareAttachmentTestTag(index) },
                ) {
                    onOpenAttachment(attachment)
                }
            }
        }
    }
}

@Composable
fun ExternalShareAttachmentRowContent(
    attachment: ExternalShareAttachment,
    openLabel: String,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    TextButton(
        onClick = onOpen,
        modifier = modifier,
    ) {
        Icon(Icons.Filled.AttachFile, contentDescription = openLabel)
        Text(attachment.name, modifier = Modifier.padding(start = 8.dp))
    }
}
