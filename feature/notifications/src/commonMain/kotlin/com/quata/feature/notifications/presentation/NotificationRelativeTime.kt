package com.quata.feature.notifications.presentation

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.ExperimentalTime

data class NotificationRelativeTimeStrings(
    val now: String, val secondsAgo: (Long) -> String, val oneMinuteAgo: String,
    val minutesAgo: (Long) -> String, val hoursAgo: (Long) -> String, val daysAgo: (Long) -> String,
    val oneWeekAgo: String, val weeksAgo: (Long) -> String, val oneMonthAgo: String,
    val monthsAgo: (Long) -> String, val oneYearAgo: String, val yearsAgo: (Long) -> String,
)

@OptIn(ExperimentalTime::class)
fun notificationRelativeTimeLabel(value: String, nowMillis: Long, strings: NotificationRelativeTimeStrings, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    if (value.isBlank()) return ""
    val timestamp = parseNotificationTimestampMillis(value, nowMillis, timeZone) ?: return value
    val seconds = ((nowMillis - timestamp).coerceAtLeast(0L) / 1_000L)
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
private fun parseNotificationTimestampMillis(value: String, nowMillis: Long, timeZone: TimeZone): Long? {
    val normalized = value.trim()
    if (normalized.equals("Ahora", true) || normalized.equals("Now", true) || normalized.equals("Maintenant", true)) return nowMillis
    if (normalized.equals("Ayer", true) || normalized.equals("Yesterday", true) || normalized.equals("Hier", true)) return nowMillis - 86_400_000L
    return runCatching { Instant.parse(normalized).toEpochMilliseconds() }
        .recoverCatching { LocalDateTime.parse(normalized.replace(' ', 'T')).toInstant(timeZone).toEpochMilliseconds() }
        .getOrNull()
}
