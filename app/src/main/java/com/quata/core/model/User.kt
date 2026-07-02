package com.quata.core.model

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val neighborhood: String = "",
    val avatarUrl: String? = null,
    val isAdmin: Boolean = false,
    val isOfficial: Boolean = false
)
