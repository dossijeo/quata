package com.quata.web

import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerRepository

/**
 * Explicit Web publication boundary.
 *
 * `SEC-MUTATIONS-001` records that the deployed contract has not proved all of: the
 * server-derived actor, membership of the selected wall, and an owner-only Storage prefix.
 * Until that evidence exists, this adapter must not issue either the Storage upload or the
 * `community_posts` POST. Keeping the failure at the repository boundary makes the shared
 * composer report a failed submission instead of treating a local draft as published.
 */
internal object WebPostComposerPublicationUnavailableRepository : PostComposerRepository {
    override suspend fun createPost(draft: PostComposerDraft): Result<String?> = Result.failure(
        webComposerPublicationUnavailableFailure(),
    )
}

internal fun webComposerPublicationUnavailableFailure(): IllegalStateException =
    IllegalStateException("web_composer_publication_contract_unverified")
