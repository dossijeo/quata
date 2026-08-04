package com.quata.feature.feed.presentation

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.quata.core.platform.ShareService
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.feed.domain.FeedReadRepository
import com.quata.feature.feed.domain.FeedRepository
import com.quata.feature.feed.domain.ReadOnlyFeedRepository
import com.quata.feature.feed.data.IosFeedReadTransport
import com.quata.feature.feed.data.IosFeedRuntimeConfiguration
import com.quata.feature.feed.data.IosAuthenticatedFeedRepository
import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.ui.components.IosMemberProfileOpeningState
import com.quata.feature.feed.data.RemoteFeedReadRepository
import platform.UIKit.UIViewController

/**
 * Narrow iOS composition root for the shared Feed UI.
 *
 * The repository stays a host dependency: it owns credentials, transport and lifecycle, while
 * this module owns the platform-neutral ViewModel and Compose presentation. This keeps the iOS
 * launcher from accidentally depending on Android's Supabase implementation.
 */
class IosFeedHostDependencies(
    val repository: FeedRepository,
    val mediaFactory: IosFeedMediaFactory,
    val shareService: ShareService,
    val onOpenUserProfile: (String) -> Unit = {},
    val initialPostId: String? = null,
    val presence: FeedUserPresence? = null,
    /** Capability gate owned by the UIKit app router; the Feed remains publicly readable. */
    val onAuthRequired: () -> Unit = {},
    val onCreatePost: () -> Unit = {},
    val profileOpeningState: IosMemberProfileOpeningState,
)

/**
 * Read-only iOS launchers use the shared browser safely while their mutation backend is not
 * available yet. A full [FeedRepository] remains required for mutation-capable surfaces.
 */
fun iosReadOnlyFeedHostDependencies(
    readRepository: FeedReadRepository,
    mediaFactory: IosFeedMediaFactory,
    shareService: ShareService,
    onOpenUserProfile: (String) -> Unit = {},
    initialPostId: String? = null,
    onAuthRequired: () -> Unit = {},
    onCreatePost: () -> Unit = {},
    profileOpeningState: IosMemberProfileOpeningState,
): IosFeedHostDependencies = IosFeedHostDependencies(
    repository = ReadOnlyFeedRepository(readRepository),
    mediaFactory = mediaFactory,
    shareService = shareService,
    onOpenUserProfile = onOpenUserProfile,
    initialPostId = initialPostId,
    onAuthRequired = onAuthRequired,
    onCreatePost = onCreatePost,
    profileOpeningState = profileOpeningState,
)

/**
 * Authenticated iOS composition path for the shared read-only browser. The caller supplies public
 * deployment configuration and a provider that refreshes/returns the current user session; no
 * token, URL or sample repository is retained by this module.
 */
fun iosPublicPostgrestReadOnlyFeedHostDependencies(
    configuration: IosFeedRuntimeConfiguration,
    mediaFactory: IosFeedMediaFactory,
    shareService: ShareService,
    onOpenUserProfile: (String) -> Unit = {},
    initialPostId: String? = null,
    onAuthRequired: () -> Unit = {},
    onCreatePost: () -> Unit = {},
    profileOpeningState: IosMemberProfileOpeningState,
): IosFeedHostDependencies = iosReadOnlyFeedHostDependencies(
    readRepository = RemoteFeedReadRepository(IosFeedReadTransport(configuration)),
    mediaFactory = mediaFactory,
    shareService = shareService,
    onOpenUserProfile = onOpenUserProfile,
    initialPostId = initialPostId,
    onAuthRequired = onAuthRequired,
    onCreatePost = onCreatePost,
    profileOpeningState = profileOpeningState,
)

/** Authenticated launch path: it shares the Keychain session owner and enables reviewed writes. */
fun iosAuthenticatedPostgrestFeedHostDependencies(
    configuration: IosFeedRuntimeConfiguration,
    authSession: IosRenewableAuthSession,
    mediaFactory: IosFeedMediaFactory,
    shareService: ShareService,
    initialPostId: String? = null,
    onOpenUserProfile: (String) -> Unit = {},
    onAuthRequired: () -> Unit = {},
    onCreatePost: () -> Unit = {},
    profileOpeningState: IosMemberProfileOpeningState,
): IosFeedHostDependencies {
    val transport = IosFeedReadTransport(configuration, authSession)
    val read = RemoteFeedReadRepository(transport)
    return IosFeedHostDependencies(
        repository = IosAuthenticatedFeedRepository(transport, ReadOnlyFeedRepository(read)),
        mediaFactory = mediaFactory,
        shareService = shareService,
        onOpenUserProfile = onOpenUserProfile,
        initialPostId = initialPostId,
        presence = IosFeedPresence(configuration, authSession),
        onAuthRequired = onAuthRequired,
        onCreatePost = onCreatePost,
        profileOpeningState = profileOpeningState,
    )
}

/**
 * Stable Swift entry point for a real [FeedRepository] supplied by the iOS composition root.
 * The screen, state and ViewModel are all common code; this iOS source merely creates the
 * Compose UIViewController.
 */
fun QuataFeedViewController(dependencies: IosFeedHostDependencies): UIViewController = ComposeUIViewController {
    QuataTheme {
        var muted by rememberSaveable { mutableStateOf(false) }
        val openingProfileUserId by dependencies.profileOpeningState.profileId.collectAsState()
        FeedScreenHost(
            padding = PaddingValues(),
            repository = dependencies.repository,
            focusedPostId = dependencies.initialPostId,
            presence = dependencies.presence,
            slots = FeedScreenPlatformSlots(
                media = { post, isCurrent, initialPositionMs, onPositionChanged ->
                    IosFeedMediaSlot(
                        post = post,
                        isCurrent = isCurrent,
                        initialPositionMs = initialPositionMs,
                        onPositionChanged = onPositionChanged,
                        isMuted = muted,
                        onMuteChange = { muted = it },
                        mediaFactory = dependencies.mediaFactory,
                    )
                },
                avatar = { post ->
                    IosFeedAuthorAvatar(
                        post = post,
                        onOpenUserProfile = dependencies.onOpenUserProfile,
                        isLoading = openingProfileUserId == post.author.id,
                    )
                },
                rankingAvatar = { item -> IosFeedRankingAvatar(item) },
                avatarWithPresence = { post, isOnline ->
                    IosFeedAuthorAvatar(
                        post = post,
                        onOpenUserProfile = dependencies.onOpenUserProfile,
                        isOnline = isOnline,
                        isLoading = openingProfileUserId == post.author.id,
                    )
                },
                rankingAvatarWithPresence = { item, isOnline -> IosFeedRankingAvatar(item, isOnline) },
                share = dependencies.shareService::share,
                showComposeMessage = true,
            ),
            onOpenUserProfile = dependencies.onOpenUserProfile,
            onAuthRequired = dependencies.onAuthRequired,
            onCreatePost = dependencies.onCreatePost,
        )
    }
}

/**
 * Honest launcher surface used only when the iOS host has no valid public runtime configuration.
 * It is intentionally separate from [QuataFeedViewController], so a caller cannot mistake an
 * unconfigured deployment for a loaded feed.
 */
fun QuataIosMigrationStatusViewController(): UIViewController = ComposeUIViewController {
    QuataTheme {
        var acknowledged by remember { mutableStateOf(false) }
        FeedStatusContent(
            message = if (acknowledged) {
                "La configuración pública sigue sin estar disponible."
            } else {
                "Quata para iOS necesita una configuración pública válida para iniciar."
            },
            retryLabel = if (acknowledged) "Comprobar de nuevo" else "Entendido",
            onRetry = { acknowledged = true },
            modifier = Modifier.testTag("quata-ios-compose-root"),
            messageTag = "migration-message",
            actionTag = "migration-action",
        )
    }
}
