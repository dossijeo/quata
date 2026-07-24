package com.quata.feature.postcomposer.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.compactButtonMinSize

/** Shared location edit/save control; the host supplies its icon and localized label. */
@Composable
fun ComposerLocationEditButtonContent(
    highlighted: Boolean,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    val backgroundColor = animateColorAsState(
        targetValue = if (highlighted) template.colors.accent.copy(alpha = 0.22f) else Color.Transparent,
        label = "composer_location_background"
    ).value
    val borderColor = animateColorAsState(
        targetValue = if (highlighted) template.colors.accent else template.colors.divider,
        label = "composer_location_border"
    ).value
    Surface(
        color = backgroundColor,
        contentColor = if (highlighted) template.colors.accent else template.colors.textPrimary,
        shape = RoundedCornerShape(9.dp),
        modifier = modifier
            .height(40.dp)
            .compactButtonMinSize()
            .border(1.dp, borderColor, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(Modifier.width(4.dp))
            Text(label, fontWeight = FontWeight.ExtraBold)
        }
    }
}
