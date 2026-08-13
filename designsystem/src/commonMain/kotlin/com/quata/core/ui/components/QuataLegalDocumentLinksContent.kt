package com.quata.core.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.quata.core.localization.QuataLanguage
import com.quata.core.moderation.LegalDocument
import com.quata.core.moderation.label
import com.quata.core.moderation.legalDocumentLabels

const val QuataLegalDocumentLinkTestTagPrefix = "legal-document-link-"

@Composable
fun QuataLegalDocumentLinksContent(
    language: QuataLanguage,
    documents: List<LegalDocument> = listOf(LegalDocument.Privacy, LegalDocument.ChildSafety),
    onOpenDocument: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = legalDocumentLabels(language)
    documents.forEach { document ->
        TextButton(
            onClick = { onOpenDocument(document) },
            modifier = modifier
                .fillMaxWidth()
                .testTag(QuataLegalDocumentLinkTestTagPrefix + document.name.lowercase()),
        ) {
            Text(document.label(labels), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ColumnScope.QuataLegalDocumentLinksColumnContent(
    language: QuataLanguage,
    documents: List<LegalDocument> = listOf(LegalDocument.Privacy, LegalDocument.ChildSafety),
    onOpenDocument: (LegalDocument) -> Unit,
) {
    QuataLegalDocumentLinksContent(
        language = language,
        documents = documents,
        onOpenDocument = onOpenDocument,
    )
}
