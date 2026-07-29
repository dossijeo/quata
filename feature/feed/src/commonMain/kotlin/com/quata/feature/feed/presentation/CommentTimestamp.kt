package com.quata.feature.feed.presentation

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Portable counterpart to the comment timestamp contract used by Android.
 *
 * The API deliberately keeps the source timestamp in the same format Android writes, while
 * rendering every supported absolute backend/mock format as a stable Spanish date label.
 */
@OptIn(ExperimentalTime::class)
fun nowCommentTimestamp(
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = commentTimestampSource(clock.now().toLocalDateTime(timeZone))

@OptIn(ExperimentalTime::class)
fun formatCommentTimestamp(
    value: String,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val normalized = value.trim()
    if (normalized.isBlank()) return ""
    val parsed = parseCommentTimestamp(normalized, timeZone) ?: return normalized
    return "${parsed.day.twoDigits()}/${(parsed.month.ordinal + 1).twoDigits()}/${parsed.year.toString().padStart(4, '0')} ${parsed.hour.twoDigits()}:${parsed.minute.twoDigits()}"
}

/** Accepts precisely the local/SQL/ISO variants accepted by the Android implementation. */
@OptIn(ExperimentalTime::class)
fun parseCommentTimestamp(
    value: String,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): LocalDateTime? {
    parseLocalCommentTimestamp(value)?.let { return it }
    return runCatching { Instant.parse(value).toLocalDateTime(timeZone) }.getOrNull()
}

private fun commentTimestampSource(value: LocalDateTime): String =
    "${value.day}/${(value.month.ordinal + 1)}/${value.year}, ${value.hour}:${value.minute.twoDigits()}:${value.second.twoDigits()}"

private fun parseLocalCommentTimestamp(value: String): LocalDateTime? {
    spanishCommentTimestampPatterns.forEach { pattern ->
        pattern.matchEntire(value)?.let { match ->
            return localDateTime(match, year = 3, month = 2, day = 1)
        }
    }
    sqlOrIsoCommentTimestampPatterns.forEach { pattern ->
        pattern.matchEntire(value)?.let { match ->
            return localDateTime(match, year = 1, month = 2, day = 3)
        }
    }
    return null
}

private fun localDateTime(
    match: MatchResult,
    year: Int,
    month: Int,
    day: Int,
): LocalDateTime? = runCatching {
    LocalDateTime(
        year = match.groupValues[year].toInt(),
        monthNumber = match.groupValues[month].toInt(),
        dayOfMonth = match.groupValues[day].toInt(),
        hour = match.groupValues[4].toInt(),
        minute = match.groupValues[5].toInt(),
        second = match.groupValues[6].toInt(),
        nanosecond = match.groupValues.getOrNull(7)?.takeIf { it.isNotBlank() }?.toInt()?.times(1_000_000) ?: 0,
    )
}.getOrNull()

private fun Int.twoDigits(): String = toString().padStart(2, '0')

private val spanishCommentTimestampPatterns = listOf(
    Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4}), (\\d{1,2}):(\\d{2}):(\\d{2})$"),
    Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4}) (\\d{1,2}):(\\d{2}):(\\d{2})$"),
)

private val sqlOrIsoCommentTimestampPatterns = listOf(
    Regex("^(\\d{4})-(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})$"),
    Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})$"),
    Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})$"),
)
