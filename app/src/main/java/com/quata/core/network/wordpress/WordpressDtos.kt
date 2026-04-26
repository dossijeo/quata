package com.quata.core.network.wordpress

import com.google.gson.annotations.SerializedName

data class WordpressTokenResponse(
    @SerializedName("token") val token: String?,
    @SerializedName("user_email") val userEmail: String?,
    @SerializedName("user_nicename") val userNiceName: String?,
    @SerializedName("user_display_name") val userDisplayName: String?
)

data class WordpressRegisterRequest(
    val email: String,
    val password: String,
    val displayName: String
)

data class WordpressRegisterResponse(
    val id: String?,
    val email: String?,
    val displayName: String?,
    val token: String?
)

data class WordpressPostDto(
    val id: Int,
    val date: String?,
    val title: RenderedDto?,
    val content: RenderedDto?,
    val excerpt: RenderedDto?,
    val author: Int?
)

data class RenderedDto(
    val rendered: String?
)

data class WordpressCreatePostRequest(
    val title: String,
    val content: String,
    val status: String = "publish"
)
