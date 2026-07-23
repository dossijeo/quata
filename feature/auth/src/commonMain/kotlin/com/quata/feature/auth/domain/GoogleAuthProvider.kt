package com.quata.feature.auth.domain

import com.quata.core.model.AuthSession

/** Platform authentication bridge. Android currently delegates to GoogleAuthHelper. */
fun interface GoogleAuthProvider {
    suspend fun signIn(): Result<AuthSession>
}
