package com.quata.feature.postcomposer.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.quata.R
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.media.MediaUploadOptimizer
import com.quata.core.session.SessionManager
import com.quata.core.text.buildPostBodyWithMeta
import com.quata.data.supabase.SupabaseApiException
import com.quata.data.supabase.SupabaseCommunityApi
import com.quata.feature.postcomposer.domain.PostComposerDestination
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType
import com.quata.wordpress.QuataWordPressClient

class PostComposerRepositoryImpl(
    private val appContext: Context,
    private val supabaseApi: SupabaseCommunityApi,
    private val wordpressClient: QuataWordPressClient,
    private val sessionManager: SessionManager,
    private val mediaUploadOptimizer: MediaUploadOptimizer
) : PostComposerRepository {
    override suspend fun loadDestinations(): Result<List<PostComposerDestination>> = runCatching {
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching listOf(PostComposerDestination("mock-feed", "Feed", isDefault = true))
        }
        destinationListFor(session.userId)
    }

    override suspend fun createPost(draft: PostComposerDraft): Result<String?> = runCatching {
        validateDraft(draft)
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching MockData.addPost(draft, session.userId)
        }

        val moderation = wordpressClient.moderateContent(
            context = "post",
            text = draft.toRemoteText(),
            imageName = draft.imageUri?.let { appContext.displayName(Uri.parse(it)) }.orEmpty(),
            imageType = draft.imageUri?.let { appContext.contentResolver.getType(Uri.parse(it)) }.orEmpty(),
            displayName = session.displayName,
            profileId = session.userId,
            url = "android://post"
        )
        if (moderation.data?.action == "block") {
            error(moderation.data.message ?: moderation.data.reason ?: "Contenido bloqueado por moderacion")
        }

        val wallId = draft.destinationWallId?.takeIf(String::isNotBlank)?.let { requested ->
            require(destinationListFor(session.userId).any { it.wallId == requested }) { "composer_destination_unavailable" }
            requested
        } ?: resolveWallId(session.userId)
        var uploadedImageStoragePath: String? = null
        val imageUrl = if (draft.type == PostComposerType.Image) {
            val media = mediaUploadOptimizer.prepareImageUpload(
                uriString = draft.imageUri ?: error("Selecciona una imagen"),
                fallbackMimeType = "image/jpeg",
                fallbackFileNameBase = "imagen"
            )
            val upload = supabaseApi.uploadPostImage(
                profileId = session.userId,
                bytes = media.bytes,
                extension = media.extension,
                mimeType = media.mimeType
            )
            uploadedImageStoragePath = upload.key?.takeIf { it.isNotBlank() }
            upload.publicUrl ?: error("Supabase no devolvio URL de imagen")
        } else {
            null
        }
        var uploadedVideoUrl: String? = null
        val videoUrl = if (draft.type == PostComposerType.Video) {
            val media = mediaUploadOptimizer.prepareVideoUploadStream(
                uriString = draft.videoUri ?: error("Selecciona o graba un video"),
                fallbackMimeType = "video/mp4",
                fallbackFileNameBase = "video"
            )
            val upload = try {
                wordpressClient.uploadPostVideoRest(
                    fileName = media.fileName,
                    mimeType = media.mimeType,
                    contentLength = media.sizeBytes,
                    openStream = media::openStream
                )
            } finally {
                media.cleanup()
            }
            Log.d(
                POST_COMPOSER_LOG_TAG,
                "video upload completed success=${upload.success} hasUrl=${!upload.data?.url.isNullOrBlank()} " +
                    "error=${upload.errorMessage?.take(240)}"
            )
            (upload.data?.url ?: error(upload.errorMessage ?: "WordPress no devolvio URL de video"))
                .also { uploadedVideoUrl = it }
        } else {
            null
        }

        runCatching {
            if (PostComposerEvidenceFaults.consumeFailInsertAfterUploadOnce() && (imageUrl != null || videoUrl != null)) {
                error("post_composer_e2e_forced_insert_after_upload_failure")
            }
            supabaseApi.createPost(
                wallId = wallId,
                profileId = session.userId,
                body = draft.toRemoteText(),
                imageUrl = imageUrl,
                videoUrl = videoUrl
            )?.id
        }.onFailure { throwable ->
            Log.e(
                POST_COMPOSER_LOG_TAG,
                "createPost failed type=${draft.type} wallId=$wallId profileId=${session.userId} " +
                    "hasImage=${imageUrl != null} hasVideo=${videoUrl != null} " +
                    "status=${(throwable as? SupabaseApiException)?.statusCode} " +
                    "body=${(throwable as? SupabaseApiException)?.responseBody?.take(800)}",
                throwable
            )
            uploadedVideoUrl?.let { orphanUrl ->
                runCatching { wordpressClient.deletePostVideoAjax(orphanUrl) }
                    .onFailure { cleanupError ->
                        Log.w(POST_COMPOSER_LOG_TAG, "Could not clean orphan uploaded video: $orphanUrl", cleanupError)
                    }
            }
            uploadedImageStoragePath?.let { orphanPath ->
                runCatching { supabaseApi.deletePostImageObject(orphanPath) }
                    .onFailure { cleanupError ->
                        Log.w(POST_COMPOSER_LOG_TAG, "Could not clean orphan uploaded image: $orphanPath", cleanupError)
                    }
            }
        }.getOrThrow()
    }.mapFailureToUserFacing(appContext, R.string.error_publish_post)

    private suspend fun resolveWallId(profileId: String): String {
        supabaseApi.getMembers(profileId = profileId).firstOrNull()?.wall_id?.let { return it }
        return supabaseApi.getActiveWallsStats(limit = 1).firstOrNull()?.id
            ?: error("No hay comunidad activa para publicar")
    }

    private suspend fun destinationListFor(profileId: String): List<PostComposerDestination> {
        val memberWallIds = supabaseApi.getMembers(profileId = profileId)
            .mapNotNull { it.wall_id.takeIf(String::isNotBlank) }
            .distinct()
        val walls = supabaseApi.getActiveWallsStats()
        val fallbackId = memberWallIds.firstOrNull() ?: walls.firstOrNull()?.id
        val memberSet = memberWallIds.toSet()
        return walls
            .filter { it.id in memberSet || memberSet.isEmpty() }
            .map { wall ->
                PostComposerDestination(
                    wallId = wall.id,
                    label = wall.name?.takeIf(String::isNotBlank) ?: wall.slug?.takeIf(String::isNotBlank) ?: "Feed",
                    subtitle = listOfNotNull(wall.city, wall.description).firstOrNull { !it.isNullOrBlank() },
                    isDefault = wall.id == fallbackId,
                )
            }
            .ifEmpty { fallbackId?.let { listOf(PostComposerDestination(it, "Feed", isDefault = true)) }.orEmpty() }
    }

    private fun validateDraft(draft: PostComposerDraft) {
        when (draft.type) {
            PostComposerType.Text -> if (draft.text.isBlank()) error("La publicacion de texto no puede estar vacia")
            PostComposerType.Image -> {
                if (draft.imageUri.isNullOrBlank()) error("Selecciona una imagen")
            }
            PostComposerType.Video -> if (draft.videoUri.isNullOrBlank()) error("Selecciona o graba un video")
        }
    }

    private fun PostComposerDraft.toRemoteText(): String = when (type) {
        PostComposerType.Text -> buildPostBodyWithMeta(cleanBody = text, textPattern = textPatternId, channel = "feed")
        PostComposerType.Image -> buildPostBodyWithMeta(imageLocation = locationLabel, channel = "feed")
        PostComposerType.Video -> buildPostBodyWithMeta(mediaTitle = text, channel = "feed")
    }

    private fun Context.displayName(uri: Uri): String {
        val fromProvider = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: ""
    }

    private companion object {
        const val POST_COMPOSER_LOG_TAG = "QuataPostComposer"
    }
}
