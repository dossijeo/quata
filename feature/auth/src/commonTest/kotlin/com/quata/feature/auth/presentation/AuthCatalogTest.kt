package com.quata.feature.auth.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthCatalogTest {
    @Test
    fun preservesEveryFormerAndroidCountryPrefixInEachAvailableLocale() {
        val english = AuthCatalog.countryPrefixes(AuthCatalogLocale.English)
        val spanish = AuthCatalog.countryPrefixes(AuthCatalogLocale.Spanish)

        assertEquals(205, english.size)
        assertEquals(english.map { it.code }, spanish.map { it.code })
        assertEquals("+240 — Equatorial Guinea", english.first().label)
        assertEquals("+240 — Guinea Ecuatorial", spanish.first().label)
        assertEquals("+34 — Spain", english.first { it.code == "34" }.label)
        assertEquals("+34 — España", spanish.first { it.code == "34" }.label)
        assertEquals("+998 — Uzbekistan", english.last().label)
        assertEquals("+998 — Uzbekistán", spanish.last().label)
    }

    @Test
    fun preservesTheExistingAuthCopyForEachAndroidLocale() {
        val english = AuthCatalog.copy(AuthCatalogLocale.English)
        val spanish = AuthCatalog.copy(AuthCatalogLocale.Spanish)
        val french = AuthCatalog.copy(AuthCatalogLocale.French)

        assertEquals("Connect, post, and chat", english.loginSubtitle)
        assertEquals("Conecta, publica y conversa", spanish.loginSubtitle)
        assertEquals("Connecte, publie et discute", french.loginSubtitle)
        assertEquals("What is your mother's name?", english.secretQuestions.first { it.value == "madre" }.label)
        assertEquals("¿Cómo se llama tu madre?", spanish.secretQuestions.first { it.value == "madre" }.label)
        assertEquals("Quel est le nom de ta mere ?", french.secretQuestions.first { it.value == "madre" }.label)
    }
}
