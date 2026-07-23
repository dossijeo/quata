package com.quata.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AndroidFusedLocationProvider(private val context: Context) : LocationProvider {
    override suspend fun lastKnown(): QuataLocation? {
        if (!context.hasLocationPermission()) return null
        return runCatching {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.await()?.toQuataLocation()
        }.getOrNull()
    }

    override suspend fun precise(timeoutMillis: Long): QuataLocation? {
        if (!context.hasLocationPermission()) return null
        val cancellation = CancellationTokenSource()
        return try {
            withTimeoutOrNull(timeoutMillis) {
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                    .await()
                    ?.toQuataLocation()
            }
        } catch (_: Exception) {
            null
        } finally {
            cancellation.cancel()
        }
    }

    override suspend fun passive(timeoutMillis: Long): QuataLocation? {
        if (!context.hasLocationPermission() || timeoutMillis <= 0L) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_PASSIVE, PassiveIntervalMillis)
            .setMinUpdateIntervalMillis(PassiveMinIntervalMillis)
            .setMaxUpdates(1)
            .setDurationMillis(timeoutMillis)
            .build()
        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        val location = result.lastLocation ?: return
                        client.removeLocationUpdates(this)
                        if (continuation.isActive) continuation.resume(location.toQuataLocation())
                    }
                }
                continuation.invokeOnCancellation { client.removeLocationUpdates(callback) }
                runCatching { client.requestLocationUpdates(request, callback, context.mainLooper) }
                    .onFailure {
                        client.removeLocationUpdates(callback)
                        if (continuation.isActive) continuation.resume(null)
                    }
            }
        }
    }

    private fun Context.hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun Location.toQuataLocation() = QuataLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = takeIf(Location::hasAccuracy)?.accuracy,
        timestampMillis = time.takeIf { it > 0L },
        speedKmh = takeIf(Location::hasSpeed)?.speed?.times(3.6f)?.toDouble(),
    )

    private companion object {
        const val PassiveIntervalMillis = 60_000L
        const val PassiveMinIntervalMillis = 30_000L
    }
}
