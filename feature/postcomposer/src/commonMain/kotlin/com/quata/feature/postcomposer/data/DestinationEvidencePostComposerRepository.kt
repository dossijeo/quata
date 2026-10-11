package com.quata.feature.postcomposer.data

import com.quata.feature.postcomposer.domain.PostComposerDestination
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerRepository

/**
 * Opt-in evidence decorator for destination loading states. It is installed only by localhost or
 * DEBUG visual gates so platform runners can exercise the shared commonMain UI without mutating
 * backend post data.
 */
class DestinationEvidencePostComposerRepository(
    private val delegate: PostComposerRepository,
    private val mode: String,
) : PostComposerRepository {
    override suspend fun loadDestinations(): Result<List<PostComposerDestination>> =
        when (mode.lowercase()) {
            "empty" -> Result.success(emptyList())
            "failure" -> Result.failure(IllegalStateException("post_composer_e2e_destination_load_failed"))
            else -> delegate.loadDestinations()
        }

    override suspend fun createPost(draft: PostComposerDraft): Result<String?> =
        delegate.createPost(draft)
}
