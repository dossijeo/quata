package com.quata.feature.postcomposer.data

import com.quata.core.config.AppConfig
import com.quata.core.session.SessionManager
import com.quata.feature.postcomposer.domain.PostComposerRepository

class PostComposerRepositoryImpl(
    private val remote: PostComposerRemoteDataSource,
    private val sessionManager: SessionManager
) : PostComposerRepository {
    override suspend fun createPost(text: String, imageUri: String?): Result<Unit> = runCatching {
        if (text.isBlank()) error("La publicación no puede estar vacía")
        if (AppConfig.USE_MOCK_BACKEND) return@runCatching

        val session = sessionManager.currentSession() ?: error("No hay sesión activa")
        when (AppConfig.FEED_SOURCE.lowercase()) {
            "supabase" -> remote.createSupabasePost(session.userId, text, imageUri)
            else -> remote.createWordpressPost(session.token, text)
        }
    }
}
