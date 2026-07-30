package com.quata.feature.notifications.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.Instant
import kotlin.time.ExperimentalTime

class NotificationPresentationTest {
    private val catalog = RelativeTimeCatalog(
        seconds = { "$it seconds" }, oneMinute = "one minute", minutes = { "$it minutes" }, hours = { "$it hours" },
        days = { "$it days" }, oneWeek = "one week", weeks = { "$it weeks" }, oneMonth = "one month",
        months = { "$it months" }, oneYear = "one year", years = { "$it years" },
    )

    @Test fun parsesIsoAndLocalizedClockWords() {
        val now = 1_700_000_000_000L
        assertEquals(now, parseNotificationTimestamp("Now", now))
        assertEquals(now - 86_400_000L, parseNotificationTimestamp("Hier", now))
        assertEquals(1_700_000_000_000L, parseNotificationTimestamp("2023-11-14T22:13:20Z", now))
        assertNull(parseNotificationTimestamp("not a timestamp", now))
    }

    @OptIn(ExperimentalTime::class)
    @Test fun keepsAndroidRelativeTimeBoundariesAndCatalogPluralization() {
        val now = 2_000_000_000_000L
        fun label(ageMinutes: Long) = formatNotificationRelativeTime(Instant.fromEpochMilliseconds(now - ageMinutes * 60_000L).toString(), now, catalog)
        assertEquals("1 seconds", formatNotificationRelativeTime("Now", now, catalog))
        assertEquals("one minute", label(1))
        assertEquals("59 minutes", label(59))
        assertEquals("1 hours", label(60))
        assertEquals("6 days", label(60L * 24L * 6L))
        assertEquals("one week", label(60L * 24L * 7L))
        assertEquals("2 weeks", label(60L * 24L * 14L))
        assertEquals("one month", label(60L * 24L * 31L))
        assertEquals("2 months", label(60L * 24L * 62L))
        assertEquals("one year", label(60L * 24L * 365L))
        assertEquals("2 years", label(60L * 24L * 365L * 2L))
    }

    @Test fun resolvesAttachmentsAndLegacyPreviewsWithoutLeakingTechnicalKeys() {
        val previews = ChatPreviewCatalog("🖼️ photo", "🎥 video", "📄 document", "🎤 voice", "📎 file")
        val sos = SosPreviewCatalog("location update", "location unavailable") { "approximate location: $it" }
        assertEquals("🖼️ photo", resolveChatPreview("[QUATA_ATTACHMENT:photo]", previews, sos))
        assertEquals("🎤 voice", resolveChatPreview("[QUATA_NOTIFICATION:chat_voice_note]", previews, sos))
        assertEquals("📎 file", resolveChatPreview("[QUATA_NOTIFICATION:chat_attachment]", previews, sos))
        assertEquals("Hello", resolveChatPreview("Hello", previews, sos))
    }

    @Test fun resolvesSosAlertUpdateAndUnavailableWithTheProvidedCatalog() {
        val sos = SosPreviewCatalog("location update", "location unavailable") { "approximate location: $it" }
        assertEquals("approximate location: https://maps.google.com/?q=40.4,-3.7", resolveSosPreview("[SOS:kind=alert;name=Ana;lat=40.4;lng=-3.7]", sos))
        assertEquals("location update", resolveSosPreview("[SOS:kind=update;name=Ana]", sos))
        assertEquals("location unavailable", resolveSosPreview("[SOS:kind=alert;name=Ana]", sos))
    }
}
