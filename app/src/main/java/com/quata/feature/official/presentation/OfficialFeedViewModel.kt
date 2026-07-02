package com.quata.feature.official.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OfficialFeedViewModel(
    private val repository: OfficialRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(OfficialFeedUiState())
    val uiState: StateFlow<OfficialFeedUiState> = _uiState.asStateFlow()
    private var feedJob: Job? = null
    private var refreshJob: Job? = null

    init {
        observeFeed()
        refreshCurrentUser()
    }

    fun onEvent(event: OfficialFeedUiEvent) {
        when (event) {
            OfficialFeedUiEvent.Refresh -> refresh()
            OfficialFeedUiEvent.ClearMessage -> _uiState.value = _uiState.value.copy(error = null, message = null)
            is OfficialFeedUiEvent.ToggleLike -> updatePostFromRepository { repository.toggleLike(event.postId) }
            is OfficialFeedUiEvent.AddComment -> updatePostFromRepository { repository.addComment(event.postId, event.comment) }
            is OfficialFeedUiEvent.DeletePost -> deletePost(event.postId)
            is OfficialFeedUiEvent.CreatePost -> createPost(event.draft)
        }
    }

    fun refreshCurrentUser() {
        viewModelScope.launch {
            repository.refreshCurrentUser()
                .onSuccess { user -> _uiState.value = _uiState.value.copy(currentUser = user) }
                .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message ?: _uiState.value.error) }
        }
    }

    private fun observeFeed() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            repository.observeOfficialFeed().collect { result ->
                result
                    .onSuccess { posts ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            posts = posts,
                            error = null
                        )
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: _uiState.value.error
                        )
                    }
            }
        }
    }

    private fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val hasPosts = _uiState.value.posts.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                isLoading = !hasPosts,
                isRefreshing = hasPosts,
                error = null
            )
            repository.refreshOfficialFeed()
                .onSuccess { posts ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        posts = posts,
                        error = null
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error.message ?: _uiState.value.error
                    )
                }
        }
    }

    private fun createPost(draft: com.quata.feature.official.domain.OfficialPostDraft) = viewModelScope.launch {
        if (_uiState.value.isPublishing) return@launch
        _uiState.value = _uiState.value.copy(isPublishing = true, error = null)
        repository.createPost(draft)
            .onSuccess { created ->
                val posts = if (created != null && _uiState.value.posts.none { it.id == created.id }) {
                    listOf(created) + _uiState.value.posts
                } else {
                    _uiState.value.posts
                }
                _uiState.value = _uiState.value.copy(
                    isPublishing = false,
                    posts = posts,
                    message = "ok"
                )
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isPublishing = false,
                    error = error.message ?: _uiState.value.error
                )
            }
    }

    private fun deletePost(postId: String) = viewModelScope.launch {
        repository.deletePost(postId)
            .onSuccess {
                _uiState.value = _uiState.value.copy(
                    posts = _uiState.value.posts.filterNot { it.id == postId }
                )
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message ?: _uiState.value.error)
            }
    }

    private fun updatePostFromRepository(action: suspend () -> Result<OfficialPostItem?>) = viewModelScope.launch {
        action()
            .onSuccess { updated ->
                if (updated != null) replacePost(updated)
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message ?: _uiState.value.error)
            }
    }

    private fun replacePost(updated: OfficialPostItem) {
        _uiState.value = _uiState.value.copy(
            posts = _uiState.value.posts.map { post ->
                if (post.id == updated.id) updated else post
            }
        )
    }

    companion object {
        fun factory(repository: OfficialRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OfficialFeedViewModel(repository) as T
        }
    }
}
