package com.quata.feature.postcomposer.domain

enum class PostComposerType {
    Text,
    Image,
    Video
}

data class PostComposerDestination(
    val wallId: String,
    val label: String,
    val subtitle: String? = null,
    val isDefault: Boolean = false
)

data class PostComposerDraft(
    val type: PostComposerType,
    val text: String = "",
    val textPatternId: String? = null,
    val imageUri: String? = null,
    val videoUri: String? = null,
    val locationLabel: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val destinationWallId: String? = null,
    val destinationLabel: String? = null
)

interface PostComposerRepository {
    suspend fun loadDestinations(): Result<List<PostComposerDestination>> = Result.success(emptyList())
    suspend fun createPost(draft: PostComposerDraft): Result<String?>
}
