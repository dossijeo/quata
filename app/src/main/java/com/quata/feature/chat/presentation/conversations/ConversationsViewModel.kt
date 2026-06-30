package com.quata.feature.chat.presentation.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.core.model.Conversation
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ConversationsViewModel(private val repository: ChatRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()
    private var conversationsJob: Job? = null
    private var usersJob: Job? = null
    private var pendingDeleteJob: Job? = null
    private var candidateSearchJob: Job? = null
    private var candidatePageJob: Job? = null

    init { observe() }

    fun onEvent(event: ConversationsUiEvent) {
        when (event) {
            ConversationsUiEvent.Refresh -> observe()
            ConversationsUiEvent.RestoreDeletedConversation -> restoreDeletedConversation()
            ConversationsUiEvent.FinalizeDeletedConversation -> finalizeDeletedConversation()
        }
    }

    fun openNewConversationPicker() {
        _uiState.value = _uiState.value.copy(
            isNewConversationPickerOpen = true,
            candidateQuery = "",
            conversationCandidates = emptyList(),
            candidateHasMore = true,
            candidateNextOffset = 0,
            candidateError = null
        )
        loadConversationCandidates(reset = true)
    }

    fun closeNewConversationPicker() {
        candidateSearchJob?.cancel()
        candidatePageJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isNewConversationPickerOpen = false,
            openingCandidateProfileId = null,
            candidateError = null
        )
    }

    fun onCandidateQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(
            candidateQuery = query,
            conversationCandidates = emptyList(),
            candidateHasMore = true,
            candidateNextOffset = 0,
            candidateError = null
        )
        candidateSearchJob?.cancel()
        candidateSearchJob = viewModelScope.launch {
            delay(260L)
            loadConversationCandidates(reset = true)
        }
    }

    fun loadMoreConversationCandidates() {
        if (!_uiState.value.isNewConversationPickerOpen) return
        if (_uiState.value.isCandidateInitialLoading || _uiState.value.isCandidatePageLoading || !_uiState.value.candidateHasMore) return
        loadConversationCandidates(reset = false)
    }

    fun openCandidateConversation(candidate: ChatConversationCandidate, onOpened: (String) -> Unit) {
        if (_uiState.value.openingCandidateProfileId != null) return
        candidate.existingConversationId?.let { conversationId ->
            _uiState.value = _uiState.value.copy(
                isNewConversationPickerOpen = false,
                candidateError = null
            )
            onOpened(conversationId)
            return
        }
        _uiState.value = _uiState.value.copy(openingCandidateProfileId = candidate.profileId, candidateError = null)
        viewModelScope.launch {
            repository.openPrivateConversation(candidate.profileId)
                .onSuccess { conversationId ->
                    _uiState.value = _uiState.value.copy(
                        openingCandidateProfileId = null,
                        isNewConversationPickerOpen = false
                    )
                    onOpened(conversationId)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        openingCandidateProfileId = null,
                        candidateError = error.message ?: "No se pudo abrir el chat"
                    )
                }
        }
    }

    private fun observe() {
        conversationsJob?.cancel()
        usersJob?.cancel()
        _uiState.value = _uiState.value.copy(currentUser = repository.currentUser())
        usersJob = viewModelScope.launch {
            repository.observeParticipantCandidates()
                .catch { }
                .collect { users ->
                    val currentUser = repository.currentUser()
                    _uiState.value = _uiState.value.copy(
                        currentUser = currentUser,
                        usersById = (users + listOfNotNull(currentUser)).associateBy { it.id }
                    )
                }
        }
        pendingDeleteJob?.cancel()
        pendingDeleteJob = viewModelScope.launch {
            repository.pendingDeletedConversation.collect { conversation ->
                _uiState.value = _uiState.value.copy(pendingDeletedConversation = conversation)
            }
        }
        conversationsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = _uiState.value.conversations.isEmpty(),
                error = null
            )
            repository.getConversations()
                .onSuccess { conversations ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        conversations = conversations.filter { it.isVisible },
                        messagesByConversation = emptyMap()
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Error cargando chats"
                    )
                }
            repository.observeConversations()
                .catch { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message ?: "Error cargando chats")
                }
                .collect { conversations ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        conversations = conversations.filter { it.isVisible },
                        messagesByConversation = emptyMap()
                    )
                }
        }
    }

    private fun restoreDeletedConversation() = viewModelScope.launch {
        repository.restorePendingDeletedConversation()
            .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message ?: "No se pudo restaurar") }
    }

    private fun finalizeDeletedConversation() = viewModelScope.launch {
        repository.finalizePendingDeletedConversation()
            .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message ?: "No se pudo borrar") }
    }

    private fun loadConversationCandidates(reset: Boolean) {
        candidatePageJob?.cancel()
        candidatePageJob = viewModelScope.launch {
            val state = _uiState.value
            val offset = if (reset) 0 else state.candidateNextOffset
            _uiState.value = state.copy(
                isCandidateInitialLoading = reset,
                isCandidatePageLoading = !reset,
                candidateError = null
            )
            repository.searchConversationCandidates(
                query = state.candidateQuery,
                limit = 30,
                offset = offset
            ).onSuccess { page ->
                val currentItems = if (reset) emptyList() else _uiState.value.conversationCandidates
                _uiState.value = _uiState.value.copy(
                    isCandidateInitialLoading = false,
                    isCandidatePageLoading = false,
                    conversationCandidates = (currentItems + page.candidates).distinctBy { it.profileId },
                    candidateHasMore = page.hasMore,
                    candidateNextOffset = page.nextOffset,
                    candidateActorNeighborhood = page.actorNeighborhood,
                    candidateError = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isCandidateInitialLoading = false,
                    isCandidatePageLoading = false,
                    candidateError = error.message ?: "No se pudo cargar la lista"
                )
            }
        }
    }

    companion object {
        fun factory(repository: ChatRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ConversationsViewModel(repository) as T
        }
    }
}
