package com.quata.feature.feed.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.feature.feed.domain.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeedViewModel(private val repository: FeedRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()
    private val loadedDetailPostIds = mutableSetOf<String>()
    private val loadingDetailPostIds = mutableSetOf<String>()

    init { load() }

    fun onEvent(event: FeedUiEvent) {
        when (event) {
            FeedUiEvent.Refresh -> load()
            is FeedUiEvent.PostDisplayed -> loadDisplayedPostDetails(event.postId)
            is FeedUiEvent.ToggleLike -> updatePostFromRepository { repository.toggleLike(event.postId) }
            is FeedUiEvent.ReportPost -> updatePostFromRepository { repository.reportPost(event.postId) }
            is FeedUiEvent.AddComment -> updatePostFromRepository { repository.addComment(event.postId, event.comment) }
        }
    }

    private fun load() = viewModelScope.launch {
        loadedDetailPostIds.clear()
        loadingDetailPostIds.clear()
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        repository.getFeed()
            .onSuccess { _uiState.value = FeedUiState(isLoading = false, posts = it) }
            .onFailure { _uiState.value = FeedUiState(isLoading = false, error = it.message ?: "Error cargando feed") }
    }

    private fun loadDisplayedPostDetails(postId: String) {
        if (postId in loadedDetailPostIds || postId in loadingDetailPostIds) return
        if (_uiState.value.posts.none { it.id == postId }) return
        loadingDetailPostIds += postId
        viewModelScope.launch {
            repository.refreshPost(postId)
                .onSuccess { updated ->
                    if (updated != null) {
                        replacePost(updated)
                        loadedDetailPostIds += postId
                    }
                    loadingDetailPostIds -= postId
                }
                .onFailure { error ->
                    loadingDetailPostIds -= postId
                    _uiState.value = _uiState.value.copy(error = error.message ?: _uiState.value.error)
                }
        }
    }

    private fun replacePost(updated: com.quata.core.model.Post) {
        _uiState.value = _uiState.value.copy(
            posts = _uiState.value.posts.map { post ->
                if (post.id == updated.id) updated else post
            }
        )
    }

    private fun updatePostFromRepository(action: suspend () -> Result<com.quata.core.model.Post?>) = viewModelScope.launch {
        action()
            .onSuccess { updated ->
                if (updated != null) {
                    replacePost(updated)
                    loadedDetailPostIds += updated.id
                }
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message ?: _uiState.value.error)
            }
    }

    companion object {
        fun factory(repository: FeedRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = FeedViewModel(repository) as T
        }
    }
}
