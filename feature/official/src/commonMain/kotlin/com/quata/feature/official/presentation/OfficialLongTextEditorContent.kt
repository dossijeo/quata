package com.quata.feature.official.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.QuataEditorScaffold
import com.quata.core.ui.components.QuataEditorToolButton

/** Shared full-screen shell for the long-form Official text editor. */
@Composable
fun OfficialLongTextEditorContent(
    title: String,
    backContentDescription: String,
    saveLabel: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    saveIcon: @Composable () -> Unit,
    editorContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    QuataEditorScaffold(
        title = title,
        showTitle = true,
        onBack = onBack,
        backContentDescription = backContentDescription,
        actions = {
            QuataEditorToolButton(label = saveLabel, enabled = true, onClick = onSave, icon = saveIcon)
        },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 10.dp),
        ) {
            editorContent(
                Modifier
                    .fillMaxSize()
                    .fillMaxWidth()
                    .border(1.dp, template.colors.divider, RoundedCornerShape(8.dp))
                    .padding(top = 6.dp, bottom = 2.dp),
            )
        }
    }
}
