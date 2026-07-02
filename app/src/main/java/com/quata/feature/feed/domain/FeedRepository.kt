package com.quata.feature.feed.domain

import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import kotlinx.coroutines.flow.Flow

interface FeedRepository {
    fun observeFeed(): Flow<Result<List<Post>>>
    suspend fun getFeed(): Result<List<Post>>
    suspend fun refreshFeed(): Result<List<Post>>
    suspend fun refreshCurrentUser(): Result<User?>
    suspend fun refreshAuthor(userId: String): Result<User?>
    suspend fun refreshPost(postId: String): Result<Post?>
    suspend fun toggleLike(postId: String): Result<Post?>
    suspend fun reportPost(postId: String): Result<Post?>
    suspend fun addComment(postId: String, comment: PostComment): Result<Post?>
    suspend fun deletePost(postId: String): Result<Unit>
}
