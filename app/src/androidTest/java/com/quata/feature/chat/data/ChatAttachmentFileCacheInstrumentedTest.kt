package com.quata.feature.chat.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.platform.PlatformFile
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ChatAttachmentFileCacheInstrumentedTest {
    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var cache: ChatAttachmentFileCache

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cacheRoot().deleteRecursively()
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
        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        cache = ChatAttachmentFileCache(
            appContext = context,
            accessTokenProvider = { "test-bearer" },
            publishableKey = "test-publishable-key",
            okHttp = client,
            canonicalUrlResolver = { candidate -> candidate.takeIf { it.startsWith(server.url("/").toString()) } },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        cacheRoot().deleteRecursively()
    }

    @Test
    fun successfulHttpsDownloadUsesAuthenticatedHeadersAndExactBytes() = runBlocking {
        server.enqueue(audioResponse())
        val remote = remote("success.m4a")

        val resolved = cache.resolveCachedAttachment(ProfileId, remote)

        assertNotNull(resolved)
        val local = File(requireNotNull(android.net.Uri.parse(resolved!!.reference).path))
        assertArrayEquals(AudioBytes, local.readBytes())
        assertEquals(AudioBytes.size.toLong(), resolved.sizeBytes)
        val request = server.takeRequest()
        assertEquals("Bearer test-bearer", request.getHeader("Authorization"))
        assertEquals("test-publishable-key", request.getHeader("apikey"))
        assertEquals("no-store", request.getHeader("Cache-Control"))
        assertTrue(request.getHeader("Accept").orEmpty().contains("audio/*"))
        assertNoTemporaryFiles()
    }

    @Test
    fun httpFailureLeavesNoFileAndTheSameDescriptorCanRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(audioResponse())
        val remote = remote("retry.m4a")

        val failed = cache.resolveCachedAttachment(ProfileId, remote)
        assertNull(failed)
        assertCacheEmpty()

        val recovered = cache.resolveCachedAttachment(ProfileId, remote)

        assertNotNull(recovered)
        val local = File(requireNotNull(android.net.Uri.parse(recovered!!.reference).path))
        assertArrayEquals(AudioBytes, local.readBytes())
        assertEquals(2, server.requestCount)
        assertNoTemporaryFiles()
    }

    @Test
    fun redirectIsNotFollowedAndLeavesNoPrivateResidue() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "https://attacker.invalid/credential-sink"),
        )

        val result = cache.resolveCachedAttachment(ProfileId, remote("redirect.m4a"))

        assertNull(result)
        assertEquals(1, server.requestCount)
        assertCacheEmpty()
    }

    @Test
    fun emptyAndOversizedResponsesFailClosedWithoutResidualFiles() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mp4")
                .setHeader("Content-Length", "0"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mp4")
                .setBody("x")
                .setHeader("Content-Length", (50L * 1024L * 1024L + 1L).toString()),
        )

        assertNull(cache.resolveCachedAttachment(ProfileId, remote("empty.m4a")))
        assertNull(cache.resolveCachedAttachment(ProfileId, remote("oversized.m4a")))

        assertEquals(2, server.requestCount)
        assertCacheEmpty()
    }

    @Test
    fun nonAllowlistedUrlNeverOpensTheTransport() = runBlocking {
        val result = cache.resolveCachedAttachment(
            ProfileId,
            PlatformFile(
                reference = "https://attacker.invalid/audio.m4a",
                displayName = "audio.m4a",
                mimeType = "audio/mp4",
            ),
        )

        assertNull(result)
        assertEquals(0, server.requestCount)
        assertFalse(cacheRoot().exists())
    }

    private fun remote(name: String) = PlatformFile(
        reference = server.url("/storage/v1/object/public/chat-attachments/$ProfileId/message/$name").toString(),
        displayName = name,
        mimeType = "audio/mp4",
    )

    private fun audioResponse() = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "audio/mp4")
        .setHeader("Content-Length", AudioBytes.size)
        .setBody(okio.Buffer().write(AudioBytes))

    private fun cacheRoot() = File(context.filesDir, "supabase_chat_cache_v1/attachments/$ProfileId")

    private fun assertCacheEmpty() {
        assertTrue(cacheRoot().listFiles().orEmpty().isEmpty())
    }

    private fun assertNoTemporaryFiles() {
        assertTrue(cacheRoot().listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    private companion object {
        const val ProfileId = "http-boundary-profile"
        val AudioBytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6)
    }
}
