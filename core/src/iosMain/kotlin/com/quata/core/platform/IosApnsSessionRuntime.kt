package com.quata.core.platform

import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.session.IosSupabaseAuthRuntimeConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** App-lifetime owner; invoke its lifecycle entry points on the UIKit main dispatcher. */
class IosApnsSessionRuntime(
    configuration: IosSupabaseAuthRuntimeConfiguration,
    private val session: IosRenewableAuthSession,
    environment: ApnsEnvironment,
) : IosApnsTokenHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transport = IosApnsRegistrationTransport(configuration)
    private val coordinator = ApnsSessionCoordinator(transport)
    private val environment = environment
    private var token: String? = null
    private var available = false
    private var loggingOut = false
    private var revision = 0L

    override fun onApnsToken(token: String) {
        this.token = token.trim().lowercase()
        if (available && !loggingOut) synchronizeValidatedSession()
    }

    /** Called only after the composition root accepted restored or interactive authentication. */
    fun sessionBecameAvailable() {
        if (loggingOut) return
        available = true
        synchronizeValidatedSession()
    }

    fun sessionBecameUnavailable() {
        available = false
        revision++
        scope.launch { coordinator.synchronize(null, null, environment) }
    }

    private fun synchronizeValidatedSession() {
        val requestRevision = ++revision
        val expected = session.restoredSession() ?: return
        scope.launch {
            val fresh = session.currentSession() ?: return@launch
            if (requestRevision != revision || !available || loggingOut || fresh.userId != expected.userId ||
                fresh.authUserId != expected.authUserId) return@launch
            coordinator.synchronize(fresh, token, environment)
        }
    }

    /** False leaves Auth untouched; the host must retain its logout action and show a retryable error. */
    fun prepareForLogout(onCompleted: (Boolean) -> Unit) {
        if (loggingOut) { onCompleted(false); return }
        val wasAvailable = available
        val originalSession = session.restoredSession()
        loggingOut = true
        available = false
        val logoutRevision = ++revision
        scope.launch {
            val remoteSuccess = try {
                val fresh = session.currentSession()
                if (fresh != null) coordinator.refreshCredential(fresh)
                val removed = coordinator.synchronize(null, null, environment) == ApnsSynchronization.Applied
                // Covers a token received this launch before registration ran. Do not create a
                // registration during logout merely to discover/remove a possible remote row.
                removed && (token == null || (fresh != null && transport.unregister(
                    ApnsRegistration(fresh, token!!, environment),
                )))
            } catch (_: Exception) { false }
            val current = session.restoredSession()
            val sameLifecycle = revision == logoutRevision && current?.userId == originalSession?.userId &&
                current?.authUserId == originalSession?.authUserId
            val success = remoteSuccess && sameLifecycle
            if (!success) {
                loggingOut = false
                available = wasAvailable && current != null && sameLifecycle
            }
            onCompleted(success)
            if (!success && available && !loggingOut) synchronizeValidatedSession()
        }
    }

    /** Auth has now cleared its Keychain session. No new registration is allowed until validation. */
    fun logoutCompleted() {
        loggingOut = false
        sessionBecameUnavailable()
    }
}
