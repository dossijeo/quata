package com.quata.feature.feed.presentation

import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.platform.ShareService
import com.quata.core.session.IosSupabaseAuthRuntimeConfiguration
import com.quata.core.session.IosSupabaseAuthSessionRefresher
import com.quata.feature.feed.data.IosFeedRuntimeConfiguration
import com.quata.core.ui.components.IosMemberProfileOpeningState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    private val launchValidationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /**
     * Returns the one Keychain-backed session owner used by both interactive Auth and Feed.
     * The launcher must not create another storage instance, otherwise a successful login would
     * not be visible to the repository rendered immediately afterwards.
     */
    fun authSessionForInteractiveLogin(): IosRenewableAuthSession = authSession

    /** Public feed is always available when deployment settings are valid, even without Keychain. */
    fun publicDependencies(
        mediaFactory: IosFeedMediaFactory,
        shareService: ShareService,
        onOpenUserProfile: (String) -> Unit = {},
        initialPostId: String? = null,
        onAuthRequired: () -> Unit = {},
        onCreatePost: () -> Unit = {},
        onBackFromFocusedPost: (() -> Unit)? = null,
        profileOpeningState: IosMemberProfileOpeningState,
        preferredLanguageTag: String? = null,
    ): IosFeedHostDependencies = iosPublicPostgrestReadOnlyFeedHostDependencies(
            configuration = configuration,
            mediaFactory = mediaFactory,
            shareService = shareService,
            onOpenUserProfile = onOpenUserProfile,
            initialPostId = initialPostId,
            onAuthRequired = onAuthRequired,
            onCreatePost = onCreatePost,
            onBackFromFocusedPost = onBackFromFocusedPost,
            profileOpeningState = profileOpeningState,
            preferredLanguageTag = preferredLanguageTag,
        )

    fun authenticatedDependencies(
        mediaFactory: IosFeedMediaFactory,
        shareService: ShareService,
        initialPostId: String? = null,
        onOpenUserProfile: (String) -> Unit = {},
        onAuthRequired: () -> Unit = {},
        onCreatePost: () -> Unit = {},
        onBackFromFocusedPost: (() -> Unit)? = null,
        profileOpeningState: IosMemberProfileOpeningState,
        preferredLanguageTag: String? = null,
    ): IosFeedHostDependencies = iosAuthenticatedPostgrestFeedHostDependencies(
        configuration = configuration,
        authSession = authSession,
        mediaFactory = mediaFactory,
        shareService = shareService,
        initialPostId = initialPostId,
        onOpenUserProfile = onOpenUserProfile,
        onAuthRequired = onAuthRequired,
        onCreatePost = onCreatePost,
        onBackFromFocusedPost = onBackFromFocusedPost,
        profileOpeningState = profileOpeningState,
        preferredLanguageTag = preferredLanguageTag,
    )

    /** Returns whether a Keychain record exists; do not use this to mount authenticated UI. */
    fun hasRestoredSession(): Boolean = authSession.restoredSession() != null

    /**
     * Asynchronously validates the Keychain session before the launcher may mount private
     * factories. An expired session only succeeds after its refresh has completed and persisted.
     * A failed refresh retains Keychain credentials for a later retry while reporting false.
     */
    fun validateRestoredSession(onCompleted: (Boolean) -> Unit) {
        launchValidationScope.launch {
            onCompleted(authSession.validatedRestoredSession() != null)
        }
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
