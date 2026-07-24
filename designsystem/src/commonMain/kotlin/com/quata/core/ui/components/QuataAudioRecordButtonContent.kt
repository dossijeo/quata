package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataOrange

/** Portable recording trigger; platform hosts own actual capture lifecycle and permissions. */
@Composable
fun QuataAudioRecordButtonContent(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(92.dp)
            .background(Color.White, CircleShape)
            .border(4.dp, QuataOrange.copy(alpha = 0.18f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (isRecording) 34.dp else 58.dp)
                .background(
                    if (isRecording) Color(0xFFE53935) else QuataOrange,
                    if (isRecording) RoundedCornerShape(8.dp) else CircleShape,
                ),
        )
    }
}
