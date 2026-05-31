package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

val CompactButtonContentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
val CompactIconButtonSize = 36.dp
val CompactIconVisualScale = 0.75f

fun Modifier.compactButtonMinSize(): Modifier =
    defaultMinSize(minWidth = ButtonDefaults.MinWidth * 0.72f, minHeight = ButtonDefaults.MinHeight * 0.7f)

@Composable
fun CompactIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    MaterialIcon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.graphicsLayer(scaleX = CompactIconVisualScale, scaleY = CompactIconVisualScale),
        tint = tint
    )
}

@Composable
fun CompactIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    MaterialIcon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.graphicsLayer(scaleX = CompactIconVisualScale, scaleY = CompactIconVisualScale),
        tint = tint
    )
}

@Composable
fun CompactIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .requiredSize(CompactIconButtonSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .background(Color.Transparent),
        contentAlignment = Alignment.Center,
        content = content
    )
}
