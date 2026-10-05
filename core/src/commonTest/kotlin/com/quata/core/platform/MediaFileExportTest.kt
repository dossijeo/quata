package com.quata.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaFileExportTest {
    @Test
    fun descriptorKeepsOneSourceAndBuildsABoundedSafeName() {
        val descriptor = mediaFileExportDescriptorOrNull(
            reference = " https://cdn.example/media/post-1 ",
            title = "  Barrio: fiesta / tarde  ",
            mimeType = "video/mp4; charset=binary",
        )

        assertEquals("https://cdn.example/media/post-1", descriptor?.reference)
        assertEquals("Barrio_ fiesta _ tarde.mp4", descriptor?.displayName)
        assertEquals("video/mp4", descriptor?.mimeType)
    }

    @Test
    fun descriptorDoesNotDuplicateAnExistingExtension() {
        assertEquals(
            "clip.MP4",
            mediaFileExportDescriptorOrNull("https://cdn.example/clip", "clip.MP4", "video/mp4")?.displayName,
        )
    }

    @Test
    fun descriptorUsesTheStorageExtensionToRefineThePersistedMediaFamily() {
        val descriptor = mediaFileExportDescriptorOrNull(
            "https://cdn.example/storage/post.png?download=1",
            "Barrio",
            "image/jpeg",
        )

        assertEquals("image/png", descriptor?.mimeType)
        assertEquals("Barrio.png", descriptor?.displayName)
    }

    @Test
    fun descriptorRejectsMissingReferenceOrMime() {
        assertNull(mediaFileExportDescriptorOrNull(" ", "media", "image/jpeg"))
        assertNull(mediaFileExportDescriptorOrNull("https://cdn.example/media", "media", " "))
    }

    @Test
    fun materializedLeaseReleasesItsPrivateResourceExactlyOnce() {
        var releases = 0
        val lease = MaterializedMediaFileLease(
            file = PlatformFile("file:///tmp/media.mp4", "media.mp4", "video/mp4", 4),
            releaseAction = { releases += 1 },
        )

        lease.release()
        lease.release()

        assertEquals(1, releases)
    }
}
