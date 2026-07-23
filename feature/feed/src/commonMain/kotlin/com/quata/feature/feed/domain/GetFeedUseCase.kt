package com.quata.feature.feed.domain

/** Shared initial feed fetch. */

class GetFeedUseCase(private val repository: FeedRepository) {
    suspend operator fun invoke() = repository.getFeed()
}
