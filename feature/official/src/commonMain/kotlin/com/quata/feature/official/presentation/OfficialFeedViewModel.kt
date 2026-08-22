package com.quata.feature.official.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.feed.QuataPagedFeedStore
import com.quata.core.model.PostComment
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
    private var exactLoadedPosts: Map<String, OfficialPostItem> = emptyMap()

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
            is OfficialFeedUiEvent.AddComment -> addComment(event.postId, event.comment)
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
                        val mergedPosts = feedStore.setRealtime(posts.withExactLoadedPosts().withLocalPendingComments())
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
                    val mergedPosts = feedStore.replaceInitialPage(posts.withExactLoadedPosts().withLocalPendingComments())
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
                    val mergedPosts = feedStore.appendOlder(posts.withLocalPendingComments())
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
                exactLoadedPosts = exactLoadedPosts - postId
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

    private fun reportComment(commentId: String) = scope.launch {
        repository.reportComment(commentId).onSuccess {
            _uiState.value = _uiState.value.copy(message = OfficialFeedMessages.CommentReported, error = null)
        }.onFailure {
            _uiState.value = _uiState.value.copy(message = OfficialFeedMessages.CommentReportFailed)
        }
    }

    private fun ensurePostLoaded(postId: String) = scope.launch {
        if (_uiState.value.posts.any { it.id == postId }) return@launch
        repeat(FocusedPostLoadAttempts) { attempt ->
            repository.getOfficialPost(postId)
                .onSuccess { post ->
                    if (post != null && _uiState.value.posts.none { it.id == post.id }) {
                        exactLoadedPosts = exactLoadedPosts + (post.id to post)
                        _uiState.value = _uiState.value.copy(
                            posts = feedStore.prependIfMissing(post),
                            error = null
                        )
                        return@launch
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message ?: _uiState.value.error)
                }
            if (_uiState.value.posts.any { it.id == postId }) return@launch
            if (attempt < FocusedPostLoadAttempts - 1) delay(FocusedPostLoadRetryDelayMillis)
        }
    }

    private fun addComment(postId: String, comment: PostComment) {
        appendLocalPendingComment(postId, comment)
        updatePostFromRepository(rollbackLocalComment = postId to comment) { repository.addComment(postId, comment) }
    }

    private fun updatePostFromRepository(
        rollbackLocalComment: Pair<String, PostComment>? = null,
        action: suspend () -> Result<OfficialPostItem?>,
    ) = scope.launch {
        action()
            .onSuccess { updated ->
                if (updated != null) replacePost(updated)
            }
            .onFailure { error ->
                rollbackLocalComment?.let { (postId, comment) -> removeLocalPendingComment(postId, comment) }
                _uiState.value = _uiState.value.copy(error = error.message ?: _uiState.value.error)
            }
    }

    private fun replacePost(updated: OfficialPostItem) {
        val reconciled = updated.withLocalPendingCommentsFrom(_uiState.value.posts.firstOrNull { it.id == updated.id })
        exactLoadedPosts = if (reconciled.id in exactLoadedPosts) exactLoadedPosts + (reconciled.id to reconciled) else exactLoadedPosts
        _uiState.value = _uiState.value.copy(
            posts = feedStore.replace(reconciled)
        )
    }

    private fun removeLocalPendingComment(postId: String, comment: PostComment) {
        if (!comment.isLocalPendingComment()) return
        _uiState.value = _uiState.value.copy(
            posts = _uiState.value.posts.map { post ->
                if (post.id == postId) post.withoutLocalPendingComment(comment) else post
            }
        )
    }

    private fun List<OfficialPostItem>.withExactLoadedPosts(): List<OfficialPostItem> =
        (this + exactLoadedPosts.values).distinctBy(OfficialPostItem::id)

    private fun appendLocalPendingComment(postId: String, comment: PostComment) {
        _uiState.value = _uiState.value.copy(
            posts = _uiState.value.posts.map { post ->
                if (post.id == postId && post.comments.none { it.id == comment.id }) {
                    post.copy(
                        comments = post.comments + comment,
                        commentsCount = (post.commentsCount + 1).coerceAtLeast(post.comments.size + 1),
                    )
                } else {
                    post
                }
            }
        )
    }

    private fun List<OfficialPostItem>.withLocalPendingComments(): List<OfficialPostItem> {
        val existingById = _uiState.value.posts.associateBy(OfficialPostItem::id)
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

private fun PostComment.isLocalPendingComment(): Boolean = id.startsWith("local_")

private fun PostComment.matchesLocalPendingComment(pending: PostComment): Boolean =
    message.trim() == pending.message.trim() &&
        replyToCommentId == pending.replyToCommentId &&
        replyToAuthorName == pending.replyToAuthorName
