package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import com.quata.core.designsystem.theme.quataTheme

/** Shared editor media-preview shell; platform hosts supply image/video rendering and edit UI. */
@Composable
fun OfficialEditorMediaPreviewContent(
    removeLabel: String,
    onRemove: () -> Unit,
    mediaContent: @Composable (Modifier) -> Unit,
    editAction: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    Surface(
        color = template.colors.surfaceAlt,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 120.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            mediaContent(Modifier.fillMaxWidth().height(140.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                editAction(Modifier.weight(1f))
                TextButton(onClick = onRemove) { Text(removeLabel) }
            }
        }
    }
}
