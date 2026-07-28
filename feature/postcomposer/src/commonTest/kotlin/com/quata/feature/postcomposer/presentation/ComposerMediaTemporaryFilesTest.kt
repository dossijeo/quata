package com.quata.feature.postcomposer.presentation

import com.quata.core.platform.PlatformFile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposerMediaTemporaryFilesTest {
    @Test
    fun onlyRecognizesTheExactOwnedTemporaryThumbnailShape() {
        assertTrue(isOwnedIosComposerVideoThumbnailPath(
            "/tmp/quata_video_thumbnail_clip_123.png", "/tmp",
        ))
        assertFalse(isOwnedIosComposerVideoThumbnailPath("/tmp/other.png", "/tmp"))
        assertFalse(isOwnedIosComposerVideoThumbnailPath("/tmp/nested/quata_video_thumbnail_clip_123.png", "/tmp"))
        assertFalse(isOwnedIosComposerVideoThumbnailPath("/private/quata_video_thumbnail_clip_123.png", "/tmp"))
        assertFalse(isOwnedIosComposerVideoThumbnailPath("/tmp/quata_video_thumbnail_clip_123.jpg", "/tmp"))
    }

    @Test
    fun lifecycleReleasesGeneratedThumbnailOnReplacementOrDraftClear() {
        val old = PlatformFile("file:///tmp/quata_video_thumbnail_old.png")
        val replacement = PlatformFile("file:///tmp/quata_video_thumbnail_new.png")

        assertTrue(iosComposerThumbnailToRelease(old, replacement) === old)
        assertTrue(iosComposerThumbnailToRelease(old) === old)
        assertFalse(iosComposerThumbnailToRelease(old, old) != null)
    }
}
