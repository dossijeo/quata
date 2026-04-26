package com.quata.feature.feed.domain

class RefreshFeedUseCase(private val repository: FeedRepository) {
    suspend operator fun invoke() = repository.refreshFeed()
}
