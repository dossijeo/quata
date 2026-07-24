package com.quata.feature.feed.domain

/** Shared feed data contract. */

import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import kotlinx.coroutines.flow.Flow

/** Read boundary required by the portable feed browser. */
interface FeedReadRepository {
    fun observeFeed(): Flow<Result<List<Post>>>
    suspend fun getFeed(): Result<List<Post>>
    suspend fun refreshFeed(): Result<List<Post>>
    suspend fun loadOlderFeedPage(beforeCreatedAt: String?, limit: Int): Result<List<Post>>
    suspend fun refreshCurrentUser(): Result<User?>
    suspend fun refreshAuthor(userId: String): Result<User?>
    suspend fun refreshPost(postId: String): Result<Post?>
}

/** Write boundary used only by Feed surfaces that expose mutations. */
interface FeedMutationRepository {
    suspend fun toggleLike(postId: String): Result<Post?>
    suspend fun reportPost(postId: String): Result<Post?>
    suspend fun addComment(postId: String, comment: PostComment): Result<Post?>
    suspend fun deletePost(postId: String): Result<Unit>
}

/** Full Feed contract retained by Android and every existing mutation-capable caller. */
interface FeedRepository : FeedReadRepository, FeedMutationRepository

/**
 * Lets a platform ship the read-only shared Feed browser before its reviewed mutation transport
 * exists. Mutation-capable UI must receive a full [FeedRepository] instead.
 */
class ReadOnlyFeedRepository(
    private val readRepository: FeedReadRepository,
) : FeedRepository, FeedReadRepository by readRepository {
    override suspend fun toggleLike(postId: String): Result<Post?> = unsupportedMutation()

    override suspend fun reportPost(postId: String): Result<Post?> = unsupportedMutation()

    override suspend fun addComment(postId: String, comment: PostComment): Result<Post?> = unsupportedMutation()

    override suspend fun deletePost(postId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("feed_mutation_not_supported"))

    private fun <T> unsupportedMutation(): Result<T> =
        Result.failure(UnsupportedOperationException("feed_mutation_not_supported"))
}
