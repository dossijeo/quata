package com.quata.core.platform

/** Injectable platform boundaries. Shared features must depend on these contracts, never on an OS Context. */
data class PlatformFile(val reference: String, val displayName: String? = null, val mimeType: String? = null, val sizeBytes: Long? = null)
data class SharePayload(val text: String? = null, val title: String? = null, val files: List<PlatformFile> = emptyList())
data class GeoLocation(val latitude: Double, val longitude: Double, val accuracyMeters: Float? = null, val timestampMillis: Long? = null)
data class PlatformContact(
    val displayName: String? = null,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
)

enum class PlatformPermission { Camera, Microphone, Photos, Files, Location, Notifications, Contacts }
enum class PermissionStatus { Granted, Denied, PermanentlyDenied, Unavailable }
enum class FilePickerSource { Documents, Gallery, Camera }
sealed interface PlatformResult<out T> {
    data class Success<T>(val value: T) : PlatformResult<T>
    data class Failure(val reason: String? = null) : PlatformResult<Nothing>
    data object Cancelled : PlatformResult<Nothing>
    data object Unsupported : PlatformResult<Nothing>
}

interface ClipboardService { suspend fun readText(): String?; suspend fun writeText(text: String) }
interface ShareService { suspend fun share(payload: SharePayload): PlatformResult<Unit> }
data class FilePickerRequest(
    val acceptedMimeTypes: List<String> = emptyList(),
    val allowMultiple: Boolean = false,
    val source: FilePickerSource = FilePickerSource.Documents,
)

interface FilePickerService {
    suspend fun pickFiles(acceptedMimeTypes: List<String> = emptyList(), allowMultiple: Boolean = false): PlatformResult<List<PlatformFile>>

    /** Camera capture is opt-in: adapters without an actual capture host report Unsupported. */
    suspend fun pick(request: FilePickerRequest): PlatformResult<List<PlatformFile>> = when (request.source) {
        FilePickerSource.Camera -> PlatformResult.Unsupported
        FilePickerSource.Documents, FilePickerSource.Gallery -> pickFiles(request.acceptedMimeTypes, request.allowMultiple)
    }
}
/**
 * User-gesture contact selection boundary. This deliberately does not imply address-book access:
 * platforms without a native picker return [PlatformResult.Unsupported].
 */
interface ContactPickerService { suspend fun pickContacts(): PlatformResult<List<PlatformContact>> }
object UnsupportedContactPickerService : ContactPickerService {
    override suspend fun pickContacts(): PlatformResult<List<PlatformContact>> = PlatformResult.Unsupported
}
interface PermissionService { suspend fun status(permission: PlatformPermission): PermissionStatus; suspend fun request(permission: PlatformPermission): PermissionStatus }
interface LocationService { suspend fun currentLocation(): PlatformResult<GeoLocation> }
interface PreferenceStore { suspend fun getString(key: String): String?; suspend fun putString(key: String, value: String); suspend fun remove(key: String) }

/** Platform service composition consumed by shared launchers/features without retaining an OS context. */
interface PlatformServices {
    val preferences: PreferenceStore
    val clipboard: ClipboardService
    val share: ShareService
    val filePicker: FilePickerService
    /**
     * Platform-owned document surface. Launchers replace the explicit unsupported default when
     * they can present a native document viewer; shared features never need an Android Context.
     */
    val documentOpener: DocumentOpenService get() = UnsupportedDocumentOpenService
    val contacts: ContactPickerService
    val location: LocationService
    val permissions: PermissionService
}
