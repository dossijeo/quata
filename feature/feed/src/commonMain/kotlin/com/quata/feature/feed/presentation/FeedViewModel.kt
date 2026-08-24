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
import kotlinx.coroutines.flow.update
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
            is FeedUiEvent.ConfirmedCommentConsumed -> _uiState.update { state ->
                state.copy(confirmedCommentIds = state.confirmedCommentIds - event.commentId)
            }
            is FeedUiEvent.DeletePost -> deletePost(event.postId)
        }
    }

    private fun observeFeed() {
        loadedDetailPostIds.clear()
        loadingDetailPostIds.clear()
        feedStore.reset()
        _uiState.update { state -> state.copy(isLoading = true, error = null) }
        feedJob?.cancel()
        feedJob = scope.launch {
            repository.observeFeed().collect { result ->
                result
                    .onSuccess { posts ->
                        val mergedPosts = feedStore.setRealtime(posts)
                        val hasMoreOlderPosts = feedStore.hasMoreOlderItems
                        loadedDetailPostIds += posts.map { it.id }
                        _uiState.update { state -> state.copy(
                            isLoading = false,
                            posts = mergedPosts.withLocalPendingCommentsFrom(state.posts),
                            currentUser = state.currentUser,
                            hasMoreOlderPosts = hasMoreOlderPosts,
                            error = null
                        ) }
                    }
                    .onFailure { error ->
                        _uiState.update { state -> state.copy(
                            isLoading = false,
                            error = error.message ?: "Error cargando feed"
                        ) }
                    }
            }
        }
    }

    private fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            val hasPosts = _uiState.value.posts.isNotEmpty()
            _uiState.update { state -> state.copy(
                isLoading = !hasPosts,
                isRefreshing = hasPosts,
                error = null
            ) }
            repository.refreshFeed()
                .onSuccess { posts ->
                    val mergedPosts = feedStore.replaceInitialPage(posts)
                    val hasMoreOlderPosts = feedStore.hasMoreOlderItems
                    loadedDetailPostIds.clear()
                    loadingDetailPostIds.clear()
                    loadedDetailPostIds += posts.map { it.id }
                    _uiState.update { state -> state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingOlder = false,
                        hasMoreOlderPosts = hasMoreOlderPosts,
                        posts = mergedPosts.withLocalPendingCommentsFrom(state.posts),
                        error = null
                    ) }
                }
                .onFailure { error ->
                    _uiState.update { state -> state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error.message ?: "Error cargando feed"
                    ) }
                }
        }
    }

    private fun loadOlderPage() {
        val state = _uiState.value
        if (loadOlderJob?.isActive == true) return
        if (state.posts.isEmpty() || !state.hasMoreOlderPosts) return
        val beforeCreatedAt = feedStore.olderCursor()
        if (beforeCreatedAt == null) {
            _uiState.update { it.copy(hasMoreOlderPosts = false) }
            return
        }

        loadOlderJob = scope.launch {
            _uiState.update { state -> state.copy(isLoadingOlder = true, error = null) }
            repository.loadOlderFeedPage(beforeCreatedAt = beforeCreatedAt, limit = FeedPageSize)
                .onSuccess { posts ->
                    val mergedPosts = feedStore.appendOlder(posts)
                    val hasMoreOlderPosts = feedStore.hasMoreOlderItems
                    loadedDetailPostIds += posts.map { it.id }
                    _uiState.update { state -> state.copy(
                        isLoadingOlder = false,
                        hasMoreOlderPosts = hasMoreOlderPosts,
                        posts = mergedPosts.withLocalPendingCommentsFrom(state.posts),
                        error = null
                    ) }
                }
                .onFailure { error ->
                    _uiState.update { state -> state.copy(
                        isLoadingOlder = false,
                        error = error.message ?: state.error
                    ) }
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
                        val feedPosts = feedStore.prependIfMissing(post)
                        _uiState.update { state -> state.copy(
                            posts = feedPosts.withLocalPendingCommentsFrom(state.posts),
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                        ) }
                        loadedDetailPostIds += postId
                    }
                    loadingDetailPostIds -= postId
                }
                .onFailure { error ->
                    loadingDetailPostIds -= postId
                    _uiState.update { state -> state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error.message ?: state.error,
                    ) }
                }
        }
    }

    private fun refreshCurrentUser() = scope.launch {
        repository.refreshCurrentUser()
            .onSuccess { user ->
                _uiState.update { state -> state.copy(currentUser = user) }
            }
            .onFailure { error ->
                _uiState.update { state -> state.copy(error = error.message ?: state.error) }
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
                        _uiState.update { state -> state.copy(error = error.message ?: state.error) }
                    }
                }
        }
    }

    private fun replacePost(updated: Post) {
        val feedPosts = feedStore.replace(updated).replacePostIfPresent(updated)
            ?: feedStore.prependIfMissing(updated)
        _uiState.update { state ->
            val reconciled = updated.withLocalPendingCommentsFrom(state.posts.firstOrNull { it.id == updated.id })
            val posts = state.posts.replacePostIfPresent(reconciled)
                ?: feedPosts.replacePostIfPresent(reconciled)
                ?: listOf(reconciled) + feedPosts
            state.copy(posts = posts)
        }
    }

    private fun addComment(postId: String, comment: PostComment) {
        _uiState.update { state -> state.copy(
            commentErrorsByPostId = state.commentErrorsByPostId - postId,
            commentErrorsByCommentId = emptyMap(),
            confirmedCommentIds = state.confirmedCommentIds - comment.id,
        ) }
        appendLocalPendingComment(postId, comment)
        updatePostFromRepository(rollbackLocalComment = postId to comment) { repository.addComment(postId, comment) }
    }

    private fun updatePostFromRepository(
        rollbackLocalComment: Pair<String, PostComment>? = null,
        action: suspend () -> Result<Post?>,
    ) = scope.launch {
        action()
            .onSuccess { updated ->
                rollbackLocalComment?.let { (postId, comment) ->
                    _uiState.update { state -> state.copy(
                        commentErrorsByPostId = state.commentErrorsByPostId - postId,
                        commentErrorsByCommentId = state.commentErrorsByCommentId - comment.id,
                        confirmedCommentIds = (state.confirmedCommentIds + comment.id).toList().takeLast(4).toSet(),
                    ) }
                }
                if (updated != null) {
                    replacePost(updated)
                    loadedDetailPostIds += updated.id
                }
            }
            .onFailure { error ->
                val message = error.message ?: "Error enviando comentario"
                rollbackLocalComment?.let { (postId, comment) ->
                    rollbackLocalPendingCommentFailure(postId, comment, message)
                }
                _uiState.update { state -> state.copy(error = message) }
            }
    }

    private fun appendLocalPendingComment(postId: String, comment: PostComment) {
        val transformed = feedStore.replace(postId) { post ->
            if (post.comments.none { it.id == comment.id }) post.copy(comments = post.comments + comment) else post
        }
        _uiState.update { state -> state.copy(
            posts = (state.posts.takeIf { posts -> posts.any { it.id == postId } } ?: transformed).map { post ->
                if (post.id == postId && post.comments.none { it.id == comment.id }) {
                    post.copy(comments = post.comments + comment)
                } else {
                    post
                }
            }
        ) }
    }

    private fun removeLocalPendingComment(postId: String, comment: PostComment) {
        if (!comment.isLocalPendingComment()) return
        val transformed = feedStore.replace(postId) { it.withoutLocalPendingComment(comment) }
        _uiState.update { state -> state.copy(
            posts = (state.posts.takeIf { posts -> posts.any { it.id == postId } } ?: transformed).map { post ->
                if (post.id == postId) post.withoutLocalPendingComment(comment) else post
            }
        ) }
    }

    private fun rollbackLocalPendingCommentFailure(postId: String, comment: PostComment, message: String) {
        if (!comment.isLocalPendingComment()) {
            _uiState.update { state -> state.copy(
                commentErrorsByPostId = state.commentErrorsByPostId + (postId to message),
                commentErrorsByCommentId = (state.commentErrorsByCommentId + (comment.id to message)).takeLastEntries(4),
                confirmedCommentIds = state.confirmedCommentIds - comment.id,
            ) }
            return
        }
        val transformed = feedStore.replace(postId) { it.withoutLocalPendingComment(comment) }
        _uiState.update { state -> state.copy(
            posts = (state.posts.takeIf { posts -> posts.any { it.id == postId } } ?: transformed).map { post ->
                if (post.id == postId) post.withoutLocalPendingComment(comment) else post
            },
            commentErrorsByPostId = state.commentErrorsByPostId + (postId to message),
            commentErrorsByCommentId = (state.commentErrorsByCommentId + (comment.id to message)).takeLastEntries(4),
            confirmedCommentIds = state.confirmedCommentIds - comment.id,
        ) }
    }

    private fun <K, V> Map<K, V>.takeLastEntries(limit: Int): Map<K, V> =
        entries.toList().takeLast(limit).associate { it.toPair() }

    private fun List<Post>.withLocalPendingCommentsFrom(existingPosts: List<Post>): List<Post> {
        val existingById = existingPosts.associateBy(Post::id)
        return map { post -> post.withLocalPendingCommentsFrom(existingById[post.id]) }
    }

    private fun deletePost(postId: String) = scope.launch {
        repository.deletePost(postId)
            .onSuccess {
                loadedDetailPostIds -= postId
                loadingDetailPostIds -= postId
                val feedPosts = feedStore.remove(postId)
                _uiState.update { state -> state.copy(
                    posts = feedPosts.withLocalPendingCommentsFrom(state.posts)
                ) }
            }
            .onFailure { error ->
                _uiState.update { state -> state.copy(error = error.message ?: state.error) }
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

private fun List<Post>.replacePostIfPresent(post: Post): List<Post>? =
    takeIf { posts -> posts.any { it.id == post.id } }?.map { current ->
        if (current.id == post.id) post else current
    }

private fun PostComment.isLocalPendingComment(): Boolean = id.startsWith("local_")

private fun PostComment.matchesLocalPendingComment(pending: PostComment): Boolean =
    message.trim() == pending.message.trim() &&
        replyToCommentId == pending.replyToCommentId &&
        replyToAuthorName == pending.replyToAuthorName
