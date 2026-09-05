package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.ui.richtext.QuataPortableRichTextEditorBox

const val OfficialRichTextEditorOpenActionTestTag = "official-rich-text-editor-open"

@Composable
fun OfficialRichTextEditorActionContent(
    html: String,
    title: String,
    onHtmlChange: (String) -> Unit,
    backContentDescription: String,
    saveLabel: String,
    modifier: Modifier = Modifier,
    actionIcon: @Composable () -> Unit = {},
    saveIcon: @Composable () -> Unit = {},
    onEditorOpenChange: (Boolean) -> Unit = {},
) {
    var editorOpen by remember { mutableStateOf(false) }
    var editorHtml by remember { mutableStateOf(html) }
    OutlinedButton(
        onClick = {
            editorHtml = html
            editorOpen = true
            onEditorOpenChange(true)
        },
        modifier = modifier.testTag(OfficialRichTextEditorOpenActionTestTag),
    ) {
        actionIcon()
        Spacer(Modifier.size(8.dp))
        Text(title, fontWeight = FontWeight.ExtraBold)
    }
    if (editorOpen) {
        OfficialLongTextEditorContent(
            title = title,
            onBack = {
                editorOpen = false
                onEditorOpenChange(false)
            },
            backContentDescription = backContentDescription,
            saveLabel = saveLabel,
            onSave = {
                onHtmlChange(editorHtml)
                editorOpen = false
                onEditorOpenChange(false)
            },
            saveIcon = saveIcon,
            editorContent = { editorModifier ->
                QuataPortableRichTextEditorBox(
                    initialHtml = editorHtml,
                    placeholder = title,
                    onHtmlChange = { editorHtml = it },
                    modifier = editorModifier,
                )
            },
        )
    }
}
