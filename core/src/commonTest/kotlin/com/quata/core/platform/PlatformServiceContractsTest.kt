package com.quata.core.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class PlatformServiceContractsTest {
    @Test
    fun filePickerDefaultRoutesDocumentAndGalleryRequestsToLegacyPicker() = runTest {
        val picker = RecordingFilePicker()

        val documentResult = picker.pick(
            FilePickerRequest(
                acceptedMimeTypes = listOf("application/pdf"),
                allowMultiple = true,
                source = FilePickerSource.Documents,
            ),
        )
        val galleryResult = picker.pick(FilePickerRequest(source = FilePickerSource.Gallery))

        assertEquals(
            listOf(
                PickerInvocation(listOf("application/pdf"), true),
                PickerInvocation(emptyList(), false),
            ),
            picker.invocations,
        )
        assertEquals(listOf(sampleFile), assertIs<PlatformResult.Success<List<PlatformFile>>>(documentResult).value)
        assertEquals(listOf(sampleFile), assertIs<PlatformResult.Success<List<PlatformFile>>>(galleryResult).value)
    }

    @Test
    fun filePickerDefaultRefusesCameraWithoutPretendingToCapture() = runTest {
        val picker = RecordingFilePicker()

        val result = picker.pick(FilePickerRequest(source = FilePickerSource.Camera))

        assertSame(PlatformResult.Unsupported, result)
        assertEquals(emptyList(), picker.invocations)
    }

    @Test
    fun unsupportedContactPickerReturnsExplicitCapabilityResult() = runTest {
        assertSame(PlatformResult.Unsupported, UnsupportedContactPickerService.pickContacts())
    }

    @Test
    fun platformServicesCanBeDrivenByCommonFakesWithoutPlatformState() = runTest {
        val services = FakePlatformServices()

        services.clipboard.writeText("Quata")
        services.preferences.putString("session", "active")
        services.permissions.grants[PlatformPermission.Location] = PermissionStatus.Granted
        val shareResult = services.share.share(SharePayload(text = "Hola", files = listOf(sampleFile)))
        val locationResult = services.location.currentLocation()

        assertEquals("Quata", services.clipboard.readText())
        assertEquals("active", services.preferences.getString("session"))
        assertEquals(PermissionStatus.Granted, services.permissions.status(PlatformPermission.Location))
        assertEquals(PermissionStatus.Granted, services.permissions.request(PlatformPermission.Location))
        assertEquals(SharePayload(text = "Hola", files = listOf(sampleFile)), services.share.payloads.single())
        assertEquals(sampleLocation, assertIs<PlatformResult.Success<GeoLocation>>(locationResult).value)
        assertSame(Unit, assertIs<PlatformResult.Success<Unit>>(shareResult).value)

        services.preferences.remove("session")
        assertNull(services.preferences.getString("session"))
    }

    @Test
    fun platformResultRetainsFailureReasonAndSuccessPayload() {
        val failure: PlatformResult<String> = PlatformResult.Failure("permission_denied")
        val success: PlatformResult<PlatformFile> = PlatformResult.Success(sampleFile)

        assertEquals("permission_denied", assertIs<PlatformResult.Failure>(failure).reason)
        assertEquals(sampleFile, assertIs<PlatformResult.Success<PlatformFile>>(success).value)
    }

    private data class PickerInvocation(val mimeTypes: List<String>, val allowMultiple: Boolean)

    private class RecordingFilePicker : FilePickerService {
        val invocations = mutableListOf<PickerInvocation>()

        override suspend fun pickFiles(
            acceptedMimeTypes: List<String>,
            allowMultiple: Boolean,
        ): PlatformResult<List<PlatformFile>> {
            invocations += PickerInvocation(acceptedMimeTypes, allowMultiple)
            return PlatformResult.Success(listOf(sampleFile))
        }
    }

    private class FakePlatformServices : PlatformServices {
        override val preferences = InMemoryPreferenceStore()
        override val clipboard = FakeClipboardService()
        override val share = RecordingShareService()
        override val filePicker: FilePickerService = RecordingFilePicker()
        override val contacts: ContactPickerService = UnsupportedContactPickerService
        override val location = FixedLocationService()
        override val permissions = FakePermissionService()
    }

    private class InMemoryPreferenceStore : PreferenceStore {
        private val values = mutableMapOf<String, String>()

        override suspend fun getString(key: String): String? = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }

    private class FakeClipboardService : ClipboardService {
        private var value: String? = null

        override suspend fun readText(): String? = value
        override suspend fun writeText(text: String) { value = text }
    }

    private class RecordingShareService : ShareService {
        val payloads = mutableListOf<SharePayload>()

        override suspend fun share(payload: SharePayload): PlatformResult<Unit> {
            payloads += payload
            return PlatformResult.Success(Unit)
        }
    }

    private class FixedLocationService : LocationService {
        override suspend fun currentLocation(): PlatformResult<GeoLocation> = PlatformResult.Success(sampleLocation)
    }

    private class FakePermissionService : PermissionService {
        val grants = mutableMapOf<PlatformPermission, PermissionStatus>()

        override suspend fun status(permission: PlatformPermission): PermissionStatus =
            grants[permission] ?: PermissionStatus.Unavailable

        override suspend fun request(permission: PlatformPermission): PermissionStatus = status(permission)
    }

    private companion object {
        val sampleFile = PlatformFile(
            reference = "memory://attachment.pdf",
            displayName = "attachment.pdf",
            mimeType = "application/pdf",
            sizeBytes = 42,
        )
        val sampleLocation = GeoLocation(latitude = 3.75, longitude = 8.78, accuracyMeters = 12f)
    }
}
