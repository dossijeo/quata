package com.quata.feature.postcomposer.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.quata.R
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.media.MediaUploadOptimizer
import com.quata.core.session.SessionManager
import com.quata.core.text.buildPostBodyWithMeta
import com.quata.data.supabase.SupabaseCommunityApi
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

        val wallId = resolveWallId(session.userId)
        val imageUrl = if (draft.type == PostComposerType.Image) {
            val media = mediaUploadOptimizer.prepareImageUpload(
                uriString = draft.imageUri ?: error("Selecciona una imagen"),
                fallbackMimeType = "image/jpeg",
                fallbackFileNameBase = "imagen"
            )
            supabaseApi.uploadPostImage(
                profileId = session.userId,
                bytes = media.bytes,
                extension = media.extension,
                mimeType = media.mimeType
            ).publicUrl ?: error("Supabase no devolvio URL de imagen")
        } else {
            null
        }
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
            upload.data?.url ?: error(upload.errorMessage ?: "WordPress no devolvio URL de video")
        } else {
            null
        }

        supabaseApi.createPost(
            wallId = wallId,
            profileId = session.userId,
            body = draft.toRemoteText(),
            imageUrl = imageUrl,
            videoUrl = videoUrl
        )?.id
    }.mapFailureToUserFacing(appContext, R.string.error_publish_post)

    private suspend fun resolveWallId(profileId: String): String {
        supabaseApi.getMembers(profileId = profileId).firstOrNull()?.wall_id?.let { return it }
        return supabaseApi.getActiveWallsStats(limit = 1).firstOrNull()?.id
            ?: error("No hay comunidad activa para publicar")
    }

    private fun validateDraft(draft: PostComposerDraft) {
        when (draft.type) {
            PostComposerType.Text -> if (draft.text.isBlank()) error("La publicacion de texto no puede estar vacia")
            PostComposerType.Image -> {
                if (draft.imageUri.isNullOrBlank()) error("Selecciona una imagen")
                if (draft.locationLabel.isNullOrBlank()) error("Falta la ubicacion de la imagen")
            }
            PostComposerType.Video -> if (draft.videoUri.isNullOrBlank()) error("Selecciona o graba un video")
        }
    }

    private fun PostComposerDraft.toRemoteText(): String = when (type) {
        PostComposerType.Text -> text
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
}
