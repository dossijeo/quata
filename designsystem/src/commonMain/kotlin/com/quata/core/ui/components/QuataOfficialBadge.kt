package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Portable verified/official account mark for avatars and identity surfaces. */
@Composable
fun QuataOfficialBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp)
            .background(Color(0xFF2F80ED), CircleShape)
            .border(1.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
    }
}
