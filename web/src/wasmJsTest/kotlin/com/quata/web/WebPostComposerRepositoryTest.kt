package com.quata.web

import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebPostComposerRepositoryTest {
    @Test
    fun publicationIsFailClosedUntilTheActorWallAndStorageContractIsVerified() {
        var result: Result<String?>? = null
        suspend { WebPostComposerPublicationUnavailableRepository.createPost(PostComposerDraft(PostComposerType.Text, text = "draft")) }
            .startCoroutine(object : Continuation<Result<String?>> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(outcome: Result<Result<String?>>) {
                    result = outcome.getOrThrow()
                }
            })

        assertTrue(requireNotNull(result).isFailure)
        assertEquals(
            "web_composer_publication_contract_unverified",
            requireNotNull(result?.exceptionOrNull()).message,
        )
    }
}
