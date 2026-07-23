package com.quata.feature.official.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.feed.QuataPagedFeedStore
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OfficialFeedViewModel(
    private val repository: OfficialRepository,
    dispatchers: AppDispatchers = AppDispatchers()
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _uiState = MutableStateFlow(OfficialFeedUiState())
    val uiState: StateFlow<OfficialFeedUiState> = _uiState.asStateFlow()
    private val feedStore = QuataPagedFeedStore(
        pageSize = OfficialFeedPageSize,
        idOf = OfficialPostItem::id,
        cursorOf = OfficialPostItem::createdAt
    )
    private var feedJob: Job? = null
    private var refreshJob: Job? = null
    private var loadOlderJob: Job? = null

    init {
        observeFeed()
        refreshCurrentUser()
    }

    fun onEvent(event: OfficialFeedUiEvent) {
        when (event) {
            OfficialFeedUiEvent.Refresh -> refresh()
            OfficialFeedUiEvent.LoadOlderPage -> loadOlderPage()
            OfficialFeedUiEvent.ClearMessage -> _uiState.value = _uiState.value.copy(
                error = null,
                message = null,
                createdPostId = null
            )
            is OfficialFeedUiEvent.ToggleLike -> updatePostFromRepository { repository.toggleLike(event.postId) }
            is OfficialFeedUiEvent.AddComment -> updatePostFromRepository { repository.addComment(event.postId, event.comment) }
            is OfficialFeedUiEvent.DeletePost -> deletePost(event.postId)
            is OfficialFeedUiEvent.CreatePost -> createPost(event.draft)
            is OfficialFeedUiEvent.CreatePosts -> createPosts(event.drafts)
            is OfficialFeedUiEvent.EnsurePostLoaded -> ensurePostLoaded(event.postId)
        }
    }

    fun refreshCurrentUser() {
        scope.launch {
            repository.refreshCurrentUser()
                .onSuccess { user -> _uiState.value = _uiState.value.copy(currentUser = user) }
                .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message ?: _uiState.value.error) }
        }
    }

    private fun observeFeed() {
        feedStore.reset()
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        feedJob?.cancel()
        feedJob = scope.launch {
            repository.observeOfficialFeed().collect { result ->
                result
                    .onSuccess { posts ->
                        val mergedPosts = feedStore.setRealtime(posts)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            posts = mergedPosts,
                            hasMoreOlderPosts = feedStore.hasMoreOlderItems,
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
        refreshJob = scope.launch {
            val hasPosts = _uiState.value.posts.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                isLoading = !hasPosts,
                isRefreshing = hasPosts,
                error = null
            )
            repository.refreshOfficialFeed()
                .onSuccess { posts ->
                    val mergedPosts = feedStore.replaceInitialPage(posts)
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
                        error = error.message ?: _uiState.value.error
                    )
                }
        }
    }

    private fun loadOlderPage() {
        val state = _uiState.value
        if (loadOlderJob?.isActive == true) return
        if (state.posts.isEmpty() || !state.hasMoreOlderPosts) return
        val beforePublishedAt = feedStore.olderCursor()
        if (beforePublishedAt == null) {
            _uiState.value = state.copy(hasMoreOlderPosts = false)
            return
        }
        loadOlderJob = scope.launch {
            _uiState.value = _uiState.value.copy(isLoadingOlder = true, error = null)
            repository.loadOlderOfficialFeedPage(beforePublishedAt = beforePublishedAt, limit = OfficialFeedPageSize)
                .onSuccess { posts ->
                    val mergedPosts = feedStore.appendOlder(posts)
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

    private fun createPost(draft: com.quata.feature.official.domain.OfficialPostDraft) = scope.launch {
        createPostsInternal(listOf(draft))
    }

    private fun createPosts(drafts: List<com.quata.feature.official.domain.OfficialPostDraft>) = scope.launch {
        createPostsInternal(drafts)
    }

    private suspend fun createPostsInternal(drafts: List<com.quata.feature.official.domain.OfficialPostDraft>) {
        if (_uiState.value.isPublishing) return
        _uiState.value = _uiState.value.copy(isPublishing = true, error = null)
        repository.createPosts(drafts)
            .onSuccess { created ->
                val posts = if (created != null && _uiState.value.posts.none { it.id == created.id }) {
                    feedStore.prependIfMissing(created)
                } else {
                    _uiState.value.posts
                }
                _uiState.value = _uiState.value.copy(
                    isPublishing = false,
                    posts = posts,
                    message = OfficialFeedMessages.PostCreated,
                    createdPostId = created?.id
                )
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isPublishing = false,
                    error = error.message ?: _uiState.value.error
                )
            }
    }

    private fun deletePost(postId: String) = scope.launch {
        repository.deletePost(postId)
            .onSuccess {
                _uiState.value = _uiState.value.copy(
                    posts = feedStore.remove(postId),
                    message = OfficialFeedMessages.PostDeleted,
                    error = null
                )
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message ?: _uiState.value.error)
            }
    }

    private fun ensurePostLoaded(postId: String) = scope.launch {
        if (_uiState.value.posts.any { it.id == postId }) return@launch
        repository.getOfficialPost(postId)
            .onSuccess { post ->
                if (post != null && _uiState.value.posts.none { it.id == post.id }) {
                    _uiState.value = _uiState.value.copy(
                        posts = feedStore.prependIfMissing(post),
                        error = null
                    )
                }
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message ?: _uiState.value.error)
            }
    }

    private fun updatePostFromRepository(action: suspend () -> Result<OfficialPostItem?>) = scope.launch {
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
            posts = feedStore.replace(updated)
        )
    }

    companion object {
        private const val OfficialFeedPageSize = 50

    }

    fun close() {
        feedJob?.cancel()
        refreshJob?.cancel()
        loadOlderJob?.cancel()
        scope.coroutineContext.cancel()
    }
}
