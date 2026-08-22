package com.quata.feature.feed.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.feed.QuataPagedFeedStore
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.feature.feed.domain.FeedRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

interface FeedStateHolder {
    val uiState: StateFlow<FeedUiState>
    fun onEvent(event: FeedUiEvent)
}

class FeedViewModel(
    private val repository: FeedRepository,
    dispatchers: AppDispatchers = AppDispatchers()
) : FeedStateHolder {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _uiState = MutableStateFlow(FeedUiState())
    override val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()
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

    override fun onEvent(event: FeedUiEvent) {
        when (event) {
            FeedUiEvent.Refresh -> refresh()
            FeedUiEvent.LoadOlderPage -> loadOlderPage()
            is FeedUiEvent.FocusPost -> focusPost(event.postId)
            is FeedUiEvent.PostDisplayed -> loadDisplayedPostDetails(event.postId, event.nextPostId)
            is FeedUiEvent.ToggleLike -> updatePostFromRepository { repository.toggleLike(event.postId) }
            is FeedUiEvent.ReportPost -> updatePostFromRepository { repository.reportPost(event.postId) }
            is FeedUiEvent.AddComment -> addComment(event.postId, event.comment)
            is FeedUiEvent.DeletePost -> deletePost(event.postId)
        }
    }

    private fun observeFeed() {
        loadedDetailPostIds.clear()
        loadingDetailPostIds.clear()
        feedStore.reset()
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        feedJob?.cancel()
        feedJob = scope.launch {
            repository.observeFeed().collect { result ->
                result
                    .onSuccess { posts ->
                        val mergedPosts = feedStore.setRealtime(posts.withLocalPendingComments())
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
        refreshJob = scope.launch {
            val hasPosts = _uiState.value.posts.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                isLoading = !hasPosts,
                isRefreshing = hasPosts,
                error = null
            )
            repository.refreshFeed()
                .onSuccess { posts ->
                    val mergedPosts = feedStore.replaceInitialPage(posts.withLocalPendingComments())
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

        loadOlderJob = scope.launch {
            _uiState.value = _uiState.value.copy(isLoadingOlder = true, error = null)
            repository.loadOlderFeedPage(beforeCreatedAt = beforeCreatedAt, limit = FeedPageSize)
                .onSuccess { posts ->
                    val mergedPosts = feedStore.appendOlder(posts.withLocalPendingComments())
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

    private fun focusPost(postId: String) {
        if (postId in loadingDetailPostIds) return
        if (_uiState.value.posts.any { it.id == postId }) {
            loadPostDetails(postId, reportErrors = true)
            return
        }
        loadingDetailPostIds += postId
        scope.launch {
            repository.refreshPost(postId)
                .onSuccess { post ->
                    if (post != null) {
                        _uiState.value = _uiState.value.copy(
                            posts = feedStore.prependIfMissing(post),
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                        )
                        loadedDetailPostIds += postId
                    }
                    loadingDetailPostIds -= postId
                }
                .onFailure { error ->
                    loadingDetailPostIds -= postId
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error.message ?: _uiState.value.error,
                    )
                }
        }
    }

    private fun refreshCurrentUser() = scope.launch {
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
        scope.launch {
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
        val reconciled = updated.withLocalPendingCommentsFrom(_uiState.value.posts.firstOrNull { it.id == updated.id })
        _uiState.value = _uiState.value.copy(
            posts = feedStore.replace(reconciled)
        )
    }

    private fun addComment(postId: String, comment: PostComment) {
        appendLocalPendingComment(postId, comment)
        updatePostFromRepository(rollbackLocalComment = postId to comment) { repository.addComment(postId, comment) }
    }

    private fun updatePostFromRepository(
        rollbackLocalComment: Pair<String, PostComment>? = null,
        action: suspend () -> Result<Post?>,
    ) = scope.launch {
        action()
            .onSuccess { updated ->
                if (updated != null) {
                    replacePost(updated)
                    loadedDetailPostIds += updated.id
                }
            }
            .onFailure { error ->
                rollbackLocalComment?.let { (postId, comment) -> removeLocalPendingComment(postId, comment) }
                _uiState.value = _uiState.value.copy(error = error.message ?: _uiState.value.error)
            }
    }

    private fun appendLocalPendingComment(postId: String, comment: PostComment) {
        val transformed = feedStore.replace(postId) { post ->
            if (post.comments.none { it.id == comment.id }) post.copy(comments = post.comments + comment) else post
        }
        val posts = transformed.takeIf { feedPosts -> feedPosts.any { it.id == postId } } ?: _uiState.value.posts
        _uiState.value = _uiState.value.copy(
            posts = posts.map { post ->
                if (post.id == postId && post.comments.none { it.id == comment.id }) {
                    post.copy(comments = post.comments + comment)
                } else {
                    post
                }
            }
        )
    }

    private fun removeLocalPendingComment(postId: String, comment: PostComment) {
        if (!comment.isLocalPendingComment()) return
        val transformed = feedStore.replace(postId) { it.withoutLocalPendingComment(comment) }
        val posts = transformed.takeIf { feedPosts -> feedPosts.any { it.id == postId } } ?: _uiState.value.posts
        _uiState.value = _uiState.value.copy(
            posts = posts.map { post ->
                if (post.id == postId) post.withoutLocalPendingComment(comment) else post
            }
        )
    }

    private fun List<Post>.withLocalPendingComments(): List<Post> {
        val existingById = _uiState.value.posts.associateBy(Post::id)
        return map { post -> post.withLocalPendingCommentsFrom(existingById[post.id]) }
    }

    private fun deletePost(postId: String) = scope.launch {
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

    fun close() {
        scope.cancel()
    }

    private companion object {
        const val FeedPageSize = 50
    }
}

internal fun Post.withLocalPendingCommentsFrom(existing: Post?): Post {
    val pending = existing?.comments.orEmpty()
        .filter { it.isLocalPendingComment() }
        .filterNot { pending -> comments.any { it.matchesLocalPendingComment(pending) } }
    return if (pending.isEmpty()) this else copy(comments = comments + pending)
}

internal fun Post.withoutLocalPendingComment(comment: PostComment): Post {
    if (!comment.isLocalPendingComment()) return this
    val filtered = comments.filterNot { it.id == comment.id && it.isLocalPendingComment() }
    return if (filtered.size == comments.size) this else copy(comments = filtered)
}

private fun PostComment.isLocalPendingComment(): Boolean = id.startsWith("local_")

private fun PostComment.matchesLocalPendingComment(pending: PostComment): Boolean =
    message.trim() == pending.message.trim() &&
        replyToCommentId == pending.replyToCommentId &&
        replyToAuthorName == pending.replyToAuthorName
