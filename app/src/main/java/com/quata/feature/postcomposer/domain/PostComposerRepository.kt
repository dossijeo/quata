package com.quata.feature.postcomposer.domain

interface PostComposerRepository {
    suspend fun createPost(text: String, imageUri: String?): Result<Unit>
}
