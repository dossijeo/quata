package com.quata.web

import com.quata.feature.whatsnew.data.QuataLocalWhatsNewCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebWhatsNewContractTest {
    @Test
    fun routesKeepPendingAndHistoryAsSeparateDestinations() {
        assertEquals(WebWhatsNewDestination.PendingReleases, webWhatsNewDestination("whats-new"))
        assertEquals(WebWhatsNewDestination.About, webWhatsNewDestination("about"))
        assertEquals(WebWhatsNewDestination.ReleaseHistory, webWhatsNewDestination("release-history"))
        assertNull(webWhatsNewDestination("feed"))
    }

    @Test
    fun webUsesARealCommonCatalogWithoutReusingAndroidReleaseVersionCodes() {
        assertEquals("common-source-controlled-web", webWhatsNewSourceKind())
        assertEquals("web-1.0-1", QuataLocalWhatsNewCatalog.webReleases().single().releaseId)
        assertEquals(1L, webWhatsNewInstalledVersionCode(null))
        assertEquals(1L, webWhatsNewInstalledVersionCode(0L))
        assertEquals(27L, webWhatsNewInstalledVersionCode(27L))
        assertEquals("", webWhatsNewReturnFragment(WebWhatsNewOrigin.Startup))
        assertEquals("settings", webWhatsNewReturnFragment(WebWhatsNewOrigin.Settings))
    }

    @Test
    fun localizedCopyFallsBackToEnglish() {
        assertEquals("Novedades", webWhatsNewStrings(listOf("es-ES")).title)
        assertEquals("Nouveautés", webWhatsNewStrings(listOf("fr-FR")).title)
        assertEquals("What's New", webWhatsNewStrings(listOf("de-DE")).title)
    }
}
