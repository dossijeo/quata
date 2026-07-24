package com.quata.feature.chat.presentation.conversations

import com.quata.core.common.AppDispatchers
import com.quata.core.model.Conversation
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatInviteContact
import com.quata.feature.chat.domain.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConversationsViewModel(
    private val repository: ChatRepository,
    private val readContacts: () -> List<ChatInviteContact> = { emptyList() },
    private val text: (com.quata.feature.chat.presentation.chat.ChatText) -> String = { "Chat error" },
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()
    private var conversationsJob: Job? = null
    private var usersJob: Job? = null
    private var pendingDeleteJob: Job? = null
    private var candidateSearchJob: Job? = null
    private var candidatePageJob: Job? = null

    init {
        observe()
        scope.launch {
            repository.syncStatus.collect { status -> _uiState.value = _uiState.value.copy(syncStatus = status) }
        }
    }

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
            inviteContacts = emptyList(),
            isInviteContactsLoading = false,
            inviteContactsError = null,
            candidateHasMore = true,
            candidateNextOffset = 0,
            selectedNewConversationProfileIds = emptySet(),
            newGroupTitle = "",
            isOpeningGroupConversation = false,
            candidateError = null
        )
        loadConversationCandidates(reset = true)
    }

    fun closeNewConversationPicker() {
        candidateSearchJob?.cancel()
        candidatePageJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isNewConversationPickerOpen = false,
            inviteContacts = emptyList(),
            isInviteContactsLoading = false,
            inviteContactsError = null,
            openingCandidateProfileId = null,
            selectedNewConversationProfileIds = emptySet(),
            newGroupTitle = "",
            isOpeningGroupConversation = false,
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
        candidateSearchJob = scope.launch {
            delay(260L)
            loadConversationCandidates(reset = true)
        }
    }

    fun loadMoreConversationCandidates() {
        if (!_uiState.value.isNewConversationPickerOpen) return
        if (_uiState.value.isCandidateInitialLoading || _uiState.value.isCandidatePageLoading || !_uiState.value.candidateHasMore) return
        loadConversationCandidates(reset = false)
    }

    fun loadInviteContacts() {
        val state = _uiState.value
        if (!state.isNewConversationPickerOpen || state.isInviteContactsLoading) return
        _uiState.value = state.copy(isInviteContactsLoading = true, inviteContactsError = null)
        scope.launch {
            val contacts = withContext(dispatchers.io) { readContacts() }
            if (contacts.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    inviteContacts = emptyList(),
                    isInviteContactsLoading = false,
                    inviteContactsError = null
                )
                return@launch
            }
            repository.matchRegisteredContactPhones(contacts.flatMap { it.phoneKeys })
                .onSuccess { registeredPhones ->
                    _uiState.value = _uiState.value.copy(
                        inviteContacts = contacts.filter { contact -> contact.phoneKeys.none(registeredPhones::contains) },
                        isInviteContactsLoading = false,
                        inviteContactsError = null
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        inviteContacts = emptyList(),
                        isInviteContactsLoading = false,
                        inviteContactsError = text(com.quata.feature.chat.presentation.chat.ChatText.LoadCandidates)
                    )
                }
        }
    }

    fun openCandidateConversation(candidate: ChatConversationCandidate, onOpened: (String) -> Unit) {
        if (_uiState.value.openingCandidateProfileId != null) return
        _uiState.value = _uiState.value.copy(openingCandidateProfileId = candidate.profileId, candidateError = null)
        scope.launch {
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
                        candidateError = text(com.quata.feature.chat.presentation.chat.ChatText.OpenConversation)
                    )
                }
        }
    }

    /** Shared group-composer state; the host chooses its visual presentation and navigation. */
    fun toggleNewConversationCandidate(candidate: ChatConversationCandidate) {
        if (_uiState.value.isOpeningGroupConversation) return
        _uiState.value = _uiState.value.let { state ->
            state.copy(
                selectedNewConversationProfileIds = state.selectedNewConversationProfileIds.let { selected ->
                    if (candidate.profileId in selected) selected - candidate.profileId else selected + candidate.profileId
                },
                candidateError = null,
            )
        }
    }

    fun onNewGroupTitleChanged(title: String) {
        _uiState.value = _uiState.value.copy(newGroupTitle = title.take(120), candidateError = null)
    }

    fun openSelectedGroupConversation(onOpened: (String) -> Unit) {
        val state = _uiState.value
        val participantIds = state.selectedNewConversationProfileIds.toList()
        if (state.isOpeningGroupConversation || participantIds.size < 2) return
        _uiState.value = state.copy(isOpeningGroupConversation = true, candidateError = null)
        scope.launch {
            repository.openGroupConversation(participantIds, state.newGroupTitle.trim().ifBlank { null })
                .onSuccess { conversationId ->
                    _uiState.value = _uiState.value.copy(
                        isOpeningGroupConversation = false,
                        isNewConversationPickerOpen = false,
                        selectedNewConversationProfileIds = emptySet(),
                        newGroupTitle = "",
                    )
                    onOpened(conversationId)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isOpeningGroupConversation = false,
                        candidateError = text(com.quata.feature.chat.presentation.chat.ChatText.OpenConversation),
                    )
                }
        }
    }

    private fun observe() {
        conversationsJob?.cancel()
        usersJob?.cancel()
        _uiState.value = _uiState.value.copy(currentUser = repository.currentUser())
        usersJob = scope.launch {
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
        pendingDeleteJob = scope.launch {
            repository.pendingDeletedConversation.collect { conversation ->
                _uiState.value = _uiState.value.copy(pendingDeletedConversation = conversation)
            }
        }
        conversationsJob = scope.launch {
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
                        error = text(com.quata.feature.chat.presentation.chat.ChatText.LoadConversations)
                    )
                }
            repository.observeConversations()
                .catch { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = text(com.quata.feature.chat.presentation.chat.ChatText.LoadConversations))
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

    private fun restoreDeletedConversation() = scope.launch {
        repository.restorePendingDeletedConversation()
            .onFailure { _ -> _uiState.value = _uiState.value.copy(error = text(com.quata.feature.chat.presentation.chat.ChatText.RestoreConversation)) }
    }

    private fun finalizeDeletedConversation() = scope.launch {
        repository.finalizePendingDeletedConversation()
            .onFailure { _ -> _uiState.value = _uiState.value.copy(error = text(com.quata.feature.chat.presentation.chat.ChatText.DeleteConversation)) }
    }

    private fun loadConversationCandidates(reset: Boolean) {
        candidatePageJob?.cancel()
        candidatePageJob = scope.launch {
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
                    candidateError = text(com.quata.feature.chat.presentation.chat.ChatText.LoadCandidates)
                )
            }
        }
    }

    fun close() {
        conversationsJob?.cancel()
        usersJob?.cancel()
        pendingDeleteJob?.cancel()
        candidateSearchJob?.cancel()
        candidatePageJob?.cancel()
        scope.coroutineContext.cancel()
    }
}
