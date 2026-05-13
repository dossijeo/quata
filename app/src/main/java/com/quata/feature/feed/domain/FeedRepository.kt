package com.quata.feature.feed.domain

import com.quata.core.model.Post
import com.quata.core.model.User

interface FeedRepository {
    suspend fun getFeed(): Result<List<Post>>
    suspend fun refreshFeed(): Result<List<Post>>
    suspend fun refreshAuthor(userId: String): Result<User?>
}
