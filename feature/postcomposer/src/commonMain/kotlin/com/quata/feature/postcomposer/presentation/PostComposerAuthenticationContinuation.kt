package com.quata.feature.postcomposer.presentation

import com.quata.feature.postcomposer.domain.PostComposerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PostComposerDraftSnapshot(
    val step: CreatePostStep,
    val text: String,
    val textPatternId: String,
    val imageUri: String?,
    val videoUri: String?,
    val locationLabel: String?,
    val latitude: Double?,
    val longitude: Double?,
    val locationOrigin: CreatePostLocationOrigin?,
    val selectedDestinationWallId: String?,
)

data class PostComposerAuthenticationContinuation(
    val requestId: Long,
    val draft: PostComposerDraftSnapshot,
    val submitType: PostComposerType,
)

/**
 * Owns the composer draft while Auth temporarily replaces its surface. Pending auto-submit and
 * retained editable state are separate so cancelling Auth can discard the former without losing
 * the latter.
 */
class PostComposerAuthenticationContinuationCoordinator {
    private val _pending = MutableStateFlow<PostComposerAuthenticationContinuation?>(null)
    val pending: StateFlow<PostComposerAuthenticationContinuation?> = _pending.asStateFlow()

    private val _retainedDraft = MutableStateFlow<PostComposerDraftSnapshot?>(null)
    val retainedDraft: StateFlow<PostComposerDraftSnapshot?> = _retainedDraft.asStateFlow()

    private var nextRequestId = 1L

    fun request(
        draft: PostComposerDraftSnapshot,
        submitType: PostComposerType,
    ): PostComposerAuthenticationContinuation {
        val continuation = PostComposerAuthenticationContinuation(nextRequestId++, draft, submitType)
        _retainedDraft.value = draft
        _pending.value = continuation
        return continuation
    }

    fun claim(requestId: Long): PostComposerAuthenticationContinuation? {
        val current = _pending.value ?: return null
        if (current.requestId != requestId) return null
        _pending.value = null
        return current
    }

    fun cancelAuthentication() {
        _pending.value = null
    }

    fun retain(draft: PostComposerDraftSnapshot) {
        _retainedDraft.value = draft
    }

    fun clear() {
        _pending.value = null
        _retainedDraft.value = null
    }
}
