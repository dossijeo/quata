package com.quata.web

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

sealed interface WebPushSessionResult {
    data object Success : WebPushSessionResult
    data object PermissionDenied : WebPushSessionResult
    data object Unsupported : WebPushSessionResult
    data class Failure(val reason: String) : WebPushSessionResult
}

/**
 * Binds the browser subscription to an authenticated Quata Web session. The coordinator does
 * not own UI or credentials: callers invoke [subscribeCurrentSession] after a successful login.
 */
class WebPushSessionCoordinator(
    private val configuration: WebRuntimeConfiguration,
    private val authRepository: WebAuthRepository,
    private val subscriptionService: BrowserWebPushSubscriptionService = BrowserWebPushSubscriptionService(),
    private val registrationService: BrowserWebPushRegistrationService = BrowserWebPushRegistrationService(),
) {
    suspend fun subscribeCurrentSession(): WebPushSessionResult {
        val session = authRepository.currentWebPushCredentials()
            ?: return WebPushSessionResult.Failure("web_push_session_missing")
        val bootstrap = configuration.webPushBootstrapConfigurationOrNull()
            ?: return WebPushSessionResult.Failure("web_push_configuration_missing")
        return when (val subscription = subscriptionService.getOrCreateSubscription(bootstrap)) {
            is WebPushSubscriptionResult.Success -> when (
                val registration = registrationService.subscribe(
                    configuration = configuration,
                    session = WebPushAuthenticatedSession(session.accessToken, session.webSessionToken),
                    subscriptionJson = subscription.subscriptionJson,
                )
            ) {
                WebPushRegistrationResult.Success -> WebPushSessionResult.Success
                is WebPushRegistrationResult.Failure -> WebPushSessionResult.Failure(registration.reason)
            }
            WebPushSubscriptionResult.PermissionDenied -> WebPushSessionResult.PermissionDenied
            WebPushSubscriptionResult.Unsupported -> WebPushSessionResult.Unsupported
            is WebPushSubscriptionResult.Failure -> WebPushSessionResult.Failure(subscription.reason)
        }
    }

    /** Server logout, browser unsubscribe and local credential cleanup are delegated in order. */
    suspend fun logoutCurrentSession(): WebPushSessionResult =
        authRepository.logoutWithBrowserUnsubscribe(::unsubscribeBrowserPush).fold(
            onSuccess = { WebPushSessionResult.Success },
            onFailure = { WebPushSessionResult.Failure(it.message ?: "web_push_logout_failed") },
        )
}

private suspend fun unsubscribeBrowserPush(): Result<Unit> = suspendCoroutine { continuation ->
    browserUnsubscribePush(
        onSuccess = { continuation.resume(Result.success(Unit)) },
        onFailure = { reason -> continuation.resume(Result.failure(IllegalStateException(reason))) },
    )
}

private fun browserUnsubscribePush(
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    const navigatorRef = globalThis.navigator;
    if (!navigatorRef?.serviceWorker) { onSuccess(); return; }
    navigatorRef.serviceWorker.ready
      .then((registration) => registration?.pushManager?.getSubscription())
      .then((subscription) => subscription ? subscription.unsubscribe() : true)
      .then(() => onSuccess())
      .catch((error) => onFailure(error?.message ?? error?.name ?? 'web_push_unsubscribe_failed'));
    """,
)
