package com.quata.designsystem.translation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.quataTheme

/** Portable Fang translation trigger; hosts supply their capture/navigation behavior through [onClick]. */
@Composable
fun FangTranslatorTriggerContent(
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    Box(
        modifier = modifier
            .size(width = 58.dp, height = 38.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.42f }
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 2.dp, y = 2.dp)
                .size(width = 31.dp, height = 25.dp)
                .background(Brush.linearGradient(listOf(QuataOrange, Color(0xFFFF8A20))), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Public, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-1).dp, y = (-1).dp)
                .size(width = 39.dp, height = 22.dp)
                .background(template.colors.surface.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                .border(1.dp, template.colors.accent.copy(alpha = 0.58f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "fang",
                color = template.colors.accent,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                lineHeight = 10.sp,
            )
        }
    }
}
