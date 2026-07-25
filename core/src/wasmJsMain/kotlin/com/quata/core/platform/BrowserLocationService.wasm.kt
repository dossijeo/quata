@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Browser location adapter backed by `navigator.geolocation`. */
class BrowserLocationService : LocationService {
    override suspend fun currentLocation(): PlatformResult<GeoLocation> {
        if (!browserGeolocationIsAvailable()) return PlatformResult.Unsupported
        return suspendCoroutine { continuation ->
            browserGetCurrentPosition(
                onSuccess = { latitude, longitude, accuracy, timestamp ->
                    continuation.resume(
                        PlatformResult.Success(
                            GeoLocation(
                                latitude = latitude,
                                longitude = longitude,
                                accuracyMeters = accuracy?.toFloat(),
                                timestampMillis = timestamp?.toLong(),
                            ),
                        ),
                    )
                },
                onError = { code, message ->
                    val reason = when (code) {
                        1 -> "location_permission_denied"
                        2 -> "location_unavailable"
                        3 -> "location_timeout"
                        else -> message ?: "location_failed"
                    }
                    continuation.resume(PlatformResult.Failure(reason))
                },
            )
        }
    }
}

/**
 * Geolocation is restricted to secure contexts. Checking it before either querying or requesting
 * the permission keeps `status(Location)` and `currentLocation()` coherent on an HTTP origin.
 */
internal fun browserGeolocationIsAvailable(): Boolean =
    js("globalThis.isSecureContext === true && typeof globalThis.navigator?.geolocation?.getCurrentPosition === 'function'")

private fun browserGetCurrentPosition(
    onSuccess: (Double, Double, Double?, Double?) -> Unit,
    onError: (Int, String?) -> Unit,
): Unit = js(
    """
    (() => {
    const timeoutMillis = 15000;
    let settled = false;
    const finish = (callback, ...args) => {
        if (settled) return;
        settled = true;
        globalThis.clearTimeout(fallbackTimeout);
        callback(...args);
    };
    // Browsers should honour the Geolocation timeout, but the fallback prevents a suspended
    // shared coroutine from being retained forever by an implementation that does not.
    const fallbackTimeout = globalThis.setTimeout(
        () => finish(onError, 3, "location_timeout"),
        timeoutMillis + 1000,
    );
    try {
        globalThis.navigator.geolocation.getCurrentPosition(
            (position) => finish(onSuccess, position.coords.latitude, position.coords.longitude, position.coords.accuracy ?? null, position.timestamp ?? null),
            (error) => finish(onError, error?.code ?? 0, error?.message ?? null),
            { enableHighAccuracy: true, timeout: timeoutMillis, maximumAge: 30000 }
        );
    } catch (_) {
        finish(onError, 0, "location_failed");
    }
    })()
    """,
)
