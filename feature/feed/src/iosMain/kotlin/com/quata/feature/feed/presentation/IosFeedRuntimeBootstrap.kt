package com.quata.feature.feed.presentation

import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.session.IosSupabaseAuthRuntimeConfiguration
import com.quata.core.session.IosSupabaseAuthSessionRefresher
import com.quata.feature.feed.data.IosFeedRuntimeConfiguration
import com.quata.feature.feed.data.IosFeedSession
import com.quata.feature.feed.data.IosFeedSessionProvider

/**
 * Restores one real Keychain-backed Supabase session into the shared Feed host. If Keychain has
 * no authenticated session this returns `null`; it does not create placeholder Feed data.
 */
class IosFeedRuntimeBootstrap(
    private val configuration: IosFeedRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession = IosRenewableAuthSession(
        IosSupabaseAuthSessionRefresher(
            IosSupabaseAuthRuntimeConfiguration(
                supabaseUrl = configuration.supabaseUrl,
                supabasePublishableKey = configuration.supabasePublishableKey,
            ),
        ),
    ),
) {
    /**
     * Returns the one Keychain-backed session owner used by both interactive Auth and Feed.
     * The launcher must not create another storage instance, otherwise a successful login would
     * not be visible to the repository rendered immediately afterwards.
     */
    fun authSessionForInteractiveLogin(): IosRenewableAuthSession = authSession

    fun restoredDependencies(
        navigationMessage: String = "Quata para iOS",
        onOpenChats: () -> Unit = {},
    ): IosFeedHostDependencies? {
        if (authSession.restoredSession() == null) return null
        return iosPostgrestReadOnlyFeedHostDependencies(
            configuration = configuration,
            sessionProvider = IosFeedSessionProvider {
                authSession.currentSession()?.let { session ->
                    IosFeedSession(accessToken = session.bearerToken, userId = session.userId)
                }
            },
            navigationMessage = navigationMessage,
            onOpenChats = onOpenChats,
        )
    }
}

/**
 * Swift-facing factory for the authenticated runtime bootstrap.
 *
 * Kotlin default constructor arguments are not exported as Swift overloads, so the UIKit host
 * calls this single-argument function instead of depending on an unavailable initializer.
 */
fun createIosFeedRuntimeBootstrap(
    configuration: IosFeedRuntimeConfiguration,
): IosFeedRuntimeBootstrap = IosFeedRuntimeBootstrap(configuration)
