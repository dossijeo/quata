@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

sealed interface WebPushSessionResult {
    data object Success : WebPushSessionResult
    data object PermissionDenied : WebPushSessionResult
    data object Unsupported : WebPushSessionResult
    data object ConsentDisabled : WebPushSessionResult
    data class Failure(val reason: String) : WebPushSessionResult
}

internal data class WebPushSessionOperations(
    val currentCredentials: suspend () -> WebPushCredentials?,
    val getExistingSubscription: suspend () -> WebPushSubscriptionResult,
    val getOrCreateSubscription: suspend (requestPermission: Boolean) -> WebPushSubscriptionResult,
    val subscribeServer: suspend (WebPushCredentials, String) -> WebPushRegistrationResult,
    val unsubscribeServer: suspend (WebPushCredentials, String) -> WebPushRegistrationResult,
    val unsubscribeBrowser: suspend () -> Result<Unit>,
    val logout: suspend () -> Result<Unit>,
)

/**
 * Consent state machine for Web Push. Restore and renewal never request permission. Every
 * multi-phase transition commits its local state only after the remote and browser phases succeed.
 */
class WebPushSessionCoordinator internal constructor(
    private val preferences: PreferenceStore,
    private val operations: WebPushSessionOperations,
) {
    constructor(
        configuration: WebRuntimeConfiguration,
        authRepository: WebAuthRepository,
        preferences: PreferenceStore,
        subscriptionService: BrowserWebPushSubscriptionService = BrowserWebPushSubscriptionService(),
        registrationService: BrowserWebPushRegistrationService = BrowserWebPushRegistrationService(),
    ) : this(
        preferences = preferences,
        operations = WebPushSessionOperations(
            currentCredentials = authRepository::currentWebPushCredentials,
            getExistingSubscription = subscriptionService::getExistingSubscription,
            getOrCreateSubscription = { requestPermission ->
                val bootstrap = configuration.webPushBootstrapConfigurationOrNull()
                if (bootstrap == null) {
                    WebPushSubscriptionResult.Failure("web_push_configuration_missing")
                } else if (requestPermission) {
                    subscriptionService.requestPermissionAndGetOrCreateSubscription(bootstrap)
                } else {
                    subscriptionService.getOrCreateSubscription(bootstrap)
                }
            },
            subscribeServer = { credentials, json ->
                registrationService.subscribe(
                    configuration,
                    WebPushAuthenticatedSession(credentials.accessToken, credentials.webSessionToken),
                    json,
                )
            },
            unsubscribeServer = { credentials, json ->
                registrationService.unsubscribe(
                    configuration,
                    WebPushAuthenticatedSession(credentials.accessToken, credentials.webSessionToken),
                    json,
                )
            },
            unsubscribeBrowser = ::unsubscribeBrowserPush,
            logout = { authRepository.logoutWithBrowserUnsubscribe(::unsubscribeBrowserPush) },
        ),
    )

    /**
     * Safe for restore/login and worker renewal. A pre-v1 browser subscription is adopted only
     * after its authenticated server reconciliation succeeds; no permission prompt is possible.
     */
    suspend fun reconcileCurrentSession(): WebPushSessionResult = when (WebPushConsent.state(preferences)) {
        WebPushConsentState.Disabled -> WebPushSessionResult.ConsentDisabled
        WebPushConsentState.Enabled -> subscribeCurrentSession(requestPermission = false)
        WebPushConsentState.Unset -> migrateExistingSubscription()
    }

    /** Call only from an explicit, accessible user interaction. */
    suspend fun enableFromUserGesture(): WebPushSessionResult {
        // This must be the first suspend call: requestPermission has to start while the browser's
        // transient user activation from the Settings button is still active.
        val subscription = operations.getOrCreateSubscription(true)
        val result = bindSubscriptionToCurrentSession(subscription)
        WebPushConsent.setEnabled(preferences, result is WebPushSessionResult.Success)
        return result
    }

    /**
     * Server-first opt-out. A server failure preserves both consent and the browser endpoint so
     * the operation can be retried without leaving an unreachable active subscription.
     */
    suspend fun disableFromUserGesture(): WebPushSessionResult {
        val credentials = operations.currentCredentials()
            ?: return WebPushSessionResult.Failure("web_push_session_missing")
        return when (val existing = operations.getExistingSubscription()) {
            is WebPushSubscriptionResult.Success -> {
                when (val server = operations.unsubscribeServer(credentials, existing.subscriptionJson)) {
                    is WebPushRegistrationResult.Failure -> WebPushSessionResult.Failure(server.reason)
                    WebPushRegistrationResult.Success -> {
                        operations.unsubscribeBrowser().fold(
                            onSuccess = {
                                WebPushConsent.setEnabled(preferences, false)
                                WebPushSessionResult.Success
                            },
                            onFailure = { WebPushSessionResult.Failure(it.message ?: "web_push_unsubscribe_failed") },
                        )
                    }
                }
            }
            WebPushSubscriptionResult.NoSubscription,
            WebPushSubscriptionResult.PermissionDenied,
            -> {
                WebPushConsent.setEnabled(preferences, false)
                WebPushSessionResult.Success
            }
            WebPushSubscriptionResult.Unsupported -> WebPushSessionResult.Unsupported
            is WebPushSubscriptionResult.Failure -> WebPushSessionResult.Failure(existing.reason)
        }
    }

    private suspend fun migrateExistingSubscription(): WebPushSessionResult {
        val existing = operations.getExistingSubscription()
        if (existing !is WebPushSubscriptionResult.Success) return when (existing) {
            WebPushSubscriptionResult.NoSubscription,
            WebPushSubscriptionResult.PermissionDenied,
            -> WebPushSessionResult.ConsentDisabled
            WebPushSubscriptionResult.Unsupported -> WebPushSessionResult.Unsupported
            is WebPushSubscriptionResult.Failure -> WebPushSessionResult.Failure(existing.reason)
            is WebPushSubscriptionResult.Success -> error("unreachable")
        }
        // The endpoint proves the legacy browser was already enrolled with granted permission.
        // Adopt that prior choice before reconciliation so Settings never claims it is off while
        // this endpoint may still receive; a transient server failure remains safely retryable.
        WebPushConsent.setEnabled(preferences, true)
        val credentials = operations.currentCredentials()
            ?: return WebPushSessionResult.Failure("web_push_session_missing")
        return when (val registration = operations.subscribeServer(credentials, existing.subscriptionJson)) {
            WebPushRegistrationResult.Success -> WebPushSessionResult.Success
            is WebPushRegistrationResult.Failure -> WebPushSessionResult.Failure(registration.reason)
        }
    }

    private suspend fun subscribeCurrentSession(requestPermission: Boolean): WebPushSessionResult {
        return bindSubscriptionToCurrentSession(operations.getOrCreateSubscription(requestPermission))
    }

    private suspend fun bindSubscriptionToCurrentSession(
        subscription: WebPushSubscriptionResult,
    ): WebPushSessionResult {
        return when (subscription) {
            is WebPushSubscriptionResult.Success -> when (
                val registration = operations.currentCredentials()?.let {
                    operations.subscribeServer(it, subscription.subscriptionJson)
                } ?: return WebPushSessionResult.Failure("web_push_session_missing")
            ) {
                WebPushRegistrationResult.Success -> WebPushSessionResult.Success
                is WebPushRegistrationResult.Failure -> WebPushSessionResult.Failure(registration.reason)
            }
            WebPushSubscriptionResult.PermissionDenied -> WebPushSessionResult.PermissionDenied
            WebPushSubscriptionResult.Unsupported -> WebPushSessionResult.Unsupported
            WebPushSubscriptionResult.NoSubscription -> WebPushSessionResult.Failure("web_push_subscription_missing")
            is WebPushSubscriptionResult.Failure -> WebPushSessionResult.Failure(subscription.reason)
        }
    }

    suspend fun logoutCurrentSession(): WebPushSessionResult = operations.logout().fold(
        onSuccess = { WebPushSessionResult.Success },
        onFailure = { WebPushSessionResult.Failure(it.message ?: "web_push_logout_failed") },
    )
}

private suspend fun unsubscribeBrowserPush(): Result<Unit> = suspendCancellableCoroutine { continuation ->
    browserUnsubscribePush(
        onSuccess = {
            if (continuation.isActive) continuation.resume(Result.success(Unit))
        },
        onFailure = { reason ->
            if (continuation.isActive) continuation.resume(Result.failure(IllegalStateException(reason)))
        },
    )
}

private fun browserUnsubscribePush(
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    (() => {
    const navigatorRef = globalThis.navigator;
    if (!navigatorRef?.serviceWorker) { onSuccess(); return; }
    navigatorRef.serviceWorker.ready
      .then((registration) => registration?.pushManager?.getSubscription())
      .then((subscription) => subscription ? subscription.unsubscribe() : true)
      .then(() => onSuccess())
      .catch((error) => onFailure(error?.message ?? error?.name ?? 'web_push_unsubscribe_failed'));
    })()
    """,
)
