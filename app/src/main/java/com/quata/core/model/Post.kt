package com.quata.core.model

data class Post(
    val id: String,
    val author: User,
    val text: String,
    val imageUrl: String? = null,
    val createdAt: String,
    val likesCount: Int = 0,
    val commentsCount: Int = 0
)
