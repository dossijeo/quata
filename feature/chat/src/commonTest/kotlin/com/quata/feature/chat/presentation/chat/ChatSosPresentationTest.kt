package com.quata.feature.chat.presentation.chat

import com.quata.core.text.SosShortcodeKind
import com.quata.core.text.SosLocationUnavailableReason
import com.quata.core.text.buildSosShortcode
import com.quata.core.text.SosPreviewCatalog
import com.quata.core.text.parseSosShortcode
import com.quata.core.text.resolveLocalizedSosPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatSosPresentationTest {
    @Test
    fun shortcodeBecomesPortableMapPresentationWithoutExposingTransportText() {
        val raw = buildSosShortcode(
            kind = SosShortcodeKind.LocationUpdate,
            senderName = "Lucía",
            latitude = 40.4168,
            longitude = -3.7038,
            ageMillis = 120_000,
            accuracyMeters = 8.4,
            speedKmh = 4.25,
        )

        val presentation = assertNotNull(resolveChatSosPresentation(raw, chatSosStringsForLanguage("es")))

        assertTrue(presentation.isUpdate)
        assertFalse(presentation.isUnavailable)
        assertEquals("https://maps.google.com/?q=40.4168,-3.7038", presentation.mapsUrl)
        assertEquals("Antiguedad de ubicacion: 2 min", presentation.age)
        assertEquals("Precision: 8 m", presentation.accuracy)
        assertEquals("Velocidad: 4.3 km/h", presentation.speed)
    }

    @Test
    fun ordinaryMessageIsNotMisclassifiedAsSos() {
        assertNull(resolveChatSosPresentation("Nos vemos en el barrio esta tarde", chatSosStringsForLanguage("es")))
    }

    @Test
    fun shortcodeUsesTheExactAndroidCatalogForEverySupportedLanguage() {
        val raw = buildSosShortcode(
            kind = SosShortcodeKind.LocationUpdate,
            senderName = "Lucía",
            latitude = 40.4168,
            longitude = -3.7038,
            ageMillis = 30_000,
            accuracyMeters = 8.4,
            speedKmh = 4.25,
        )

        val spanish = assertNotNull(resolveChatSosPresentation(raw, chatSosStringsForLanguage("es-ES")))
        assertEquals("Actualizacion de ubicacion SOS", spanish.title)
        assertEquals("Se ha obtenido una ubicacion mas precisa.", spanish.body)
        assertEquals("Antiguedad de ubicacion: menos de 1 minuto", spanish.age)

        val english = assertNotNull(resolveChatSosPresentation(raw, chatSosStringsForLanguage("en")))
        assertEquals("SOS location update", english.title)
        assertEquals("Location age: less than 1 minute", english.age)
        assertEquals("Accuracy: 8 m", english.accuracy)

        val french = assertNotNull(resolveChatSosPresentation(raw, chatSosStringsForLanguage("fr-FR")))
        assertEquals("Mise a jour de position SOS", french.title)
        assertEquals("Age de la position : moins d'1 minute", french.age)
        assertEquals("Vitesse : 4.3 km/h", french.speed)
    }

    @Test
    fun shortcodePreservesUnavailableReasonForPermissionAndFallbackSurfaces() {
        val raw = buildSosShortcode(
            kind = SosShortcodeKind.Alert,
            senderName = "Lucía",
            customMessage = "Necesito ayuda",
            locationUnavailableReason = SosLocationUnavailableReason.PermissionDenied,
        )

        val parsed = assertNotNull(raw.parseSosShortcode())
        assertFalse(parsed.hasLocation)
        assertEquals(SosLocationUnavailableReason.PermissionDenied, parsed.locationUnavailableReason)

        val presentation = assertNotNull(resolveChatSosPresentation(raw, chatSosStringsForLanguage("es")))
        assertTrue(presentation.isUnavailable)
        assertEquals("📍 Ubicación no disponible: permiso denegado", presentation.unavailableLabel)
        assertEquals(
            "📍 Ubicación no disponible: permiso denegado",
            resolveLocalizedSosPreview(raw, SosPreviewCatalog.Spanish),
        )
    }

    @Test
    fun unavailableReasonsAreLocalizedWithoutChangingMessagesThatHaveCoordinates() {
        val denied = buildSosShortcode(
            kind = SosShortcodeKind.Alert,
            senderName = "Lucía",
            locationUnavailableReason = SosLocationUnavailableReason.Timeout,
        )
        val withCoordinates = buildSosShortcode(
            kind = SosShortcodeKind.Alert,
            senderName = "Lucía",
            latitude = 3.7523,
            longitude = 8.7741,
            locationUnavailableReason = SosLocationUnavailableReason.PermissionDenied,
        )

        val english = assertNotNull(resolveChatSosPresentation(denied, chatSosStringsForLanguage("en")))
        assertEquals("📍 Location unavailable: timed out", english.unavailableLabel)

        assertFalse(withCoordinates.contains("reason="))
        val located = assertNotNull(withCoordinates.parseSosShortcode())
        assertTrue(located.hasLocation)
        assertEquals(null, located.locationUnavailableReason)
    }
}
