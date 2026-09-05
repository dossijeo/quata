package com.quata.feature.official.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.feed.QuataPagedFeedStore
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OfficialFeedViewModel(
    private val repository: OfficialRepository,
    dispatchers: AppDispatchers = AppDispatchers(),
    initialCurrentUser: User? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _uiState = MutableStateFlow(OfficialFeedUiState(currentUser = initialCurrentUser))
    val uiState: StateFlow<OfficialFeedUiState> = _uiState.asStateFlow()
    private val feedStore = QuataPagedFeedStore(
        pageSize = OfficialFeedPageSize,
        idOf = OfficialPostItem::id,
        cursorOf = OfficialPostItem::createdAt
    )
    private var feedJob: Job? = null
    private var refreshJob: Job? = null
    private var loadOlderJob: Job? = null
    private var exactLoadedPosts: Map<String, OfficialPostItem> = emptyMap()

    init {
        observeFeed()
        refreshCurrentUser()
    }

    fun onEvent(event: OfficialFeedUiEvent) {
        when (event) {
            OfficialFeedUiEvent.Refresh -> refresh()
            OfficialFeedUiEvent.LoadOlderPage -> loadOlderPage()
            OfficialFeedUiEvent.ClearMessage -> _uiState.update { state -> state.copy(
                error = null,
                message = null,
                createdPostId = null
            ) }
            is OfficialFeedUiEvent.ToggleLike -> updatePostFromRepository { repository.toggleLike(event.postId) }
            is OfficialFeedUiEvent.AddComment -> addComment(event.postId, event.comment)
            is OfficialFeedUiEvent.ConfirmedCommentConsumed -> _uiState.update { state ->
                state.copy(confirmedCommentIds = state.confirmedCommentIds - event.commentId)
            }
            is OfficialFeedUiEvent.ReportComment -> reportComment(event.commentId)
            is OfficialFeedUiEvent.DeletePost -> deletePost(event.postId)
            is OfficialFeedUiEvent.CreatePost -> createPost(event.draft)
            is OfficialFeedUiEvent.CreatePosts -> createPosts(event.drafts)
            is OfficialFeedUiEvent.EnsurePostLoaded -> ensurePostLoaded(event.postId)
        }
    }

    fun refreshCurrentUser() {
        scope.launch {
            repository.refreshCurrentUser()
                .onSuccess { user -> _uiState.update { state -> state.copy(currentUser = user) } }
                .onFailure { error -> _uiState.update { state -> state.copy(error = error.message ?: state.error) } }
        }
    }

    private fun observeFeed() {
        feedStore.reset()
        _uiState.update { state -> state.copy(isLoading = true, error = null) }
        feedJob?.cancel()
        feedJob = scope.launch {
            repository.observeOfficialFeed().collect { result ->
                result
                    .onSuccess { posts ->
                        val mergedPosts = feedStore.setRealtime(posts.withExactLoadedPosts())
                        val hasMoreOlderPosts = feedStore.hasMoreOlderItems
                        _uiState.update { state -> state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            posts = mergedPosts.withLocalPendingCommentsFrom(state.posts),
                            hasMoreOlderPosts = hasMoreOlderPosts,
                            error = null
                        ) }
                    }
                    .onFailure { error ->
                        _uiState.update { state -> state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: state.error
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
            repository.refreshOfficialFeed()
                .onSuccess { posts ->
                    val mergedPosts = feedStore.replaceInitialPage(posts.withExactLoadedPosts())
                    val hasMoreOlderPosts = feedStore.hasMoreOlderItems
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
                        error = error.message ?: state.error
                    ) }
                }
        }
    }

    private fun loadOlderPage() {
        val state = _uiState.value
        if (loadOlderJob?.isActive == true) return
        if (state.posts.isEmpty() || !state.hasMoreOlderPosts) return
        val beforePublishedAt = feedStore.olderCursor()
        if (beforePublishedAt == null) {
            _uiState.update { it.copy(hasMoreOlderPosts = false) }
            return
        }
        loadOlderJob = scope.launch {
            _uiState.update { state -> state.copy(isLoadingOlder = true, error = null) }
            repository.loadOlderOfficialFeedPage(beforePublishedAt = beforePublishedAt, limit = OfficialFeedPageSize)
                .onSuccess { posts ->
                    val mergedPosts = feedStore.appendOlder(posts)
                    val hasMoreOlderPosts = feedStore.hasMoreOlderItems
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

    private fun createPost(draft: com.quata.feature.official.domain.OfficialPostDraft) = scope.launch {
        createPostsInternal(listOf(draft))
    }

    private fun createPosts(drafts: List<com.quata.feature.official.domain.OfficialPostDraft>) = scope.launch {
        createPostsInternal(drafts)
    }

    private suspend fun createPostsInternal(drafts: List<com.quata.feature.official.domain.OfficialPostDraft>) {
        if (_uiState.value.isPublishing) return
        _uiState.update { state -> state.copy(isPublishing = true, error = null) }
        repository.createPosts(drafts)
            .onSuccess { created ->
                val feedPosts = created?.let(feedStore::prependIfMissing)
                _uiState.update { state -> state.copy(
                    isPublishing = false,
                    posts = if (created != null && state.posts.none { it.id == created.id }) {
                        feedPosts.orEmpty().withLocalPendingCommentsFrom(state.posts)
                    } else {
                        state.posts
                    },
                    message = OfficialFeedMessages.PostCreated,
                    createdPostId = created?.id
                ) }
            }
            .onFailure { error ->
                _uiState.update { state -> state.copy(
                    isPublishing = false,
                    error = error.message ?: state.error
                ) }
            }
    }

    private fun deletePost(postId: String) = scope.launch {
        repository.deletePost(postId)
            .onSuccess {
                exactLoadedPosts = exactLoadedPosts - postId
                val feedPosts = feedStore.remove(postId)
                _uiState.update { state -> state.copy(
                    posts = feedPosts.withLocalPendingCommentsFrom(state.posts),
                    message = OfficialFeedMessages.PostDeleted,
                    error = null
                ) }
            }
            .onFailure { error ->
                _uiState.update { state -> state.copy(error = error.message ?: state.error) }
            }
    }

    private fun reportComment(commentId: String) = scope.launch {
        repository.reportComment(commentId).onSuccess {
            _uiState.update { state -> state.copy(message = OfficialFeedMessages.CommentReported, error = null) }
        }.onFailure {
            _uiState.update { state -> state.copy(message = OfficialFeedMessages.CommentReportFailed) }
        }
    }

    private fun ensurePostLoaded(postId: String) = scope.launch {
        if (_uiState.value.posts.any { it.id == postId }) return@launch
        repeat(FocusedPostLoadAttempts) { attempt ->
            repository.getOfficialPost(postId)
                .onSuccess { post ->
                    if (post != null) {
                        exactLoadedPosts = exactLoadedPosts + (post.id to post)
                        val feedPosts = feedStore.prependIfMissing(post)
                        _uiState.update { state -> state.copy(
                            posts = if (state.posts.none { it.id == post.id }) {
                                feedPosts.withLocalPendingCommentsFrom(state.posts)
                            } else {
                                state.posts
                            },
                            error = null
                        ) }
                        return@launch
                    }
                }
                .onFailure { error ->
                    _uiState.update { state -> state.copy(error = error.message ?: state.error) }
                }
            if (_uiState.value.posts.any { it.id == postId }) return@launch
            if (attempt < FocusedPostLoadAttempts - 1) delay(FocusedPostLoadRetryDelayMillis)
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
        action: suspend () -> Result<OfficialPostItem?>,
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

    private fun replacePost(updated: OfficialPostItem) {
        val feedPosts = feedStore.replace(updated).replacePostIfPresent(updated)
            ?: feedStore.prependIfMissing(updated)
        var reconciledForExactCache: OfficialPostItem? = null
        _uiState.update { state ->
            val reconciled = updated.withLocalPendingCommentsFrom(state.posts.firstOrNull { it.id == updated.id })
            reconciledForExactCache = reconciled
            val posts = state.posts.replacePostIfPresent(reconciled)
                ?: feedPosts.replacePostIfPresent(reconciled)
                ?: listOf(reconciled) + feedPosts
            state.copy(posts = posts)
        }
        reconciledForExactCache?.let { reconciled ->
            exactLoadedPosts = if (reconciled.id in exactLoadedPosts) exactLoadedPosts + (reconciled.id to reconciled) else exactLoadedPosts
        }
    }

    private fun removeLocalPendingComment(postId: String, comment: PostComment) {
        if (!comment.isLocalPendingComment()) return
        exactLoadedPosts = exactLoadedPosts.mapValues { (_, post) ->
            if (post.id == postId) post.withoutLocalPendingComment(comment) else post
        }
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
        exactLoadedPosts = exactLoadedPosts.mapValues { (_, post) ->
            if (post.id == postId) post.withoutLocalPendingComment(comment) else post
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

    private fun List<OfficialPostItem>.withExactLoadedPosts(): List<OfficialPostItem> =
        (this + exactLoadedPosts.values).distinctBy(OfficialPostItem::id)

    private fun appendLocalPendingComment(postId: String, comment: PostComment) {
        exactLoadedPosts = exactLoadedPosts.mapValues { (_, post) ->
            if (post.id == postId && post.comments.none { it.id == comment.id }) {
                post.copy(
                    comments = post.comments + comment,
                    commentsCount = (post.commentsCount + 1).coerceAtLeast(post.comments.size + 1),
                )
            } else {
                post
            }
        }
        val transformed = feedStore.replace(postId) { post ->
            if (post.comments.none { it.id == comment.id }) {
                post.copy(
                    comments = post.comments + comment,
                    commentsCount = (post.commentsCount + 1).coerceAtLeast(post.comments.size + 1),
                )
            } else {
                post
            }
        }
        _uiState.update { state -> state.copy(
            posts = (state.posts.takeIf { posts -> posts.any { it.id == postId } } ?: transformed).map { post ->
                if (post.id == postId && post.comments.none { it.id == comment.id }) {
                    post.copy(
                        comments = post.comments + comment,
                        commentsCount = (post.commentsCount + 1).coerceAtLeast(post.comments.size + 1),
                    )
                } else {
                    post
                }
            }
        ) }
    }

    private fun List<OfficialPostItem>.withLocalPendingCommentsFrom(existingPosts: List<OfficialPostItem>): List<OfficialPostItem> {
        val existingById = existingPosts.associateBy(OfficialPostItem::id)
        return map { post -> post.withLocalPendingCommentsFrom(existingById[post.id]) }
    }

    companion object {
        private const val OfficialFeedPageSize = 50
        private const val FocusedPostLoadAttempts = 4
        private const val FocusedPostLoadRetryDelayMillis = 750L

    }

    fun close() {
        feedJob?.cancel()
        refreshJob?.cancel()
        loadOlderJob?.cancel()
        scope.coroutineContext.cancel()
    }
}

internal fun OfficialPostItem.withLocalPendingCommentsFrom(existing: OfficialPostItem?): OfficialPostItem {
    val pending = existing?.comments.orEmpty()
        .filter { it.isLocalPendingComment() }
        .filterNot { pending -> comments.any { it.matchesLocalPendingComment(pending) } }
    return if (pending.isEmpty()) {
        this
    } else {
        copy(
            comments = comments + pending,
            commentsCount = commentsCount.coerceAtLeast(comments.size + pending.size),
        )
    }
}

internal fun OfficialPostItem.withoutLocalPendingComment(comment: PostComment): OfficialPostItem {
    if (!comment.isLocalPendingComment()) return this
    val filtered = comments.filterNot { it.id == comment.id && it.isLocalPendingComment() }
    val removed = comments.size - filtered.size
    return if (removed == 0) {
        this
    } else {
        copy(
            comments = filtered,
            commentsCount = (commentsCount - removed).coerceAtLeast(filtered.size),
        )
    }
}

private fun List<OfficialPostItem>.replacePostIfPresent(post: OfficialPostItem): List<OfficialPostItem>? =
    takeIf { posts -> posts.any { it.id == post.id } }?.map { current ->
        if (current.id == post.id) post else current
    }

private fun PostComment.isLocalPendingComment(): Boolean = id.startsWith("local_")

private fun PostComment.matchesLocalPendingComment(pending: PostComment): Boolean =
    message.trim() == pending.message.trim() &&
        replyToCommentId == pending.replyToCommentId &&
        replyToAuthorName == pending.replyToAuthorName
