package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

const val ComposerFeedbackErrorTestTag = "composer-feedback-error"
const val ComposerFeedbackSuccessTestTag = "composer-feedback-success"

/** Shared submission feedback placed below a composer form. */
@Composable
fun ColumnScope.ComposerSubmissionFeedbackContent(
    errorMessage: String?,
    successMessage: String?,
) {
    errorMessage?.let { message ->
        Spacer(Modifier.height(14.dp))
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .testTag(ComposerFeedbackErrorTestTag)
                .semantics { contentDescription = ComposerFeedbackErrorTestTag },
        )
    }
    successMessage?.let { message ->
        Spacer(Modifier.height(14.dp))
        Text(
            message,
            color = quataTheme().colors.accent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .testTag(ComposerFeedbackSuccessTestTag)
                .semantics { contentDescription = ComposerFeedbackSuccessTestTag },
        )
    }
}
