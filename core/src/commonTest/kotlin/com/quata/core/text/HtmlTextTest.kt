package com.quata.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlTextTest {
    @Test
    fun normalizesTagsBreaksAndEntities() {
        assertEquals("Hola & adiós\nMundo", "<b>Hola &amp; adi&#243;s</b><br>Mundo".decodeHtmlEntities())
    }
}
