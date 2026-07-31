package com.quata.feature.notifications.presentation

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.ExperimentalTime

/**
 * Portable implementation of Android's chat relative-time thresholds.
 *
 * Hosts provide their localized fragments, while parsing and the boundary semantics stay shared
 * for Android, Web and iOS. A value we cannot parse remains visible verbatim rather than being
 * misrepresented as recent activity.
 */
data class NotificationRelativeTimeStrings(
    val now: String,
    val secondsAgo: (Long) -> String,
    val oneMinuteAgo: String,
    val minutesAgo: (Long) -> String,
    val hoursAgo: (Long) -> String,
    val daysAgo: (Long) -> String,
    val oneWeekAgo: String,
    val weeksAgo: (Long) -> String,
    val oneMonthAgo: String,
    val monthsAgo: (Long) -> String,
    val oneYearAgo: String,
    val yearsAgo: (Long) -> String,
)

@OptIn(ExperimentalTime::class)
fun notificationRelativeTimeLabel(
    value: String,
    nowMillis: Long,
    strings: NotificationRelativeTimeStrings,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val timestampMillis = parseNotificationTimestampMillis(value, nowMillis, timeZone) ?: return value
    val seconds = ((nowMillis - timestampMillis).coerceAtLeast(0L) / 1_000L)
    val minutes = seconds / 60L
    return when {
        seconds < 60L -> strings.secondsAgo(seconds.coerceAtLeast(1L))
        minutes < 2L -> strings.oneMinuteAgo
        minutes < 60L -> strings.minutesAgo(minutes)
        minutes < 24L * 60L -> strings.hoursAgo(minutes / 60L)
        minutes < 7L * 24L * 60L -> strings.daysAgo(minutes / (24L * 60L))
        minutes < 14L * 24L * 60L -> strings.oneWeekAgo
        minutes < 31L * 24L * 60L -> strings.weeksAgo(minutes / (7L * 24L * 60L))
        minutes < 62L * 24L * 60L -> strings.oneMonthAgo
        minutes < 365L * 24L * 60L -> strings.monthsAgo(minutes / (31L * 24L * 60L))
        minutes < 2L * 365L * 24L * 60L -> strings.oneYearAgo
        else -> strings.yearsAgo(minutes / (365L * 24L * 60L))
    }
}

@OptIn(ExperimentalTime::class)
private fun parseNotificationTimestampMillis(
    value: String,
    nowMillis: Long,
    timeZone: TimeZone,
): Long? {
    val normalized = value.trim()
    if (normalized.isBlank()) return nowMillis
    if (normalized.equals("Ahora", ignoreCase = true) ||
        normalized.equals("Now", ignoreCase = true) ||
        normalized.equals("Maintenant", ignoreCase = true)
    ) return nowMillis
    if (normalized.equals("Ayer", ignoreCase = true) ||
        normalized.equals("Yesterday", ignoreCase = true) ||
        normalized.equals("Hier", ignoreCase = true)
    ) return nowMillis - 24L * 60L * 60L * 1_000L

    return runCatching { Instant.parse(normalized).toEpochMilliseconds() }
        .recoverCatching {
            LocalDateTime.parse(normalized.replace(' ', 'T')).toInstant(timeZone).toEpochMilliseconds()
        }
        .getOrNull()
}
