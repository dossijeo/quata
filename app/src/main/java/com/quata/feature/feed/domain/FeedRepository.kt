package com.quata.feature.feed.domain

import com.quata.core.model.Post

interface FeedRepository {
    suspend fun getFeed(): Result<List<Post>>
    suspend fun refreshFeed(): Result<List<Post>>
}
