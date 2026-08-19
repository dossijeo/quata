package com.quata.feature.postcomposer.data

import com.quata.feature.postcomposer.domain.PostComposerDestination
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerRepository

/**
 * Opt-in evidence decorator that fails the first publish before any remote mutation, then delegates
 * unchanged. Production routes must only install it from explicit localhost/DEBUG evidence gates.
 */
class FailOncePostComposerRepository(
    private val delegate: PostComposerRepository,
    private val failureMessage: String = "post_composer_e2e_forced_first_publish_failure",
) : PostComposerRepository {
    private var hasFailed = false

    override suspend fun loadDestinations(): Result<List<PostComposerDestination>> =
        delegate.loadDestinations()

    override suspend fun createPost(draft: PostComposerDraft): Result<String?> {
        if (!hasFailed) {
            hasFailed = true
            return Result.failure(IllegalStateException(failureMessage))
        }
        return delegate.createPost(draft)
    }
}
