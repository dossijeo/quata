package com.quata.feature.feed.data

import android.content.Context
import com.quata.R
import com.quata.core.config.AppConfig
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.data.MockData
import com.quata.core.media.QuataMediaCache
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.session.SessionManager
import com.quata.data.supabase.CommunityPost
import com.quata.data.supabase.SupabaseCacheMode
import com.quata.feature.feed.domain.FeedRepository
import com.quata.feature.profile.data.ProfileRemoteDataSource
import com.quata.wordpress.QuataWordPressClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FeedRepositoryImpl(
    private val appContext: Context,
    private val remote: FeedRemoteDataSource,
    private val profileRemote: ProfileRemoteDataSource,
    private val wordpressClient: QuataWordPressClient,
    private val sessionManager: SessionManager
) : FeedRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFeed(): Flow<Result<List<Post>>> =
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.postsFlow.map { Result.success(it) }
        } else {
            remote.observePosts()
                .flatMapLatest { posts ->
                    val postIds = posts.map { it.id }
                    if (postIds.isEmpty()) {
                        flowOf(FeedSnapshot(posts = posts))
                    } else {
                        combine(
                            remote.observeComments(postIds).emptyOnFailure(),
                            remote.observeLikes(postIds).emptyOnFailure()
                        ) { comments, likes ->
                            FeedSnapshot(posts = posts, comments = comments, likes = likes)
                        }
                    }
                }
                .flatMapLatest { snapshot ->
                    val profileIds = snapshot.profileIds()
                    if (profileIds.isEmpty()) {
                        flowOf(buildPosts(snapshot.posts, snapshot.comments, snapshot.likes, emptyList()))
                    } else {
                        remote.observeProfiles(profileIds)
                            .emptyOnFailure()
                            .map { profiles -> buildPosts(snapshot.posts, snapshot.comments, snapshot.likes, profiles) }
                    }
                }
                .map { posts -> Result.success(posts) }
                .catch { error ->
                    emit(Result.failure<List<Post>>(error).mapFailureToUserFacing(appContext, R.string.error_load_feed))
                }
        }

    override suspend fun getFeed(): Result<List<Post>> =
        runCatching { loadPostShells(SupabaseCacheMode.CACHE_FIRST, FeedPageSize, 0) }.mapFailureToUserFacing(appContext, R.string.error_load_feed)
    override suspend fun refreshFeed(): Result<List<Post>> =
        runCatching { loadPostShells(SupabaseCacheMode.NETWORK_ONLY, FeedPageSize, 0) }.mapFailureToUserFacing(appContext, R.string.error_load_feed)

    override suspend fun loadOlderFeedPage(beforeCreatedAt: String?, limit: Int): Result<List<Post>> =
        runCatching {
            if (AppConfig.USE_MOCK_BACKEND) {
                val cursorIndex = beforeCreatedAt
                    ?.let { cursor -> MockData.posts.indexOfFirst { it.createdAt == cursor } }
                    ?.takeIf { it >= 0 }
                    ?: -1
                MockData.posts
                    .drop(cursorIndex + 1)
                    .take(limit.coerceAtLeast(1))
            } else {
                loadPostShells(
                    cacheMode = SupabaseCacheMode.CACHE_FIRST,
                    limit = limit.coerceAtLeast(1),
                    offset = 0,
                    createdBefore = beforeCreatedAt
                )
            }
        }.mapFailureToUserFacing(appContext, R.string.error_load_feed)

    override suspend fun refreshCurrentUser(): Result<User?> = runCatching {
        val userId = sessionManager.currentSession()?.userId ?: return@runCatching null
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.userById(userId).copy(isAdmin = true)
        } else {
            profileRemote.getProfile(userId)?.toDomainUser()
        }
    }.mapFailureToUserFacing(appContext, R.string.error_load_profile)

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
            loadPost(postId, SupabaseCacheMode.NETWORK_ONLY)
        }
    }.mapFailureToUserFacing(appContext, R.string.error_load_feed)

    override suspend fun toggleLike(postId: String): Result<Post?> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.togglePostLike(postId)
        } else {
            val session = sessionManager.currentSession() ?: error("No hay sesion activa")
            remote.toggleLike(postId, session.userId)
            loadPost(postId, SupabaseCacheMode.NETWORK_ONLY)
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun reportPost(postId: String): Result<Post?> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.reportPost(postId)
        } else {
            val session = sessionManager.currentSession() ?: error("No hay sesion activa")
            remote.reportPost(postId, session.userId)
            loadPost(postId, SupabaseCacheMode.NETWORK_ONLY)?.copy(isReportedByCurrentUser = true)
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun addComment(postId: String, comment: PostComment): Result<Post?> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.addComment(postId, comment)
        } else {
            val session = sessionManager.currentSession() ?: error("No hay sesion activa")
            remote.addComment(postId, session.userId, comment.toRemoteBody())
            loadPost(postId, SupabaseCacheMode.NETWORK_ONLY)
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun deletePost(postId: String): Result<Unit> = runCatching {
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            val videoUrl = MockData.posts.firstOrNull { it.id == postId }?.videoUrl
            val deleted = MockData.deletePost(postId, session.userId, isAdmin = true)
            if (!deleted) error("No se pudo borrar la publicacion")
            QuataMediaCache.removeVideo(appContext, videoUrl)
        } else {
            val post = remote.getPost(postId)
            val isOwnPost = post?.profile_id == session.userId || post?.author_id == session.userId
            val currentUser = if (isOwnPost) null else profileRemote.getProfile(session.userId)?.toDomainUser()
            deleteWordPressVideo(post)
            remote.deletePost(postId, profileId = if (currentUser?.isAdmin == true) null else session.userId)
            QuataMediaCache.removeVideo(appContext, post?.video_url)
        }
        Unit
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    private suspend fun deleteWordPressVideo(post: CommunityPost?) {
        val mediaUrl = post?.video_url?.takeIf { it.isNotBlank() }
            ?: return

        runCatching { wordpressClient.deletePostVideoAjax(mediaUrl) }
    }

    private suspend fun loadPostShells(
        cacheMode: SupabaseCacheMode,
        limit: Int,
        offset: Int,
        createdBefore: String? = null
    ): List<Post> {
        if (AppConfig.USE_MOCK_BACKEND) return MockData.posts.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(1))

        val posts = remote.getPosts(
            limit = limit,
            offset = offset,
            createdBefore = createdBefore,
            cacheMode = cacheMode
        )
        val postIds = posts.map { it.id }
        val comments = if (postIds.isEmpty()) emptyList() else remote.getComments(postIds, cacheMode)
        val likes = if (postIds.isEmpty()) emptyList() else remote.getLikes(postIds, cacheMode)
        val profileIds = FeedSnapshot(posts, comments, likes).profileIds()
        val profilesById = if (profileIds.isEmpty()) emptyMap() else remote.getProfiles(profileIds, cacheMode).associateBy { it.id }
        return buildPosts(posts, comments, likes, profilesById.values.toList())
    }

    private fun buildPosts(
        posts: List<CommunityPost>,
        comments: List<com.quata.data.supabase.CommunityComment>,
        likes: List<com.quata.data.supabase.CommunityPostLike>,
        profiles: List<com.quata.data.supabase.CommunityProfile>
    ): List<Post> {
        val currentUserId = sessionManager.currentSession()?.userId
        val profilesById = profiles.associateBy { it.id }
        val commentsByPostId = comments.groupBy { it.post_id }
        val likesByPostId = likes.groupBy { it.post_id }

        return posts.map { post ->
            val authorId = post.profile_id ?: post.author_id.orEmpty()
            val author = profilesById[authorId]?.toDomainUser()
                ?: User(authorId.ifBlank { "unknown" }, "", "Usuario")
            val postComments = commentsByPostId[post.id].orEmpty()
                .toDomainComments { comment ->
                    profilesById[comment.profile_id]?.display_name
                        ?: profilesById[comment.profile_id]?.nombre
                        ?: "Usuario"
                }
            val postLikes = likesByPostId[post.id].orEmpty()
            post.toDomain(
                author = author,
                comments = postComments,
                likesCount = postLikes.size,
                likedByCurrentUser = currentUserId != null && postLikes.any { it.profile_id == currentUserId }
            )
        }
    }

    private suspend fun loadPost(
        postId: String,
        cacheMode: SupabaseCacheMode = SupabaseCacheMode.CACHE_FIRST
    ): Post? {
        val currentUserId = sessionManager.currentSession()?.userId
        val post = remote.getPost(postId, cacheMode) ?: return null
        val comments = remote.getComments(listOf(postId), cacheMode)
        val likes = remote.getLikes(listOf(postId), cacheMode)
        val profileIds = (
            listOfNotNull(post.profile_id ?: post.author_id) +
                comments.mapNotNull { it.profile_id } +
                likes.mapNotNull { it.profile_id }
            ).distinct()
        val profilesById = if (profileIds.isEmpty()) emptyMap() else remote.getProfiles(profileIds, cacheMode).associateBy { it.id }
        val authorId = post.profile_id ?: post.author_id.orEmpty()
        val author = profilesById[authorId]?.toDomainUser()
            ?: User(authorId.ifBlank { "unknown" }, "", "Usuario")
        val postComments = comments
            .filter { it.post_id == post.id }
            .toDomainComments { comment ->
                profilesById[comment.profile_id]?.display_name
                    ?: profilesById[comment.profile_id]?.nombre
                    ?: "Usuario"
            }
        val postLikes = likes.filter { it.post_id == post.id }
        return post.toDomain(
            author = author,
            comments = postComments,
            likesCount = postLikes.size,
            likedByCurrentUser = currentUserId != null && postLikes.any { it.profile_id == currentUserId }
        )
    }

    private data class FeedSnapshot(
        val posts: List<CommunityPost>,
        val comments: List<com.quata.data.supabase.CommunityComment> = emptyList(),
        val likes: List<com.quata.data.supabase.CommunityPostLike> = emptyList()
    ) {
        fun profileIds(): List<String> = (
            posts.mapNotNull { it.profile_id ?: it.author_id } +
                comments.mapNotNull { it.profile_id } +
                likes.mapNotNull { it.profile_id }
            ).distinct()
    }

    private fun <T> Flow<List<T>>.emptyOnFailure(): Flow<List<T>> =
        catch { emit(emptyList()) }

    private companion object {
        const val FeedPageSize = 50
    }
}
