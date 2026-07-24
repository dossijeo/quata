package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared text controls used by the advanced Official post editor. */
@Composable
fun OfficialAdvancedTextFieldsContent(
    title: String,
    summary: String,
    titleLabel: String,
    summaryLabel: String,
    onTitleChange: (String) -> Unit,
    onSummaryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(titleLabel) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = summary,
            onValueChange = onSummaryChange,
            label = { Text(summaryLabel) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
    }
}
