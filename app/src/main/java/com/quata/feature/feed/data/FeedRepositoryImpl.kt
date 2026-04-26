package com.quata.feature.feed.data

import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.model.Post
import com.quata.feature.feed.domain.FeedRepository

class FeedRepositoryImpl(private val remote: FeedRemoteDataSource) : FeedRepository {
    override suspend fun getFeed(): Result<List<Post>> = load()
    override suspend fun refreshFeed(): Result<List<Post>> = load()

    private suspend fun load(): Result<List<Post>> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.posts
        } else {
            when (AppConfig.FEED_SOURCE.lowercase()) {
                "supabase" -> remote.getSupabasePosts().map { it.toDomain() }
                else -> remote.getWordpressPosts().map { it.toDomain() }
            }
        }
    }
}
