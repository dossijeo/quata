package com.quata.feature.postcomposer.presentation

import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
}
