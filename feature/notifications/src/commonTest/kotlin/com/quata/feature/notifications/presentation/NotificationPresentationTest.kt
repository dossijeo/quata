package com.quata.feature.notifications.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.quata.core.model.NotificationItem
import com.quata.core.text.SosPreviewCatalog
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.time.ExperimentalTime

class NotificationPresentationTest {
    @Test
    fun localizesPortableAttachmentPreviewTokensOutsideAndroid() {
        val strings = strings()

        assertEquals("Foto", strings.localizedNotificationBody("[QUATA_ATTACHMENT:photo]"))
        assertEquals("Nota", strings.localizedNotificationBody("[QUATA_NOTIFICATION:chat_voice_note]"))
        assertEquals("Texto normal", strings.localizedNotificationBody("Texto normal"))
    }

    @Test
    fun localizesSosBeforeAttachmentMarkersAndLeavesUnknownShortcodesUntouched() {
        val strings = strings()
        val sosThatLooksLikeAnAttachment = "[SOS:kind=alert;name=Ana;custom=%5BQUATA_ATTACHMENT%3Aphoto%5D]"

        assertEquals("📍 Ubicación no disponible", strings.localizedNotificationBody(sosThatLooksLikeAnAttachment))
        assertEquals("[SOS_UNKNOWN:alert]", strings.localizedNotificationBody("[SOS_UNKNOWN:alert]"))
        assertEquals("Foto", strings.localizedNotificationBody("[QUATA_ATTACHMENT:photo]"))
    }

    @Test
    fun usesTheSameRelativeThresholdsAsAndroid() {
        val now = 1_000_000L
        val relative = notificationRelativeTimeLabel(
            value = "1970-01-01T00:15:40Z",
            nowMillis = now,
            strings = relativeStrings(),
        )
        assertEquals("hace 1 min", relative)
        assertEquals(
            "hace 5 min",
            notificationRelativeTimeLabel("1970-01-01T00:11:40Z", now, relativeStrings()),
        )
    }

    @Test
    fun blankCreatedAtRemainsBlankLikeAndroid() {
        assertEquals("", notificationRelativeTimeLabel("   ", 1_000_000L, relativeStrings()))
    }

    @Test
    @OptIn(ExperimentalTime::class)
    fun parsesLocalizedClockWordsIsoAndLeavesInvalidValuesVisible() {
        val now = 2_000_000_000_000L

        assertEquals("hace 1 s", notificationRelativeTimeLabel("Now", now, relativeStrings(), TimeZone.UTC))
        assertEquals("hace 1 d", notificationRelativeTimeLabel("Hier", now, relativeStrings(), TimeZone.UTC))
        assertEquals(
            "hace 5 min",
            notificationRelativeTimeLabel(
                Instant.fromEpochMilliseconds(now - 5L * 60_000L).toString(),
                now,
                relativeStrings(),
                TimeZone.UTC,
            ),
        )
        assertEquals(
            "not a timestamp",
            notificationRelativeTimeLabel("not a timestamp", now, relativeStrings(), TimeZone.UTC),
        )
    }

    @Test
    @OptIn(ExperimentalTime::class)
    fun keepsWeekMonthAndYearThresholdsFromTheAndroidContract() {
        val now = 2_000_000_000_000L
        fun label(ageMinutes: Long): String = notificationRelativeTimeLabel(
            value = Instant.fromEpochMilliseconds(now - ageMinutes * 60_000L).toString(),
            nowMillis = now,
            strings = relativeStrings(),
            timeZone = TimeZone.UTC,
        )

        assertEquals("hace 6 d", label(60L * 24L * 6L))
        assertEquals("hace 1 semana", label(60L * 24L * 7L))
        assertEquals("hace 2 semanas", label(60L * 24L * 14L))
        assertEquals("hace 1 mes", label(60L * 24L * 31L))
        assertEquals("hace 2 meses", label(60L * 24L * 62L))
        assertEquals("hace 1 año", label(60L * 24L * 365L))
        assertEquals("hace 2 años", label(60L * 24L * 365L * 2L))
    }

    @Test
    fun anonymousClickRequestsAuthenticationWithoutMutatingAndKeepsTheItem() {
        val item = notificationItem()
        val calls = mutableListOf<String>()

        handleNotificationClick(
            item = item,
            canMutate = false,
            onMarkRead = { calls += "mark" },
            onOpenConversation = { calls += "open:$it" },
            onAuthenticationRequired = { calls += "auth:${it.conversationId}" },
        )

        assertEquals(listOf("auth:conversation-7"), calls)
    }

    @Test
    fun anonymousSwipeRequestsAuthenticationButRejectsDismissTransition() {
        val calls = mutableListOf<String>()
        val accepted = handleNotificationDismissAttempt(
            canDismiss = false,
            onDismiss = { calls += "dismiss" },
            onDismissBlocked = { calls += "auth" },
        )

        assertFalse(accepted)
        assertEquals(listOf("auth"), calls)
    }

    @Test
    fun authenticatedInteractionsMutateAndAcceptDismissTransition() {
        val item = notificationItem()
        val calls = mutableListOf<String>()
        handleNotificationClick(
            item = item,
            canMutate = true,
            onMarkRead = { calls += "mark" },
            onOpenConversation = { calls += "open:$it" },
            onAuthenticationRequired = { calls += "auth" },
        )
        val accepted = handleNotificationDismissAttempt(
            canDismiss = true,
            onDismiss = { calls += "dismiss" },
            onDismissBlocked = { calls += "blocked" },
        )

        assertTrue(accepted)
        assertEquals(listOf("mark", "open:conversation-7", "dismiss"), calls)
    }

    @Test
    fun errorRetryActionInvokesTheSharedRetryCallback() {
        var retries = 0

        handleNotificationRetry { retries += 1 }

        assertEquals(1, retries)
    }

    private fun notificationItem() = NotificationItem(
        id = "notification-1",
        conversationId = "conversation-7",
        title = "Aviso",
        body = "Mensaje",
        createdAt = "",
        unreadCount = 1,
    )

    private fun strings() = NotificationsStrings(
        title = "Avisos",
        subtitle = "Actividad",
        backContentDescription = "Volver",
        loadingLabel = "Cargando",
        emptyTitle = "Vacío",
        emptyMessage = "Sin actividad",
        errorTitle = "Error",
        retryLabel = "Reintentar",
        relativeTime = { _, _ -> "" },
        localizedBody = { it },
        sosPreviewCatalog = SosPreviewCatalog.Spanish,
        photoPreview = "Foto",
        videoPreview = "Vídeo",
        documentPreview = "Documento",
        voiceNotePreview = "Nota",
        filePreview = "Archivo",
    )

    private fun relativeStrings() = NotificationRelativeTimeStrings(
        now = "ahora",
        secondsAgo = { "hace $it s" },
        oneMinuteAgo = "hace 1 min",
        minutesAgo = { "hace $it min" },
        hoursAgo = { "hace $it h" },
        daysAgo = { "hace $it d" },
        oneWeekAgo = "hace 1 semana",
        weeksAgo = { "hace $it semanas" },
        oneMonthAgo = "hace 1 mes",
        monthsAgo = { "hace $it meses" },
        oneYearAgo = "hace 1 año",
        yearsAgo = { "hace $it años" },
    )
}
