package com.quata.feature.postcomposer.data

import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.session.SessionManager
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType

class PostComposerRepositoryImpl(
    private val remote: PostComposerRemoteDataSource,
    private val sessionManager: SessionManager
) : PostComposerRepository {
    override suspend fun createPost(draft: PostComposerDraft): Result<String?> = runCatching {
        validateDraft(draft)
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching MockData.addPost(draft, session.userId)
        }

        val text = draft.toRemoteText()
        when (AppConfig.FEED_SOURCE.lowercase()) {
            "supabase" -> remote.createSupabasePost(session.userId, text, draft.imageUri).firstOrNull()?.id
            else -> remote.createWordpressPost(session.token, text).id.toString()
        }
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
        PostComposerType.Image -> locationLabel.orEmpty()
        PostComposerType.Video -> text.ifBlank { "Video" }
    }
}
