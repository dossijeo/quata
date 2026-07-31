package com.quata.feature.notifications.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationPresentationTest {
    @Test
    fun localizesPortableAttachmentPreviewTokensOutsideAndroid() {
        val strings = strings()

        assertEquals("Foto", strings.localizedNotificationBody("[QUATA_ATTACHMENT:photo]"))
        assertEquals("Nota", strings.localizedNotificationBody("[QUATA_NOTIFICATION:chat_voice_note]"))
        assertEquals("Texto normal", strings.localizedNotificationBody("Texto normal"))
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

    private fun strings() = NotificationsStrings(
        title = "Avisos",
        subtitle = "Actividad",
        backContentDescription = "Volver",
        loadingLabel = "Cargando",
        emptyTitle = "Vacío",
        emptyMessage = "Sin actividad",
        errorTitle = "Error",
        relativeTime = { _, _ -> "" },
        localizedBody = { it },
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
