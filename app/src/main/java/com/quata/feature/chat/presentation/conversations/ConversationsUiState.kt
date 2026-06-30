package com.quata.feature.chat.presentation.conversations

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.feature.chat.domain.ChatConversationCandidate

data class ConversationsUiState(
    val isLoading: Boolean = false,
    val currentUser: User? = null,
    val conversations: List<Conversation> = emptyList(),
    val messagesByConversation: Map<String, List<Message>> = emptyMap(),
    val usersById: Map<String, User> = emptyMap(),
    val pendingDeletedConversation: Conversation? = null,
    val isNewConversationPickerOpen: Boolean = false,
    val candidateQuery: String = "",
    val conversationCandidates: List<ChatConversationCandidate> = emptyList(),
    val isCandidateInitialLoading: Boolean = false,
    val isCandidatePageLoading: Boolean = false,
    val candidateHasMore: Boolean = true,
    val candidateNextOffset: Int = 0,
    val candidateActorNeighborhood: String = "",
    val openingCandidateProfileId: String? = null,
    val candidateError: String? = null,
    val error: String? = null
)
