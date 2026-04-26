package com.quata.feature.postcomposer.domain

class CreatePostUseCase(private val repository: PostComposerRepository) {
    suspend operator fun invoke(text: String, imageUri: String?) = repository.createPost(text, imageUri)
}
