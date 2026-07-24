package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Concrete Core Location boundary for a UIKit/SwiftUI launcher.
 *
 * The host owns one CLLocationManager and its delegate for the whole injected lifetime. It only
 * asks for `when in use` authorization and never reports a location before Core Location grants
 * it. The app must declare `NSLocationWhenInUseUsageDescription` in its iOS target.
 */
@OptIn(ExperimentalForeignApi::class)
class IosCoreLocationHost(
    private val manager: CLLocationManager = CLLocationManager(),
) : NSObject(), LocationService, PermissionService, CLLocationManagerDelegateProtocol {
    private var permissionContinuation: kotlinx.coroutines.CancellableContinuation<PermissionStatus>? = null
    private var locationContinuation: kotlinx.coroutines.CancellableContinuation<PlatformResult<GeoLocation>>? = null

    init {
        manager.delegate = this
    }

    override suspend fun status(permission: PlatformPermission): PermissionStatus = when (permission) {
        PlatformPermission.Location -> authorizationStatus().toLocationPermissionStatus()
        else -> PermissionStatus.Unavailable
    }

    override suspend fun request(permission: PlatformPermission): PermissionStatus {
        if (permission != PlatformPermission.Location) return PermissionStatus.Unavailable
        val current = authorizationStatus().toLocationPermissionStatus()
        if (current != PermissionStatus.Denied) return current
        return suspendCancellableCoroutine { continuation ->
            if (permissionContinuation != null) {
                continuation.resume(PermissionStatus.Unavailable)
                return@suspendCancellableCoroutine
            }
            permissionContinuation = continuation
            continuation.invokeOnCancellation {
                if (permissionContinuation === continuation) permissionContinuation = null
            }
            manager.requestWhenInUseAuthorization()
        }
    }

    override suspend fun currentLocation(): PlatformResult<GeoLocation> {
        if (!CLLocationManager.locationServicesEnabled()) return PlatformResult.Unsupported
        if (authorizationStatus().toLocationPermissionStatus() != PermissionStatus.Granted) {
            return PlatformResult.Failure("location_permission_not_granted")
        }
        return suspendCancellableCoroutine { continuation ->
            if (locationContinuation != null) {
                continuation.resume(PlatformResult.Failure("location_request_in_progress"))
                return@suspendCancellableCoroutine
            }
            locationContinuation = continuation
            continuation.invokeOnCancellation {
                if (locationContinuation === continuation) locationContinuation = null
            }
            manager.requestLocation()
        }
    }

    override fun locationManager(
        manager: CLLocationManager,
        didChangeAuthorizationStatus: CLAuthorizationStatus,
    ) {
        permissionContinuation?.let { continuation ->
            permissionContinuation = null
            if (continuation.isActive) {
                continuation.resume(didChangeAuthorizationStatus.toLocationPermissionStatus())
            }
        }
    }

    override fun locationManager(
        manager: CLLocationManager,
        didUpdateLocations: List<*>,
    ) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation
        val continuation = locationContinuation ?: return
        locationContinuation = null
        if (!continuation.isActive) return
        if (location == null) {
            continuation.resume(PlatformResult.Failure("location_unavailable"))
            return
        }
        val coordinate = location.coordinate.useContents { latitude to longitude }
        continuation.resume(
            PlatformResult.Success(
                GeoLocation(
                    latitude = coordinate.first,
                    longitude = coordinate.second,
                    accuracyMeters = location.horizontalAccuracy
                        .takeIf { it >= 0.0 }
                        ?.toFloat(),
                    timestampMillis = (location.timestamp.timeIntervalSince1970() * 1_000.0).toLong(),
                ),
            ),
        )
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        val continuation = locationContinuation ?: return
        locationContinuation = null
        if (continuation.isActive) {
            continuation.resume(PlatformResult.Failure(didFailWithError.localizedDescription))
        }
    }

    private fun authorizationStatus(): CLAuthorizationStatus = CLLocationManager.authorizationStatus()
}

@OptIn(ExperimentalForeignApi::class)
private fun CLAuthorizationStatus.toLocationPermissionStatus(): PermissionStatus = when (this) {
    kCLAuthorizationStatusAuthorizedAlways,
    kCLAuthorizationStatusAuthorizedWhenInUse -> PermissionStatus.Granted
    kCLAuthorizationStatusNotDetermined -> PermissionStatus.Denied
    kCLAuthorizationStatusDenied,
    kCLAuthorizationStatusRestricted -> PermissionStatus.PermanentlyDenied
    else -> PermissionStatus.Unavailable
}

/** Routes Location to Core Location while retaining real notification permission behavior. */
class IosCompositePermissionService(
    private val location: PermissionService,
    private val notifications: PermissionService = IosNotificationPermissionService(),
) : PermissionService {
    override suspend fun status(permission: PlatformPermission): PermissionStatus = when (permission) {
        PlatformPermission.Location -> location.status(permission)
        PlatformPermission.Notifications -> notifications.status(permission)
        else -> PermissionStatus.Unavailable
    }

    override suspend fun request(permission: PlatformPermission): PermissionStatus = when (permission) {
        PlatformPermission.Location -> location.request(permission)
        PlatformPermission.Notifications -> notifications.request(permission)
        else -> PermissionStatus.Unavailable
    }
}
