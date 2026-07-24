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
import com.quata.core.navigation.quataChatDeepLinkOrNull
import com.quata.core.navigation.quataChatUrl
import com.quata.core.navigation.quataOfficialPostIdOrNull
import com.quata.core.navigation.quataPostIdOrNull
import com.quata.core.ui.components.QuataBottomNavigation
import com.quata.core.ui.components.QuataNavigationItem
import com.quata.designsystem.effects.fluidTouchEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Alignment
import com.quata.feature.auth.presentation.AuthSessionShellContent
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
    if ('serviceWorker' in globalThis.navigator) {
      const locale = globalThis.navigator.language || globalThis.document?.documentElement?.lang || 'en';
      globalThis.navigator.serviceWorker.register('/quata-sw.js')
        .then(() => globalThis.navigator.serviceWorker.ready)
        .then((registration) => registration.active?.postMessage({ type: 'quata:set-notification-locale', locale }))
        .catch(() => {});
    }
    """,
)

/**
 * A service worker is the only browser context that receives `pushsubscriptionchange`. It
 * notifies an open launcher, which has the active access/web-session tokens needed to perform
 * the authenticated, idempotent `subscribe` request.
 */
private fun observeWebPushSubscriptionChanges(onChanged: () -> Unit): () -> Unit = js(
    """
    const container = globalThis.navigator?.serviceWorker;
    if (!container?.addEventListener) return () => {};
    const listener = (event) => {
      if (event?.data?.type === 'quata:push-subscription-change') onChanged();
    };
    container.addEventListener('message', listener);
    return () => container.removeEventListener('message', listener);
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
    val notificationsRepository = remember(chatRepository) { WebNotificationsRepository(chatRepository) }
    val profileRepository = remember(platformServices.preferences, platformServices.contacts) {
        WebProfileRepository(
            preferences = platformServices.preferences,
            contactPicker = platformServices.contacts,
        )
    }
    val whatsNewRepository: WhatsNewRepository = remember(runtimeConfiguration, authRepository) {
        WebWhatsNewRepository(
            rpcClient = WebPostgrestRpcClient(runtimeConfiguration, authRepository),
        )
    }
    var isSessionReady by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(QuataThemeMode.System) }
    var touchFlowEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(platformServices.preferences) {
        isSessionReady = platformServices.preferences.getString(WebSessionReadyKey) == "true"
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
    LaunchedEffect(navigation, runtimeConfiguration.isBackendConfigured) {
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
                    )
                },
            ) {
                if (navigation.route == "settings") {
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
                        onBack = { navigateWebFragment("") },
                        onOpenConversation = ::navigateWebConversation,
                    )
                } else if (navigation.route == "profile") {
                    WebProfileHost(repository = profileRepository)
                } else if (navigation.route == "official" || navigation.officialPostId != null) {
                    WebOfficialHost(
                        repository = officialRepository,
                        officialPostId = navigation.officialPostId,
                        navigationMessage = navigation.message,
                    )
                } else if (navigation.route == "chat" || navigation.chatConversationId != null) {
                    WebChatHost(
                        repository = chatRepository,
                        audioPlayer = platformServices.audioPlayer,
                        filePicker = platformServices.filePicker,
                        documentOpener = platformServices.documentOpener,
                        conversationId = navigation.chatConversationId,
                        navigationMessage = navigation.message,
                        onOpenConversation = ::navigateWebConversation,
                        onBackToList = { navigateWebFragment("chat") },
                    )
                } else {
                    WebFeedHost(
                        repository = feedRepository,
                        navigationMessage = navigation.message,
                        onOpenChats = { navigateWebFragment("chat") },
                        sharedPostId = navigation.postId,
                        onBackToFeed = { navigateWebFragment("") },
                    )
                }
            }
            } else {
            WebLoginHost(
                platformServices = platformServices,
                configuration = runtimeConfiguration,
                onLoginSuccess = {
                    isSessionReady = true
                },
            )
            }
        }
    }
}

private val webNavigationItems = listOf(
    QuataNavigationItem("", "Inicio", Icons.Filled.Home),
    QuataNavigationItem("chat", "Chats", Icons.Filled.Chat),
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

private data class WebNavigationState(
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

private fun String.toWebNavigationState(): WebNavigationState {
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
    const listener = () => onChanged(globalThis.location?.hash?.replace(/^#/, '') || '');
    globalThis.addEventListener?.('hashchange', listener);
    """,
)

private const val WebThemeModeKey = "quata_web_theme_mode"
private const val WebTouchFlowEnabledKey = "quata_web_touch_flow_enabled"
