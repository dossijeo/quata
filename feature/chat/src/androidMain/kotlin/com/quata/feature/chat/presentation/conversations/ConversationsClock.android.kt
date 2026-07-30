package com.quata.feature.chat.presentation.conversations

internal actual fun conversationsCurrentTimeMillis(): Long = System.currentTimeMillis()

internal actual fun parseConversationUpdatedAtMillis(value: String, nowMillis: Long): Long? {
    val normalized = value.trim()
    if (normalized.equals("Ahora", true) || normalized.equals("Now", true) || normalized.equals("Maintenant", true)) return nowMillis
    if (normalized.equals("Ayer", true) || normalized.equals("Yesterday", true) || normalized.equals("Hier", true)) return nowMillis - 86_400_000L
    return normalized.toLongOrNull() ?: runCatching { java.time.Instant.parse(normalized).toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(normalized).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
}
