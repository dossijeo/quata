package com.quata.feature.chat.presentation.chat

import com.quata.core.language.QuataTranslationLanguage
import com.quata.core.language.QuataTranslationResult
import com.quata.core.language.TextTranslator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatTranslationGatewayTest {
    @Test
    fun gatewayUsesTheVisibleDirectionAndReturnsToggleableState() = runTest {
        val translator = CapturingTranslator()
        val gateway = FangChatTranslationGateway(translator)
        val direction = ChatTranslationDirection(QuataTranslationLanguage.Fang, QuataTranslationLanguage.Spanish)

        val state = gateway.translate(" Mbolo ", direction)

        assertEquals("Mbolo", translator.text)
        assertEquals(direction.source, translator.source)
        assertEquals(direction.target, translator.target)
        assertEquals("Hola", state.translation)
        assertEquals("FAN→ES", state.directionLabel)
        assertTrue(state.showTranslation)
    }

    @Test
    fun preferredDirectionIsLocalizedAndReversible() {
        val french = chatTranslationDirectionForLanguage("fr-FR")
        assertEquals(QuataTranslationLanguage.Fang, french.source)
        assertEquals(QuataTranslationLanguage.French, french.target)
        assertEquals(ChatTranslationDirection(QuataTranslationLanguage.French, QuataTranslationLanguage.Fang), french.reversed())
        assertEquals(QuataTranslationLanguage.English, chatTranslationDirectionForLanguage("en_US").target)
        assertEquals(QuataTranslationLanguage.Spanish, chatTranslationDirectionForLanguage("es").target)
    }

    @Test
    fun sharedOverlayCopyMatchesThePublishedAndroidContract() {
        val spanish = chatTranslatorStringsForLanguage("es-ES")
        assertEquals("Traductor Fang", spanish.contentDescription)
        assertEquals("Modo traductor activo", spanish.activeTitle)
        assertEquals("Toca cualquier mensaje para traducirlo", spanish.instruction)
        assertEquals("Salir", spanish.exit)

        val english = chatTranslatorStringsForLanguage("en")
        assertEquals("Translator mode active", english.activeTitle)
        assertEquals("Tap any message to translate it", english.instruction)

        val french = chatTranslatorStringsForLanguage("fr-FR")
        assertEquals("Mode traducteur actif", french.activeTitle)
        assertEquals("Touchez un message pour le traduire", french.instruction)
    }

    private class CapturingTranslator : TextTranslator {
        var text: String? = null
        var source: QuataTranslationLanguage? = null
        var target: QuataTranslationLanguage? = null

        override suspend fun translate(
            text: String,
            sourceLanguage: QuataTranslationLanguage,
            targetLanguage: QuataTranslationLanguage,
        ): QuataTranslationResult {
            this.text = text
            source = sourceLanguage
            target = targetLanguage
            return QuataTranslationResult("Hola", false, listOf(sourceLanguage, targetLanguage), null, null, null)
        }
    }
}
