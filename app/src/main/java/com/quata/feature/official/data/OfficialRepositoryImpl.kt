package com.quata.feature.official.data

import android.content.Context
import android.util.Log
import com.quata.R
import com.quata.core.common.UserFacingException
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.media.MediaUploadOptimizer
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.session.SessionManager
import com.quata.core.text.decodeHtmlEntities
import com.quata.core.text.parsePostCommentBody
import com.quata.core.text.stripHtmlTagsAndDecode
import com.quata.core.text.toRemoteCommentBody
import com.quata.data.supabase.CommunityProfile
import com.quata.data.supabase.OfficialPost
import com.quata.data.supabase.OfficialPostComment
import com.quata.data.supabase.OfficialPostLike
import com.quata.data.supabase.SupabaseCacheMode
import com.quata.data.supabase.SupabaseCommunityApi
import com.quata.feature.feed.data.toDomainUser
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialRepository
import com.quata.wordpress.QuataWordPressClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class OfficialRepositoryImpl(
    private val appContext: Context,
    private val supabaseApi: SupabaseCommunityApi,
    private val wordpressClient: QuataWordPressClient,
    private val sessionManager: SessionManager,
    private val mediaUploadOptimizer: MediaUploadOptimizer
) : OfficialRepository {
    private val mockPostsState = MutableStateFlow(mockOfficialPosts())

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOfficialFeed(): Flow<Result<List<OfficialPostItem>>> =
        if (AppConfig.USE_MOCK_BACKEND) {
            mockPostsState.map { Result.success(it) }
        } else {
            supabaseApi.observeOfficialPosts()
                .flatMapLatest { posts ->
                    val postIds = posts.map { it.id }
                    if (postIds.isEmpty()) {
                        flowOf(OfficialSnapshot(posts = posts))
                    } else {
                        combine(
                            supabaseApi.observeOfficialLikes(postIds).emptyOnFailure(),
                            supabaseApi.observeOfficialComments(postIds).emptyOnFailure()
                        ) { likes, comments ->
                            OfficialSnapshot(posts = posts, likes = likes, comments = comments)
                        }
                    }
                }
                .flatMapLatest { snapshot ->
                    val profileIds = snapshot.profileIds()
                    if (profileIds.isEmpty()) {
                        flowOf(buildPosts(snapshot, emptyList()))
                    } else {
                        supabaseApi.observeProfiles(profileIds)
                            .emptyOnFailure()
                            .map { profiles -> buildPosts(snapshot, profiles) }
                    }
                }
                .map { posts -> Result.success(posts) }
                .catch { error ->
                    emit(Result.failure<List<OfficialPostItem>>(error).mapFailureToUserFacing(appContext, R.string.error_load_official_feed))
                }
        }

    override suspend fun getOfficialFeed(): Result<List<OfficialPostItem>> =
        runCatching { loadOfficialFeed(SupabaseCacheMode.CACHE_FIRST) }
            .mapFailureToUserFacing(appContext, R.string.error_load_official_feed)

    override suspend fun refreshOfficialFeed(): Result<List<OfficialPostItem>> =
        runCatching { loadOfficialFeed(SupabaseCacheMode.NETWORK_ONLY) }
            .mapFailureToUserFacing(appContext, R.string.error_load_official_feed)

    override suspend fun refreshCurrentUser(): Result<User?> = runCatching {
        val userId = sessionManager.currentSession()?.userId ?: return@runCatching null
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.userById(userId).copy(isAdmin = true, isOfficial = true)
        } else {
            supabaseApi.getProfiles(listOf(userId), cacheMode = SupabaseCacheMode.NETWORK_ONLY)
                .firstOrNull()
                ?.toDomainUser()
        }
    }.mapFailureToUserFacing(appContext, R.string.error_load_profile)

    override suspend fun createPost(draft: OfficialPostDraft): Result<OfficialPostItem?> = runCatching {
        val session = sessionManager.currentSession()
            ?: throw UserFacingException(appContext.getString(R.string.error_backend_unauthorized))
        val contentHtml = draft.contentHtmlOrFallback()
        if (AppConfig.USE_MOCK_BACKEND) {
            val currentUser = MockData.userById(session.userId).copy(isAdmin = true, isOfficial = true)
            val created = OfficialPostItem(
                id = "official_${System.currentTimeMillis()}",
                author = currentUser,
                title = draft.title.trim(),
                summary = draft.summary.trim(),
                contentHtml = contentHtml,
                contentPlain = contentHtml.stripHtmlTagsAndDecode(),
                readMoreLabel = draft.readMoreLabel.trim(),
                type = draft.type,
                mediaUrl = draft.mediaUrl,
                mediaType = draft.mediaType,
                linkUrl = draft.linkUrl,
                isLive = draft.isLive,
                createdAt = appContext.getString(R.string.common_now)
            )
            mockPostsState.value = listOf(created) + mockPostsState.value
            created
        } else {
            val currentProfile = supabaseApi.getProfiles(listOf(session.userId), cacheMode = SupabaseCacheMode.NETWORK_ONLY).firstOrNull()
            if (currentProfile?.is_official != true) {
                throw UserFacingException(appContext.getString(R.string.official_not_allowed))
            }
            val publicMediaUrl = uploadOfficialMediaIfNeeded(session.userId, draft)
            val created = supabaseApi.createOfficialPost(
                profileId = session.userId,
                title = draft.title.trim(),
                summary = draft.summary.trim().takeIf { it.isNotBlank() },
                postType = draft.type.remoteValue,
                contentHtml = contentHtml,
                readMoreLabel = draft.readMoreLabel.trim().takeIf { it.isNotBlank() },
                mediaUrl = publicMediaUrl,
                mediaType = draft.mediaType?.remoteValue,
                linkUrl = draft.linkUrl?.trim()?.takeIf { it.isNotBlank() },
                isLive = draft.isLive
            ) ?: return@runCatching null
            loadOfficialPost(created.id, SupabaseCacheMode.NETWORK_ONLY)
        }
    }.mapFailureToUserFacing(appContext, R.string.error_publish_post)

    private suspend fun uploadOfficialMediaIfNeeded(profileId: String, draft: OfficialPostDraft): String? {
        val rawUri = draft.mediaUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (rawUri.isRemoteUrl()) return rawUri
        return when (draft.mediaType) {
            OfficialMediaType.Image -> {
                val media = mediaUploadOptimizer.prepareImageUpload(
                    uriString = rawUri,
                    fallbackMimeType = "image/jpeg",
                    fallbackFileNameBase = "oficial"
                )
                supabaseApi.uploadPostImage(
                    profileId = profileId,
                    bytes = media.bytes,
                    extension = media.extension,
                    mimeType = media.mimeType
                ).publicUrl ?: error("Supabase no devolvio URL de imagen")
            }
            OfficialMediaType.Video -> {
                val media = mediaUploadOptimizer.prepareVideoUploadStream(
                    uriString = rawUri,
                    fallbackMimeType = "video/mp4",
                    fallbackFileNameBase = "oficial"
                )
                val upload = try {
                    wordpressClient.uploadPostVideoRest(
                        fileName = media.fileName,
                        mimeType = media.mimeType,
                        contentLength = media.sizeBytes,
                        openStream = media::openStream
                    )
                } finally {
                    media.cleanup()
                }
                Log.d(
                    OFFICIAL_REPOSITORY_LOG_TAG,
                    "official video upload success=${upload.success} hasUrl=${!upload.data?.url.isNullOrBlank()} error=${upload.errorMessage?.take(240)}"
                )
                upload.data?.url ?: error(upload.errorMessage ?: "WordPress no devolvio URL de video")
            }
            null -> null
        }
    }

    override suspend fun deletePost(postId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            mockPostsState.value = mockPostsState.value.filterNot { it.id == postId }
        } else {
            supabaseApi.deleteOfficialPost(postId)
        }
        Unit
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun toggleLike(postId: String): Result<OfficialPostItem?> = runCatching {
        val session = sessionManager.currentSession()
            ?: throw UserFacingException(appContext.getString(R.string.error_backend_unauthorized))
        if (AppConfig.USE_MOCK_BACKEND) {
            mockPostsState.value = mockPostsState.value.map { post ->
                if (post.id != postId) {
                    post
                } else {
                    val liked = !post.isLikedByCurrentUser
                    post.copy(
                        isLikedByCurrentUser = liked,
                        likesCount = (post.likesCount + if (liked) 1 else -1).coerceAtLeast(0)
                    )
                }
            }
            mockPostsState.value.firstOrNull { it.id == postId }
        } else {
            supabaseApi.toggleOfficialLike(postId, session.userId)
            loadOfficialPost(postId, SupabaseCacheMode.NETWORK_ONLY)
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun addComment(postId: String, comment: PostComment): Result<OfficialPostItem?> = runCatching {
        val session = sessionManager.currentSession()
            ?: throw UserFacingException(appContext.getString(R.string.error_backend_unauthorized))
        if (AppConfig.USE_MOCK_BACKEND) {
            val currentUser = MockData.userById(session.userId)
            val newComment = comment.copy(
                id = "official_comment_${System.currentTimeMillis()}",
                authorName = currentUser.displayName,
                timestamp = appContext.getString(R.string.common_now)
            )
            mockPostsState.value = mockPostsState.value.map { post ->
                if (post.id == postId) {
                    post.copy(
                        comments = post.comments + newComment,
                        commentsCount = post.commentsCount + 1
                    )
                } else {
                    post
                }
            }
            mockPostsState.value.firstOrNull { it.id == postId }
        } else {
            supabaseApi.addOfficialComment(postId, session.userId, comment.toRemoteCommentBody())
            loadOfficialPost(postId, SupabaseCacheMode.NETWORK_ONLY)
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    private suspend fun loadOfficialFeed(cacheMode: SupabaseCacheMode): List<OfficialPostItem> {
        if (AppConfig.USE_MOCK_BACKEND) return mockPostsState.value
        val posts = supabaseApi.getOfficialPosts(cacheMode = cacheMode)
        val postIds = posts.map { it.id }
        val likes = supabaseApi.getOfficialLikes(postIds, cacheMode)
        val comments = supabaseApi.getOfficialComments(postIds, cacheMode)
        val snapshot = OfficialSnapshot(posts = posts, likes = likes, comments = comments)
        val profiles = snapshot.profileIds().takeIf { it.isNotEmpty() }
            ?.let { supabaseApi.getProfiles(it, cacheMode = cacheMode) }
            .orEmpty()
        return buildPosts(snapshot, profiles)
    }

    private suspend fun loadOfficialPost(postId: String, cacheMode: SupabaseCacheMode): OfficialPostItem? {
        val post = supabaseApi.getOfficialPosts(limit = 1, postId = postId, cacheMode = cacheMode).firstOrNull() ?: return null
        val likes = supabaseApi.getOfficialLikes(listOf(postId), cacheMode)
        val comments = supabaseApi.getOfficialComments(listOf(postId), cacheMode)
        val snapshot = OfficialSnapshot(posts = listOf(post), likes = likes, comments = comments)
        val profiles = supabaseApi.getProfiles(snapshot.profileIds(), cacheMode = cacheMode)
        return buildPosts(snapshot, profiles).firstOrNull()
    }

    private fun buildPosts(
        snapshot: OfficialSnapshot,
        profiles: List<CommunityProfile>
    ): List<OfficialPostItem> {
        val currentUserId = sessionManager.currentSession()?.userId
        val profilesById = profiles.associateBy { it.id }
        val likesByPostId = snapshot.likes.groupBy { it.official_post_id }
        val commentsByPostId = snapshot.comments.groupBy { it.official_post_id }
        return snapshot.posts.map { post ->
            val author = profilesById[post.profile_id]?.toDomainUser()
                ?: User(post.profile_id.orEmpty().ifBlank { "official" }, "", appContext.getString(R.string.official_account_fallback), isOfficial = true)
            val postLikes = likesByPostId[post.id].orEmpty()
            val remoteComments = commentsByPostId[post.id].orEmpty()
            val parsedById = remoteComments.associate { comment ->
                comment.id to comment.body.orEmpty().decodeHtmlEntities().parsePostCommentBody()
            }
            val postComments = remoteComments.map { comment ->
                val parsed = parsedById.getValue(comment.id)
                val target = parsed.commentId?.let { targetId -> remoteComments.firstOrNull { it.id == targetId } }
                val targetParsed = target?.let { parsedById[it.id] }
                PostComment(
                    id = comment.id,
                    authorName = profilesById[comment.profile_id]?.display_name
                        ?: profilesById[comment.profile_id]?.nombre
                        ?: appContext.getString(R.string.comments_you),
                    message = parsed.message,
                    timestamp = comment.created_at.orEmpty(),
                    replyToAuthorName = parsed.authorName ?: target?.let { targetComment ->
                        profilesById[targetComment.profile_id]?.display_name
                            ?: profilesById[targetComment.profile_id]?.nombre
                    },
                    replyToMessage = targetParsed?.message,
                    replyToCommentId = parsed.commentId
                )
            }
            post.toDomain(
                author = author.copy(isOfficial = true),
                likesCount = postLikes.size,
                likedByCurrentUser = currentUserId != null && postLikes.any { it.profile_id == currentUserId },
                comments = postComments
            )
        }
    }

    private data class OfficialSnapshot(
        val posts: List<OfficialPost>,
        val likes: List<OfficialPostLike> = emptyList(),
        val comments: List<OfficialPostComment> = emptyList()
    ) {
        fun profileIds(): List<String> = (
            posts.mapNotNull { it.profile_id } +
                comments.mapNotNull { it.profile_id } +
                likes.mapNotNull { it.profile_id }
            ).distinct()
    }

    private fun OfficialPost.toDomain(
        author: User,
        likesCount: Int,
        likedByCurrentUser: Boolean,
        comments: List<PostComment>
    ): OfficialPostItem {
        val safeTitle = title?.decodeHtmlEntities()?.takeIf { it.isNotBlank() }
            ?: content_html.orEmpty().stripHtmlTagsAndDecode().lineSequence().firstOrNull().orEmpty()
            ?: appContext.getString(R.string.official_post_default_title)
        val safeHtml = content_html.orEmpty()
        val safePlain = safeHtml.stripHtmlTagsAndDecode()
        return OfficialPostItem(
            id = id,
            author = author,
            title = safeTitle.ifBlank { appContext.getString(R.string.official_post_default_title) },
            summary = summary?.decodeHtmlEntities()?.takeIf { it.isNotBlank() }
                ?: safePlain.take(180),
            contentHtml = safeHtml,
            contentPlain = safePlain,
            readMoreLabel = read_more_label?.decodeHtmlEntities().orEmpty(),
            type = OfficialPostType.fromRemote(post_type),
            mediaUrl = media_url,
            mediaType = OfficialMediaType.fromRemote(media_type),
            linkUrl = link_url,
            isLive = is_live == true,
            createdAt = published_at ?: created_at.orEmpty(),
            likesCount = likesCount,
            commentsCount = comments.size,
            isLikedByCurrentUser = likedByCurrentUser,
            comments = comments
        )
    }

    private fun <T> Flow<List<T>>.emptyOnFailure(): Flow<List<T>> =
        catch { emit(emptyList()) }

    private fun String.isRemoteUrl(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private fun OfficialPostDraft.contentHtmlOrFallback(): String {
        val richText = contentHtml.trim()
        if (richText.stripHtmlTagsAndDecode().isNotBlank()) return richText
        val fallback = summary.trim().ifBlank { title.trim() }.ifBlank {
            appContext.getString(R.string.official_post_default_title)
        }
        return "<p>${fallback.escapeOfficialHtml()}</p>"
    }

    private fun String.escapeOfficialHtml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun mockOfficialPosts(): List<OfficialPostItem> {
        val author = User(
            id = "official_health",
            email = "salud@quata.app",
            displayName = "Ministerio de Sanidad",
            neighborhood = "Cuenta oficial del Gobierno de Guinea Ecuatorial",
            avatarUrl = null,
            isOfficial = true
        )
        return listOf(
            OfficialPostItem(
                id = "official_mock_1",
                author = author,
                title = "Campana Nacional de Vacunacion 2025",
                summary = "Nuevos puntos de vacunacion en Bata y Malabo.",
                contentHtml = "<h1>Campana Nacional de Vacunacion 2025</h1><p>A partir del lunes se habilitaran nuevos puntos de vacunacion en Bata y Malabo.</p><blockquote>La vacunacion es gratuita, segura y protege a toda la comunidad.</blockquote>",
                contentPlain = "Campana Nacional de Vacunacion 2025\nA partir del lunes se habilitaran nuevos puntos de vacunacion en Bata y Malabo.",
                readMoreLabel = appContext.getString(R.string.official_read_more),
                type = OfficialPostType.Announcement,
                linkUrl = "https://www.salud.ge",
                isLive = true,
                createdAt = appContext.getString(R.string.common_now),
                likesCount = 27,
                commentsCount = 1
            )
        )
    }

    private companion object {
        const val OFFICIAL_REPOSITORY_LOG_TAG = "QuataOfficial"
    }
}
