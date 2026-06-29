package com.quata.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val PreciseLocationTimeoutMillis = 60_000L

fun Context.hasQuataLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
suspend fun Context.quataLastLocation(): Location? {
    if (!hasQuataLocationPermission()) return null
    return runCatching {
        LocationServices.getFusedLocationProviderClient(this).lastLocation.await()
    }.getOrNull()
}

@SuppressLint("MissingPermission")
suspend fun Context.quataPreciseLocation(
    timeoutMillis: Long = PreciseLocationTimeoutMillis
): Location? {
    if (!hasQuataLocationPermission()) return null
    val cancellationTokenSource = CancellationTokenSource()
    return try {
        withTimeoutOrNull(timeoutMillis) {
            LocationServices.getFusedLocationProviderClient(this@quataPreciseLocation)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                .await()
        }
    } catch (_: Exception) {
        null
    } finally {
        cancellationTokenSource.cancel()
    }
}

suspend fun Context.quataPreciseLocationWithRetries(
    attempts: Int = 3,
    delayBetweenAttemptsMillis: Long = 15_000L
): Location? {
    repeat(attempts.coerceAtLeast(1)) { attempt ->
        quataPreciseLocation()?.let { return it }
        if (attempt < attempts - 1) {
            delay(delayBetweenAttemptsMillis)
        }
    }
    return null
}
