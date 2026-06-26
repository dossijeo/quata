package com.quata.feature.feed.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.feature.feed.domain.FeedRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FeedViewModel(private val repository: FeedRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()
    private val loadedDetailPostIds = mutableSetOf<String>()
    private val loadingDetailPostIds = mutableSetOf<String>()
    private var feedJob: Job? = null
    private var refreshJob: Job? = null

    init { observeFeed() }

    fun onEvent(event: FeedUiEvent) {
        when (event) {
            FeedUiEvent.Refresh -> refresh()
            is FeedUiEvent.PostDisplayed -> loadDisplayedPostDetails(event.postId, event.nextPostId)
            is FeedUiEvent.ToggleLike -> updatePostFromRepository { repository.toggleLike(event.postId) }
            is FeedUiEvent.ReportPost -> updatePostFromRepository { repository.reportPost(event.postId) }
            is FeedUiEvent.AddComment -> updatePostFromRepository { repository.addComment(event.postId, event.comment) }
            is FeedUiEvent.DeletePost -> deletePost(event.postId)
        }
    }

    private fun observeFeed() {
        loadedDetailPostIds.clear()
        loadingDetailPostIds.clear()
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            repository.observeFeed().collect { result ->
                result
                    .onSuccess { posts ->
                        loadedDetailPostIds += posts.map { it.id }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            posts = posts,
                            error = null
                        )
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Error cargando feed"
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
            repository.refreshFeed()
                .onSuccess { posts ->
                    loadedDetailPostIds.clear()
                    loadingDetailPostIds.clear()
                    loadedDetailPostIds += posts.map { it.id }
                    _uiState.value = FeedUiState(isLoading = false, posts = posts)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error.message ?: "Error cargando feed"
                    )
                }
        }
    }

    private fun loadDisplayedPostDetails(postId: String, nextPostId: String?) {
        loadPostDetails(postId, reportErrors = true)
        nextPostId
            ?.takeIf { it != postId }
            ?.let { loadPostDetails(it, reportErrors = false) }
    }

    private fun loadPostDetails(postId: String, reportErrors: Boolean) {
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
                    if (reportErrors) {
                        _uiState.value = _uiState.value.copy(error = error.message ?: _uiState.value.error)
                    }
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

    private fun deletePost(postId: String) = viewModelScope.launch {
        repository.deletePost(postId)
            .onSuccess {
                loadedDetailPostIds -= postId
                loadingDetailPostIds -= postId
                _uiState.value = _uiState.value.copy(
                    posts = _uiState.value.posts.filterNot { it.id == postId }
                )
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
