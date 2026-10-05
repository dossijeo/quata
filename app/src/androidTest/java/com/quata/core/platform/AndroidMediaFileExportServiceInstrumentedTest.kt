package com.quata.core.platform

import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AndroidMediaFileExportServiceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var share: RecordingShareService
    private lateinit var service: AndroidMediaFileExportService
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.cacheDir, "quata_media_exports").deleteRecursively()
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        server = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory(), false)
            start()
        }
        share = RecordingShareService()
        client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .build()
        service = AndroidMediaFileExportService(
            context,
            share,
            client,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        File(context.cacheDir, "quata_media_exports").deleteRecursively()
    }

    @Test
    fun shareCancellationReceivesExactFileAndLeavesNoPrivateResidual() = runBlocking {
        server.enqueue(mediaResponse("image/png"))
        share.result = PlatformResult.Cancelled
        val descriptor = descriptor("share-cancel-${System.nanoTime()}.png", "image/png")

        val result = service.export(descriptor, MediaFileExportAction.Share)

        assertEquals(PlatformResult.Cancelled, result)
        assertEquals(1, share.calls)
        assertEquals(descriptor.displayName, share.name)
        assertEquals(descriptor.mimeType, share.mime)
        assertArrayEquals(MediaBytes, share.bytes)
        assertTrue(File(context.cacheDir, "quata_media_exports").listFiles().orEmpty().isEmpty())
        assertEquals(descriptor.mimeType, server.takeRequest().getHeader("Accept"))
    }

    @Test
    fun failedMaterializationHasNoEffectAndRetryDownloadsExactMediaStoreFile() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(mediaResponse())
        val displayName = "retry-${System.nanoTime()}.mp4"
        val descriptor = descriptor(displayName)

        val failed = service.export(descriptor, MediaFileExportAction.Download)
        val retried = service.export(descriptor, MediaFileExportAction.Download)

        assertTrue(failed is PlatformResult.Failure)
        assertTrue(retried is PlatformResult.Success)
        assertEquals(0, share.calls)
        assertTrue(File(context.cacheDir, "quata_media_exports").listFiles().orEmpty().isEmpty())
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(displayName),
            null,
        )!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(MediaBytes.size.toLong(), cursor.getLong(1))
            assertEquals("video/mp4", cursor.getString(2))
            val id = cursor.getLong(0)
            context.contentResolver.delete(
                android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                null,
                null,
            )
            assertFalse(cursor.moveToNext())
        }
    }

    @Test
    fun mismatchedMimeFailsBeforeShareAndCleansTemporaryFile() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/jpeg")
                .setBody(okio.Buffer().write(MediaBytes)),
        )

        val result = service.export(descriptor("wrong-mime.mp4"), MediaFileExportAction.Share)

        assertTrue(result is PlatformResult.Failure)
        assertEquals(0, share.calls)
        assertTrue(File(context.cacheDir, "quata_media_exports").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun successfulShareKeepsThenExplicitlyReleasesItsBoundedLease() = runBlocking {
        server.enqueue(mediaResponse())
        share.result = PlatformResult.Success(Unit)
        val shortLeaseService = AndroidMediaFileExportService(
            context = context,
            shareService = share,
            client = client,
            shareLeaseRetentionMillis = 50,
        )

        val result = shortLeaseService.export(descriptor("lease.mp4"), MediaFileExportAction.Share)

        assertTrue(result is PlatformResult.Success)
        assertEquals(1, File(context.cacheDir, "quata_media_exports").listFiles().orEmpty().size)
        withTimeout(2_000) {
            while (File(context.cacheDir, "quata_media_exports").listFiles().orEmpty().isNotEmpty()) delay(10)
        }
    }

    @Test
    fun cancellingAnInflightRequestCancelsTransportAndLeavesNoPrivateResidual() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch {
            service.export(descriptor("cancel-inflight.mp4"), MediaFileExportAction.Share)
        }
        val request = withContext(Dispatchers.IO) {
            server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)
        }
        assertNotNull(request)

        withTimeout(2_000) { job.cancelAndJoin() }

        withTimeout(2_000) {
            while (File(context.cacheDir, "quata_media_exports").listFiles().orEmpty().isNotEmpty()) delay(10)
        }
        assertEquals(0, share.calls)
    }

    private fun descriptor(name: String, mimeType: String = "video/mp4") = MediaFileExportDescriptor(
        reference = server.url("/$name").toString(),
        displayName = name,
        mimeType = mimeType,
    )

    private fun mediaResponse(mimeType: String = "video/mp4") = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", mimeType)
        .setHeader("Content-Length", MediaBytes.size)
        .setBody(okio.Buffer().write(MediaBytes))

    private class RecordingShareService : ShareService {
        var result: PlatformResult<Unit> = PlatformResult.Success(Unit)
        var calls = 0
        var name: String? = null
        var mime: String? = null
        var bytes: ByteArray? = null

        override suspend fun share(payload: SharePayload): PlatformResult<Unit> {
            calls += 1
            val file = payload.files.single()
            name = file.displayName
            mime = file.mimeType
            bytes = File(requireNotNull(android.net.Uri.parse(file.reference).path)).readBytes()
            return result
        }
    }

    private companion object {
        val MediaBytes = byteArrayOf(0, 1, 2, 3, 4, 5)
    }
}
