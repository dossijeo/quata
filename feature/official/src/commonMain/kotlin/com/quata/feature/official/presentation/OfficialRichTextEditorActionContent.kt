package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quata.core.ui.richtext.QuataPortableRichTextEditorBox

class OfficialRichTextEditorE2eActions(
    val open: () -> Unit,
    val save: () -> Unit,
)

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
    e2eBridgeInstaller: ((OfficialRichTextEditorE2eActions) -> (() -> Unit))? = null,
) {
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorHtml by rememberSaveable { mutableStateOf(html) }
    val latestHtml by rememberUpdatedState(html)
    val latestOnHtmlChange by rememberUpdatedState(onHtmlChange)
    val latestOnEditorOpenChange by rememberUpdatedState(onEditorOpenChange)
    fun openEditor() {
        editorHtml = latestHtml
        editorOpen = true
        latestOnEditorOpenChange(true)
    }
    fun closeEditor() {
        editorOpen = false
        latestOnEditorOpenChange(false)
    }
    DisposableEffect(e2eBridgeInstaller) {
        val uninstall = e2eBridgeInstaller?.invoke(
            OfficialRichTextEditorE2eActions(
                open = ::openEditor,
                save = {
                    latestOnHtmlChange(editorHtml)
                    closeEditor()
                },
            ),
        )
        onDispose { uninstall?.invoke() }
    }
    OutlinedButton(
        onClick = ::openEditor,
        modifier = modifier,
    ) {
        actionIcon()
        Spacer(Modifier.size(8.dp))
        Text(title, fontWeight = FontWeight.ExtraBold)
    }
    if (editorOpen) {
        Dialog(
            onDismissRequest = ::closeEditor,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            OfficialLongTextEditorContent(
                title = title,
                onBack = ::closeEditor,
                backContentDescription = backContentDescription,
                saveLabel = saveLabel,
                onSave = {
                    latestOnHtmlChange(editorHtml)
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
