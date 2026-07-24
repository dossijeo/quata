package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme

/** Portable quick/advanced editor-mode selector with state controlled by the host. */
@Composable
fun OfficialEditorModeSelectorContent(
    title: String,
    description: String,
    isAdvanced: Boolean,
    onAdvancedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    OfficialEditorSectionCardContent(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = template.colors.textPrimary,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = description,
                    color = template.colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                )
            }
            Switch(checked = isAdvanced, onCheckedChange = onAdvancedChange)
        }
    }
}
