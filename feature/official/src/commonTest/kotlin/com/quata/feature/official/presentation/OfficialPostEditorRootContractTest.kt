package com.quata.feature.official.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfficialPostEditorRootContractTest {
    @Test
    fun `publication validation rejects missing title or meaningful html`() {
        assertFalse(isOfficialEditorDraftValid("", "<p>Body</p>"))
        assertFalse(isOfficialEditorDraftValid("Title", "<p>&nbsp;</p>"))
        assertTrue(isOfficialEditorDraftValid("Title", "<p>Body</p>"))
    }

    @Test
    fun `editor copy is available in every supported authoring language`() {
        assertTrue(OfficialPostEditorStrings.forLanguage("es").publish.isNotBlank())
        assertTrue(OfficialPostEditorStrings.forLanguage("en-US").publish.isNotBlank())
        assertTrue(OfficialPostEditorStrings.forLanguage("fr-FR").publish.isNotBlank())
    }
}
