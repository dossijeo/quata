package com.quata.core.ui.components

import java.util.Locale

fun avatarInitials(displayName: String): String {
    val words = displayName.trim()
        .split(Regex("[\\s\\-_.]+"))
        .filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> "${words.first().first()}${words.last().first()}"
        words.size == 1 -> words.first().take(2)
        else -> "Q"
    }.uppercase(Locale.ROOT)
}

fun avatarFallbackColorArgb(stableId: String): Int {
    val hash = stableId.trim().lowercase(Locale.ROOT)
        .fold(0L) { accumulator, character ->
            (accumulator * 31L + character.code) and 0x7FFF_FFFFL
        }
    return AVATAR_FALLBACK_COLORS[(hash % AVATAR_FALLBACK_COLORS.size).toInt()]
}

private val AVATAR_FALLBACK_COLORS = intArrayOf(
    0xFFE65100.toInt(),
    0xFF1565C0.toInt(),
    0xFF2E7D32.toInt(),
    0xFF6A1B9A.toInt(),
    0xFFAD1457.toInt(),
    0xFF00695C.toInt(),
    0xFF283593.toInt(),
    0xFF4E342E.toInt()
)
