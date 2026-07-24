package com.quata.core.platform

/** Request for a still-photo capture. Platform launchers own camera UI and lifecycle. */
data class CameraCaptureRequest(
    val displayName: String? = null,
    val mimeType: String = "image/jpeg",
)

/**
 * Injectable camera boundary. Successful captures must return a readable [PlatformFile], normally
 * a content URI on Android. iOS and Web launchers provide their own hosts; shared ViewModels never
 * retain a platform controller or Context.
 */
interface CameraCaptureService {
    suspend fun capturePhoto(request: CameraCaptureRequest = CameraCaptureRequest()): PlatformResult<PlatformFile>
}
