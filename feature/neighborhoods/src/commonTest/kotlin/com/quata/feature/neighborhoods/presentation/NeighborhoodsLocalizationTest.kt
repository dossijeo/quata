package com.quata.feature.neighborhoods.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class NeighborhoodsLocalizationTest {
    @Test
    fun spanishCatalogMatchesAndroidCommunitiesCopy() {
        val strings = neighborhoodsScreenStringsForLanguage("es-ES")

        assertEquals("Abre una comunidad", strings.list.title)
        assertEquals("Selecciona o busca un barrio para abrir su chat comunitario.", strings.list.searchPlaceholder)
        assertEquals("Abrir chat", strings.list.openChat)
        assertEquals("Usuarios · Malabo", strings.members.title("Malabo"))
        assertEquals("1 usuario", strings.members.memberCount(1))
    }

    @Test
    fun frenchAndEnglishCatalogsRemainComplete() {
        assertEquals("Ouvrir le chat", neighborhoodsScreenStringsForLanguage("fr").list.openChat)
        assertEquals("Open chat", neighborhoodsScreenStringsForLanguage("en-US").list.openChat)
        assertEquals("Open chat", neighborhoodsScreenStringsForLanguage(null).list.openChat)
    }
}
