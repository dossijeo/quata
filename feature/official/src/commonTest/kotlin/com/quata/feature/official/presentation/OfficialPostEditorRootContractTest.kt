package com.quata.feature.official.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import com.quata.feature.official.domain.OfficialPostLanguage

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
}
