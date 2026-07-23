package com.quata.core.language

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FangTranslationServiceTest {
    @Test
    fun translatesThroughTheCommonProtocolAndWarmsUpOnce() = runTest {
        val transport = FakeTransport()
        val service = FangTranslationService("https://translator.test/", transport)

        val result = service.translate(
            text = "Mbolo",
            sourceLanguage = QuataTranslationLanguage.Fang,
            targetLanguage = QuataTranslationLanguage.Spanish,
        )

        assertEquals("Hola", result.translation)
        assertEquals(listOf(QuataTranslationLanguage.Fang, QuataTranslationLanguage.Spanish), result.route)
        assertEquals(listOf("https://translator.test/warmup", "https://translator.test/translate"), transport.postUrls)
        assertTrue(transport.bodies.last().contains("\"src_lang\":\"fan_Latn\""))
    }

    @Test
    fun exposesRemoteErrorDetailsWithoutPlatformTypes() = runTest {
        val service = FangTranslationService("https://translator.test", FakeTransport(translateResponse = TranslationHttpResponse(422, "", "{\"detail\":\"Idioma no soportado\"}")))

        val error = assertFailsWith<QuataTranslationException> {
            service.translate("Mbolo", QuataTranslationLanguage.Fang, QuataTranslationLanguage.Spanish, warmupFirst = false)
        }

        assertEquals(422, error.statusCode)
        assertEquals("Idioma no soportado", error.message)
    }

    private class FakeTransport(
        private val translateResponse: TranslationHttpResponse = TranslationHttpResponse(200, "OK", "{\"translation\":\"Hola\",\"route\":[\"fan_Latn\",\"spa_Latn\"]}"),
    ) : TranslationHttpTransport {
        val postUrls = mutableListOf<String>()
        val bodies = mutableListOf<String>()

        override suspend fun get(url: String) = TranslationHttpResponse(200, "OK", "")
        override suspend fun post(url: String, body: String): TranslationHttpResponse {
            postUrls += url
            bodies += body
            return if (url.endsWith("/warmup")) TranslationHttpResponse(200, "OK", "") else translateResponse
        }
    }
}
