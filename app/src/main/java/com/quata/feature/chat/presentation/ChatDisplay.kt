package com.quata.feature.chat.presentation

import android.content.Context
import com.quata.R
import com.quata.core.model.Conversation
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

fun Conversation.chatDisplayTitle(): String = when {
    isEmergency -> "\uD83D\uDEA8 SOS"
    !communityName.isNullOrBlank() -> communityName
    isGroup && participantNames.isNotEmpty() && title.isGeneratedChatTitle(id) -> participantNames.joinToString(", ")
    title.isNotBlank() -> title
    isGroup && participantNames.isNotEmpty() -> participantNames.joinToString(", ")
    else -> ""
}

private fun String.isGeneratedChatTitle(conversationId: String): Boolean {
    val numericId = conversationId.substringAfterLast(':', missingDelimiterValue = "")
    return numericId.isNotBlank() && this == "Chat $numericId"
}

fun Conversation.relativeUpdatedAt(context: Context, nowMillis: Long = System.currentTimeMillis()): String {
    val timestamp = updatedAtMillis ?: parseUpdatedAtMillis(updatedAt, nowMillis) ?: return updatedAt
    return relativeTimeLabel(context, timestamp, nowMillis)
}

fun relativeTimeLabel(context: Context, value: String, nowMillis: Long = System.currentTimeMillis()): String {
    val timestamp = parseUpdatedAtMillis(value, nowMillis) ?: return value
    return relativeTimeLabel(context, timestamp, nowMillis)
}

fun relativeTimeLabel(context: Context, timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val now = Instant.ofEpochMilli(nowMillis)
    val then = Instant.ofEpochMilli(timestampMillis)
    val seconds = ChronoUnit.SECONDS.between(then, now).coerceAtLeast(0)
    val minutes = seconds / 60
    return when {
        seconds < 60 -> context.getString(R.string.time_seconds_ago, seconds.coerceAtLeast(1))
        minutes < 2 -> context.getString(R.string.time_one_minute_ago)
        minutes < 60 -> context.getString(R.string.time_minutes_ago, minutes)
        minutes < 60 * 24 -> context.getString(R.string.time_hours_ago, minutes / 60)
        minutes < 60 * 24 * 7 -> context.getString(R.string.time_days_ago, minutes / (60 * 24))
        minutes < 60 * 24 * 14 -> context.getString(R.string.time_one_week_ago)
        minutes < 60 * 24 * 31 -> context.getString(R.string.time_weeks_ago, minutes / (60 * 24 * 7))
        minutes < 60 * 24 * 62 -> context.getString(R.string.time_one_month_ago)
        minutes < 60 * 24 * 365 -> context.getString(R.string.time_months_ago, minutes / (60 * 24 * 31))
        minutes < 60 * 24 * 365 * 2 -> context.getString(R.string.time_one_year_ago)
        else -> context.getString(R.string.time_years_ago, minutes / (60 * 24 * 365))
    }
}

private fun parseUpdatedAtMillis(value: String, nowMillis: Long): Long? {
    val normalized = value.trim()
    if (normalized.isBlank()) return null
    if (listOf("Ahora", "Now", "Maintenant").any { normalized.equals(it, ignoreCase = true) }) return nowMillis
    if (listOf("Ayer", "Yesterday", "Hier").any { normalized.equals(it, ignoreCase = true) }) {
        return nowMillis - 24L * 60L * 60L * 1000L
    }
    return try {
        Instant.parse(normalized).toEpochMilli()
    } catch (_: DateTimeParseException) {
        try {
            LocalDateTime.parse(normalized)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
