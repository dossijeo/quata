package com.quata.feature.notifications.presentation

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import com.quata.core.text.SosShortcodeKind
import com.quata.core.text.parseSosShortcode
import kotlin.time.ExperimentalTime

/** Localized labels used by the portable relative-time formatter. */
data class RelativeTimeCatalog(
    val seconds: (Long) -> String,
    val oneMinute: String,
    val minutes: (Long) -> String,
    val hours: (Long) -> String,
    val days: (Long) -> String,
    val oneWeek: String,
    val weeks: (Long) -> String,
    val oneMonth: String,
    val months: (Long) -> String,
    val oneYear: String,
    val years: (Long) -> String,
)

/** Localized labels for the semantic attachment previews written by chat. */
data class ChatPreviewCatalog(
    val photo: String,
    val video: String,
    val document: String,
    val voiceNote: String,
    val file: String,
)

/** Localized equivalents of Android's SOS inbox preview labels. */
data class SosPreviewCatalog(
    val locationUpdate: String,
    val locationUnavailable: String,
    val approximateLocation: (String) -> String,
)

fun formatNotificationRelativeTime(
    value: String,
    nowMillis: Long,
    catalog: RelativeTimeCatalog,
): String {
    val timestamp = parseNotificationTimestamp(value, nowMillis) ?: return value
    val minutes = ((nowMillis - timestamp).coerceAtLeast(0) / 1_000L) / 60L
    val seconds = ((nowMillis - timestamp).coerceAtLeast(0) / 1_000L)
    return when {
        seconds < 60L -> catalog.seconds(seconds.coerceAtLeast(1))
        minutes < 2L -> catalog.oneMinute
        minutes < 60L -> catalog.minutes(minutes)
        minutes < 60L * 24L -> catalog.hours(minutes / 60L)
        minutes < 60L * 24L * 7L -> catalog.days(minutes / (60L * 24L))
        minutes < 60L * 24L * 14L -> catalog.oneWeek
        minutes < 60L * 24L * 31L -> catalog.weeks(minutes / (60L * 24L * 7L))
        minutes < 60L * 24L * 62L -> catalog.oneMonth
        minutes < 60L * 24L * 365L -> catalog.months(minutes / (60L * 24L * 31L))
        minutes < 60L * 24L * 365L * 2L -> catalog.oneYear
        else -> catalog.years(minutes / (60L * 24L * 365L))
    }
}

@OptIn(ExperimentalTime::class)
fun parseNotificationTimestamp(value: String, nowMillis: Long): Long? {
    val normalized = value.trim()
    if (normalized.isBlank()) return null
    if (normalized.equals("Ahora", true) || normalized.equals("Now", true) || normalized.equals("Maintenant", true)) return nowMillis
    if (normalized.equals("Ayer", true) || normalized.equals("Yesterday", true) || normalized.equals("Hier", true)) return nowMillis - DAY_MILLIS
    return runCatching { Instant.parse(normalized).toEpochMilliseconds() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(normalized).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() }.getOrNull()
}

fun resolveChatPreview(
    raw: String,
    catalog: ChatPreviewCatalog,
    sosCatalog: SosPreviewCatalog,
): String = resolveSosPreview(raw, sosCatalog) ?: when (raw.trim()) {
    "[QUATA_ATTACHMENT:photo]" -> catalog.photo
    "[QUATA_ATTACHMENT:video]" -> catalog.video
    "[QUATA_ATTACHMENT:document]" -> catalog.document
    "[QUATA_ATTACHMENT:voice_note]", "[QUATA_NOTIFICATION:chat_voice_note]" -> catalog.voiceNote
    "[QUATA_ATTACHMENT:file]", "[QUATA_NOTIFICATION:chat_attachment]" -> catalog.file
    else -> raw
}

fun resolveSosPreview(raw: String, catalog: SosPreviewCatalog): String? {
    val message = raw.parseSosShortcode() ?: return null
    return when {
        message.kind == SosShortcodeKind.LocationUpdate -> catalog.locationUpdate
        !message.hasLocation -> catalog.locationUnavailable
        else -> catalog.approximateLocation(requireNotNull(message.mapsUrl))
    }
}

private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
