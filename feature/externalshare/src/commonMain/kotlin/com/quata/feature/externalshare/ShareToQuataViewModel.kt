package com.quata.feature.externalshare

import com.quata.core.common.AppDispatchers
import com.quata.core.model.Conversation
import com.quata.core.model.User
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ShareToQuataUiState(
    val currentUser: User? = null,
    val candidateQuery: String = "",
    val recentCandidates: List<ChatConversationCandidate> = emptyList(),
    val candidates: List<ChatConversationCandidate> = emptyList(),
    val selectedProfileIds: Set<String> = emptySet(),
    val isInitialLoading: Boolean = false,
    val isPageLoading: Boolean = false,
    val hasMore: Boolean = true,
    val nextOffset: Int = 0,
    val actorNeighborhood: String = "",
    val isSending: Boolean = false,
    val isComplete: Boolean = false,
    val completedConversationId: String? = null,
    val error: String? = null
)

class ShareToQuataViewModel(
    private val repository: ChatRepository,
    private val payload: ExternalSharePayload,
    private val text: (ShareText) -> String = { "No se pudo enviar" },
    private val resolvePreview: (String) -> String = { it },
    private val conversationTitle: (Conversation) -> String = { it.title },
    private val isFavoriteConversation: (String) -> Boolean = { false },
    dispatchers: AppDispatchers = AppDispatchers()
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _uiState = MutableStateFlow(ShareToQuataUiState(currentUser = repository.currentUser()))
    val uiState: StateFlow<ShareToQuataUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var pageJob: Job? = null

    init {
        payload.directConversationId?.let { conversationId ->
            sendToConversationIds(listOf(conversationId))
        } ?: run {
            loadRecentConversations()
            loadCandidates(reset = true)
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(
            candidateQuery = query,
            candidates = emptyList(),
            hasMore = true,
            nextOffset = 0,
            error = null
        )
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(260L)
            loadCandidates(reset = true)
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isInitialLoading || state.isPageLoading || !state.hasMore || state.isSending) return
        loadCandidates(reset = false)
    }

    fun toggle(profileId: String) {
        if (_uiState.value.isSending) return
        val selected = _uiState.value.selectedProfileIds
        _uiState.value = _uiState.value.copy(
            selectedProfileIds = if (profileId in selected) selected - profileId else selected + profileId
        )
    }

    fun send() {
        val selected = _uiState.value.selectedProfileIds.toList()
        if (selected.isEmpty() || _uiState.value.isSending) return
        scope.launch {
            runCatching {
                val destinations = (_uiState.value.recentCandidates + _uiState.value.candidates)
                    .associateBy { it.profileId }
                val conversationIds = selected.map { destinationId ->
                    val candidate = destinations[destinationId]
                        ?: error("Destino de chat no disponible")
                    candidate.existingConversationId
                        ?: repository.openPrivateConversation(candidate.profileId).getOrThrow()
                }.distinct()
                conversationIds
            }.onSuccess(::sendToConversationIds)
                .onFailure { showSendError() }
        }
    }

    private fun sendToConversationIds(conversationIds: List<String>) {
        if (conversationIds.isEmpty() || _uiState.value.isSending) return
        _uiState.value = _uiState.value.copy(isSending = true, error = null)
        scope.launch {
            runCatching {
                conversationIds.forEach { conversationId -> sendPayload(conversationId) }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    isComplete = true,
                    completedConversationId = conversationIds.singleOrNull()
                )
            }.onFailure {
                showSendError()
            }
        }
    }

    private suspend fun sendPayload(conversationId: String) {
        if (payload.attachments.isEmpty()) {
            repository.sendMessage(
                conversationId = conversationId,
                text = payload.text,
                clientMessageId = newClientMessageId()
            ).getOrThrow()
            return
        }
        payload.attachments.forEachIndexed { index, attachment ->
            repository.sendMessage(
                conversationId = conversationId,
                text = payload.text.takeIf { index == 0 }.orEmpty(),
                attachmentUri = attachment.uri,
                attachmentName = attachment.name,
                attachmentMimeType = attachment.mimeType,
                clientMessageId = newClientMessageId()
            ).getOrThrow()
        }
    }

    private fun showSendError() {
        _uiState.value = _uiState.value.copy(
            isSending = false,
            error = text(ShareText.SendError)
        )
    }

    private fun loadRecentConversations() {
        scope.launch {
            repository.getConversations().onSuccess { conversations ->
                val currentUserId = repository.currentUser()?.id
                val recent = conversations.asSequence()
                    .filter { conversation ->
                        conversation.isVisible &&
                            !conversation.isEmergency &&
                            !isFavoriteConversation(conversation.id) &&
                            (
                                conversation.participantIds.isEmpty() ||
                                    conversation.participantIds.any { it != currentUserId }
                            )
                    }
                    .sortedByDescending { it.updatedAtMillis ?: Long.MIN_VALUE }
                    .take(RECENT_CONVERSATION_COUNT)
                    .map { conversation ->
                        val privatePeerId = if (!conversation.isGroup) {
                            conversation.participantIds.firstOrNull { it != currentUserId }
                        } else {
                            null
                        }
                        ChatConversationCandidate(
                            profileId = privatePeerId ?: "conversation:${conversation.id}",
                            displayName = conversationTitle(conversation),
                            neighborhood = resolvePreview(conversation.lastMessagePreview),
                            phone = "",
                            avatarUrl = conversation.avatarUrl,
                            sectionKey = "recent",
                            neighborhoodGroup = "",
                            existingConversationId = conversation.id
                        )
                    }
                    .filter { it.displayName.isNotBlank() }
                    .toList()
                _uiState.value = _uiState.value.copy(recentCandidates = recent)
            }
        }
    }

    private fun loadCandidates(reset: Boolean) {
        pageJob?.cancel()
        pageJob = scope.launch {
            val state = _uiState.value
            val offset = if (reset) 0 else state.nextOffset
            _uiState.value = state.copy(
                isInitialLoading = reset,
                isPageLoading = !reset,
                error = null
            )
            repository.searchConversationCandidates(
                query = state.candidateQuery,
                limit = PAGE_SIZE,
                offset = offset
            ).onSuccess { page ->
                val current = if (reset) emptyList() else _uiState.value.candidates
                val currentUserId = repository.currentUser()?.id
                _uiState.value = _uiState.value.copy(
                    currentUser = repository.currentUser(),
                    isInitialLoading = false,
                    isPageLoading = false,
                    candidates = (current + page.candidates)
                        .filterNot { it.profileId == currentUserId }
                        .distinctBy { it.profileId },
                    hasMore = page.hasMore,
                    nextOffset = page.nextOffset,
                    actorNeighborhood = page.actorNeighborhood,
                    error = null
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isInitialLoading = false,
                    isPageLoading = false,
                    error = text(ShareText.LoadCandidates)
                )
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val RECENT_CONVERSATION_COUNT = 3
    }

    fun close() {
        searchJob?.cancel()
        pageJob?.cancel()
        scope.coroutineContext.cancel()
    }
}

enum class ShareText { SendError, LoadCandidates }

private fun newClientMessageId(): String = "share-${Random.nextLong().toString(16)}"
