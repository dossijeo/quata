package com.quata.feature.neighborhoods.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NeighborhoodsViewModel(
    private val repository: NeighborhoodRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(NeighborhoodsUiState())
    val uiState: StateFlow<NeighborhoodsUiState> = _uiState.asStateFlow()
    private var communitiesJob: Job? = null

    fun startObservingCommunities() {
        if (communitiesJob?.isActive == true) return
        communitiesJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.observeCommunities()
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "No se pudieron cargar las comunidades"
                    )
                }
                .collect { communities ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        communities = communities,
                        error = null
                    )
                }
        }
    }

    fun stopObservingCommunities() {
        communitiesJob?.cancel()
        communitiesJob = null
    }

    fun openChat(neighborhood: String, onOpened: (String) -> Unit) {
        if (_uiState.value.openingChatNeighborhood != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isOpeningChat = true,
                openingChatNeighborhood = neighborhood,
                error = null
            )
            repository.openNeighborhoodChat(neighborhood)
                .onSuccess { conversationId ->
                    _uiState.value = _uiState.value.copy(
                        isOpeningChat = false,
                        openingChatNeighborhood = null
                    )
                    onOpened(conversationId)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isOpeningChat = false,
                        openingChatNeighborhood = null,
                        error = error.message ?: "No se pudo abrir el chat"
                    )
                }
        }
    }

    fun toggleFollowUser(userId: String) {
        viewModelScope.launch {
            repository.toggleFollowUser(userId)
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message ?: "No se pudo actualizar el seguimiento")
                }
            refreshSelectedProfile(userId)
        }
    }

    fun openPrivateChat(userId: String, onOpened: (String) -> Unit) {
        if (_uiState.value.openingPrivateChatUserId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(openingPrivateChatUserId = userId, error = null)
            repository.openPrivateChat(userId)
                .onSuccess { conversationId ->
                    _uiState.value = _uiState.value.copy(openingPrivateChatUserId = null)
                    onOpened(conversationId)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        openingPrivateChatUserId = null,
                        error = error.message ?: "No se pudo abrir PRIVI"
                    )
                }
        }
    }

    fun openUserProfile(userId: String) {
        viewModelScope.launch {
            val cachedProfile = repository.getCachedUserProfile(userId)
            if (cachedProfile != null) {
                _uiState.value = _uiState.value.copy(
                    selectedProfile = cachedProfile,
                    openingProfileUserId = null,
                    refreshingProfileUserId = userId,
                    error = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    openingProfileUserId = userId,
                    refreshingProfileUserId = null,
                    error = null
                )
            }
            repository.getUserProfile(userId)
                .onSuccess { profile ->
                    repository.cacheUserProfile(profile)
                    val currentState = _uiState.value
                    val shouldUpdateVisibleProfile =
                        currentState.selectedProfile?.user?.id == userId ||
                            currentState.openingProfileUserId == userId ||
                            currentState.refreshingProfileUserId == userId
                    _uiState.value = currentState.copy(
                        openingProfileUserId = if (currentState.openingProfileUserId == userId) null else currentState.openingProfileUserId,
                        refreshingProfileUserId = if (currentState.refreshingProfileUserId == userId) null else currentState.refreshingProfileUserId,
                        selectedProfile = if (shouldUpdateVisibleProfile) profile else currentState.selectedProfile,
                        error = null
                    )
                }
                .onFailure { error ->
                    val currentState = _uiState.value
                    _uiState.value = currentState.copy(
                        openingProfileUserId = if (currentState.openingProfileUserId == userId) null else currentState.openingProfileUserId,
                        refreshingProfileUserId = if (currentState.refreshingProfileUserId == userId) null else currentState.refreshingProfileUserId,
                        error = error.message ?: "No se pudo abrir el perfil"
                    )
                }
        }
    }

    fun closeUserProfile() {
        _uiState.value = _uiState.value.copy(
            openingProfileUserId = null,
            refreshingProfileUserId = null,
            selectedProfile = null
        )
    }

    fun reportProfilePost(postId: String) {
        viewModelScope.launch {
            val profileUserId = _uiState.value.selectedProfile?.user?.id
            repository.reportPost(postId)
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message ?: "No se pudo reportar")
                }
            if (profileUserId != null) {
                refreshSelectedProfile(profileUserId)
            }
        }
    }

    private suspend fun refreshSelectedProfile(userId: String) {
        val current = _uiState.value.selectedProfile ?: return
        if (current.user.id != userId) return
        repository.getUserProfile(userId)
            .onSuccess { profile -> _uiState.value = _uiState.value.copy(selectedProfile = profile) }
    }

    companion object {
        fun factory(repository: NeighborhoodRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NeighborhoodsViewModel(repository) as T
        }
    }
}
