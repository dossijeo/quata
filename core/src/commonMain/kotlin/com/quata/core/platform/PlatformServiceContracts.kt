package com.quata.core.platform

/** Injectable platform boundaries. Shared features must depend on these contracts, never on an OS Context. */
data class PlatformFile(val reference: String, val displayName: String? = null, val mimeType: String? = null, val sizeBytes: Long? = null)
data class SharePayload(val text: String? = null, val title: String? = null, val files: List<PlatformFile> = emptyList())
data class GeoLocation(val latitude: Double, val longitude: Double, val accuracyMeters: Float? = null, val timestampMillis: Long? = null)

enum class PlatformPermission { Camera, Microphone, Photos, Files, Location, Notifications, Contacts }
enum class PermissionStatus { Granted, Denied, PermanentlyDenied, Unavailable }
sealed interface PlatformResult<out T> {
    data class Success<T>(val value: T) : PlatformResult<T>
    data class Failure(val reason: String? = null) : PlatformResult<Nothing>
    data object Cancelled : PlatformResult<Nothing>
    data object Unsupported : PlatformResult<Nothing>
}

interface ClipboardService { suspend fun readText(): String?; suspend fun writeText(text: String) }
interface ShareService { suspend fun share(payload: SharePayload): PlatformResult<Unit> }
interface FilePickerService { suspend fun pickFiles(acceptedMimeTypes: List<String> = emptyList(), allowMultiple: Boolean = false): PlatformResult<List<PlatformFile>> }
interface PermissionService { suspend fun status(permission: PlatformPermission): PermissionStatus; suspend fun request(permission: PlatformPermission): PermissionStatus }
interface LocationService { suspend fun currentLocation(): PlatformResult<GeoLocation> }
interface PreferenceStore { suspend fun getString(key: String): String?; suspend fun putString(key: String, value: String); suspend fun remove(key: String) }
