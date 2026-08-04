package com.quata.feature.feed.data

import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.feed.domain.FeedRepository
import kotlinx.coroutines.flow.Flow

/** Full iOS Feed repository backed by the existing renewable Keychain session. */
class IosAuthenticatedFeedRepository(
    private val transport: IosFeedReadTransport,
    private val read: FeedRepository,
) : FeedRepository {
    override fun observeFeed(): Flow<Result<List<Post>>> = read.observeFeed()
    override suspend fun getFeed(): Result<List<Post>> = read.getFeed()
    override suspend fun refreshFeed(): Result<List<Post>> = read.refreshFeed()
    override suspend fun loadOlderFeedPage(beforeCreatedAt: String?, limit: Int): Result<List<Post>> = read.loadOlderFeedPage(beforeCreatedAt, limit)
    override suspend fun refreshCurrentUser(): Result<User?> = read.refreshCurrentUser()
    override suspend fun refreshAuthor(userId: String): Result<User?> = read.refreshAuthor(userId)
    override suspend fun refreshPost(postId: String): Result<Post?> = read.refreshPost(postId)
    override suspend fun toggleLike(postId: String): Result<Post?> = runCatching {
        val userId = transport.currentUserId().getOrThrow() ?: error("ios_feed_session_missing")
        val current = refreshPost(postId).getOrThrow() ?: error("ios_feed_post_missing")
        if (current.isLikedByCurrentUser) transport.mutate("community_post_likes", "DELETE", mapOf("post_id" to "eq.$postId", "profile_id" to "eq.$userId")).getOrThrow()
        else transport.mutate("community_post_likes", "POST", body = "{\"post_id\":\"$postId\",\"profile_id\":\"$userId\"}").getOrThrow()
        refreshPost(postId).getOrThrow()
    }
    override suspend fun reportPost(postId: String): Result<Post?> = runCatching {
        val userId = transport.currentUserId().getOrThrow() ?: error("ios_feed_session_missing")
        transport.reportPostRpc("{\"p_actor_profile_id\":\"$userId\",\"p_target_type\":\"community_post\",\"p_target_id\":\"$postId\",\"p_reason\":\"other\"}").getOrThrow()
        refreshPost(postId).getOrThrow()
    }
    override suspend fun addComment(postId: String, comment: PostComment): Result<Post?> = runCatching {
        val userId = transport.currentUserId().getOrThrow() ?: error("ios_feed_session_missing")
        transport.mutate("community_comments", "POST", body = "{\"post_id\":\"$postId\",\"profile_id\":\"$userId\",\"body\":${comment.message.iosJsonString()}}").getOrThrow()
        refreshPost(postId).getOrThrow()
    }
    override suspend fun deletePost(postId: String): Result<Unit> = runCatching {
        val userId = transport.currentUserId().getOrThrow() ?: error("ios_feed_session_missing")
        val post = refreshPost(postId).getOrThrow() ?: error("ios_feed_post_missing")
        check(post.author.id == userId || refreshCurrentUser().getOrNull()?.isAdmin == true) { "ios_feed_delete_forbidden" }
        transport.mutate("community_posts", "DELETE", mapOf("id" to "eq.$postId")).getOrThrow()
    }
}

private fun String.iosJsonString(): String = buildString { append('"'); this@iosJsonString.forEach { append(if (it == '"' || it == '\\') "\\$it" else it) }; append('"') }
