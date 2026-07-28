@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.capability.QuataFeature
import com.quata.core.navigation.quataChatDeepLinkOrNull
import com.quata.core.navigation.quataChatUrl
import com.quata.core.navigation.quataOfficialPostIdOrNull
import com.quata.core.navigation.quataPostIdOrNull
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.ui.components.QuataBottomNavigation
import com.quata.core.ui.components.QuataNavigationItem
import com.quata.designsystem.effects.fluidTouchEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.quata.feature.auth.presentation.AuthSessionShellContent
import com.quata.feature.neighborhoods.presentation.NeighborhoodListStrings
import com.quata.feature.neighborhoods.presentation.NeighborhoodUserRowStrings
import com.quata.feature.neighborhoods.presentation.NeighborhoodUsersStrings
import com.quata.feature.whatsnew.domain.WhatsNewRepository
import kotlinx.browser.document
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ensureWebClientInstanceId()
    registerWebPushWorker()
    val platformServices = WebPlatformServices()
    val runtimeConfiguration = WebRuntimeConfiguration.fromDocument()
    ComposeViewport(document.getElementById("quata-root")!!) {
        QuataWebApp(platformServices, runtimeConfiguration)
    }
}

private fun registerWebPushWorker(): Unit = js(
    """
    (() => {
    if ('serviceWorker' in globalThis.navigator) {
      const locale = globalThis.navigator.language || globalThis.document?.documentElement?.lang || 'en';
      globalThis.navigator.serviceWorker.register('/quata-sw.js')
        .then(() => globalThis.navigator.serviceWorker.ready)
        .then((registration) => registration.active?.postMessage({ type: 'quata:set-notification-locale', locale }))
        .catch(() => {});
    }
    })()
    """,
)

/**
 * A service worker is the only browser context that receives `pushsubscriptionchange`. It
 * notifies an open launcher, which has the active access/web-session tokens needed to perform
 * the authenticated, idempotent `subscribe` request.
 */
private fun observeWebPushSubscriptionChanges(onChanged: () -> Unit): () -> Unit = js(
    """
    (() => {
    const container = globalThis.navigator?.serviceWorker;
    if (!container?.addEventListener) return () => {};
    const listener = (event) => {
      if (event?.data?.type === 'quata:push-subscription-change') onChanged();
    };
    container.addEventListener('message', listener);
    return () => container.removeEventListener('message', listener);
    })()
    """,
)

/** Delivers a persisted Web Share Target payload to an already-open launcher. */
private fun observeIncomingWebShares(onReceived: () -> Unit): () -> Unit = js(
    """
    (() => {
    const container = globalThis.navigator?.serviceWorker;
    if (!container?.addEventListener) return () => {};
    const listener = (event) => {
      if (event?.data?.type === 'quata:incoming-share') onReceived();
    };
    container.addEventListener('message', listener);
    return () => container.removeEventListener('message', listener);
    })()
    """,
)

@Composable
private fun QuataWebApp(
    platformServices: WebPlatformServices,
    runtimeConfiguration: WebRuntimeConfiguration,
) {
    val scope = rememberCoroutineScope()
    val navigation = rememberWebNavigation()
    val authRepository = remember(runtimeConfiguration, platformServices.preferences) {
        WebAuthRepository(runtimeConfiguration, platformServices.preferences)
    }
    val sessionCoordinator = remember(runtimeConfiguration, authRepository) {
        WebPushSessionCoordinator(
            configuration = runtimeConfiguration,
            authRepository = authRepository,
        )
    }
    val feedRepository = remember(runtimeConfiguration, authRepository) {
        WebFeedRepository(
            client = WebPostgrestClient(runtimeConfiguration, authRepository),
            authRepository = authRepository,
        )
    }
    val officialRepository = remember(runtimeConfiguration, authRepository) {
        WebOfficialRepository(
            client = WebPostgrestClient(runtimeConfiguration, authRepository),
            authRepository = authRepository,
        )
    }
    val chatRepository = remember(runtimeConfiguration, authRepository) {
        WebChatRepository(
            rpcClient = WebPostgrestRpcClient(runtimeConfiguration, authRepository),
            authRepository = authRepository,
            attachmentUploader = WebChatAttachmentUploader(runtimeConfiguration, authRepository),
        )
    }
    val neighborhoodsRepository = remember(runtimeConfiguration, authRepository) {
        WebNeighborhoodsRepository(
            client = WebPostgrestClient(runtimeConfiguration, authRepository),
            authRepository = authRepository,
        )
    }
    val notificationsRepository = remember(chatRepository) { WebNotificationsRepository(chatRepository) }
    val profileRepository = remember(runtimeConfiguration, authRepository, platformServices.preferences, platformServices.contacts) {
        WebProfileRepository(
            preferences = platformServices.preferences,
            contactPicker = platformServices.contacts,
            remoteGateway = WebProfileRemoteGateway(WebPostgrestClient(runtimeConfiguration, authRepository)),
            remoteSessionProvider = WebProfileSessionProvider(authRepository),
            remoteAvailable = {
                runtimeConfiguration.supabaseUrl?.isNotBlank() == true &&
                    runtimeConfiguration.supabasePublishableKey?.isNotBlank() == true &&
                    authRepository.activeProfileSessionOrNull() != null
            },
        )
    }
    val whatsNewRepository: WhatsNewRepository = remember(runtimeConfiguration, authRepository) {
        WebWhatsNewRepository(
            rpcClient = WebPostgrestRpcClient(runtimeConfiguration, authRepository),
        )
    }
    val incomingShareStore = remember { WebIncomingShareStore() }
    var isSessionReady by remember { mutableStateOf(false) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(QuataThemeMode.System) }
    var touchFlowEnabled by remember { mutableStateOf(true) }
    DisposableEffect(authRepository, sessionCoordinator, platformServices.preferences) {
        val removeBridge = installWebAuthE2eBridge(
            login = { countryCode, phone, password, resolve, reject ->
                scope.launch {
                    authRepository.login(countryCode, phone, password).fold(
                        onSuccess = {
                            platformServices.preferences.putString(WebSessionReadyKey, "true")
                            isSessionReady = true
                            currentUserId = authRepository.activeProfileSessionOrNull()?.userId
                            resolve("authenticated")
                        },
                        onFailure = { reject("login_failed") },
                    )
                }
            },
            restore = { resolve, reject ->
                scope.launch {
                    val restored = authRepository.restoreLocalSession()
                    if (restored == null) {
                        reject("restore_failed")
                    } else {
                        isSessionReady = true
                        currentUserId = restored.userId
                        resolve("restored")
                    }
                }
            },
            logout = { resolve, reject ->
                scope.launch {
                    val result = sessionCoordinator.logoutCurrentSession()
                    platformServices.preferences.remove(WebSessionReadyKey)
                    isSessionReady = false
                    currentUserId = null
                    if (result is WebPushSessionResult.Success) resolve("logged_out") else reject("logout_failed")
                }
            },
        )
        onDispose(removeBridge)
    }
    val capabilityRegistry = remember(runtimeConfiguration, isSessionReady, currentUserId) {
        webFeatureCapabilityRegistry(
            configuration = runtimeConfiguration,
            hasAuthenticatedSession = isSessionReady && currentUserId != null,
        )
    }
    DisposableEffect(platformServices.documentOpener) {
        val uninstall = installDocmentisProductSmokeBridge { reference, displayName, mimeType, complete ->
            scope.launch {
                val result = platformServices.documentOpener.open(
                    PlatformFile(
                        reference = reference,
                        displayName = displayName.takeIf(String::isNotBlank),
                        mimeType = mimeType.takeIf(String::isNotBlank),
                    ),
                )
                when (result) {
                    is PlatformResult.Success -> complete("success", null)
                    is PlatformResult.Failure -> complete("failure", result.reason)
                    PlatformResult.Cancelled -> complete("cancelled", null)
                    PlatformResult.Unsupported -> complete("unsupported", null)
                }
            }
        }
        onDispose(uninstall)
    }
    LaunchedEffect(platformServices.preferences) {
        isSessionReady = platformServices.preferences.getString(WebSessionReadyKey) == "true"
        currentUserId = authRepository.sessionForAuthenticatedRequest()?.userId
        themeMode = QuataThemeMode.fromStorageValue(platformServices.preferences.getString(WebThemeModeKey))
        touchFlowEnabled = platformServices.preferences.getString(WebTouchFlowEnabledKey) != "false"
    }
    LaunchedEffect(isSessionReady, sessionCoordinator) {
        if (isSessionReady) {
            platformServices.preferences.putString(
                "web.push.subscription_status",
                sessionCoordinator.subscribeCurrentSession().diagnosticValue(),
            )
        }
    }
    DisposableEffect(isSessionReady, sessionCoordinator) {
        val stopObserving = observeWebPushSubscriptionChanges {
            if (isSessionReady) {
                scope.launch {
                    platformServices.preferences.putString(
                        "web.push.subscription_status",
                        sessionCoordinator.subscribeCurrentSession().diagnosticValue(),
                    )
                }
            }
        }
        onDispose(stopObserving)
    }
    DisposableEffect(isSessionReady) {
        val stopObserving = observeIncomingWebShares {
            if (isSessionReady) navigateWebFragment("share-target")
        }
        onDispose(stopObserving)
    }
    LaunchedEffect(navigation, runtimeConfiguration.isBackendConfigured) {
        platformServices.preferences.putString("web.runtime.backend_configured", runtimeConfiguration.isBackendConfigured.toString())
        platformServices.preferences.putString("web.navigation.route", navigation.route)
        navigation.chatConversationId?.let { platformServices.preferences.putString("web.navigation.chat", it) }
        platformServices.preferences.putString(
            "web.runtime.backend_configured",
            runtimeConfiguration.isBackendConfigured.toString(),
        )
    }
    QuataTheme(mode = themeMode) {
        Box(Modifier.fillMaxSize().fluidTouchEffect(enabled = touchFlowEnabled)) {
            if (isSessionReady) {
            AuthSessionShellContent(
                isLoggingOut = isLoggingOut,
                logoutLabel = "Cerrar sesión",
                loggingOutLabel = "Cerrando sesión...",
                logoutButtonOverride = { label, enabled, onClick, modifier ->
                    WebNativeButton(label, enabled, onClick, modifier.fillMaxWidth().height(48.dp))
                },
                onLogout = {
                    scope.launch {
                        isLoggingOut = true
                        val result = sessionCoordinator.logoutCurrentSession()
                        platformServices.preferences.remove(WebSessionReadyKey)
                        platformServices.preferences.putString(
                            "web.auth.logout_status",
                            result.diagnosticValue(),
                        )
                        isSessionReady = false
                        isLoggingOut = false
                    }
                },
                bottomNavigation = {
                    QuataBottomNavigation(
                        items = webNavigationItems,
                        selectedId = navigation.route,
                        onItemClick = ::navigateWebFragment,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        itemOverride = { item, selected, onClick, itemModifier ->
                            WebNativeButton(item.label, true, onClick, itemModifier.height(72.dp), selected)
                        },
                    )
                },
            ) {
                if (navigation.route == "share-target") {
                    WebExternalShareHost(
                        repository = chatRepository,
                        clipboardService = platformServices.clipboard,
                        store = incomingShareStore,
                        onFinished = { conversationId ->
                            if (conversationId != null) navigateWebConversation(conversationId) else navigateWebFragment("chat")
                        },
                        onDismiss = { navigateWebFragment("chat") },
                    )
                } else if (navigation.route == "share-target-error") {
                    WebShareTargetErrorHost(onDismiss = { navigateWebFragment("chat") })
                } else if (navigation.route == "settings") {
                    WebSettingsHost(
                        touchFlowEnabled = touchFlowEnabled,
                        themeMode = themeMode,
                        accountLifecycleActions = remember(authRepository) { WebAuthAccountLifecycleActions(authRepository) },
                        onTouchFlowEnabledChange = { enabled ->
                            touchFlowEnabled = enabled
                            scope.launch { platformServices.preferences.putString(WebTouchFlowEnabledKey, enabled.toString()) }
                        },
                        onThemeModeChange = { mode ->
                            themeMode = mode
                            scope.launch { platformServices.preferences.putString(WebThemeModeKey, mode.storageValue) }
                        },
                        onAccountLifecycleSuccess = {
                            scope.launch {
                                val result = sessionCoordinator.logoutCurrentSession()
                                platformServices.preferences.remove(WebSessionReadyKey)
                                platformServices.preferences.putString("web.auth.logout_status", result.diagnosticValue())
                                navigateWebFragment("")
                                isSessionReady = false
                            }
                        },
                    )
                } else if (navigation.route == "whats-new" || navigation.route == "about") {
                    WebWhatsNewHost(
                        repository = whatsNewRepository,
                        installedVersionCode = runtimeConfiguration.releaseVersionCode,
                        onBack = { navigateWebFragment("settings") },
                    )
                } else if (navigation.route == "notifications") {
                    WebNotificationsHost(
                        repository = notificationsRepository,
                        runtimeConfiguration = runtimeConfiguration,
                        onBack = { navigateWebFragment("") },
                        onOpenConversation = ::navigateWebConversation,
                    )
                } else if (navigation.route == "profile") {
                    WebFeatureCapabilityRoute(capabilityRegistry, QuataFeature.Profile) {
                        WebProfileHost(repository = profileRepository)
                    }
                } else if (navigation.route == "composer") {
                    WebFeatureCapabilityRoute(capabilityRegistry, QuataFeature.Composer) {
                        WebPostComposerRoute(
                            platformServices = platformServices,
                        )
                    }
                } else if (navigation.route == "communities") {
                    WebFeatureCapabilityRoute(capabilityRegistry, QuataFeature.Communities) {
                        WebNeighborhoodsHost(
                            repository = neighborhoodsRepository,
                            currentUserId = currentUserId,
                            strings = webNeighborhoodsStrings,
                            slots = webNeighborhoodsSlots,
                            rankingItems = emptyList(),
                            onOpenConversation = ::navigateWebConversation,
                            onOpenUserRoute = { navigateWebFragment("communities") },
                            onOpenRankingItem = { },
                            onSubmitComment = { },
                            commentsEnabled = false,
                        )
                    }
                } else if (navigation.route == "official" || navigation.officialPostId != null) {
                    WebFeatureCapabilityRoute(capabilityRegistry, QuataFeature.Official) {
                        WebOfficialHost(
                            repository = officialRepository,
                            officialPostId = navigation.officialPostId,
                            navigationMessage = navigation.message,
                        )
                    }
                } else if (navigation.route == "chat" || navigation.chatConversationId != null) {
                    WebFeatureCapabilityRoute(capabilityRegistry, QuataFeature.Chat) {
                        WebChatHost(
                            repository = chatRepository,
                            audioPlayer = platformServices.audioPlayer,
                            audioRecorder = platformServices.audioRecorder,
                            audioRecordingReferences = platformServices.audioRecordingReferences,
                            filePicker = platformServices.filePicker,
                            documentOpener = platformServices.documentOpener,
                            conversationId = navigation.chatConversationId,
                            navigationMessage = navigation.message,
                            onOpenConversation = ::navigateWebConversation,
                            onBackToList = { navigateWebFragment("chat") },
                        )
                    }
                } else {
                    WebFeatureCapabilityRoute(capabilityRegistry, QuataFeature.Feed) {
                        WebFeedHost(
                            repository = feedRepository,
                            navigationMessage = navigation.message,
                            onOpenChats = { navigateWebFragment("chat") },
                            sharedPostId = navigation.postId,
                            onBackToFeed = { navigateWebFragment("") },
                        )
                    }
                }
            }
            } else {
            WebFeatureCapabilityRoute(capabilityRegistry, QuataFeature.Auth) {
                WebLoginHost(
                    platformServices = platformServices,
                    runtimeConfiguration = runtimeConfiguration,
                    repository = authRepository,
                    onLoginSuccess = {
                        isSessionReady = true
                        currentUserId = authRepository.activeProfileSessionOrNull()?.userId
                    },
                )
            }
            }
        }
    }
}

/**
 * Every authenticated Web vertical remains reachable from normal navigation, not only through a
 * hand-written hash URL. Composer exposes its local shell here, while publication stays
 * fail-closed until the actor, wall-membership and Storage authorization contract is verified.
 */
internal val webNavigationItems = listOf(
    QuataNavigationItem("", "Inicio", Icons.Filled.Home),
    QuataNavigationItem("composer", "Publicar", Icons.Filled.AddCircle),
    QuataNavigationItem("chat", "Chats", Icons.AutoMirrored.Filled.Chat),
    QuataNavigationItem("notifications", "Avisos", Icons.Filled.Notifications),
    QuataNavigationItem("profile", "Perfil", Icons.Filled.Person),
    QuataNavigationItem("settings", "Ajustes", Icons.Filled.Settings),
)

private fun WebPushSessionResult.diagnosticValue(): String = when (this) {
    WebPushSessionResult.Success -> "subscribed"
    WebPushSessionResult.PermissionDenied -> "permission_denied"
    WebPushSessionResult.Unsupported -> "unsupported"
    is WebPushSessionResult.Failure -> "failure:$reason"
}

internal data class WebNavigationState(
    val route: String,
    val message: String,
    val chatConversationId: String? = null,
    val officialPostId: String? = null,
    val postId: String? = null,
)

@Composable
private fun rememberWebNavigation(): WebNavigationState {
    var fragment by remember { mutableStateOf(browserFragment()) }
    DisposableEffect(Unit) {
        observeBrowserFragmentChanges { fragment = it }
        onDispose { }
    }
    return remember(fragment) { fragment.toWebNavigationState() }
}

internal fun String.toWebNavigationState(): WebNavigationState {
    if (trim('/').equals("auth", ignoreCase = true) || trim('/').equals("login", ignoreCase = true)) {
        return WebNavigationState(route = "auth", message = "Inicio de sesi\u00f3n de Quata Web.")
    }
    if (trim('/').equals("share-target", ignoreCase = true)) {
        return WebNavigationState(route = "share-target", message = "Contenido recibido para compartir.")
    }
    if (trim('/').equals("share-target-error", ignoreCase = true)) {
        return WebNavigationState(route = "share-target-error", message = "No se pudo recibir el contenido compartido.")
    }
    if (trim('/').equals("settings", ignoreCase = true)) {
        return WebNavigationState(route = "settings", message = "Apariencia de Quata Web.")
    }
    if (trim('/').equals("whats-new", ignoreCase = true) || trim('/').equals("about", ignoreCase = true)) {
        return WebNavigationState(route = trim('/').lowercase(), message = "Novedades e historial de versiones de Quata Web.")
    }
    if (trim('/').equals("notifications", ignoreCase = true)) {
        return WebNavigationState(route = "notifications", message = "Notificaciones de Quata Web.")
    }
    if (trim('/').equals("profile", ignoreCase = true)) {
        return WebNavigationState(route = "profile", message = "Perfil y contactos SOS de Quata Web.")
    }
    if (trim('/').equals("composer", ignoreCase = true)) {
        return WebNavigationState(route = "composer", message = "Crear una publicaci\u00f3n en Quata Web.")
    }
    if (trim('/').equals("communities", ignoreCase = true)) {
        return WebNavigationState(route = "communities", message = "Comunidades y barrios de Quata Web.")
    }
    if (trim('/').equals("official", ignoreCase = true)) {
        return WebNavigationState(route = "official", message = "Comunicados oficiales de Quata Web.")
    }
    if (trim('/').equals("chat", ignoreCase = true)) {
        return WebNavigationState(route = "chat", message = "Conversaciones de Quata Web.")
    }
    val canonicalUrl = "https://egquata.com/#$this"
    canonicalUrl.quataChatDeepLinkOrNull()?.let { chat ->
        return WebNavigationState(
            route = "chat/${chat.conversationId}",
            message = "Conversación abierta desde un enlace.",
            chatConversationId = chat.conversationId,
        )
    }
    canonicalUrl.quataOfficialPostIdOrNull()?.let { postId ->
        return WebNavigationState(
            route = "official/$postId",
            message = "Comunicado oficial abierto desde un enlace.",
            officialPostId = postId,
        )
    }
    canonicalUrl.quataPostIdOrNull()?.let { postId ->
        return WebNavigationState(
            route = "post/$postId",
            postId = postId,
            message = "Enlace de publicación recibido. La vista compartida se habilitará al conectar datos web.",
        )
    }
    return WebNavigationState(route = "feed", message = "Quata Web se está preparando.")
}

private fun browserFragment(): String = js("globalThis.location?.hash?.replace(/^#/, '') || ''")

private fun navigateWebFragment(fragment: String): Unit = js("globalThis.location.hash = fragment")

/** Emits the same encoded fragment consumed by the common chat deep-link parser. */
private fun navigateWebConversation(conversationId: String) {
    navigateWebFragment(quataChatUrl(conversationId).substringAfter('#'))
}

private fun observeBrowserFragmentChanges(onChanged: (String) -> Unit): Unit = js(
    """
    (() => {
    const listener = () => onChanged(globalThis.location?.hash?.replace(/^#/, '') || '');
    globalThis.addEventListener?.('hashchange', listener);
    })()
    """,
)

private const val WebThemeModeKey = "quata_web_theme_mode"
private const val WebTouchFlowEnabledKey = "quata_web_touch_flow_enabled"

private val webNeighborhoodsStrings = WebNeighborhoodsStrings(
    list = NeighborhoodListStrings(
        title = "Comunidades",
        searchPlaceholder = "Buscar barrio",
        loading = "Cargando comunidades…",
        oneUser = "1 miembro",
        users = { "$it miembros" },
        oneMessage = "1 mensaje",
        messages = { "$it mensajes" },
        viewUsers = "Ver miembros",
        openChat = "Abrir conversación",
        timeLabel = { "Actividad reciente" },
    ),
    members = NeighborhoodUsersStrings(
        title = { "Miembros de $it" },
        subtitle = "Directorio de la comunidad",
        backContentDescription = "Volver a comunidades",
        memberCount = { "$it miembros" },
        row = NeighborhoodUserRowStrings(follow = "Seguir", following = "Siguiendo", chat = "Chat"),
    ),
    commentsTitle = "Comentarios",
    commentsClose = "Cerrar comentarios",
    commentPlaceholder = "Escribe un comentario",
    sendComment = "Enviar",
    profilePosts = "Publicaciones",
    profileFollowers = "Seguidores",
    profileFollowing = "Siguiendo",
    back = "Volver",
    ranking = com.quata.core.ui.components.QuataLiveRankingStrings(
        title = "Ranking",
        subtitle = "Actividad de la comunidad",
        monitoredPosts = "Publicaciones seguidas",
        updated = "Actualizado recientemente",
        live = "EN DIRECTO",
        close = "Cerrar ranking",
        openPost = "Abrir publicación",
    ),
)

private val webNeighborhoodsSlots = WebNeighborhoodsSlots(
    avatar = { user, _, onClick -> androidx.compose.material3.TextButton(onClick = onClick) {
        androidx.compose.material3.Text(user.displayName.take(1).uppercase())
    } },
    profileMedia = { profile ->
        if (profile.posts.isEmpty()) androidx.compose.material3.Text("No hay publicaciones públicas.")
    },
    profileAttachments = { profile ->
        if (profile.attachments.isEmpty()) androidx.compose.material3.Text("No hay adjuntos compartidos.")
    },
    rankingAvatar = { item -> androidx.compose.material3.Text(item.title.take(1).uppercase()) },
)
