package com.quata.feature.feed.presentation

import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.session.IosSupabaseAuthRuntimeConfiguration
import com.quata.core.session.IosSupabaseAuthSessionRefresher
import com.quata.feature.feed.data.IosFeedRuntimeConfiguration

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

    /** Public feed is always available when deployment settings are valid, even without Keychain. */
    fun publicDependencies(
        navigationMessage: String = "Quata para iOS",
        onOpenChats: () -> Unit = {},
        onBackToFeed: () -> Unit = {},
        initialPostId: String? = null,
    ): IosFeedHostDependencies = iosPublicPostgrestReadOnlyFeedHostDependencies(
            configuration = configuration,
            navigationMessage = navigationMessage,
            onOpenChats = onOpenChats,
            onBackToFeed = onBackToFeed,
            initialPostId = initialPostId,
        )

    fun authenticatedDependencies(
        shareService: ShareService,
        initialPostId: String? = null,
        onOpenUserProfile: (String) -> Unit = {},
    ): IosFeedHostDependencies = iosAuthenticatedPostgrestFeedHostDependencies(
        configuration = configuration,
        authSession = authSession,
        shareService = shareService,
        initialPostId = initialPostId,
        onOpenUserProfile = onOpenUserProfile,
    )

    /** Session restoration remains the gate for interactive iOS feature factories. */
    fun hasRestoredSession(): Boolean = authSession.restoredSession() != null
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
