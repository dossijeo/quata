package com.quata.feature.feed.data

import android.content.Context
import com.quata.R
import com.quata.core.config.AppConfig
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.data.MockData
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.session.SessionManager
import com.quata.feature.feed.domain.FeedRepository
import com.quata.feature.profile.data.ProfileRemoteDataSource

class FeedRepositoryImpl(
    private val appContext: Context,
    private val remote: FeedRemoteDataSource,
    private val profileRemote: ProfileRemoteDataSource,
    private val sessionManager: SessionManager
) : FeedRepository {
    override suspend fun getFeed(): Result<List<Post>> =
        runCatching { loadPostShells() }.mapFailureToUserFacing(appContext, R.string.error_load_feed)
    override suspend fun refreshFeed(): Result<List<Post>> = getFeed()

    override suspend fun refreshAuthor(userId: String): Result<User?> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.userById(userId)
        } else {
            profileRemote.getProfile(userId)?.toDomainUser()
        }
    }.mapFailureToUserFacing(appContext, R.string.error_load_profile)

    override suspend fun refreshPost(postId: String): Result<Post?> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.posts.firstOrNull { it.id == postId }
        } else {
            loadPost(postId)
        }
    }.mapFailureToUserFacing(appContext, R.string.error_load_feed)

    override suspend fun toggleLike(postId: String): Result<Post?> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.togglePostLike(postId)
        } else {
            val session = sessionManager.currentSession() ?: error("No hay sesion activa")
            remote.toggleLike(postId, session.userId)
            loadPost(postId)
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun reportPost(postId: String): Result<Post?> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.reportPost(postId)
        } else {
            loadPost(postId)?.copy(isReportedByCurrentUser = true)
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun addComment(postId: String, comment: PostComment): Result<Post?> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.addComment(postId, comment)
        } else {
            val session = sessionManager.currentSession() ?: error("No hay sesion activa")
            remote.addComment(postId, session.userId, comment.message)
            loadPost(postId)
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    private suspend fun loadPostShells(): List<Post> {
        if (AppConfig.USE_MOCK_BACKEND) return MockData.posts

        val posts = remote.getPosts()
        val profileIds = posts.mapNotNull { it.profile_id ?: it.author_id }.distinct()
        val profilesById = if (profileIds.isEmpty()) emptyMap() else remote.getProfiles(profileIds).associateBy { it.id }

        return posts.map { post ->
            val authorId = post.profile_id ?: post.author_id.orEmpty()
            val author = profilesById[authorId]?.toDomainUser()
                ?: User(authorId.ifBlank { "unknown" }, "", "Usuario")
            post.toDomain(
                author = author,
                comments = emptyList(),
                likesCount = 0,
                likedByCurrentUser = false
            )
        }
    }

    private suspend fun loadPost(postId: String): Post? {
        val currentUserId = sessionManager.currentSession()?.userId
        val post = remote.getPost(postId) ?: return null
        val comments = remote.getComments(listOf(postId))
        val likes = remote.getLikes(listOf(postId))
        val profileIds = (
            listOfNotNull(post.profile_id ?: post.author_id) +
                comments.mapNotNull { it.profile_id } +
                likes.mapNotNull { it.profile_id }
            ).distinct()
        val profilesById = if (profileIds.isEmpty()) emptyMap() else remote.getProfiles(profileIds).associateBy { it.id }
        val authorId = post.profile_id ?: post.author_id.orEmpty()
        val author = profilesById[authorId]?.toDomainUser()
            ?: User(authorId.ifBlank { "unknown" }, "", "Usuario")
        val postComments = comments
            .filter { it.post_id == post.id }
            .map { comment ->
                comment.toDomain(
                    authorName = profilesById[comment.profile_id]?.display_name
                        ?: profilesById[comment.profile_id]?.nombre
                        ?: "Usuario"
                )
            }
        val postLikes = likes.filter { it.post_id == post.id }
        return post.toDomain(
            author = author,
            comments = postComments,
            likesCount = postLikes.size,
            likedByCurrentUser = currentUserId != null && postLikes.any { it.profile_id == currentUserId }
        )
    }
}
