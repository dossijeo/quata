package com.quata.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SosPreviewLocalizationTest {
    @Test
    fun spanishCatalogKeepsTheExactAndroidLocationUnavailableLiteral() {
        assertEquals("📍 Ubicación no disponible", SosPreviewCatalog.Spanish.locationUnavailable)
    }

    @Test
    fun localizesKnownAlertInEverySupportedLanguage() {
        val shortcode = buildSosShortcode(
            kind = SosShortcodeKind.Alert,
            senderName = "Ana",
            latitude = 40.4168,
            longitude = -3.7038,
        )

        assertEquals(
            "Ubicacion aproximada: https://maps.google.com/?q=40.4168,-3.7038",
            resolveLocalizedSosPreview(shortcode, SosPreviewCatalog.Spanish),
        )
        assertEquals(
            "Location (approximate): https://maps.google.com/?q=40.4168,-3.7038",
            resolveLocalizedSosPreview(shortcode, SosPreviewCatalog.English),
        )
        assertEquals(
            "Position approximative : https://maps.google.com/?q=40.4168,-3.7038",
            resolveLocalizedSosPreview(shortcode, SosPreviewCatalog.French),
        )
    }

    @Test
    fun localizesUpdateAndDoesNotClaimUnknownShortcodes() {
        assertEquals(
            "SOS location update",
            resolveLocalizedSosPreview("[SOS:kind=update;name=Ana]", SosPreviewCatalog.English),
        )
        assertNull(resolveLocalizedSosPreview("[SOS_UNKNOWN:kind=alert]", SosPreviewCatalog.Spanish))
        assertNull(resolveLocalizedSosPreview("plain message", SosPreviewCatalog.Spanish))
    }
}
