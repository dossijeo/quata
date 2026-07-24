package com.quata.feature.official.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme

/** Shared visual shell for small form sections in the Official editor. */
@Composable
fun OfficialEditorSectionCardContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val template = quataTheme()
    Surface(
        color = template.colors.surface,
        contentColor = template.colors.textPrimary,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, template.colors.divider.copy(alpha = 0.55f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun OfficialEditorSectionTitleContent(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = quataTheme().colors.textPrimary,
        fontWeight = FontWeight.Black,
        fontSize = 16.sp,
        modifier = modifier,
    )
}
