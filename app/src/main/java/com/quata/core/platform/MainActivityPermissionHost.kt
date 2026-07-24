package com.quata.core.platform

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred

/** Activity Result bridge injected into [AndroidPermissionService] by the Android launcher. */
class MainActivityPermissionHost(activity: ComponentActivity) {
    private var pending: CompletableDeferred<PermissionStatus>? = null
    private var requestedPermission: PlatformPermission? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val permission = requestedPermission
        val status = when {
            permission == null -> PermissionStatus.Unavailable
            permission == PlatformPermission.Location && (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) -> PermissionStatus.Granted
            permission != PlatformPermission.Location && grants.values.all { it } -> PermissionStatus.Granted
            else -> PermissionStatus.Denied
        }
        pending?.complete(status)
        pending = null
        requestedPermission = null
    }

    suspend fun request(permission: PlatformPermission): PermissionStatus {
        val permissions = permission.runtimePermissions() ?: return PermissionStatus.Unavailable
        if (pending != null) return PermissionStatus.Unavailable
        return CompletableDeferred<PermissionStatus>().also { deferred ->
            pending = deferred
            requestedPermission = permission
            launcher.launch(permissions)
        }.await()
    }

    fun close() {
        pending?.complete(PermissionStatus.Unavailable)
        pending = null
        requestedPermission = null
    }
}

private fun PlatformPermission.runtimePermissions(): Array<String>? = when (this) {
    PlatformPermission.Location -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    PlatformPermission.Camera -> arrayOf(Manifest.permission.CAMERA)
    PlatformPermission.Microphone -> arrayOf(Manifest.permission.RECORD_AUDIO)
    PlatformPermission.Notifications -> arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    PlatformPermission.Photos -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    PlatformPermission.Contacts -> arrayOf(Manifest.permission.READ_CONTACTS)
    PlatformPermission.Files -> null
}
