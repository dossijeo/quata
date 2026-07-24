package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

/** Portable online/offline presence badge intended for avatar overlays. */
@Composable
fun QuataAvatarPresenceBadgeContent(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Box(
        modifier = modifier
            .size(15.dp)
            .background(if (isOnline) Color(0xFF25B56A) else template.colors.surface.copy(alpha = 0.96f), CircleShape)
            .border(1.5.dp, template.colors.surface, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!isOnline) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = template.colors.textSecondary.copy(alpha = 0.72f),
                modifier = Modifier.size(10.dp)
            )
        }
    }
}
