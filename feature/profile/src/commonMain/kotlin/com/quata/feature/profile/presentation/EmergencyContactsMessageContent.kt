package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.QuataPanel
import androidx.compose.material3.Text

/**
 * Shared portrait body for the SOS message tab. The platform host owns the text-input slot so
 * it can keep IME, focus and bring-into-view integration close to its window implementation.
 */
@Composable
fun EmergencyContactsMessageContent(
    scrollState: ScrollState,
    showHeader: Boolean,
    headerStrings: EmergencyContactsHeaderStrings,
    title: String,
    hint: String,
    onTabSelected: (EmergencyContactsTab) -> Unit,
    onDismiss: () -> Unit,
    messageInput: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        if (showHeader) {
            EmergencyContactsHeaderContent(
                selectedTab = EmergencyContactsTab.Message,
                strings = headerStrings,
                onTabSelected = onTabSelected,
                onDismiss = onDismiss
            )
            Spacer(Modifier.height(10.dp))
        }
        QuataPanel {
            Column {
                Text(title, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text(hint, color = template.colors.textSecondary)
                Spacer(Modifier.height(10.dp))
                messageInput()
            }
        }
    }
}
