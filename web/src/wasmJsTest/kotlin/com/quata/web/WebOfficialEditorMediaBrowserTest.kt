package com.quata.web

import com.quata.feature.official.domain.OfficialMediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs in Chrome through wasmJsBrowserTest: validates the browser-backed editor contract. */
class WebOfficialEditorMediaBrowserTest {
    @Test
    fun browserEditorPublishesImageAndVideoCanvasCodecCapabilities() {
        assertEquals(setOf(OfficialMediaType.Image, OfficialMediaType.Video), BrowserOfficialMediaEditor.supportedTypes)
        assertTrue(browserOfficialRichTextCancel().isSuccess)
    }
}
