package com.quata.core.location

import kotlinx.coroutines.delay

data class QuataLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val timestampMillis: Long? = null,
    val speedKmh: Double? = null,
)

interface LocationProvider {
    suspend fun lastKnown(): QuataLocation?
    suspend fun precise(timeoutMillis: Long): QuataLocation?
    suspend fun passive(timeoutMillis: Long): QuataLocation?
}

suspend fun LocationProvider.preciseWithRetries(
    attempts: Int = 3,
    timeoutMillis: Long = 60_000L,
    delayBetweenAttemptsMillis: Long = 15_000L,
): QuataLocation? {
    repeat(attempts.coerceAtLeast(1)) { attempt ->
        precise(timeoutMillis)?.let { return it }
        if (attempt < attempts - 1) delay(delayBetweenAttemptsMillis)
    }
    return null
}
