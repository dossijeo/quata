package com.quata.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
class DocumentFixtureSignatureTest {
    @Test fun fixtureNamesAreStableForPlatformResourceTests() {
        assertEquals(listOf("fixture.pdf", "fixture.rtf", "fixture.docx", "fixture.pptx", "fixture.xlsx"), fixtureNames)
    }
    private val fixtureNames = listOf("fixture.pdf", "fixture.rtf", "fixture.docx", "fixture.pptx", "fixture.xlsx")
}
