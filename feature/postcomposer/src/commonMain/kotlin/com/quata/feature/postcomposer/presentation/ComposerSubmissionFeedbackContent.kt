package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

/** Shared submission feedback placed below a composer form. */
@Composable
fun ColumnScope.ComposerSubmissionFeedbackContent(
    errorMessage: String?,
    successMessage: String?,
) {
    errorMessage?.let { message ->
        Spacer(Modifier.height(14.dp))
        Text(message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    }
    successMessage?.let { message ->
        Spacer(Modifier.height(14.dp))
        Text(message, color = quataTheme().colors.accent, fontWeight = FontWeight.Bold)
    }
}
