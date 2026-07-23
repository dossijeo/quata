package com.quata.feature.externalshare

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalShareAttachmentPolicyTest {
    @Test
    fun acceptsMediaAndDocumentsUsingCommonContracts() {
        assertTrue(isSupportedSharedAttachment("share://photo", "photo.bin", "image/jpeg"))
        assertTrue(isSupportedSharedAttachment("share://notes", "notes.md", null))
        assertTrue(isSupportedSharedAttachment("share://report", "report.pdf", "application/pdf"))
    }

    @Test
    fun rejectsUnknownBinaryFiles() {
        assertFalse(isSupportedSharedAttachment("share://archive", "archive.bin", "application/octet-stream"))
    }
}
