package com.quata.feature.feed.domain

/** Shared refresh action. */

class RefreshFeedUseCase(private val repository: FeedRepository) {
    suspend operator fun invoke() = repository.refreshFeed()
}
