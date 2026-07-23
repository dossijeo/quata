package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun QuataAvatarFallback(
    name: String,
    stableId: String = name,
    modifier: Modifier = Modifier.size(44.dp)
) {
    val initials = avatarInitials(name)
    BoxWithConstraints(
        modifier = modifier.background(Color(avatarFallbackColorArgb(stableId)), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val fontSize = with(LocalDensity.current) {
            (minOf(maxWidth, maxHeight) * if (initials.length > 1) 0.34f else 0.46f).toSp()
        }
        Text(initials, fontWeight = FontWeight.Black, fontSize = fontSize, color = Color.White)
    }
}

fun avatarInitials(displayName: String): String {
    val words = displayName.trim().split(Regex("[\\s\\-_.]+")).filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> "${words.first().first()}${words.last().first()}"
        words.size == 1 -> words.first().take(2)
        else -> "Q"
    }.uppercase()
}

fun avatarFallbackColorArgb(stableId: String): Int {
    val hash = stableId.trim().lowercase()
        .fold(0L) { accumulator, character -> (accumulator * 31L + character.code) and 0x7FFF_FFFFL }
    return AvatarFallbackColors[(hash % AvatarFallbackColors.size).toInt()]
}

private val AvatarFallbackColors = intArrayOf(
    0xFFE65100.toInt(), 0xFF1565C0.toInt(), 0xFF2E7D32.toInt(), 0xFF6A1B9A.toInt(),
    0xFFAD1457.toInt(), 0xFF00695C.toInt(), 0xFF283593.toInt(), 0xFF4E342E.toInt()
)
