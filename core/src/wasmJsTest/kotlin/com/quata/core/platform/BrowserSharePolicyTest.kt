package com.quata.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserSharePolicyTest {
    @Test
    fun promotesOnlyCredentialFreeHttpUrls() {
        assertEquals("https://quata.example/post/42", BrowserSharePolicy.webUrlOrNull(" https://quata.example/post/42 "))
        assertEquals("http://localhost:8080/post/42", BrowserSharePolicy.webUrlOrNull("http://localhost:8080/post/42"))
        assertEquals(null, BrowserSharePolicy.webUrlOrNull("https://user:secret@quata.example/post/42"))
        assertEquals(null, BrowserSharePolicy.webUrlOrNull("content://com.quata/private"))
        assertEquals(null, BrowserSharePolicy.webUrlOrNull("https://quata.example/post/42\nnext"))
    }

    @Test
    fun doesNotTurnStandaloneLocalUrisIntoSharedText() {
        assertEquals(null, BrowserSharePolicy.safeTextOrNull("file:///private/report.pdf"))
        assertEquals(null, BrowserSharePolicy.safeTextOrNull("content://com.quata/private"))
        assertEquals(null, BrowserSharePolicy.safeTextOrNull("blob:https://quata.example/capability"))
        assertEquals("Mensaje para compartir", BrowserSharePolicy.safeTextOrNull(" Mensaje para compartir "))
        assertEquals("Mira https://quata.example/post/42", BrowserSharePolicy.safeTextOrNull("Mira https://quata.example/post/42"))
        assertEquals("https://quata.example/post/42", BrowserSharePolicy.safeTextOrNull("https://quata.example/post/42"))
    }

    @Test
    fun acceptsOnlyBlobReferencesForBrowserFiles() {
        assertTrue(BrowserSharePolicy.hasSafeBrowserFileReferences(listOf(PlatformFile("blob:https://quata.example/id"))))
        assertFalse(BrowserSharePolicy.hasSafeBrowserFileReferences(listOf(PlatformFile("content://com.quata/id"))))
        assertFalse(BrowserSharePolicy.hasSafeBrowserFileReferences(listOf(PlatformFile("file:///private/id"))))
        assertFalse(BrowserSharePolicy.hasSafeBrowserFileReferences(listOf(PlatformFile("https://signed.example/object?token=secret"))))
    }
}
