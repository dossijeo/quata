package com.quata.web

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Credentials returned by the web-only login flow; neither is inferred from browser storage. */
data class WebPushAuthenticatedSession(
    val accessToken: String,
    val webSessionToken: String,
)

sealed interface WebPushRegistrationResult {
    data object Success : WebPushRegistrationResult
    data class Failure(val reason: String, val statusCode: Int? = null) : WebPushRegistrationResult
}

/**
 * Authenticated server registration only. Browser subscription creation/unsubscription remains in
 * [BrowserWebPushSubscriptionService] so callers can preserve the required logout order.
 */
class BrowserWebPushRegistrationService {
    suspend fun subscribe(
        configuration: WebRuntimeConfiguration,
        session: WebPushAuthenticatedSession,
        subscriptionJson: String,
    ): WebPushRegistrationResult = post(
        configuration = configuration,
        session = session,
        action = "subscribe",
        subscriptionJson = subscriptionJson,
    )

    suspend fun unsubscribe(
        configuration: WebRuntimeConfiguration,
        session: WebPushAuthenticatedSession,
        subscriptionJson: String,
    ): WebPushRegistrationResult = post(
        configuration = configuration,
        session = session,
        action = "unsubscribe",
        subscriptionJson = subscriptionJson,
    )

    suspend fun logout(
        configuration: WebRuntimeConfiguration,
        session: WebPushAuthenticatedSession,
    ): WebPushRegistrationResult = post(
        configuration = configuration,
        session = session,
        action = "logout",
        subscriptionJson = null,
    )

    private suspend fun post(
        configuration: WebRuntimeConfiguration,
        session: WebPushAuthenticatedSession,
        action: String,
        subscriptionJson: String?,
    ): WebPushRegistrationResult {
        val endpoint = configuration.webPushEndpointOrNull()
            ?: return WebPushRegistrationResult.Failure("web_push_configuration_missing")
        if (session.accessToken.isBlank() || session.webSessionToken.isBlank()) {
            return WebPushRegistrationResult.Failure("web_push_session_missing")
        }
        if (action != "logout" && subscriptionJson.isNullOrBlank()) {
            return WebPushRegistrationResult.Failure("web_push_subscription_missing")
        }
        return suspendCoroutine { continuation ->
            browserPostWebPushRegistration(
                endpoint = endpoint,
                publishableKey = configuration.supabasePublishableKey.orEmpty(),
                accessToken = session.accessToken,
                webSessionToken = session.webSessionToken,
                action = action,
                subscriptionJson = subscriptionJson,
                onSuccess = { continuation.resume(WebPushRegistrationResult.Success) },
                onFailure = { reason, status ->
                    continuation.resume(WebPushRegistrationResult.Failure(reason ?: "web_push_request_failed", status))
                },
            )
        }
    }
}

private fun WebRuntimeConfiguration.webPushEndpointOrNull(): String? =
    webPushBootstrapConfigurationOrNull()
        ?.vapidEndpoint
        ?.takeIf { !supabasePublishableKey.isNullOrBlank() }

private fun browserPostWebPushRegistration(
    endpoint: String,
    publishableKey: String,
    accessToken: String,
    webSessionToken: String,
    action: String,
    subscriptionJson: String?,
    onSuccess: () -> Unit,
    onFailure: (String?, Int?) -> Unit,
): Unit = js(
    """
    let subscription = null;
    try {
      if (subscriptionJson != null) subscription = JSON.parse(subscriptionJson);
    } catch (_) {
      onFailure('web_push_subscription_invalid', null);
      return;
    }
    let body;
    if (action === 'subscribe') {
      body = { action: 'subscribe', subscription };
    } else if (action === 'unsubscribe') {
      const endpointValue = subscription?.endpoint;
      if (typeof endpointValue !== 'string' || endpointValue.length === 0) {
        onFailure('web_push_subscription_endpoint_missing', null);
        return;
      }
      body = { action: 'unsubscribe', subscription: { endpoint: endpointValue } };
    } else if (action === 'logout') {
      body = { action: 'logout' };
    } else {
      onFailure('web_push_action_invalid', null);
      return;
    }
    if (typeof globalThis.fetch !== 'function') {
      onFailure('web_push_fetch_unsupported', null);
      return;
    }
    globalThis.fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        apikey: publishableKey,
        Authorization: `Bearer ${'$'}{accessToken}`,
        'x-quata-web-session': webSessionToken,
      },
      body: JSON.stringify(body),
    }).then(async (response) => {
      if (response.ok) {
        onSuccess();
        return;
      }
      const text = await response.text().catch(() => '');
      onFailure(text || `web_push_http_${'$'}{response.status}`, response.status);
    }).catch((error) => onFailure(error?.message ?? error?.name ?? 'web_push_request_failed', null));
    """,
)
