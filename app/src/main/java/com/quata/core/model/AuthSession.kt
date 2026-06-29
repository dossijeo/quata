package com.quata.core.model

data class AuthSession(
    val token: String,
    val userId: String,
    val email: String,
    val displayName: String,
    val authUserId: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Long? = null
) {
    val bearerToken: String
        get() = accessToken?.takeIf { it.isNotBlank() } ?: token

    fun isSupabaseAuthenticated(): Boolean =
        accessToken?.isNotBlank() == true && refreshToken?.isNotBlank() == true

    fun shouldRefresh(nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): Boolean =
        refreshToken?.isNotBlank() == true &&
            expiresAt != null &&
            expiresAt - nowEpochSeconds <= REFRESH_LEEWAY_SECONDS

    companion object {
        private const val REFRESH_LEEWAY_SECONDS = 120L
    }
}
