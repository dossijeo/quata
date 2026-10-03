package com.quata.feature.auth.domain

import com.quata.core.model.AuthSession

/** Platform authentication bridge. Android currently delegates to GoogleAuthHelper. */
fun interface GoogleAuthProvider {
    suspend fun signIn(): Result<AuthSession>
}

/** Authenticated account action; linking never creates or selects a Qüata profile. */
fun interface GoogleIdentityLinker {
    suspend fun linkGoogleIdentity(): Result<AuthSession>
}
