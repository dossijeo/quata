package com.quata.feature.postcomposer.domain

enum class PostComposerType {
    Text,
    Image,
    Video
}

data class PostComposerDraft(
    val type: PostComposerType,
    val text: String = "",
    val imageUri: String? = null,
    val videoUri: String? = null,
    val locationLabel: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

interface PostComposerRepository {
    suspend fun createPost(draft: PostComposerDraft): Result<Unit>
}
