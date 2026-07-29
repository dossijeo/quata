package com.quata.feature.feed.presentation

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.feed.domain.FeedReadRepository
import com.quata.feature.feed.domain.FeedRepository
import com.quata.feature.feed.domain.ReadOnlyFeedRepository
import com.quata.feature.feed.data.IosFeedReadTransport
import com.quata.feature.feed.data.IosFeedRuntimeConfiguration
import com.quata.feature.feed.data.IosAuthenticatedFeedRepository
import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.platform.ShareService
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
    val shareService: ShareService,
    /** Real UIKit member-profile route supplied by the authenticated composition root. */
    val onOpenUserProfile: (String) -> Unit = {},
    val initialPostId: String? = null,
)

/**
 * Read-only iOS launchers use the shared browser safely while their mutation backend is not
 * available yet. A full [FeedRepository] remains required for mutation-capable surfaces.
 */
fun iosReadOnlyFeedHostDependencies(
    readRepository: FeedReadRepository,
    shareService: ShareService,
    onOpenUserProfile: (String) -> Unit = {},
    initialPostId: String? = null,
): IosFeedHostDependencies = IosFeedHostDependencies(
    repository = ReadOnlyFeedRepository(readRepository),
    shareService = shareService,
    onOpenUserProfile = onOpenUserProfile,
    initialPostId = initialPostId,
)

/**
 * Authenticated iOS composition path for the shared read-only browser. The caller supplies public
 * deployment configuration and a provider that refreshes/returns the current user session; no
 * token, URL or sample repository is retained by this module.
 */
fun iosPublicPostgrestReadOnlyFeedHostDependencies(
    configuration: IosFeedRuntimeConfiguration,
    shareService: ShareService,
    onOpenUserProfile: (String) -> Unit = {},
    initialPostId: String? = null,
): IosFeedHostDependencies = iosReadOnlyFeedHostDependencies(
    readRepository = RemoteFeedReadRepository(IosFeedReadTransport(configuration)),
    shareService = shareService,
    onOpenUserProfile = onOpenUserProfile,
    initialPostId = initialPostId,
)

/** Authenticated launch path: it shares the Keychain session owner and enables reviewed writes. */
fun iosAuthenticatedPostgrestFeedHostDependencies(
    configuration: IosFeedRuntimeConfiguration,
    authSession: IosRenewableAuthSession,
    shareService: ShareService,
    initialPostId: String? = null,
    onOpenUserProfile: (String) -> Unit = {},
): IosFeedHostDependencies {
    val transport = IosFeedReadTransport(configuration, authSession)
    val read = RemoteFeedReadRepository(transport)
    return IosFeedHostDependencies(
        repository = IosAuthenticatedFeedRepository(transport, ReadOnlyFeedRepository(read)),
        shareService = shareService,
        onOpenUserProfile = onOpenUserProfile,
        initialPostId = initialPostId,
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
        FeedScreenHost(
            padding = PaddingValues(),
            repository = dependencies.repository,
            focusedPostId = dependencies.initialPostId,
            slots = FeedScreenPlatformSlots(
                media = { post, isCurrent, initialPositionMs, onPositionChanged ->
                    IosFeedMediaSlot(
                        post = post,
                        isCurrent = isCurrent,
                        initialPositionMs = initialPositionMs,
                        onPositionChanged = onPositionChanged,
                        isMuted = muted,
                        onMuteChange = { muted = it },
                    )
                },
                avatar = { post -> IosFeedAuthorAvatar(post, dependencies.onOpenUserProfile) },
                rankingAvatar = ::IosFeedRankingAvatar,
                share = dependencies.shareService::share,
                showComposeMessage = true,
            ),
            onOpenUserProfile = dependencies.onOpenUserProfile,
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

private val IosFeedHostStrings = FeedBrowserHostStrings(
    loading = "Cargando publicaciones…",
    retry = "Reintentar",
    loadFailure = "No se pudo cargar el feed.",
    refresh = "Actualizar",
    refreshing = "Actualizando…",
    conversations = "Conversaciones",
    loadingOlder = "Cargando…",
    loadOlder = "Cargar anteriores",
    noText = "Publicación sin texto",
    readMore = "Leer más",
    close = "Cerrar",
    empty = "Aún no hay publicaciones disponibles.",
    mediaUnavailable = "",
)
