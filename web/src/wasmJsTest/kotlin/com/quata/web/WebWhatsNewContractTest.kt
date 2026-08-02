package com.quata.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebWhatsNewContractTest {
    @Test
    fun routesKeepPendingAndHistoryAsSeparateDestinations() {
        assertEquals(WebWhatsNewDestination.PendingReleases, webWhatsNewDestination("whats-new"))
        assertEquals(WebWhatsNewDestination.ReleaseHistory, webWhatsNewDestination("about"))
        assertEquals(WebWhatsNewDestination.ReleaseHistory, webWhatsNewDestination("release-history"))
        assertNull(webWhatsNewDestination("feed"))
    }

    @Test
    fun webDoesNotReuseAndroidReleaseApi() {
        assertEquals("source-controlled-empty", webWhatsNewSourceKind())
        assertEquals("", webWhatsNewReturnFragment(WebWhatsNewOrigin.Startup))
        assertEquals("settings", webWhatsNewReturnFragment(WebWhatsNewOrigin.Settings))
    }

    @Test
    fun localizedCopyFallsBackToEnglish() {
        assertEquals("Novedades", webWhatsNewStrings(listOf("es-ES")).strings.title)
        assertEquals("Nouveautés", webWhatsNewStrings(listOf("fr-FR")).strings.title)
        assertEquals("What's New", webWhatsNewStrings(listOf("de-DE")).strings.title)
    }
}
