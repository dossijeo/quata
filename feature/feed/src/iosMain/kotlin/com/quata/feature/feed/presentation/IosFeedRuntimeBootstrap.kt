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
