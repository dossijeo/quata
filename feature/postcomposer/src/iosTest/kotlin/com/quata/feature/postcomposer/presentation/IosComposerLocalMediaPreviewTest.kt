package com.quata.feature.postcomposer.presentation

import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class IosComposerLocalMediaPreviewTest {
    @Test
    fun codecFailureIsAnExplicitUnavailableState() {
        val preview = PlatformResult.Failure("video_thumbnail_decode_failed").toIosComposerVideoPreview()
        assertEquals("video_thumbnail_decode_failed", assertIs<IosComposerVideoPreview.Unavailable>(preview).reason)
    }

    @Test
    fun successfulExtractorOutputIsTheOnlyThumbnailState() {
        val file = PlatformFile("file:///tmp/thumb.png", mimeType = "image/png")
        assertEquals(file, assertIs<IosComposerVideoPreview.Thumbnail>(PlatformResult.Success(file).toIosComposerVideoPreview()).file)
    }

    @Test
    fun cancelledConsumerDoesNotRetainAnInFlightDecodeResult() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        var decodes = 0
        val cache = IosComposerPreviewImageCache(dispatcher) {
            decodes += 1
            gate.await()
            IosComposerImagePreview.Unavailable
        }
        val file = PlatformFile("file:///tmp/photo.jpg", mimeType = "image/jpeg")
        val consumer = async { cache.acquire(file) }

        runCurrent()
        assertEquals(1, decodes)
        cache.release(file)
        consumer.cancelAndJoin()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, cache.retainedEntryCount())
    }
}
