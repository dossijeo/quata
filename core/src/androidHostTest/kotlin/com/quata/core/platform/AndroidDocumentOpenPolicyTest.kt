package com.quata.core.platform

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidDocumentOpenPolicyTest {
    @Test
    fun directReferencesOnlyAllowContentWithAnAuthority() {
        assertTrue(AndroidDocumentOpenPolicy.allowsDirectReference("content://media/documents/42"))

        assertFalse(AndroidDocumentOpenPolicy.allowsDirectReference("https://cdn.quata.example/files/report.pdf"))
        assertFalse(AndroidDocumentOpenPolicy.allowsDirectReference("file:///sdcard/Download/report.pdf"))
        assertFalse(AndroidDocumentOpenPolicy.allowsDirectReference("http://cdn.quata.example/report.pdf"))
        assertFalse(AndroidDocumentOpenPolicy.allowsDirectReference("content:///missing-authority"))
        assertFalse(AndroidDocumentOpenPolicy.allowsDirectReference("https:///missing-host"))
        assertFalse(AndroidDocumentOpenPolicy.allowsDirectReference("/data/user/0/com.quata/files/report.pdf"))
    }

    @Test
    fun fileProviderOutputMustBeAContentUriSoReadGrantsCannotTargetFileUris() {
        assertTrue(AndroidDocumentOpenPolicy.isContentReference("content://com.quata.fileprovider/cache/report.pdf"))
        assertFalse(AndroidDocumentOpenPolicy.isContentReference("file:///data/user/0/com.quata/cache/report.pdf"))
        assertFalse(AndroidDocumentOpenPolicy.isContentReference("https://cdn.quata.example/report.pdf"))
    }

    @Test
    fun onlyFilesInsideAppCacheOrFilesAreEligibleForFileProviderConversion() {
        val root = createTempDir(prefix = "quata-document-policy-")
        try {
            val cache = File(root, "cache").apply { mkdirs() }
            val files = File(root, "files").apply { mkdirs() }
            val external = File(root, "external").apply { mkdirs() }
            val cachedDocument = File(cache, "document.pdf").apply { writeText("pdf") }
            val persistedDocument = File(files, "document.docx").apply { writeText("docx") }
            val externalDocument = File(external, "document.pdf").apply { writeText("pdf") }

            assertTrue(AndroidDocumentOpenPolicy.isAppOwnedFile(cachedDocument, cache, files))
            assertTrue(AndroidDocumentOpenPolicy.isAppOwnedFile(persistedDocument, cache, files))
            assertFalse(AndroidDocumentOpenPolicy.isAppOwnedFile(externalDocument, cache, files))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun infersStableMimeTypesForReaderSupportedDocuments() {
        val cases = mapOf(
            "report.pdf" to "application/pdf",
            "notes.rtf" to "application/rtf",
            "letter.docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "budget.xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "slides.pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        )

        cases.forEach { (name, expectedMime) ->
            assertEquals(expectedMime, AndroidDocumentOpenPolicy.inferMimeType(PlatformFile(reference = name)))
        }
    }
}
