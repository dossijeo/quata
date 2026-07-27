package com.quata.core.accessibility

import kotlin.test.Test
import kotlin.test.assertEquals

class CriticalControlAccessibilityCatalogTest {
    @Test
    fun spanishCatalogProvidesNameRoleStateAndFocusForCriticalControls() {
        val publish = CriticalControlsAccessibilityCatalog.forLanguageTag("es-ES").publish

        assertEquals("Publicar", publish.name)
        assertEquals("botón", publish.role)
        assertEquals("no seleccionado, publicando", publish.state(isSelected = false, isEnabled = false))
        assertEquals("con foco", publish.focus(isFocused = true))
        assertEquals("Vídeo", CriticalControlsAccessibilityCatalog.forLanguageTag("es-ES").composer.videoType)
    }

    @Test
    fun englishCatalogProvidesNameRoleStateAndFocusForCriticalControls() {
        val type = CriticalControlsAccessibilityCatalog.forLanguageTag("EN-us").composerType

        assertEquals("Post type", type.name)
        assertEquals("button", type.role)
        assertEquals("selected, available", type.state(isSelected = true, isEnabled = true))
        assertEquals("not focused", type.focus(isFocused = false))
        val copy = CriticalControlsAccessibilityCatalog.forLanguageTag("EN-us").composer
        assertEquals("Text", copy.textType)
        assertEquals("Publish", copy.publish)
        assertEquals("1 character", copy.characters(1))
        assertEquals("3 characters", copy.characters(3))
    }

    @Test
    fun unknownLanguageTagsKeepTheSpanishFallback() {
        val copy = CriticalControlsAccessibilityCatalog.forLanguageTag("fr-FR")
        assertEquals("Volver", copy.back.name)
        assertEquals("1 carácter", copy.composer.characters(1))
        assertEquals("2 caracteres", copy.composer.characters(2))
    }
}
