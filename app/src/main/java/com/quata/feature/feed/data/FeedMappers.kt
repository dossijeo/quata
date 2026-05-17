package com.quata.feature.feed.data

import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.network.wordpress.WordpressPostDto
import com.quata.data.supabase.CommunityComment
import com.quata.data.supabase.CommunityPost
import com.quata.data.supabase.CommunityProfile

fun WordpressPostDto.toDomain(): Post {
    val body = excerpt?.rendered ?: content?.rendered ?: title?.rendered ?: ""
    return Post(
        id = id.toString(),
        author = User(author?.toString() ?: "wp", "", "WordPress"),
        text = body.stripHtml().trim().ifBlank { "Publicacion de WordPress" },
        imageUrl = null,
        placeName = null,
        rankingLabel = "#0",
        createdAt = date ?: "",
        likesCount = 0,
        comments = emptyList()
    )
}

fun CommunityPost.toDomain(
    author: User,
    comments: List<PostComment>,
    likesCount: Int,
    likedByCurrentUser: Boolean
): Post = Post(
    id = id,
    author = author,
    text = body ?: content.orEmpty(),
    imageUrl = image_url,
    videoUrl = video_url,
    placeName = null,
    rankingLabel = "#0",
    createdAt = created_at ?: "",
    likesCount = likesCount,
    isLikedByCurrentUser = likedByCurrentUser,
    comments = comments
)

fun CommunityComment.toDomain(authorName: String): PostComment =
    body.orEmpty().parseReplyShortcode().let { parsed ->
        PostComment(
            id = id,
            authorName = authorName,
            message = parsed.message,
            timestamp = created_at.orEmpty(),
            replyToAuthorName = parsed.authorName,
            replyToCommentId = parsed.commentId
        )
    }

fun List<CommunityComment>.toDomainComments(authorNameFor: (CommunityComment) -> String): List<PostComment> {
    val parsedById = associate { comment -> comment.id to comment.body.orEmpty().parseReplyShortcode() }
    return map { comment ->
        val parsed = parsedById.getValue(comment.id)
        val target = parsed.commentId?.let { targetId -> firstOrNull { it.id == targetId } }
        val targetParsed = target?.let { parsedById[it.id] }
        PostComment(
            id = comment.id,
            authorName = authorNameFor(comment),
            message = parsed.message,
            timestamp = comment.created_at.orEmpty(),
            replyToAuthorName = parsed.authorName ?: target?.let(authorNameFor),
            replyToMessage = targetParsed?.message,
            replyToCommentId = parsed.commentId
        )
    }
}

fun PostComment.toRemoteBody(): String {
    val cleanMessage = message.trim()
    val replyId = replyToCommentId?.takeIf { it.isNotBlank() && !it.startsWith("local_") }
    val replyAuthor = replyToAuthorName?.takeIf { it.isNotBlank() }?.replace("]", "")?.replace("\n", " ")
    return if (replyId != null && replyAuthor != null) {
        "[reply:$replyId:$replyAuthor] $cleanMessage"
    } else {
        cleanMessage
    }
}

private fun String.parseReplyShortcode(): ParsedCommentBody {
    val match = ReplyShortcodeRegex.find(this)
    if (match == null) return ParsedCommentBody(message = trim())
    return ParsedCommentBody(
        commentId = match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() },
        authorName = match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() },
        message = removeRange(match.range).trim()
    )
}

private data class ParsedCommentBody(
    val message: String,
    val commentId: String? = null,
    val authorName: String? = null
)

private val ReplyShortcodeRegex = Regex("""^\s*\[reply:([^:\]]+):([^\]]+)]\s*""")

fun CommunityProfile.toDomainUser(): User =
    User(
        id = id,
        email = "${country_code.orEmpty()}${phone_local.orEmpty()}@phone.quata.app",
        displayName = display_name?.takeIf { it.isNotBlank() }
            ?: nombre?.takeIf { it.isNotBlank() }
            ?: phone_local?.takeIf { it.isNotBlank() }
            ?: "Usuario",
        neighborhood = neighborhood?.takeIf { it.isNotBlank() } ?: barrio.orEmpty(),
        avatarUrl = avatar_url ?: avatar
    )

private fun String.stripHtml(): String = replace(Regex("<[^>]*>"), "")
