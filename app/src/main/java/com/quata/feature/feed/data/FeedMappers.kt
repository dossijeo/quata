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
    PostComment(
        id = id,
        authorName = authorName,
        message = body.orEmpty(),
        timestamp = created_at.orEmpty()
    )

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
