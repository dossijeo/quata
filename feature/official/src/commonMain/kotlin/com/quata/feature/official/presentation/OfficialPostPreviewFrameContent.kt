package com.quata.feature.official.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

/** Responsive editor preview frame; the host supplies the post-card implementation. */
@Composable
fun OfficialPostPreviewFrameContent(
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val template = quataTheme()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val proportionalHeight = maxWidth * 1.45f
        val previewHeight = when {
            proportionalHeight < 420.dp -> 420.dp
            proportionalHeight > 560.dp -> 560.dp
            else -> proportionalHeight
        }
        Surface(
            color = template.colors.surfaceAlt,
            contentColor = template.colors.textPrimary,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .border(1.dp, template.colors.divider.copy(alpha = 0.55f), RoundedCornerShape(18.dp)),
        ) {
            content(Modifier.fillMaxSize())
        }
    }
}
