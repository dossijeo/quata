package com.quata.feature.chat.presentation.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatDetailLocalizationTest {
    @Test
    fun resolvesVisibleDetailCopyForEverySupportedLanguage() {
        assertEquals("Message", chatDetailStringsForLanguage("en-US").message)
        assertEquals("Mensaje", chatDetailStringsForLanguage("es-ES").message)
        assertEquals("Message", chatDetailStringsForLanguage("fr-FR").message)
        assertEquals("2 members", chatDetailStringsForLanguage("en").memberCount(2))
        assertEquals("2 miembros", chatDetailStringsForLanguage("es").memberCount(2))
        assertEquals("2 membres", chatDetailStringsForLanguage("fr").memberCount(2))
    }
}
