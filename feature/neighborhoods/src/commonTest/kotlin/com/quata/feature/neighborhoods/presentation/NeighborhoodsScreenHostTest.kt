package com.quata.feature.neighborhoods.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NeighborhoodsScreenHostTest {
    @Test fun anonymous users cannot follow or open chats() {
        assertFalse(canPerformNeighborhoodPrivateAction(null))
        assertFalse(canPerformNeighborhoodPrivateAction("  "))
        assertTrue(canPerformNeighborhoodPrivateAction("profile-id"))
    }

    @Test fun catalog covers English Spanish and French() {
        assertTrue(defaultNeighborhoodsScreenStrings("en-US").list.title == "Communities")
        assertTrue(defaultNeighborhoodsScreenStrings("es-ES").list.title == "Comunidades")
        assertTrue(defaultNeighborhoodsScreenStrings("fr-FR").list.title == "Communautés")
    }
}
