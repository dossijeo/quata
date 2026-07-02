package com.quata.core.ui.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.QuataScreen

@Composable
fun RichTextEditorQaScreen(
    padding: PaddingValues,
    onBack: () -> Unit
) {
    val template = quataTheme()
    var resetToken by rememberSaveable { mutableStateOf(0) }
    var html by rememberSaveable(resetToken) { mutableStateOf(RichTextEditorQaInitialHtml) }
    val editorSeed = remember(resetToken) { html }

    QuataScreen(padding = padding) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(template.colors.background)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                CompactIconButton(onClick = onBack) {
                    CompactIcon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = template.colors.textPrimary
                    )
                }
                Text(
                    text = "Editor QA",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 23.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { resetToken += 1 }) {
                    Text("Reset")
                }
            }
            Spacer(Modifier.height(10.dp))
            QuataRichTextEditorBox(
                initialHtml = editorSeed,
                placeholder = "Escribe una publicacion oficial...",
                onHtmlChange = { html = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .border(1.dp, template.colors.divider.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("HTML generado", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    Text(html, color = template.colors.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

private val RichTextEditorQaInitialHtml = """
    <h1>Try These</h1>
    <ul class="todo">
        <li>Tap here and start typing</li>
        <li checked="true">Select text, then use the toolbar to make it bold or italic</li>
        <li>Hold and drag any block to reorder it</li>
        <li>Long-press a block to select it, then tap others to multi-select and delete</li>
        <li>Press Enter at the end of this line to create a new block</li>
    </ul>
    <ol><li>Hello</li></ol>
    <ul class="todo"><li checked="true">Type / to open slash commands (or just use / icon from the toolbar)</li></ul>
    <h2>Rich Text</h2>
    <p>Mix <b>bold</b>, <i>italic</i>, <u>underline</u>, <s>strikethrough</s>, <code>code</code>, <mark>highlights</mark>, and <a href="https://egquata.com">links</a> in any block.</p>
    <h2>Block Types</h2>
    <ul><li>Bullet lists for unordered content</li><li>Nest ideas without numbering</li></ul>
    <ol><li>Numbered lists auto-increment</li><li>Delete or reorder and they renumber</li><li>Try deleting the second item above</li></ol>
    <blockquote>The best way to predict the future is to invent it. - Alan Kay</blockquote>
    <hr>
    <p>Your changes are saved automatically. Hit Reset in the toolbar to start fresh.</p>
""".trimIndent()

private val RichTextEditorQaLegacyInitialHtml = """
    <h1>Try These</h1>
    <ul class="todo">
        <li>Tap here and start typing</li>
        <li checked="true">Select text, then use the toolbar to make it bold or italic</li>
        <li>Hold and drag any block to reorder it</li>
        <li>Long-press a block to select it, then tap others to multi-select and delete</li>
    </ul>
    <h2>Rich Text</h2>
    <p>Mix <b>bold</b>, <i>italic</i>, <u>underline</u>, <s>strikethrough</s>, <code>code</code>, <mark>highlights</mark>, and <a href="https://egquata.com">links</a> in any block.</p>
    <h2>Block Types</h2>
    <ul><li>Bullet lists for unordered content</li><li>Nest ideas without numbering</li></ul>
    <ol><li>Numbered lists auto-increment</li><li>Delete or reorder and they renumber</li></ol>
    <aside>La informacion destacada debe verse bien en claro y oscuro.</aside>
    <blockquote>The best way to predict the future is to invent it. - Alan Kay</blockquote>
    <pre><code>fun publish() = "Qüata"</code></pre>
""".trimIndent()
