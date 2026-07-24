package com.quata.feature.official.data

import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.text.decodeHtmlEntities
import com.quata.core.text.parsePostCommentBody
import com.quata.core.text.stripHtmlTagsAndDecode
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialPostType

/** Portable PostgREST fields used to assemble the Official feed. */
data class OfficialRemotePost(
    val id: String,
    val profileId: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val postType: String? = null,
    val contentHtml: String? = null,
    val readMoreLabel: String? = null,
    val language: String? = null,
    val translationGroupId: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val linkUrl: String? = null,
    val isLive: Boolean = false,
    val publishedAt: String? = null,
    val createdAt: String? = null,
)

data class OfficialRemoteLike(val postId: String? = null, val profileId: String? = null)

data class OfficialRemoteComment(
    val id: String,
    val postId: String? = null,
    val profileId: String? = null,
    val body: String? = null,
    val createdAt: String? = null,
)

data class OfficialRemoteProfile(
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

fun officialRemoteProfileIds(
    posts: List<OfficialRemotePost>,
    comments: List<OfficialRemoteComment> = emptyList(),
    likes: List<OfficialRemoteLike> = emptyList(),
): List<String> = (
    posts.mapNotNull(OfficialRemotePost::profileId) +
        comments.mapNotNull(OfficialRemoteComment::profileId) +
        likes.mapNotNull(OfficialRemoteLike::profileId)
    ).distinct()

fun buildOfficialDomainPosts(
    posts: List<OfficialRemotePost>,
    comments: List<OfficialRemoteComment>,
    likes: List<OfficialRemoteLike>,
    profiles: List<OfficialRemoteProfile>,
    currentUserId: String?,
    defaultTitle: String,
    defaultCommentAuthor: String,
): List<OfficialPostItem> {
    val profilesById = profiles.associateBy(OfficialRemoteProfile::id)
    val likesByPostId = likes.groupBy(OfficialRemoteLike::postId)
    val commentsByPostId = comments.groupBy(OfficialRemoteComment::postId)
    return posts.map { post ->
        val author = profilesById[post.profileId]?.toOfficialDomainUser()
            ?: User(post.profileId.orEmpty().ifBlank { "official" }, "", defaultTitle, isOfficial = true)
        val postLikes = likesByPostId[post.id].orEmpty()
        val remoteComments = commentsByPostId[post.id].orEmpty()
        post.toOfficialDomain(
            author = author.copy(isOfficial = true),
            comments = remoteComments.toOfficialDomainComments(profilesById, defaultCommentAuthor),
            likesCount = postLikes.size,
            likedByCurrentUser = currentUserId != null && postLikes.any { it.profileId == currentUserId },
            defaultTitle = defaultTitle,
        )
    }
}

fun OfficialRemotePost.toOfficialDomain(
    author: User,
    comments: List<PostComment>,
    likesCount: Int,
    likedByCurrentUser: Boolean,
    defaultTitle: String,
): OfficialPostItem {
    val safeHtml = contentHtml.orEmpty()
    val safePlain = safeHtml.stripHtmlTagsAndDecode()
    val safeTitle = title?.decodeHtmlEntities()?.takeIf(String::isNotBlank)
        ?: safePlain.lineSequence().firstOrNull().orEmpty()
    return OfficialPostItem(
        id = id,
        author = author,
        title = safeTitle.ifBlank { defaultTitle },
        summary = summary?.decodeHtmlEntities()?.takeIf(String::isNotBlank) ?: safePlain.take(180),
        contentHtml = safeHtml,
        contentPlain = safePlain,
        readMoreLabel = readMoreLabel?.decodeHtmlEntities().orEmpty(),
        language = OfficialPostLanguage.fromRemote(language),
        translationGroupId = translationGroupId,
        type = OfficialPostType.fromRemote(postType),
        mediaUrl = mediaUrl,
        mediaType = OfficialMediaType.fromRemote(mediaType),
        linkUrl = linkUrl,
        isLive = isLive,
        createdAt = publishedAt ?: createdAt.orEmpty(),
        likesCount = likesCount,
        commentsCount = comments.size,
        isLikedByCurrentUser = likedByCurrentUser,
        comments = comments,
    )
}

fun List<OfficialRemoteComment>.toOfficialDomainComments(
    profilesById: Map<String, OfficialRemoteProfile>,
    defaultCommentAuthor: String,
): List<PostComment> {
    fun authorName(comment: OfficialRemoteComment): String =
        profilesById[comment.profileId]?.displayName?.takeIf(String::isNotBlank)
            ?: profilesById[comment.profileId]?.fallbackName?.takeIf(String::isNotBlank)
            ?: defaultCommentAuthor

    val parsedById = associate { it.id to it.body.orEmpty().decodeHtmlEntities().parsePostCommentBody() }
    return map { comment ->
        val parsed = parsedById.getValue(comment.id)
        val target = parsed.commentId?.let { targetId -> firstOrNull { it.id == targetId } }
        val targetParsed = target?.let { parsedById[it.id] }
        PostComment(
            id = comment.id,
            authorName = authorName(comment),
            message = parsed.message,
            timestamp = comment.createdAt.orEmpty(),
            authorId = comment.profileId,
            replyToAuthorName = parsed.authorName ?: target?.let(::authorName),
            replyToMessage = targetParsed?.message,
            replyToCommentId = parsed.commentId,
        )
    }
}

fun OfficialRemoteProfile.toOfficialDomainUser(): User = User(
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
