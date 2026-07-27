package com.quata.feature.postcomposer.data

import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosComposerPublicationTransportTest {
    @Test
    fun defaultCapabilityFailsClosedBeforeActorLookupOrTransportPublication() = runTest {
        var actorLookupCount = 0
        val transport = RecordingTransport()
        val repository = IosActorBoundPostComposerRepository(
            authenticatedActorId = {
                actorLookupCount += 1
                "actor-42"
            },
            transport = transport,
        )

        val result = repository.createPost(PostComposerDraft(type = PostComposerType.Text, text = "Hola"))

        assertFalse(result.isSuccess)
        assertEquals("ios_composer_publication_contract_not_verified", result.exceptionOrNull()?.message)
        assertEquals(0, actorLookupCount)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun verifiedInternalTransportBindsTheTrimmedAuthenticatedActorToTheDraft() = runTest {
        val transport = RecordingTransport()
        val draft = PostComposerDraft(type = PostComposerType.Text, text = "Hola")
        val repository = IosActorBoundPostComposerRepository(
            authenticatedActorId = { " actor-42 " },
            transport = transport,
            capability = VerifiedIosComposerRlsAndStorageContract,
        )

        assertEquals("post-1", repository.createPost(draft).getOrThrow())
        assertEquals(IosActorBoundPublicationRequest(actorId = "actor-42", draft = draft), transport.requests.single())
    }

    private class RecordingTransport : IosComposerPublicationTransport {
        val requests = mutableListOf<IosActorBoundPublicationRequest>()

        override suspend fun publish(request: IosActorBoundPublicationRequest): Result<String?> {
            requests += request
            return Result.success("post-1")
        }
    }
}
