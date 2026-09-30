package com.quata.web

import com.quata.core.navigation.AuthenticationContinuationCoordinator
import com.quata.core.navigation.AuthenticationContinuationKind
import com.quata.core.navigation.PendingAuthenticationContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Transfers the one-shot action out of a Compose effect before clearing its StateFlow key. */
internal fun CoroutineScope.resumeGlobalSosAfterAuthentication(
    coordinator: AuthenticationContinuationCoordinator,
    pending: PendingAuthenticationContinuation?,
    isAuthenticated: Boolean,
    dispatch: suspend () -> Unit,
): Boolean {
    if (
        !isAuthenticated ||
        pending?.intent?.kind != AuthenticationContinuationKind.GlobalSosDispatch ||
        coordinator.claim(pending.requestId) == null
    ) {
        return false
    }
    launch { dispatch() }
    return true
}
