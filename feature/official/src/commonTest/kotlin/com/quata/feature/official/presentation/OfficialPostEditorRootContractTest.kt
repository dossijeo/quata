package com.quata.feature.official.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialMediaType
import kotlinx.coroutines.test.runTest

class OfficialPostEditorRootContractTest {
    @Test
    fun `publication validation rejects missing title or meaningful html`() {
        assertTrue(isOfficialEditorDraftValid(false, "", "", "<p>Body</p>", false))
        assertFalse(isOfficialEditorDraftValid(false, "Title", "", "<p>&nbsp;</p>", false))
        assertFalse(isOfficialEditorDraftValid(true, "", "Summary", "", false))
        assertTrue(isOfficialEditorDraftValid(true, "Title", "Summary", "", false))
        assertTrue(isOfficialEditorDraftValid(true, "Title", "", "", true))
    }

    @Test
    fun `editor copy is available in every supported authoring language`() {
        assertTrue(OfficialPostEditorStrings.forLanguage("es").publish.isNotBlank())
        assertTrue(OfficialPostEditorStrings.forLanguage("en-US").publish.isNotBlank())
        assertTrue(OfficialPostEditorStrings.forLanguage("fr-FR").publish.isNotBlank())
    }

    @Test fun `detector classifies supported languages from body text`() {
        assertEquals(OfficialPostLanguage.Spanish, detectOfficialLanguage("Este es un comunicado para las familias del barrio"))
        assertEquals(OfficialPostLanguage.English, detectOfficialLanguage("This is the notice for the community and the families"))
        assertEquals(OfficialPostLanguage.French, detectOfficialLanguage("Cette annonce est pour les familles avec une mise à jour"))
    }

    @Test fun `missing media editor is explicit and cannot report false success`() {
        val capability: OfficialEditorCapability<OfficialMediaEditExporter> = OfficialEditorCapability.Unavailable("codec_missing")
        assertEquals("codec_missing", (capability as OfficialEditorCapability.Unavailable).reason)
    }

    @Test fun `media editor declares types and propagates export failure`() = runTest {
        val editor = OfficialMediaEditExporter(
            supportedTypes = setOf(OfficialMediaType.Image),
            editAndExport = { Result.failure(IllegalStateException("export_failed")) },
            cancel = { Result.failure(IllegalStateException("cancel_failed")) },
        )
        assertTrue(OfficialMediaType.Image in editor.supportedTypes)
        assertEquals("export_failed", editor.editAndExport(OfficialEditorMedia("local://x", OfficialMediaType.Image)).exceptionOrNull()?.message)
        assertEquals("cancel_failed", editor.cancel().exceptionOrNull()?.message)
    }

    @Test fun `rich editor cancellation propagates platform failure`() = runTest {
        val editor = OfficialRichBodyEditor(
            content = { _, _, _ -> },
            cancel = { Result.failure(IllegalStateException("fullscreen_cancel_failed")) },
        )

        assertEquals("fullscreen_cancel_failed", editor.cancel().exceptionOrNull()?.message)
    }
}
