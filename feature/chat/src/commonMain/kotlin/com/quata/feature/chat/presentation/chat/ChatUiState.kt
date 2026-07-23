package com.quata.feature.chat.presentation.chat

/** Platform-neutral conversation render state. */

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatSyncStatus

data class ChatUiState(
    val messageText: String = "",
    val attachmentUri: String? = null,
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null,
    val currentUser: User? = null,
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val participantCandidates: List<User> = emptyList(),
    val participantSearch: String = "",
    val selectedParticipantIds: List<String> = emptyList(),
    val selectedMessageId: String? = null,
    val replyToMessage: Message? = null,
    val editingMessage: Message? = null,
    val isForwardDialogOpen: Boolean = false,
    val selectedForwardProfileIds: List<String> = emptyList(),
    val forwardCandidateQuery: String = "",
    val forwardConversationCandidates: List<ChatConversationCandidate> = emptyList(),
    val isForwardCandidateInitialLoading: Boolean = false,
    val isForwardCandidatePageLoading: Boolean = false,
    val forwardCandidateHasMore: Boolean = true,
    val forwardCandidateNextOffset: Int = 0,
    val forwardCandidateActorNeighborhood: String = "",
    val forwardCandidateError: String? = null,
    val isAddParticipantsOpen: Boolean = false,
    val participantCandidateQuery: String = "",
    val participantConversationCandidates: List<ChatConversationCandidate> = emptyList(),
    val isParticipantCandidateInitialLoading: Boolean = false,
    val isParticipantCandidatePageLoading: Boolean = false,
    val participantCandidateHasMore: Boolean = true,
    val participantCandidateNextOffset: Int = 0,
    val participantCandidateActorNeighborhood: String = "",
    val addingCandidateProfileId: String? = null,
    val participantCandidateError: String? = null,
    val isLoading: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
    val hasMoreHistory: Boolean = true,
    val typingProfileIds: Set<String> = emptySet(),
    val syncStatus: ChatSyncStatus = ChatSyncStatus.Refreshing,
    val isConversationActionInProgress: Boolean = false,
    val shouldCloseConversation: Boolean = false,
    val notice: String? = null,
    val error: String? = null
)
