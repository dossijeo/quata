package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

/** Shared card shell for post-composer controls and preview sections. */
@Composable
fun ComposerSectionPanelContent(
    title: String,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    val panelColor = if (highlighted) template.colors.surfaceRaised else template.colors.surface
    val contentColor = template.colors.textPrimary
    Surface(
        color = panelColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, template.colors.divider, RoundedCornerShape(24.dp)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title.uppercase(), color = contentColor.copy(alpha = 0.75f), fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
    Spacer(Modifier.height(18.dp))
}
