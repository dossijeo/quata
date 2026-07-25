package com.quata.feature.feed.data

import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.text.decodeHtmlEntities
import com.quata.core.text.parsePostCommentBody

/** Portable shape of the PostgREST fields consumed by the shared Feed contract. */
data class FeedRemotePost(
    val id: String,
    val profileId: String? = null,
    val authorId: String? = null,
    val body: String? = null,
    val content: String? = null,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val createdAt: String? = null,
)

data class FeedRemoteComment(
    val id: String,
    val postId: String? = null,
    val profileId: String? = null,
    val body: String? = null,
    val createdAt: String? = null,
)

data class FeedRemoteLike(val postId: String? = null, val profileId: String? = null)

data class FeedRemoteProfile(
    val id: String,
    val displayName: String? = null,
    val fallbackName: String? = null,
    val countryCode: String? = null,
    val phoneLocal: String? = null,
    val neighborhood: String? = null,
    val barrio: String? = null,
    val avatarUrl: String? = null,
    val avatar: String? = null,
    val isAdmin: Boolean = false,
    val isOfficial: Boolean = false,
)

/**
 * Pure field-to-wire-model mapping shared by the browser and URLSession adapters.
 *
 * Transports remain responsible for parsing their native JSON representation into scalar fields,
 * as well as for HTTP, credentials and status/error handling. Keeping that boundary explicit
 * avoids making `commonMain` depend on either kotlinx.serialization JSON objects or Foundation.
 */
fun feedRemotePostFromFields(
    field: (String) -> String?,
    missingIdError: () -> Throwable = { IllegalStateException("feed_remote_response_missing_id") },
): FeedRemotePost = FeedRemotePost(
    id = field.requiredFeedRemoteId(missingIdError),
    profileId = field("profile_id"),
    authorId = field("author_id"),
    body = field("body"),
    content = field("content"),
    imageUrl = field("image_url"),
    videoUrl = field("video_url"),
    createdAt = field("created_at"),
)

fun feedRemoteCommentFromFields(
    field: (String) -> String?,
    missingIdError: () -> Throwable = { IllegalStateException("feed_remote_response_missing_id") },
): FeedRemoteComment = FeedRemoteComment(
    id = field.requiredFeedRemoteId(missingIdError),
    postId = field("post_id"),
    profileId = field("profile_id"),
    body = field("body"),
    createdAt = field("created_at"),
)

fun feedRemoteLikeFromFields(field: (String) -> String?): FeedRemoteLike = FeedRemoteLike(
    postId = field("post_id"),
    profileId = field("profile_id"),
)

fun feedRemoteProfileFromFields(
    field: (String) -> String?,
    booleanField: (String) -> Boolean,
    missingIdError: () -> Throwable = { IllegalStateException("feed_remote_response_missing_id") },
): FeedRemoteProfile = FeedRemoteProfile(
    id = field.requiredFeedRemoteId(missingIdError),
    displayName = field("display_name"),
    fallbackName = field("nombre"),
    countryCode = field("country_code"),
    phoneLocal = field("phone_local"),
    neighborhood = field("neighborhood"),
    barrio = field("barrio"),
    avatarUrl = field("avatar_url"),
    avatar = field("avatar"),
    isAdmin = booleanField("is_admin"),
    isOfficial = booleanField("is_official"),
)

private fun ((String) -> String?).requiredFeedRemoteId(missingIdError: () -> Throwable): String =
    invoke("id") ?: throw missingIdError()

fun feedRemoteProfileIds(
    posts: List<FeedRemotePost>,
    comments: List<FeedRemoteComment> = emptyList(),
    likes: List<FeedRemoteLike> = emptyList(),
): List<String> = (
    posts.mapNotNull { it.profileId ?: it.authorId } +
        comments.mapNotNull(FeedRemoteComment::profileId) +
        likes.mapNotNull(FeedRemoteLike::profileId)
    ).distinct()

fun buildFeedDomainPosts(
    posts: List<FeedRemotePost>,
    comments: List<FeedRemoteComment>,
    likes: List<FeedRemoteLike>,
    profiles: List<FeedRemoteProfile>,
    currentUserId: String?,
): List<Post> {
    val profilesById = profiles.associateBy(FeedRemoteProfile::id)
    val commentsByPostId = comments.groupBy(FeedRemoteComment::postId)
    val likesByPostId = likes.groupBy(FeedRemoteLike::postId)
    return posts.map { post ->
        val authorId = post.profileId ?: post.authorId.orEmpty()
        val author = profilesById[authorId]?.toFeedDomainUser()
            ?: User(authorId.ifBlank { "unknown" }, "", "Usuario")
        val postComments = commentsByPostId[post.id].orEmpty().toFeedDomainComments { comment ->
            profilesById[comment.profileId]?.displayName
                ?: profilesById[comment.profileId]?.fallbackName
                ?: "Usuario"
        }
        val postLikes = likesByPostId[post.id].orEmpty()
        post.toFeedDomain(
            author = author,
            comments = postComments,
            likesCount = postLikes.size,
            likedByCurrentUser = currentUserId != null && postLikes.any { it.profileId == currentUserId },
        )
    }
}

fun FeedRemotePost.toFeedDomain(
    author: User,
    comments: List<PostComment>,
    likesCount: Int,
    likedByCurrentUser: Boolean,
): Post = Post(
    id = id,
    author = author,
    text = (body ?: content.orEmpty()).decodeHtmlEntities(),
    imageUrl = imageUrl,
    videoUrl = videoUrl,
    placeName = null,
    rankingLabel = "#0",
    createdAt = createdAt.orEmpty(),
    likesCount = likesCount,
    isLikedByCurrentUser = likedByCurrentUser,
    comments = comments,
)

fun List<FeedRemoteComment>.toFeedDomainComments(authorNameFor: (FeedRemoteComment) -> String): List<PostComment> {
    val parsedById = associate { it.id to it.body.orEmpty().decodeHtmlEntities().parsePostCommentBody() }
    return map { comment ->
        val parsed = parsedById.getValue(comment.id)
        val target = parsed.commentId?.let { targetId -> firstOrNull { it.id == targetId } }
        val targetParsed = target?.let { parsedById[it.id] }
        PostComment(
            id = comment.id,
            authorName = authorNameFor(comment),
            message = parsed.message,
            timestamp = comment.createdAt.orEmpty(),
            authorId = comment.profileId,
            replyToAuthorName = parsed.authorName ?: target?.let(authorNameFor),
            replyToMessage = targetParsed?.message,
            replyToCommentId = parsed.commentId,
        )
    }
}

fun FeedRemoteProfile.toFeedDomainUser(): User = User(
    id = id,
    email = "${countryCode.orEmpty()}${phoneLocal.orEmpty()}@phone.quata.app",
    displayName = displayName?.takeIf(String::isNotBlank)
        ?: fallbackName?.takeIf(String::isNotBlank)
        ?: phoneLocal?.takeIf(String::isNotBlank)
        ?: "Usuario",
    neighborhood = neighborhood?.takeIf(String::isNotBlank) ?: barrio.orEmpty(),
    avatarUrl = avatarUrl ?: avatar,
    isAdmin = isAdmin,
    isOfficial = isOfficial,
)
