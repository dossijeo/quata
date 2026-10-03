package com.quata.feature.auth.domain

import com.quata.core.model.AuthSession
import kotlin.coroutines.cancellation.CancellationException

class GoogleOAuthUserCancellation : CancellationException("google_oauth_user_cancelled")

/** Platform authentication bridge. Android currently delegates to GoogleAuthHelper. */
fun interface GoogleAuthProvider {
    /** Starts any user-activation-sensitive work before returning the suspendable exchange. */
    fun beginSignIn(): suspend () -> Result<AuthSession>
}

/** Authenticated account action; linking never creates or selects a Qüata profile. */
fun interface GoogleIdentityLinker {
    /** Starts any user-activation-sensitive work before returning the suspendable exchange. */
    fun beginIdentityLink(): suspend () -> Result<AuthSession>
}
