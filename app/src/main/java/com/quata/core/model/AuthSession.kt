package com.quata.core.model

data class AuthSession(
    val token: String,
    val userId: String,
    val email: String,
    val displayName: String
)
