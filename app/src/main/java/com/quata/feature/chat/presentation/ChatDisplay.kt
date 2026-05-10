package com.quata.feature.chat.presentation

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
    val now = Instant.ofEpochMilli(nowMillis)
    val then = Instant.ofEpochMilli(timestamp)
    val minutes = ChronoUnit.MINUTES.between(then, now).coerceAtLeast(0)
    return when {
        minutes < 1 -> "hace 1 min"
        minutes < 60 -> "hace $minutes min"
        minutes < 60 * 24 -> {
            val hours = minutes / 60
            "hace $hours h"
        }
        minutes < 60 * 24 * 30 -> {
            val days = minutes / (60 * 24)
            "hace $days d"
        }
        minutes < 60 * 24 * 365 -> {
            val months = minutes / (60 * 24 * 30)
            "hace $months ${if (months == 1L) "mes" else "meses"}"
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
