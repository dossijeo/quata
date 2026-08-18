package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

/** Shared landscape contacts-column shell; platform hosts supply search and avatar-backed rows. */
@Composable
fun EmergencyContactsLandscapeContactsSectionContent(
    title: String,
    selectedCountLabel: String,
    errorMessage: String?,
    searchInput: @Composable () -> Unit,
    users: @Composable (Modifier) -> Unit,
    contactActions: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Column(modifier) {
        Text(title, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        searchInput()
        Spacer(Modifier.height(8.dp))
        Text(selectedCountLabel, color = template.colors.accent, fontWeight = FontWeight.Bold)
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
        contactActions?.let { actions ->
            Spacer(Modifier.height(8.dp))
            actions()
        }
        Spacer(Modifier.height(8.dp))
        users(Modifier.weight(1f))
    }
}
