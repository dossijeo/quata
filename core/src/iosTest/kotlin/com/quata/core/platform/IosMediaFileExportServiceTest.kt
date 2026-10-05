package com.quata.core.platform

import com.quata.core.data.toFoundationData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosMediaFileExportServiceTest {
    private val descriptor = MediaFileExportDescriptor(
        reference = "http://localhost/media.mp4",
        displayName = "Qüata clip.mp4",
        mimeType = "video/mp4",
    )

    @Test
    fun materializerWritesExactBytesNameAndMimeThenLeaseRemovesThePrivateFile() = runBlocking {
        val expected = byteArrayOf(0, 1, 2, 3, 4)
        val imageDescriptor = descriptor.copy(
            reference = "http://localhost/media.png",
            displayName = "Qüata image.png",
            mimeType = "image/png",
        )
        val materializer = IosMediaFileMaterializer(
            IosMediaExportTransport {
                IosMediaExportResponse(expected.toFoundationData(), "image/png")
            },
        )

        val result = materializer.materialize(imageDescriptor)
        val lease = assertIs<PlatformResult.Success<MaterializedMediaFileLease>>(result).value
        val path = NSURL(string = lease.file.reference)?.path ?: error("test_file_path_missing")
        val data = NSFileManager.defaultManager.contentsAtPath(path) ?: error("test_file_data_missing")

        assertEquals(imageDescriptor.displayName, lease.file.displayName)
        val physicalName = path.substringAfterLast('/')
        // APFS may expose canonically decomposed Unicode; it must still be the descriptor name,
        // never the lease UUID that owns the containing directory.
        assertTrue(physicalName.startsWith("Q") && physicalName.endsWith("ata image.png"))
        assertEquals(imageDescriptor.mimeType, lease.file.mimeType)
        assertEquals(expected.size.toLong(), lease.file.sizeBytes)
        assertContentEquals(expected, data.bytes?.readBytes(data.length.toInt()))
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath(path))

        lease.release()
        lease.release()

        assertFalse(NSFileManager.defaultManager.fileExistsAtPath(path))
    }

    @Test
    fun mismatchedMimeFailsBeforeAFileOrShareEffectExists() = runBlocking {
        val materializer = IosMediaFileMaterializer(
            IosMediaExportTransport {
                IosMediaExportResponse(byteArrayOf(1).toFoundationData(), "image/jpeg")
            },
        )
        var shareCalls = 0
        val service = IosMediaFileExportService(
            shareService = object : ShareService {
                override suspend fun share(payload: SharePayload): PlatformResult<Unit> {
                    shareCalls += 1
                    return PlatformResult.Success(Unit)
                }
            },
            materializer = materializer,
        )

        val result = service.export(descriptor, MediaFileExportAction.Share)

        assertIs<PlatformResult.Failure>(result)
        assertEquals(0, shareCalls)
    }

    @Test
    fun traversalNameIsRejectedBeforeTransportOrFilesystemEffects() = runBlocking {
        var transportCalls = 0
        val materializer = IosMediaFileMaterializer(
            IosMediaExportTransport {
                transportCalls += 1
                IosMediaExportResponse(byteArrayOf(1).toFoundationData(), "video/mp4")
            },
        )

        val result = materializer.materialize(descriptor.copy(displayName = "../../outside.mp4"))

        assertIs<PlatformResult.Failure>(result)
        assertEquals("ios_media_export_name_invalid", result.reason)
        assertEquals(0, transportCalls)
    }

    @Test
    fun cancellingAnInflightMaterializationCancelsTheTransportAndCreatesNoLease() = runBlocking {
        var transportCancelled = false
        val materializer = IosMediaFileMaterializer(
            IosMediaExportTransport {
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation { transportCancelled = true }
                }
            },
        )

        val job = launch { materializer.materialize(descriptor) }
        yield()
        job.cancelAndJoin()

        assertTrue(transportCancelled)
    }

    @Test
    fun cancelledShareReceivesExactlyOneMaterializedFileAndReleasesItsLease() = runBlocking {
        var releases = 0
        var received: PlatformFile? = null
        val materialized = PlatformFile("file:///tmp/quata.mp4", descriptor.displayName, descriptor.mimeType, 5)
        val service = IosMediaFileExportService(
            shareService = object : ShareService {
                override suspend fun share(payload: SharePayload): PlatformResult<Unit> {
                    assertEquals(1, payload.files.size)
                    received = payload.files.single()
                    return PlatformResult.Cancelled
                }
            },
            materializer = MediaFileMaterializer {
                PlatformResult.Success(MaterializedMediaFileLease(materialized) { releases += 1 })
            },
        )

        val result = service.export(descriptor, MediaFileExportAction.Share)

        assertEquals(PlatformResult.Cancelled, result)
        assertEquals(materialized, received)
        assertEquals(1, releases)
    }

    @Test
    fun dismissingTheNativeActivityControllerIsCancelledRatherThanSuccess() = runBlocking {
        var controller: UIActivityViewController? = null
        val service = IosShareService(
            IosSharePresenter {
                controller = it
                PlatformResult.Success(Unit)
            },
        )
        val outcome = async {
            service.share(
                SharePayload(
                    files = listOf(PlatformFile("file:///tmp/quata.mp4", "quata.mp4", "video/mp4", 5)),
                ),
            )
        }
        while (controller == null) yield()

        requireNotNull(controller).completionWithItemsHandler?.invoke(null, false, null, null)

        assertEquals(PlatformResult.Cancelled, outcome.await())
    }
}
