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

private fun browserGeolocationIsAvailable(): Boolean =
    js("typeof globalThis.navigator?.geolocation?.getCurrentPosition === 'function'")

private fun browserGetCurrentPosition(
    onSuccess: (Double, Double, Double?, Double?) -> Unit,
    onError: (Int, String?) -> Unit,
): Unit = js(
    """
    globalThis.navigator.geolocation.getCurrentPosition(
        (position) => onSuccess(position.coords.latitude, position.coords.longitude, position.coords.accuracy ?? null, position.timestamp ?? null),
        (error) => onError(error?.code ?? 0, error?.message ?? null),
        { enableHighAccuracy: true, timeout: 15000, maximumAge: 30000 }
    );
    """,
)
