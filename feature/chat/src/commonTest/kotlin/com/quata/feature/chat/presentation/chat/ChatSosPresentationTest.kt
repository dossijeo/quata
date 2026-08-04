package com.quata.feature.chat.presentation.chat

import com.quata.core.text.SosShortcodeKind
import com.quata.core.text.buildSosShortcode
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

        val presentation = assertNotNull(resolveChatSosPresentation(raw))

        assertTrue(presentation.isUpdate)
        assertFalse(presentation.isUnavailable)
        assertEquals("https://maps.google.com/?q=40.4168,-3.7038", presentation.mapsUrl)
        assertEquals("Antigüedad: 2 min", presentation.age)
        assertEquals("Precisión: 8 m", presentation.accuracy)
        assertEquals("Velocidad: 4.3 km/h", presentation.speed)
    }

    @Test
    fun ordinaryMessageIsNotMisclassifiedAsSos() {
        assertNull(resolveChatSosPresentation("Nos vemos en el barrio esta tarde"))
    }
}
