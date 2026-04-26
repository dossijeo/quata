package com.quata.core.model

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null
)
