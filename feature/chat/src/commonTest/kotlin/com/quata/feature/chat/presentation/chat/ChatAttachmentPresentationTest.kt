package com.quata.feature.chat.presentation.chat

import com.quata.core.platform.PlatformFile
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatAttachmentPresentationTest {
    @Test
    fun mimeTypesDriveMediaAndDocumentPresentation() {
        assertEquals(ChatAttachmentKind.Image, kind("opaque", "image/jpeg"))
        assertEquals(ChatAttachmentKind.Video, kind("opaque", "video/mp4"))
        assertEquals(ChatAttachmentKind.Audio, kind("opaque", "audio/mp4"))
        assertEquals(ChatAttachmentKind.Document, kind("report.pdf", "application/pdf"))
    }

    @Test
    fun extensionFallbackPreservesLegacyAttachmentsWithoutMime() {
        assertEquals(ChatAttachmentKind.Image, kind("photo.webp", null))
        assertEquals(ChatAttachmentKind.Video, kind("clip.mov?download=1", null))
        assertEquals(ChatAttachmentKind.Audio, kind("voice.opus", null))
        assertEquals(ChatAttachmentKind.File, kind("archive.zip", null))
    }

    private fun kind(name: String, mime: String?) = chatAttachmentKind(
        PlatformFile(reference = "https://example.invalid/$name", displayName = name, mimeType = mime),
    )
}
