package com.quata.feature.postcomposer.data

import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerDestination
import com.quata.feature.postcomposer.domain.PostComposerType
import com.quata.core.text.extractPostMeta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ActorBoundPostComposerRepositoryTest {
    @Test
    fun publicationUsesAuthenticatedActorModerationMembershipAndActualInsertId() = runTest {
        val transport = RecordingTransport()
        val result = ActorBoundPostComposerRepository(transport).createPost(PostComposerDraft(type = PostComposerType.Text, text = "Hola"))

        assertEquals("post-db-9", result.getOrThrow())
        assertEquals(listOf("session", "moderate:actor-7", "wall:actor-7", "insert:wall-5:actor-7"), transport.calls)
        assertEquals("actor-7", transport.insert?.actorProfileId)
        assertEquals("wall-5", transport.insert?.wallId)
    }

    @Test
    fun destinationsAreLoadedFromAuthenticatedActor() = runTest {
        val transport = RecordingTransport(destinations = listOf(PostComposerDestination("wall-9", "Bata", isDefault = true)))
        val result = ActorBoundPostComposerRepository(transport).loadDestinations()

        assertEquals(listOf(PostComposerDestination("wall-9", "Bata", isDefault = true)), result.getOrThrow())
        assertEquals(listOf("session", "destinations:actor-7"), transport.calls)
    }

    @Test
    fun selectedDestinationWallIsUsedAfterAvailabilityCheck() = runTest {
        val transport = RecordingTransport(destinations = listOf(PostComposerDestination("wall-9", "Bata", isDefault = true)))
        val result = ActorBoundPostComposerRepository(transport).createPost(
            PostComposerDraft(type = PostComposerType.Text, text = "Hola", destinationWallId = "wall-9"),
        )

        assertEquals("post-db-9", result.getOrThrow())
        assertEquals(listOf("session", "moderate:actor-7", "destinations:actor-7", "insert:wall-9:actor-7"), transport.calls)
        assertEquals("wall-9", transport.insert?.wallId)
    }

    @Test
    fun imagePublicationCarriesLocationInSharedRemoteBody() = runTest {
        val transport = RecordingTransport(destinations = listOf(PostComposerDestination("wall-9", "Bata", isDefault = true)))
        val result = ActorBoundPostComposerRepository(transport).createPost(
            PostComposerDraft(
                type = PostComposerType.Image,
                imageUri = "file:///photo.png",
                locationLabel = "Malabo Centro",
                latitude = 3.7523,
                longitude = 8.7741,
                destinationWallId = "wall-9",
            ),
        )

        assertEquals("post-db-9", result.getOrThrow())
        assertEquals("Malabo Centro", transport.insert?.body?.extractPostMeta()?.imageLocation)
        assertEquals("feed", transport.insert?.body?.extractPostMeta()?.channel)
    }

    @Test
    fun unavailableDestinationFailsBeforeInsert() = runTest {
        val transport = RecordingTransport(destinations = listOf(PostComposerDestination("wall-9", "Bata", isDefault = true)))
        val result = ActorBoundPostComposerRepository(transport).createPost(
            PostComposerDraft(type = PostComposerType.Text, text = "Hola", destinationWallId = "wall-x"),
        )

        assertFalse(result.isSuccess)
        assertEquals("composer_destination_unavailable", result.exceptionOrNull()?.message)
        assertEquals(listOf("session", "moderate:actor-7", "destinations:actor-7"), transport.calls)
    }

    @Test
    fun moderationFailurePreventsWallLookupAndInsert() = runTest {
        val transport = RecordingTransport(moderationFailure = true)
        val result = ActorBoundPostComposerRepository(transport).createPost(PostComposerDraft(type = PostComposerType.Text, text = "No"))

        assertFalse(result.isSuccess)
        assertEquals(listOf("session", "moderate:actor-7"), transport.calls)
    }

    @Test
    fun failedInsertRollsBackUploadedMediaAndReleasesTemporaryFile() = runTest {
        val transport = RecordingTransport(insertFailure = IllegalStateException("insert failed"))
        val result = ActorBoundPostComposerRepository(transport).createPost(PostComposerDraft(type = PostComposerType.Image, imageUri = "file:///photo.png"))

        assertFalse(result.isSuccess)
        assertTrue(transport.calls.contains("rollback:storage:actor-7/img-1.png"))
        assertTrue(transport.calls.contains("release:file:///photo.png"))
    }

    @Test
    fun failAfterUploadDecoratorExercisesRollbackOnceThenAllowsRetry() = runTest {
        val transport = RecordingTransport()
        val repository = ActorBoundPostComposerRepository(FailInsertAfterUploadComposerTransport(transport))
        val draft = PostComposerDraft(type = PostComposerType.Image, imageUri = "file:///photo.png")

        val first = repository.createPost(draft)
        val second = repository.createPost(draft)

        assertFalse(first.isSuccess)
        assertEquals("post_composer_e2e_forced_insert_after_upload_failure", first.exceptionOrNull()?.message)
        assertEquals("post-db-9", second.getOrThrow())
        assertEquals(1, transport.calls.count { it == "rollback:storage:actor-7/img-1.png" })
        assertEquals(1, transport.calls.count { it == "insert:wall-5:actor-7" })
    }

    @Test
    fun cancellationIsRethrownAfterRollbackAndTemporaryRelease() = runTest {
        val transport = RecordingTransport(insertFailure = CancellationException("cancelled"))

        assertFailsWith<CancellationException> {
            ActorBoundPostComposerRepository(transport).createPost(
                PostComposerDraft(type = PostComposerType.Image, imageUri = "file:///photo.png"),
            )
        }

        assertTrue(transport.calls.contains("rollback:storage:actor-7/img-1.png"))
        assertTrue(transport.calls.contains("release:file:///photo.png"))
    }

    @Test
    fun rollbackFailureIsSuppressedWithoutHidingOriginalInsertFailure() = runTest {
        val transport = RecordingTransport(
            insertFailure = IllegalStateException("insert failed"),
            rollbackFailure = IllegalStateException("rollback failed"),
        )

        val failure = ActorBoundPostComposerRepository(transport)
            .createPost(PostComposerDraft(type = PostComposerType.Image, imageUri = "file:///photo.png"))
            .exceptionOrNull()

        assertEquals("insert failed", failure?.message)
        assertEquals("rollback failed", failure?.suppressedExceptions?.single()?.message)
        assertTrue(transport.calls.contains("release:file:///photo.png"))
    }

    @Test
    fun missingInsertIdIsPublicationFailureAndRollsBackUploadedMedia() = runTest {
        val transport = RecordingTransport(insertId = null)
        val result = ActorBoundPostComposerRepository(transport).createPost(PostComposerDraft(type = PostComposerType.Image, imageUri = "file:///photo.png"))

        assertFalse(result.isSuccess)
        assertEquals("composer_post_id_missing", result.exceptionOrNull()?.message)
        assertTrue(transport.calls.contains("rollback:storage:actor-7/img-1.png"))
        assertTrue(transport.calls.contains("release:file:///photo.png"))
    }

    private class RecordingTransport(
        private val moderationFailure: Boolean = false,
        private val insertFailure: Throwable? = null,
        private val insertId: String? = "post-db-9",
        private val rollbackFailure: Throwable? = null,
        private val destinations: List<PostComposerDestination>? = null,
    ) : ActorBoundComposerTransport {
        val calls = mutableListOf<String>()
        var insert: ComposerPostInsert? = null
        override suspend fun renewableSession() = ComposerActorSession("actor-7", "Ada").also { calls += "session" }
        override suspend fun moderate(actor: ComposerActorSession, draft: PostComposerDraft): Result<Unit> = runCatching { calls += "moderate:${actor.profileId}"; if (moderationFailure) error("blocked") }
        override suspend fun loadDestinations(actorProfileId: String): Result<List<PostComposerDestination>> =
            destinations?.let { Result.success(it).also { calls += "destinations:$actorProfileId" } }
                ?: super.loadDestinations(actorProfileId)
        override suspend fun resolveWallId(actorProfileId: String) = Result.success("wall-5").also { calls += "wall:$actorProfileId" }
        override suspend fun prepareImage(reference: String) = Result.success(ComposerPreparedMedia(reference, "photo.png", "image/png"))
        override suspend fun prepareVideo(reference: String) = Result.success(ComposerPreparedMedia(reference, "movie.mp4", "video/mp4"))
        override suspend fun uploadImage(actorProfileId: String, media: ComposerPreparedMedia) = Result.success(ComposerUploadedMedia("https://cdn/photo.png", "storage:actor-7/img-1.png"))
        override suspend fun uploadVideo(actorProfileId: String, media: ComposerPreparedMedia) = Result.success(ComposerUploadedMedia("https://wp/video.mp4", "https://wp/video.mp4"))
        override suspend fun insertPost(request: ComposerPostInsert): Result<String?> { insert = request; calls += "insert:${request.wallId}:${request.actorProfileId}"; return insertFailure?.let { Result.failure(it) } ?: Result.success(insertId) }
        override suspend fun rollbackUploadedMedia(media: ComposerUploadedMedia): Result<Unit> {
            calls += "rollback:${media.rollbackToken}"
            return rollbackFailure?.let { Result.failure(it) } ?: Result.success(Unit)
        }
        override suspend fun releasePreparedMedia(media: ComposerPreparedMedia) = Result.success(Unit).also { calls += "release:${media.reference}" }
    }
}
