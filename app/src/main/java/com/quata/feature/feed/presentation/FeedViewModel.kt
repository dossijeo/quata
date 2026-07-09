package com.quata.feature.feed.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.core.feed.QuataPagedFeedStore
import com.quata.core.model.Post
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
    private val feedStore = QuataPagedFeedStore(
        pageSize = FeedPageSize,
        idOf = Post::id,
        cursorOf = Post::createdAt
    )
    private var feedJob: Job? = null
    private var refreshJob: Job? = null
    private var loadOlderJob: Job? = null

    init {
        observeFeed()
        refreshCurrentUser()
    }

    fun onEvent(event: FeedUiEvent) {
        when (event) {
            FeedUiEvent.Refresh -> refresh()
            FeedUiEvent.LoadOlderPage -> loadOlderPage()
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
        feedStore.reset()
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            repository.observeFeed().collect { result ->
                result
                    .onSuccess { posts ->
                        val mergedPosts = feedStore.setRealtime(posts)
                        loadedDetailPostIds += posts.map { it.id }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            posts = mergedPosts,
                            currentUser = _uiState.value.currentUser,
                            hasMoreOlderPosts = feedStore.hasMoreOlderItems,
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
                    val mergedPosts = feedStore.replaceInitialPage(posts)
                    loadedDetailPostIds.clear()
                    loadingDetailPostIds.clear()
                    loadedDetailPostIds += posts.map { it.id }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingOlder = false,
                        hasMoreOlderPosts = feedStore.hasMoreOlderItems,
                        posts = mergedPosts,
                        error = null
                    )
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

    private fun loadOlderPage() {
        val state = _uiState.value
        if (loadOlderJob?.isActive == true) return
        if (state.posts.isEmpty() || !state.hasMoreOlderPosts) return
        val beforeCreatedAt = feedStore.olderCursor()
        if (beforeCreatedAt == null) {
            _uiState.value = state.copy(hasMoreOlderPosts = false)
            return
        }

        loadOlderJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingOlder = true, error = null)
            repository.loadOlderFeedPage(beforeCreatedAt = beforeCreatedAt, limit = FeedPageSize)
                .onSuccess { posts ->
                    val mergedPosts = feedStore.appendOlder(posts)
                    loadedDetailPostIds += posts.map { it.id }
                    _uiState.value = _uiState.value.copy(
                        isLoadingOlder = false,
                        hasMoreOlderPosts = feedStore.hasMoreOlderItems,
                        posts = mergedPosts,
                        error = null
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingOlder = false,
                        error = error.message ?: _uiState.value.error
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

    private fun refreshCurrentUser() = viewModelScope.launch {
        repository.refreshCurrentUser()
            .onSuccess { user ->
                _uiState.value = _uiState.value.copy(currentUser = user)
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message ?: _uiState.value.error)
            }
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

    private fun replacePost(updated: Post) {
        _uiState.value = _uiState.value.copy(
            posts = feedStore.replace(updated)
        )
    }

    private fun updatePostFromRepository(action: suspend () -> Result<Post?>) = viewModelScope.launch {
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
                    posts = feedStore.remove(postId)
                )
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message ?: _uiState.value.error)
            }
    }

    companion object {
        private const val FeedPageSize = 50

        fun factory(repository: FeedRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = FeedViewModel(repository) as T
        }
    }
}
