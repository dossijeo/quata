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
    override suspend fun createPost(draft: PostComposerDraft): Result<Unit> = runCatching {
        validateDraft(draft)
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.addPost(draft)
            return@runCatching
        }

        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val text = draft.toRemoteText()
        when (AppConfig.FEED_SOURCE.lowercase()) {
            "supabase" -> remote.createSupabasePost(session.userId, text, draft.imageUri)
            else -> remote.createWordpressPost(session.token, text)
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
        PostComposerType.Image -> listOfNotNull("Foto con ubicacion", locationLabel).joinToString("\n")
        PostComposerType.Video -> text.ifBlank { "Video" }
    }
}
