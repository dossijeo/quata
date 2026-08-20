package com.quata.feature.postcomposer.presentation

import com.quata.core.platform.PermissionService
import com.quata.core.platform.PermissionStatus
import com.quata.core.platform.PlatformPermission

enum class CreatePostMediaPermissionRequest {
    GalleryImage,
    CameraImage,
    GalleryVideo,
    CameraVideo,
}

const val CreatePostMediaPermissionDeniedReason = "post_composer_media_permission_denied"

val CreatePostMediaPermissionRequest.requiredPermissions: List<PlatformPermission>
    get() = when (this) {
        CreatePostMediaPermissionRequest.GalleryImage -> listOf(PlatformPermission.Photos)
        CreatePostMediaPermissionRequest.GalleryVideo -> listOf(PlatformPermission.Videos)
        CreatePostMediaPermissionRequest.CameraImage -> listOf(PlatformPermission.Camera)
        CreatePostMediaPermissionRequest.CameraVideo -> listOf(PlatformPermission.Camera, PlatformPermission.Microphone)
    }

suspend fun PermissionService.ensureCreatePostMediaPermissions(
    request: CreatePostMediaPermissionRequest,
    allowUnavailable: Set<PlatformPermission> = emptySet(),
): Boolean {
    for (permission in request.requiredPermissions) {
        when (val current = status(permission)) {
            PermissionStatus.Granted -> continue
            PermissionStatus.Unavailable -> if (permission in allowUnavailable) continue else return false
            PermissionStatus.PermanentlyDenied -> return false
            PermissionStatus.Denied -> {
                val requested = request(permission)
                if (requested == PermissionStatus.Granted) continue
                if (requested == PermissionStatus.Unavailable && permission in allowUnavailable) continue
                return false
            }
        }
    }
    return true
}
