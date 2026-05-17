package com.quata.feature.chat.presentation

import android.content.Context
import com.quata.core.model.Conversation
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

fun Conversation.chatDisplayTitle(): String = when {
    isEmergency -> "\uD83D\uDEA8 SOS"
    !communityName.isNullOrBlank() -> communityName
    isGroup && participantNames.isNotEmpty() -> participantNames.joinToString(", ")
    else -> title
}

fun Conversation.relativeUpdatedAt(nowMillis: Long = System.currentTimeMillis()): String {
    val timestamp = updatedAtMillis ?: parseUpdatedAtMillis(updatedAt, nowMillis) ?: return updatedAt
    return relativeTimeLabel(timestamp, nowMillis)
}

fun Conversation.relativeUpdatedAt(context: Context, nowMillis: Long = System.currentTimeMillis()): String {
    val timestamp = updatedAtMillis ?: parseUpdatedAtMillis(updatedAt, nowMillis) ?: return updatedAt
    return relativeTimeLabel(timestamp, nowMillis)
}

fun relativeTimeLabel(value: String, nowMillis: Long = System.currentTimeMillis()): String {
    val timestamp = parseUpdatedAtMillis(value, nowMillis) ?: return value
    return relativeTimeLabel(timestamp, nowMillis)
}

fun relativeTimeLabel(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val now = Instant.ofEpochMilli(nowMillis)
    val then = Instant.ofEpochMilli(timestampMillis)
    val seconds = ChronoUnit.SECONDS.between(then, now).coerceAtLeast(0)
    val minutes = seconds / 60
    return when {
        seconds < 60 -> "hace ${seconds.coerceAtLeast(1)} seg"
        minutes < 60 -> "hace $minutes min"
        minutes < 60 * 24 -> "hace ${minutes / 60} h"
        minutes < 60 * 24 * 7 -> {
            val days = minutes / (60 * 24)
            "hace $days ${if (days == 1L) "d\u00EDa" else "d\u00EDas"}"
        }
        minutes < 60 * 24 * 31 -> {
            val weeks = minutes / (60 * 24 * 7)
            "hace $weeks sem"
        }
        minutes < 60 * 24 * 365 -> {
            val months = minutes / (60 * 24 * 31)
            "hace $months mes"
        }
        else -> {
            val years = minutes / (60 * 24 * 365)
            "hace $years ${if (years == 1L) "a\u00F1o" else "a\u00F1os"}"
        }
    }
}

private fun parseUpdatedAtMillis(value: String, nowMillis: Long): Long? {
    val normalized = value.trim()
    if (normalized.isBlank()) return null
    if (normalized.equals("Ahora", ignoreCase = true)) return nowMillis
    if (normalized.equals("Ayer", ignoreCase = true)) return nowMillis - 24L * 60L * 60L * 1000L
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
