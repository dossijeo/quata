package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quata.core.ui.richtext.QuataPortableRichTextEditorBox

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
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorHtml by rememberSaveable { mutableStateOf(html) }
    OutlinedButton(
        onClick = {
            editorHtml = html
            editorOpen = true
            onEditorOpenChange(true)
        },
        modifier = modifier,
    ) {
        actionIcon()
        Spacer(Modifier.size(8.dp))
        Text(title, fontWeight = FontWeight.ExtraBold)
    }
    if (editorOpen) {
        val closeEditor = {
            editorOpen = false
            onEditorOpenChange(false)
        }
        Dialog(
            onDismissRequest = closeEditor,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            OfficialLongTextEditorContent(
                title = title,
                onBack = closeEditor,
                backContentDescription = backContentDescription,
                saveLabel = saveLabel,
                onSave = {
                    onHtmlChange(editorHtml)
                    closeEditor()
                },
                saveIcon = saveIcon,
                modifier = Modifier.fillMaxSize(),
                editorContent = { editorModifier ->
                    QuataPortableRichTextEditorBox(
                        initialHtml = editorHtml,
                        placeholder = title,
                        onHtmlChange = { editorHtml = it },
                        modifier = editorModifier,
                        fillAvailableHeight = true,
                    )
                },
            )
        }
    }
}
