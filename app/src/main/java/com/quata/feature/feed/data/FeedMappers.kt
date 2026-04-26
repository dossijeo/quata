package com.quata.feature.feed.data

import com.quata.core.model.Post
import com.quata.core.model.User
import com.quata.core.network.supabase.SupabasePostDto
import com.quata.core.network.wordpress.WordpressPostDto

fun WordpressPostDto.toDomain(): Post {
    val body = excerpt?.rendered ?: content?.rendered ?: title?.rendered ?: ""
    return Post(
        id = id.toString(),
        author = User(author?.toString() ?: "wp", "", "WordPress"),
        text = body.stripHtml().trim().ifBlank { "Publicación de WordPress" },
        imageUrl = null,
        placeName = null,
        rankingLabel = "#0",
        createdAt = date ?: "",
        likesCount = 0,
        comments = emptyList()
    )
}

fun SupabasePostDto.toDomain(): Post = Post(
    id = id,
    author = User(userId ?: "supabase", "", "Qüata user"),
    text = text.orEmpty(),
    imageUrl = imageUrl,
    placeName = null,
    rankingLabel = "#0",
    createdAt = createdAt ?: "",
    likesCount = 0,
    comments = emptyList()
)

private fun String.stripHtml(): String = replace(Regex("<[^>]*>"), "")
