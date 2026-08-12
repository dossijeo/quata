@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.WebElementView
import com.quata.core.localization.QuataLanguage
import com.quata.core.moderation.LegalDocument
import com.quata.core.moderation.label
import com.quata.core.moderation.legalDocumentLabels
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WebNativeLegalDocumentLinksContent(
    language: QuataLanguage,
    documents: List<LegalDocument> = listOf(LegalDocument.Privacy, LegalDocument.ChildSafety),
    onOpenDocument: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = legalDocumentLabels(language)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        documents.forEach { legalDocument ->
            val label = legalDocument.label(labels)
            WebElementView(
                factory = {
                    (document.createElement("button") as HTMLButtonElement).apply {
                        type = "button"
                        style.width = "100%"
                        style.height = "100%"
                        style.border = "0"
                        style.background = "transparent"
                        style.color = "#ff7518"
                        style.font = "500 14px system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
                        style.textAlign = "left"
                        style.padding = "0 12px"
                        style.cursor = "pointer"
                        style.setProperty("pointer-events", "auto")
                    }
                },
                update = { button ->
                    button.textContent = label
                    button.setAttribute("aria-label", label)
                    button.onclick = { onOpenDocument(legalDocument); null }
                },
                onRelease = { button -> button.onclick = null },
                modifier = Modifier.fillMaxWidth().height(40.dp),
            )
        }
    }
}
