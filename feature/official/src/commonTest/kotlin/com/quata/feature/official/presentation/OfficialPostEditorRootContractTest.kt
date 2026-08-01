package com.quata.feature.official.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
}
