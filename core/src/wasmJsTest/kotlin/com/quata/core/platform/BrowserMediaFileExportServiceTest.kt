@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.core.platform

import kotlinx.coroutines.test.runTest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BrowserMediaFileExportServiceTest {
    private val descriptor = MediaFileExportDescriptor(
        reference = "https://cdn.example.test/media/source.mp4",
        displayName = "Qüata clip.mp4",
        mimeType = "video/mp4",
    )

    @AfterTest
    fun cleanup() {
        if (browserMediaExportTestEnvironmentAvailable()) restoreBrowserMediaExportFixture()
    }

    @Test
    fun downloadMaterializesExactFileAndRevokesObjectUrl() = runTest {
        if (!browserMediaExportTestEnvironmentAvailable()) return@runTest
        installBrowserMediaExportFixture("png")
        val imageDescriptor = descriptor.copy(
            reference = "https://cdn.example.test/media/source.png",
            displayName = "Qüata image.png",
            mimeType = "image/png",
        )

        val result = BrowserMediaFileExportService().export(imageDescriptor, MediaFileExportAction.Download)
        waitForBrowserMediaExportRelease()

        assertIs<PlatformResult.Success<Unit>>(result)
        assertEquals(1, browserMediaExportRequestCount(), "request count")
        assertEquals(imageDescriptor.reference, browserMediaExportLastUrl())
        assertTrue(browserMediaExportCredentialsOmitted())
        assertEquals(imageDescriptor.displayName, browserMediaExportDownloadName())
        assertEquals(imageDescriptor.mimeType, browserMediaExportBlobType())
        assertEquals(3, browserMediaExportBlobSize())
        assertEquals(1, browserMediaExportRevokeCount(), "object URL revoke count")
        assertEquals(0, browserMediaExportBlobMapSize(), "leased Blob map")
        assertEquals(0, browserMediaExportShareCount())
    }

    @Test
    fun shareReceivesOneMaterializedFileAndReportsUserCancellation() = runTest {
        if (!browserMediaExportTestEnvironmentAvailable()) return@runTest
        installBrowserMediaExportFixture("cancel")

        val result = BrowserMediaFileExportService().export(descriptor, MediaFileExportAction.Share)
        waitForBrowserMediaExportRelease()

        assertEquals(PlatformResult.Cancelled, result)
        assertEquals(1, browserMediaExportShareCount())
        assertEquals(descriptor.displayName, browserMediaExportSharedName())
        assertEquals(descriptor.mimeType, browserMediaExportSharedType())
        assertEquals(3, browserMediaExportSharedSize())
        assertEquals(0, browserMediaExportDownloadCount())
        assertEquals(1, browserMediaExportRevokeCount())
        assertEquals(0, browserMediaExportBlobMapSize())
    }

    @Test
    fun failedMaterializationHasNoEffectAndSameDescriptorCanRetry() = runTest {
        if (!browserMediaExportTestEnvironmentAvailable()) return@runTest
        installBrowserMediaExportFixture("failure")
        val service = BrowserMediaFileExportService()

        val failed = service.export(descriptor, MediaFileExportAction.Share)
        browserMediaExportAllowSuccess()
        val retried = service.export(descriptor, MediaFileExportAction.Share)
        waitForBrowserMediaExportRelease()

        assertIs<PlatformResult.Failure>(failed)
        assertIs<PlatformResult.Success<Unit>>(retried)
        assertEquals(2, browserMediaExportRequestCount())
        assertEquals(1, browserMediaExportShareCount())
        assertEquals(descriptor.displayName, browserMediaExportSharedName())
        assertEquals(0, browserMediaExportDownloadCount())
        assertEquals(1, browserMediaExportRevokeCount())
        assertEquals(0, browserMediaExportBlobMapSize())
    }

    @Test
    fun oversizedResponseFailsBeforeDownloadOrShare() = runTest {
        if (!browserMediaExportTestEnvironmentAvailable()) return@runTest
        installBrowserMediaExportFixture("oversize")

        val result = BrowserMediaFileExportService().export(descriptor, MediaFileExportAction.Download)

        assertIs<PlatformResult.Failure>(result)
        assertEquals(0, browserMediaExportDownloadCount())
        assertEquals(0, browserMediaExportShareCount())
        assertEquals(0, browserMediaExportRevokeCount())
        assertEquals(0, browserMediaExportBlobMapSize())
    }

    @Test
    fun feedAndOfficialRejectMismatchedMimeBeforeAnyEffect() = runTest {
        if (!browserMediaExportTestEnvironmentAvailable()) return@runTest
        installBrowserMediaExportFixture("wrong-mime")

        val result = BrowserMediaFileExportService().export(descriptor, MediaFileExportAction.Share)

        assertIs<PlatformResult.Failure>(result)
        assertEquals(0, browserMediaExportShareCount())
        assertEquals(0, browserMediaExportDownloadCount())
        assertEquals(0, browserMediaExportBlobMapSize())
    }

    @Test
    fun chatCompatibilityCanRetainTheResponseMimeForHistoricalMetadata() = runTest {
        if (!browserMediaExportTestEnvironmentAvailable()) return@runTest
        installBrowserMediaExportFixture("wrong-mime")

        val result = BrowserMediaFileExportService().export(
            descriptor.copy(allowResponseMimeOverride = true),
            MediaFileExportAction.Share,
        )
        waitForBrowserMediaExportRelease()

        assertIs<PlatformResult.Success<Unit>>(result)
        assertEquals("image/png", browserMediaExportSharedType())
        assertEquals(3, browserMediaExportSharedSize())
        assertEquals(1, browserMediaExportRevokeCount())
        assertEquals(0, browserMediaExportBlobMapSize())
    }

    @Test
    fun missingContentLengthUsesTheBoundedReaderAndStillExportsExactBytes() = runTest {
        if (!browserMediaExportTestEnvironmentAvailable()) return@runTest
        installBrowserMediaExportFixture("missing-length")

        val result = BrowserMediaFileExportService().export(descriptor, MediaFileExportAction.Share)
        waitForBrowserMediaExportRelease()

        assertIs<PlatformResult.Success<Unit>>(result)
        assertEquals(3, browserMediaExportSharedSize())
        assertEquals(descriptor.mimeType, browserMediaExportSharedType())
        assertEquals(1, browserMediaExportRevokeCount())
        assertEquals(0, browserMediaExportBlobMapSize())
    }
}

@JsFun("() => typeof globalThis.document !== 'undefined' && typeof globalThis.HTMLAnchorElement === 'function'")
private external fun browserMediaExportTestEnvironmentAvailable(): Boolean

@JsFun(
    """(mode) => {
      if (globalThis.__quataMediaExportTest) throw new Error('media_export_test_already_installed');
      const navigatorObject = globalThis.navigator;
      const shareDescriptor = Object.getOwnPropertyDescriptor(navigatorObject, 'share');
      const canShareDescriptor = Object.getOwnPropertyDescriptor(navigatorObject, 'canShare');
      const state = globalThis.__quataMediaExportTest = {
        mode,
        requests: 0,
        lastUrl: '',
        credentialsOmitted: false,
        downloads: 0,
        downloadName: '',
        shares: 0,
        sharedName: '',
        sharedType: '',
        sharedSize: 0,
        blobType: '',
        blobSize: 0,
        revokes: 0,
        originalFetch: globalThis.fetch,
        originalCreateObjectURL: URL.createObjectURL,
        originalRevokeObjectURL: URL.revokeObjectURL,
        originalAnchorClick: HTMLAnchorElement.prototype.click,
        shareDescriptor,
        canShareDescriptor,
      };
      globalThis.fetch = async (url, options = {}) => {
        state.requests += 1;
        state.lastUrl = String(url);
        state.credentialsOmitted = options.credentials === 'omit' && options.redirect === 'error';
        if (state.mode === 'failure') return new Response('', { status: 503 });
        if (state.mode === 'oversize') {
          return new Response(new Uint8Array([1]), {
            status: 200,
            headers: { 'content-type': 'video/mp4', 'content-length': String(50 * 1024 * 1024 + 1) },
          });
        }
        const responseHeaders = { 'content-type': ['wrong-mime', 'png'].includes(state.mode) ? 'image/png' : 'video/mp4' };
        if (state.mode !== 'missing-length') responseHeaders['content-length'] = '3';
        return new Response(new Uint8Array([1, 2, 3]), {
          status: 200,
          headers: responseHeaders,
        });
      };
      URL.createObjectURL = blob => {
        state.blobType = blob.type;
        state.blobSize = blob.size;
        return 'blob:quata-media-export-test';
      };
      URL.revokeObjectURL = () => { state.revokes += 1; };
      HTMLAnchorElement.prototype.click = function() {
        state.downloads += 1;
        state.downloadName = this.download;
      };
      Object.defineProperty(navigatorObject, 'canShare', {
        configurable: true,
        value: ({ files }) => Array.isArray(files) && files.length === 1,
      });
      Object.defineProperty(navigatorObject, 'share', {
        configurable: true,
        value: async ({ files }) => {
          const file = files[0];
          state.shares += 1;
          state.sharedName = file.name;
          state.sharedType = file.type;
          state.sharedSize = file.size;
          if (state.mode === 'cancel') throw new DOMException('cancelled', 'AbortError');
        },
      });
    }""",
)
private external fun installBrowserMediaExportFixture(mode: String)

@JsFun("() => { const s = globalThis.__quataMediaExportTest; if (s) s.mode = 'success'; }")
private external fun browserMediaExportAllowSuccess()

@JsFun(
    """() => {
      const state = globalThis.__quataMediaExportTest;
      if (!state) return;
      globalThis.fetch = state.originalFetch;
      URL.createObjectURL = state.originalCreateObjectURL;
      URL.revokeObjectURL = state.originalRevokeObjectURL;
      HTMLAnchorElement.prototype.click = state.originalAnchorClick;
      if (state.shareDescriptor) Object.defineProperty(globalThis.navigator, 'share', state.shareDescriptor);
      else delete globalThis.navigator.share;
      if (state.canShareDescriptor) Object.defineProperty(globalThis.navigator, 'canShare', state.canShareDescriptor);
      else delete globalThis.navigator.canShare;
      delete globalThis.__quataMediaExportTest;
    }""",
)
private external fun restoreBrowserMediaExportFixture()

private suspend fun waitForBrowserMediaExportRelease(): Unit = suspendCoroutine { continuation ->
    scheduleBrowserMediaExportRelease { continuation.resume(Unit) }
}

@JsFun("(callback) => globalThis.setTimeout(callback, 1100)")
private external fun scheduleBrowserMediaExportRelease(callback: () -> Unit)

private fun browserMediaExportRequestCount(): Int = js("globalThis.__quataMediaExportTest.requests")
private fun browserMediaExportLastUrl(): String = js("globalThis.__quataMediaExportTest.lastUrl")
private fun browserMediaExportCredentialsOmitted(): Boolean = js("globalThis.__quataMediaExportTest.credentialsOmitted")
private fun browserMediaExportDownloadCount(): Int = js("globalThis.__quataMediaExportTest.downloads")
private fun browserMediaExportDownloadName(): String = js("globalThis.__quataMediaExportTest.downloadName")
private fun browserMediaExportShareCount(): Int = js("globalThis.__quataMediaExportTest.shares")
private fun browserMediaExportSharedName(): String = js("globalThis.__quataMediaExportTest.sharedName")
private fun browserMediaExportSharedType(): String = js("globalThis.__quataMediaExportTest.sharedType")
private fun browserMediaExportSharedSize(): Int = js("globalThis.__quataMediaExportTest.sharedSize")
private fun browserMediaExportBlobType(): String = js("globalThis.__quataMediaExportTest.blobType")
private fun browserMediaExportBlobSize(): Int = js("globalThis.__quataMediaExportTest.blobSize")
private fun browserMediaExportRevokeCount(): Int = js("globalThis.__quataMediaExportTest.revokes")
private fun browserMediaExportBlobMapSize(): Int = js("globalThis.__quataMediaExportBlobs?.size || 0")
