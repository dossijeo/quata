package com.quata.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

@Composable
fun QuataPanel(
    contentPadding: PaddingValues = PaddingValues(14.dp),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val template = quataTheme()
    Surface(
        color = template.colors.surface.copy(alpha = 0.82f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, template.colors.divider.copy(alpha = 0.72f), RoundedCornerShape(20.dp))
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}
