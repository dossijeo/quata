package com.quata.feature.postcomposer.data

import com.quata.core.text.buildPostBodyWithMeta
import com.quata.feature.postcomposer.domain.PostComposerDestination
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class ComposerActorSession(val profileId: String, val displayName: String)
data class ComposerPreparedMedia(val reference: String, val name: String, val mimeType: String)
data class ComposerUploadedMedia(val publicUrl: String, val rollbackToken: String)
data class ComposerPostInsert(val actorProfileId: String, val wallId: String, val body: String, val imageUrl: String? = null, val videoUrl: String? = null)

fun composerModerationFields(
    actor: ComposerActorSession,
    draft: PostComposerDraft,
    mediaName: String,
    mediaType: String,
    sourceUrl: String,
): Map<String, String> = mapOf(
    "action" to "quqos_moderate_content",
    "context" to "post",
    "text" to draft.toRemoteText(),
    "image_name" to mediaName,
    "image_type" to mediaType,
    "image_score" to "0",
    "display_name" to actor.displayName,
    "profile_id" to actor.profileId,
    "url" to sourceUrl,
)

/** Platform boundary: actor is session-bound; destinations come from the authenticated backend. */
interface ActorBoundComposerTransport {
    suspend fun renewableSession(): ComposerActorSession?
    suspend fun moderate(actor: ComposerActorSession, draft: PostComposerDraft): Result<Unit>
    suspend fun loadDestinations(actorProfileId: String): Result<List<PostComposerDestination>> =
        resolveWallId(actorProfileId).map { wallId -> listOf(PostComposerDestination(wallId, "Feed", isDefault = true)) }
    suspend fun resolveWallId(actorProfileId: String): Result<String>
    suspend fun prepareImage(reference: String): Result<ComposerPreparedMedia>
    suspend fun prepareVideo(reference: String): Result<ComposerPreparedMedia>
    suspend fun uploadImage(actorProfileId: String, media: ComposerPreparedMedia): Result<ComposerUploadedMedia>
    suspend fun uploadVideo(actorProfileId: String, media: ComposerPreparedMedia): Result<ComposerUploadedMedia>
    suspend fun insertPost(request: ComposerPostInsert): Result<String?>
    suspend fun rollbackUploadedMedia(media: ComposerUploadedMedia): Result<Unit>
    suspend fun releasePreparedMedia(media: ComposerPreparedMedia): Result<Unit> = Result.success(Unit)
}

class ActorBoundPostComposerRepository(private val transport: ActorBoundComposerTransport) : PostComposerRepository {
    override suspend fun loadDestinations(): Result<List<PostComposerDestination>> = runCatching {
        val actor = transport.renewableSession() ?: error("composer_authenticated_actor_missing")
        require(actor.profileId.isNotBlank()) { "composer_authenticated_actor_missing" }
        transport.loadDestinations(actor.profileId).getOrThrow()
            .filter { it.wallId.isNotBlank() && it.label.isNotBlank() }
            .distinctBy { it.wallId }
    }

    override suspend fun createPost(draft: PostComposerDraft): Result<String?> = try {
        validateComposerDraft(draft)
        val actor = transport.renewableSession() ?: error("composer_authenticated_actor_missing")
        require(actor.profileId.isNotBlank()) { "composer_authenticated_actor_missing" }
        transport.moderate(actor, draft).getOrThrow()
        val wallId = draft.destinationWallId?.takeIf(String::isNotBlank)?.let { requested ->
            val destinations = transport.loadDestinations(actor.profileId).getOrThrow()
            require(destinations.any { it.wallId == requested }) { "composer_destination_unavailable" }
            requested
        } ?: transport.resolveWallId(actor.profileId).getOrThrow()
        require(wallId.isNotBlank()) { "composer_wall_unavailable" }
        var prepared: ComposerPreparedMedia? = null
        var uploaded: ComposerUploadedMedia? = null
        try {
            uploaded = when (draft.type) {
                PostComposerType.Text -> null
                PostComposerType.Image -> transport.prepareImage(requireNotNull(draft.imageUri)).getOrThrow().also { prepared = it }.let { transport.uploadImage(actor.profileId, it).getOrThrow() }
                PostComposerType.Video -> transport.prepareVideo(requireNotNull(draft.videoUri)).getOrThrow().also { prepared = it }.let { transport.uploadVideo(actor.profileId, it).getOrThrow() }
            }
            val postId = transport.insertPost(
                ComposerPostInsert(actor.profileId, wallId, draft.toRemoteText(), uploaded?.publicUrl?.takeIf { draft.type == PostComposerType.Image }, uploaded?.publicUrl?.takeIf { draft.type == PostComposerType.Video }),
            ).getOrThrow()?.takeIf(String::isNotBlank) ?: error("composer_post_id_missing")
            Result.success(postId)
        } catch (failure: Throwable) {
            uploaded?.let { orphan -> withContext(NonCancellable) { transport.rollbackUploadedMedia(orphan).exceptionOrNull()?.let(failure::addSuppressed) } }
            throw failure
        } finally {
            prepared?.let { temporary -> withContext(NonCancellable) { transport.releasePreparedMedia(temporary) } }
        }
    } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Throwable) { Result.failure(failure) }
}

/** Canonical remote representation shared by moderation and insertion, matching Android. */
fun PostComposerDraft.toRemoteText(): String = when (type) {
    PostComposerType.Text -> buildPostBodyWithMeta(cleanBody = text, textPattern = textPatternId, channel = "feed")
    PostComposerType.Image -> buildPostBodyWithMeta(imageLocation = locationLabel, channel = "feed")
    PostComposerType.Video -> buildPostBodyWithMeta(mediaTitle = text, channel = "feed")
}

internal fun validateComposerDraft(draft: PostComposerDraft) { when (draft.type) {
    PostComposerType.Text -> require(draft.text.isNotBlank()) { "composer_text_empty" }
    PostComposerType.Image -> require(!draft.imageUri.isNullOrBlank()) { "composer_image_missing" }
    PostComposerType.Video -> require(!draft.videoUri.isNullOrBlank()) { "composer_video_missing" }
} }
