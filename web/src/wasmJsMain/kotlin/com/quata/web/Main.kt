package com.quata.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.navigation.quataChatDeepLinkOrNull
import com.quata.core.navigation.quataOfficialPostIdOrNull
import com.quata.core.navigation.quataPostIdOrNull
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
        )
    }
    var isSessionReady by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }
    LaunchedEffect(platformServices.preferences) {
        isSessionReady = platformServices.preferences.getString(WebSessionReadyKey) == "true"
    }
    LaunchedEffect(navigation, runtimeConfiguration.isBackendConfigured) {
        platformServices.preferences.putString("web.navigation.route", navigation.route)
        navigation.chatConversationId?.let { platformServices.preferences.putString("web.navigation.chat", it) }
        platformServices.preferences.putString(
            "web.runtime.backend_configured",
            runtimeConfiguration.isBackendConfigured.toString(),
        )
    }
    QuataTheme {
        if (isSessionReady) {
            Box(Modifier.fillMaxSize()) {
                if (navigation.officialPostId != null) {
                    WebOfficialHost(
                        repository = officialRepository,
                        officialPostId = navigation.officialPostId,
                        navigationMessage = navigation.message,
                    )
                } else if (navigation.route == "chat" || navigation.chatConversationId != null) {
                    WebChatHost(
                        repository = chatRepository,
                        audioPlayer = platformServices.audioPlayer,
                        conversationId = navigation.chatConversationId,
                        navigationMessage = navigation.message,
                        onOpenConversation = { conversationId -> navigateWebFragment("chat/$conversationId") },
                        onBackToList = { navigateWebFragment("chat") },
                    )
                } else {
                    WebFeedHost(
                        repository = feedRepository,
                        navigationMessage = navigation.message,
                        onOpenChats = { navigateWebFragment("chat") },
                    )
                }
                Button(
                    enabled = !isLoggingOut,
                    onClick = {
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
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                ) {
                    Text(if (isLoggingOut) "Cerrando sesi\u00f3n..." else "Cerrar sesi\u00f3n")
                }
            }
        } else {
            WebLoginHost(
                platformServices = platformServices,
                configuration = runtimeConfiguration,
                onLoginSuccess = { pushCoordinator ->
                    isSessionReady = true
                    scope.launch {
                        platformServices.preferences.putString(
                            "web.push.subscription_status",
                            pushCoordinator.subscribeCurrentSession().diagnosticValue(),
                        )
                    }
                },
            )
        }
    }
}

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
            message = "Enlace de publicación recibido. La vista compartida se habilitará al conectar datos web.",
        )
    }
    return WebNavigationState(route = "feed", message = "Quata Web se está preparando.")
}

private fun browserFragment(): String = js("globalThis.location?.hash?.replace(/^#/, '') || ''")

private fun navigateWebFragment(fragment: String): Unit = js("globalThis.location.hash = fragment")

private fun observeBrowserFragmentChanges(onChanged: (String) -> Unit): Unit = js(
    """
    const listener = () => onChanged(globalThis.location?.hash?.replace(/^#/, '') || '');
    globalThis.addEventListener?.('hashchange', listener);
    """,
)
