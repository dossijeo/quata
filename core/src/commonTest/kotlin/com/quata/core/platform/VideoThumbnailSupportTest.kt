package com.quata.core.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoThumbnailSupportTest {
    @Test
    fun recognizesVideoMimeTypeAndCommonExtensions() {
        assertTrue(VideoThumbnailSupport.isVideo(PlatformFile("file:///tmp/capture.bin", mimeType = "video/mp4")))
        assertTrue(VideoThumbnailSupport.isVideo(PlatformFile("file:///tmp/capture.mov", displayName = "capture.mov")))
        assertFalse(VideoThumbnailSupport.isVideo(PlatformFile("file:///tmp/report.pdf", mimeType = "application/pdf")))
    }

    @Test
    fun acceptsOnlyLocalNativeReferencesForNativeThumbnailing() {
        assertTrue(VideoThumbnailSupport.hasLocalFileReference(PlatformFile("file:///tmp/capture.mp4")))
        assertTrue(VideoThumbnailSupport.hasLocalFileReference(PlatformFile("/tmp/capture.mp4")))
        assertFalse(VideoThumbnailSupport.hasLocalFileReference(PlatformFile("https://cdn.quata.example/capture.mp4")))
        assertFalse(VideoThumbnailSupport.hasLocalFileReference(PlatformFile("content://quata/capture.mp4")))
        assertFalse(VideoThumbnailSupport.hasLocalFileReference(PlatformFile("file://")))
    }

    @Test
    fun acceptsOnlyBrowserOwnedBlobReferencesForBrowserThumbnailing() {
        assertTrue(VideoThumbnailSupport.hasBrowserReadableReference(PlatformFile("blob:https://quata.app/id")))
        assertTrue(VideoThumbnailSupport.hasBrowserReadableReference(PlatformFile(" BLOB:https://quata.app/id ")))
        assertFalse(VideoThumbnailSupport.hasBrowserReadableReference(PlatformFile("blob:")))
        assertFalse(VideoThumbnailSupport.hasBrowserReadableReference(PlatformFile("https://cdn.quata.example/capture.mp4")))
        assertFalse(VideoThumbnailSupport.hasBrowserReadableReference(PlatformFile("data:video/mp4;base64,AAAA")))
        assertFalse(VideoThumbnailSupport.hasBrowserReadableReference(PlatformFile("content://quata/capture.mp4")))
    }
}
