package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.QuataPanel

/** Shared message panel used by the landscape SOS editor; focus and IME stay in the input slot. */
@Composable
fun EmergencyContactsLandscapeMessagePanelContent(
    title: String,
    hint: String,
    errorMessage: String?,
    input: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    QuataPanel(contentPadding = PaddingValues(12.dp), modifier = modifier) {
        Column {
            Text(title, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text(
                hint,
                color = template.colors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics {
                        testTag = ProfileSosErrorTestTag
                        contentDescription = ProfileSosErrorTestTag
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            input()
        }
    }
}
