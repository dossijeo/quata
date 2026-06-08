package com.quata.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.QuataResolvedTheme
import com.quata.core.designsystem.theme.quataTheme

@Composable
fun QuataLogo(modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        QuataBrandMark(
            compact = false,
            modifier = Modifier.width(210.dp)
        )
        if (subtitle != null) {
            Spacer(Modifier.height(10.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun QuataBrandMark(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val template = quataTheme()
    val wordColor = if (template.resolvedTheme == QuataResolvedTheme.Light) {
        Color.Black
    } else {
        Color(0xFFFFEDD5)
    }
    val markSize = if (compact) 22.sp else 70.sp
    val markLineHeight = if (compact) 22.sp else 70.sp
    val wordSize = if (compact) 5.sp else 18.sp
    val wordLineHeight = if (compact) 7.sp else 22.sp
    val lineWidth = if (compact) 42.dp else 128.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = if (compact) 4.dp else 0.dp)
    ) {
        Text(
            text = "Q\u00DC",
            color = template.colors.accent,
            fontSize = markSize,
            lineHeight = markLineHeight,
            fontWeight = FontWeight.Black,
            letterSpacing = if (compact) 3.sp else 7.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(
                shadow = Shadow(
                    color = template.colors.accent.copy(alpha = 0.30f),
                    offset = Offset(0f, if (compact) 1.2f else 3f),
                    blurRadius = if (compact) 5f else 14f
                )
            )
        )
        Canvas(
            modifier = Modifier
                .padding(top = if (compact) 1.dp else 6.dp)
                .width(lineWidth)
                .height(if (compact) 1.dp else 2.dp)
        ) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        template.colors.accent.copy(alpha = 0.95f),
                        template.colors.accentSoft.copy(alpha = 0.90f),
                        Color.Transparent
                    )
                ),
                cornerRadius = CornerRadius(size.height, size.height)
            )
        }
        Text(
            text = "Q\u00DCATA",
            color = wordColor,
            fontSize = wordSize,
            lineHeight = wordLineHeight,
            fontWeight = FontWeight.Black,
            letterSpacing = if (compact) 2.sp else 8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = if (compact) 2.dp else 8.dp)
        )
    }
}
