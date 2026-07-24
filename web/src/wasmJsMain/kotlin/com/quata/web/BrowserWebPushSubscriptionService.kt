package com.quata.web

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Public, deployment-provided endpoint only; no Supabase credential or session belongs here. */
data class WebPushBootstrapConfiguration(
    val vapidEndpoint: String,
    val serviceWorkerPath: String = "/quata-sw.js",
)

sealed interface WebPushSubscriptionResult {
    /** JSON produced by `PushSubscription.toJSON()` for a later authenticated registration. */
    data class Success(val subscriptionJson: String) : WebPushSubscriptionResult
    data object PermissionDenied : WebPushSubscriptionResult
    data object Unsupported : WebPushSubscriptionResult
    data class Failure(val reason: String) : WebPushSubscriptionResult
}

/**
 * Browser-only Web Push bootstrap. It deliberately stops before any authenticated POST: callers
 * must later bind [WebPushSubscriptionResult.Success] to an active web session themselves.
 */
class BrowserWebPushSubscriptionService {
    suspend fun getOrCreateSubscription(
        configuration: WebPushBootstrapConfiguration,
    ): WebPushSubscriptionResult {
        if (configuration.vapidEndpoint.isBlank()) return WebPushSubscriptionResult.Failure("vapid_endpoint_missing")
        return suspendCoroutine { continuation ->
            browserGetOrCreatePushSubscription(
                vapidEndpoint = configuration.vapidEndpoint,
                workerPath = configuration.serviceWorkerPath,
                onSuccess = { json -> continuation.resume(WebPushSubscriptionResult.Success(json)) },
                onPermissionDenied = { continuation.resume(WebPushSubscriptionResult.PermissionDenied) },
                onUnsupported = { continuation.resume(WebPushSubscriptionResult.Unsupported) },
                onFailure = { reason -> continuation.resume(WebPushSubscriptionResult.Failure(reason ?: "web_push_failed")) },
            )
        }
    }
}

private fun browserGetOrCreatePushSubscription(
    vapidEndpoint: String,
    workerPath: String,
    onSuccess: (String) -> Unit,
    onPermissionDenied: () -> Unit,
    onUnsupported: () -> Unit,
    onFailure: (String?) -> Unit,
): Unit = js(
    """
    const navigatorRef = globalThis.navigator;
    const notification = globalThis.Notification;
    if (!navigatorRef?.serviceWorker || !notification?.requestPermission || typeof globalThis.fetch !== 'function') {
      onUnsupported();
      return;
    }
    const base64UrlToUint8Array = (value) => {
      if (typeof value !== 'string' || value.length === 0) throw new Error('vapid_public_key_missing');
      const padding = '='.repeat((4 - (value.length % 4)) % 4);
      const base64 = (value + padding).replace(/-/g, '+').replace(/_/g, '/');
      const raw = globalThis.atob(base64);
      return Uint8Array.from(raw, char => char.charCodeAt(0));
    };
    const permission = notification.permission === 'granted'
      ? Promise.resolve('granted')
      : notification.requestPermission();
    permission.then((state) => {
      if (state !== 'granted') { onPermissionDenied(); return null; }
      return navigatorRef.serviceWorker.register(workerPath).then(() => navigatorRef.serviceWorker.ready);
    }).then((registration) => {
      if (registration == null) return null;
      if (!registration.pushManager) { onUnsupported(); return null; }
      return globalThis.fetch(vapidEndpoint).then((response) => {
        if (!response.ok) throw new Error('vapid_key_fetch_failed');
        return response.json();
      }).then((payload) => ({ registration, publicKey: payload?.public_key }));
    }).then((resolved) => {
      if (resolved == null) return null;
      return resolved.registration.pushManager.getSubscription().then((existing) => existing ||
        resolved.registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: base64UrlToUint8Array(resolved.publicKey),
        })
      );
    }).then((subscription) => {
      if (subscription != null) onSuccess(JSON.stringify(subscription.toJSON()));
    }).catch((error) => {
      if (error?.name === 'NotAllowedError') onPermissionDenied();
      else if (error?.name === 'NotSupportedError') onUnsupported();
      else onFailure(error?.message ?? error?.name ?? 'web_push_failed');
    });
    """,
)
