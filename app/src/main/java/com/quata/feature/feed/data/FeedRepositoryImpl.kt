package com.quata.feature.feed.data

import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.model.Post
import com.quata.core.model.User
import com.quata.feature.feed.domain.FeedRepository
import com.quata.feature.profile.data.ProfileRemoteDataSource

class FeedRepositoryImpl(
    private val remote: FeedRemoteDataSource,
    private val profileRemote: ProfileRemoteDataSource
) : FeedRepository {
    override suspend fun getFeed(): Result<List<Post>> = runCatching { loadPosts() }
    override suspend fun refreshFeed(): Result<List<Post>> = getFeed()

    override suspend fun refreshAuthor(userId: String): Result<User?> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.userById(userId)
        } else {
            val profile = profileRemote.getProfile(userId)
                ?: profileRemote.getDirectoryProfiles().firstOrNull { it.id == userId }
                ?: return@runCatching null
            User(
                id = profile.id,
                email = profile.email.orEmpty(),
                displayName = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.email.orEmpty(),
                neighborhood = profile.neighborhood.orEmpty(),
                avatarUrl = profile.avatarUrl
            )
        }
    }

    private suspend fun loadPosts(): List<Post> =
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.posts
        } else {
            when (AppConfig.FEED_SOURCE.lowercase()) {
                "supabase" -> remote.getSupabasePosts().map { it.toDomain() }
                else -> remote.getWordpressPosts().map { it.toDomain() }
            }
        }
}
