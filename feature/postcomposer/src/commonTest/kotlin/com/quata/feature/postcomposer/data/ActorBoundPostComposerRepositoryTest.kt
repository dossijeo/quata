package com.quata.feature.postcomposer.data

import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun moderationFailurePreventsWallLookupAndInsert() = runTest {
        val transport = RecordingTransport(moderationFailure = true)
        val result = ActorBoundPostComposerRepository(transport).createPost(PostComposerDraft(type = PostComposerType.Text, text = "No"))

        assertFalse(result.isSuccess)
        assertEquals(listOf("session", "moderate:actor-7"), transport.calls)
    }

    @Test
    fun failedInsertRollsBackUploadedMediaAndReleasesTemporaryFile() = runTest {
        val transport = RecordingTransport(insertFailure = true)
        val result = ActorBoundPostComposerRepository(transport).createPost(PostComposerDraft(type = PostComposerType.Image, imageUri = "file:///photo.png"))

        assertFalse(result.isSuccess)
        assertTrue(transport.calls.contains("rollback:storage:actor-7/img-1.png"))
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
        private val insertFailure: Boolean = false,
        private val insertId: String? = "post-db-9",
    ) : ActorBoundComposerTransport {
        val calls = mutableListOf<String>()
        var insert: ComposerPostInsert? = null
        override suspend fun renewableSession() = ComposerActorSession("actor-7", "Ada").also { calls += "session" }
        override suspend fun moderate(actor: ComposerActorSession, draft: PostComposerDraft): Result<Unit> = runCatching { calls += "moderate:${actor.profileId}"; if (moderationFailure) error("blocked") }
        override suspend fun resolveWallId(actorProfileId: String) = Result.success("wall-5").also { calls += "wall:$actorProfileId" }
        override suspend fun prepareImage(reference: String) = Result.success(ComposerPreparedMedia(reference, "photo.png", "image/png"))
        override suspend fun prepareVideo(reference: String) = Result.success(ComposerPreparedMedia(reference, "movie.mp4", "video/mp4"))
        override suspend fun uploadImage(actorProfileId: String, media: ComposerPreparedMedia) = Result.success(ComposerUploadedMedia("https://cdn/photo.png", "storage:actor-7/img-1.png"))
        override suspend fun uploadVideo(actorProfileId: String, media: ComposerPreparedMedia) = Result.success(ComposerUploadedMedia("https://wp/video.mp4", "https://wp/video.mp4"))
        override suspend fun insertPost(request: ComposerPostInsert): Result<String?> { insert = request; calls += "insert:${request.wallId}:${request.actorProfileId}"; return if (insertFailure) Result.failure(IllegalStateException("insert failed")) else Result.success(insertId) }
        override suspend fun rollbackUploadedMedia(media: ComposerUploadedMedia) = Result.success(Unit).also { calls += "rollback:${media.rollbackToken}" }
        override suspend fun releasePreparedMedia(media: ComposerPreparedMedia) = Result.success(Unit).also { calls += "release:${media.reference}" }
    }
}
