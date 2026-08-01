package com.quata.feature.neighborhoods.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NeighborhoodsScreenHostTest {
    @Test fun `anonymous users can browse but private actions require auth`() {
        assertFalse(canPerformNeighborhoodPrivateAction(null))
        assertFalse(canPerformNeighborhoodPrivateAction("  "))
        assertTrue(canPerformNeighborhoodPrivateAction("profile-id"))
    }

    @Test fun `catalog covers English Spanish and French`() {
        assertTrue(defaultNeighborhoodsScreenStrings("en-US").list.title == "Communities")
        assertTrue(defaultNeighborhoodsScreenStrings("es-ES").list.title == "Comunidades")
        assertTrue(defaultNeighborhoodsScreenStrings("fr-FR").list.title == "Communautés")
    }

    @Test fun `relative activity labels cover Android calendar buckets`() {
        val now = 400L * 86_400_000L
        assertTrue(neighborhoodTimeLabel(now - 86_400_000L, now, "Comunidades") == "Ayer")
        assertTrue(neighborhoodTimeLabel(now - 3L * 86_400_000L, now, "Communities") == "3 days ago")
        assertTrue(neighborhoodTimeLabel(now - 14L * 86_400_000L, now, "Communautés") == "Il y a 2 semaines")
        assertTrue(neighborhoodTimeLabel(now - 60L * 86_400_000L, now, "Comunidades") == "Hace 2 meses")
        assertTrue(neighborhoodTimeLabel(now - 365L * 86_400_000L, now, "Communities") == "1 year ago")
    }
}
