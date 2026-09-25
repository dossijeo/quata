package com.quata.feature.auth.data

/**
 * Orders the remote effects that must finish before Android discards the only credentials that can
 * authorize them. If Auth revocation fails after push retirement, the previous push registration is
 * restored on a best-effort basis and the local authenticated session remains available for retry.
 */
internal class AndroidLogoutCoordinator(
    private val prepareRemoteSession: suspend () -> Unit,
    private val preparePrivateDataCleanup: suspend () -> Unit,
    private val cancelPrivateDataCleanup: suspend () -> Unit,
    private val retirePush: suspend () -> Unit,
    private val revokeAuthSession: suspend () -> Unit,
    private val restorePush: suspend () -> Unit,
    private val clearPrivateData: suspend () -> Unit,
    private val retireLocalSession: suspend () -> Unit,
    private val recordPrivateDataCleanupFailure: (Throwable) -> Unit,
) {
    suspend fun logout() {
        prepareRemoteSession()
        preparePrivateDataCleanup()
        var pushRetired = false
        try {
            retirePush()
            pushRetired = true
            revokeAuthSession()
        } catch (failure: Throwable) {
            if (pushRetired) runCatching { restorePush() }
            runCatching { cancelPrivateDataCleanup() }
            throw failure
        }
        val cleanupFailure = runCatching { clearPrivateData() }.exceptionOrNull()
        retireLocalSession()
        cleanupFailure?.let(recordPrivateDataCleanupFailure)
    }
}
