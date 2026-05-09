package com.quata.feature.postcomposer.domain

class CreatePostUseCase(private val repository: PostComposerRepository) {
    suspend operator fun invoke(draft: PostComposerDraft) = repository.createPost(draft)
}
