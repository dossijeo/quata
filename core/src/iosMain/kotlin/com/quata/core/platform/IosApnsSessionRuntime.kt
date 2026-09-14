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
    private val allowRegistration: Boolean = true,
) : IosApnsTokenHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transport = JournaledApnsRegistrationTransport(
        IosApnsRegistrationTransport(configuration), IosApnsRegistrationJournal(configuration.supabaseUrl),
    )
    private val coordinator = ApnsSessionCoordinator(transport)
    private val environment = environment
    private var token: String? = null
    private var permissionAllowed: Boolean? = null
    private var available = false
    private var loggingOut = false
    private var revision = 0L

    override fun onApnsToken(token: String) {
        this.token = token.trim().lowercase()
        if (available && !loggingOut) synchronizeValidatedSession()
    }

    /** Called only after the composition root accepted restored or interactive authentication. */
    fun sessionBecameAvailable() {
        if (loggingOut || !allowRegistration) return
        available = true
        synchronizeValidatedSession()
    }

    fun refreshIfAvailable() {
        if (available && !loggingOut) synchronizeValidatedSession()
    }

    fun notificationPermissionChanged(allowed: Boolean) {
        permissionAllowed = allowed
        if (available && !loggingOut) synchronizeValidatedSession()
    }

    fun sessionBecameUnavailable() {
        available = false
        revision++
        scope.launch { coordinator.synchronize(null, null, environment) }
    }

    private fun synchronizeValidatedSession() {
        if (permissionAllowed == null) return
        val requestRevision = ++revision
        val expected = session.restoredSession() ?: return
        scope.launch {
            val fresh = session.currentSession() ?: return@launch
            if (requestRevision != revision || !available || loggingOut || fresh.userId != expected.userId ||
                fresh.authUserId != expected.authUserId) return@launch
            val allowed = permissionAllowed == true
            val synchronized = coordinator.synchronize(fresh, if (allowed) token else null, environment)
            if (!allowed && synchronized == ApnsSynchronization.Applied && requestRevision == revision) {
                // Recover a registration from an earlier process even when this launch has not
                // received an OS token. Failures leave the journal available for the next retry.
                transport.recover(fresh)
            }
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
                if (!allowRegistration) {
                    transport.recoverWithSession { session.currentSession() }
                } else {
                    val fresh = session.currentSession()
                    if (fresh != null) coordinator.refreshCredential(fresh)
                    val removed = coordinator.synchronize(null, null, environment) == ApnsSynchronization.Applied
                    // Covers a token received this launch before registration ran. Do not create a
                    // registration during logout merely to discover/remove a possible remote row.
                    removed && transport.recover(fresh) && (token == null || (fresh != null && transport.unregister(
                        ApnsRegistration(fresh, token!!, environment),
                    )))
                }
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

fun createIosApnsSessionRuntime(
    configuration: IosSupabaseAuthRuntimeConfiguration,
    session: IosRenewableAuthSession,
    environment: String,
    allowRegistration: Boolean,
): IosApnsSessionRuntime {
    val parsed = when (environment) {
        "development" -> ApnsEnvironment.Sandbox
        "production" -> ApnsEnvironment.Production
        else -> null
    }
    // Recovery uses the environment persisted in each journal entry. An absent build setting
    // must disable new registrations without bypassing cleanup from a previous installation.
    return IosApnsSessionRuntime(configuration, session, parsed ?: ApnsEnvironment.Sandbox,
        allowRegistration && parsed != null)
}
