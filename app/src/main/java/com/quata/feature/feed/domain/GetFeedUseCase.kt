package com.quata.feature.feed.domain

class GetFeedUseCase(private val repository: FeedRepository) {
    suspend operator fun invoke() = repository.getFeed()
}
