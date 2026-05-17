package com.quata.core.model

data class Post(
    val id: String,
    val author: User,
    val text: String,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val placeName: String? = null,
    val rankingLabel: String = "#1",
    val createdAt: String,
    val likesCount: Int = 0,
    val isLikedByCurrentUser: Boolean = false,
    val isReportedByCurrentUser: Boolean = false,
    val comments: List<PostComment> = emptyList()
)

data class PostComment(
    val id: String,
    val authorName: String,
    val message: String,
    val timestamp: String,
    val replyToAuthorName: String? = null,
    val replyToMessage: String? = null,
    val replyToCommentId: String? = null
)
