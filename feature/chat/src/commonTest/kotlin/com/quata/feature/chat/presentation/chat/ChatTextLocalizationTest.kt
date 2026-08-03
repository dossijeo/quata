package com.quata.feature.chat.presentation.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatTextLocalizationTest {
    @Test
    fun resolvesSupportedLanguagesAndRegionalTags() {
        assertEquals("No se pudieron cargar los chats.", chatTextForLanguage(ChatText.LoadConversations, "es-ES"))
        assertEquals("Impossible d’envoyer le message.", chatTextForLanguage(ChatText.Send, "fr_FR"))
        assertEquals("Could not open the chat.", chatTextForLanguage(ChatText.OpenConversation, "en-US"))
    }

    @Test
    fun unknownOrMissingLanguageFallsBackToEnglish() {
        assertEquals("Could not load messages.", chatTextForLanguage(ChatText.LoadMessages, "de"))
        assertEquals("You", chatTextForLanguage(ChatText.You, null))
    }
}
