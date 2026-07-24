package com.quata.feature.feed.presentation

import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.feed.domain.FeedReadRepository
import com.quata.feature.feed.domain.FeedRepository
import com.quata.feature.feed.domain.ReadOnlyFeedRepository
import com.quata.feature.feed.data.IosFeedReadTransport
import com.quata.feature.feed.data.IosFeedRuntimeConfiguration
import com.quata.feature.feed.data.IosFeedSessionProvider
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
    val navigationMessage: String = "Quata para iOS",
    val onOpenChats: () -> Unit = {},
)

/**
 * Read-only iOS launchers use the shared browser safely while their mutation backend is not
 * available yet. A full [FeedRepository] remains required for mutation-capable surfaces.
 */
fun iosReadOnlyFeedHostDependencies(
    readRepository: FeedReadRepository,
    navigationMessage: String = "Quata para iOS",
    onOpenChats: () -> Unit = {},
): IosFeedHostDependencies = IosFeedHostDependencies(
    repository = ReadOnlyFeedRepository(readRepository),
    navigationMessage = navigationMessage,
    onOpenChats = onOpenChats,
)

/**
 * Authenticated iOS composition path for the shared read-only browser. The caller supplies public
 * deployment configuration and a provider that refreshes/returns the current user session; no
 * token, URL or sample repository is retained by this module.
 */
fun iosPostgrestReadOnlyFeedHostDependencies(
    configuration: IosFeedRuntimeConfiguration,
    sessionProvider: IosFeedSessionProvider,
    navigationMessage: String = "Quata para iOS",
    onOpenChats: () -> Unit = {},
): IosFeedHostDependencies = iosReadOnlyFeedHostDependencies(
    readRepository = RemoteFeedReadRepository(IosFeedReadTransport(configuration, sessionProvider)),
    navigationMessage = navigationMessage,
    onOpenChats = onOpenChats,
)

/**
 * Stable Swift entry point for a real [FeedRepository] supplied by the iOS composition root.
 * The screen, state and ViewModel are all common code; this iOS source merely creates the
 * Compose UIViewController.
 */
fun QuataFeedViewController(dependencies: IosFeedHostDependencies): UIViewController = ComposeUIViewController {
    QuataTheme {
        FeedBrowserHostContent(
            repository = dependencies.repository,
            navigationMessage = dependencies.navigationMessage,
            strings = IosFeedHostStrings,
            onOpenChats = dependencies.onOpenChats,
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
        FeedStatusContent(
            message = "Quata para iOS necesita una configuración pública válida para iniciar.",
            retryLabel = "Entendido",
            onRetry = {},
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
    mediaUnavailable = "El contenido multimedia aún no está disponible en Quata iOS.",
)
