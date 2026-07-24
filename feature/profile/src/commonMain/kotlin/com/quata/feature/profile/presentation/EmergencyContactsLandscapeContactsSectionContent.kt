package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

/** Shared landscape contacts-column shell; platform hosts supply search and avatar-backed rows. */
@Composable
fun EmergencyContactsLandscapeContactsSectionContent(
    title: String,
    selectedCountLabel: String,
    searchInput: @Composable () -> Unit,
    users: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Column(modifier) {
        Text(title, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        searchInput()
        Spacer(Modifier.height(8.dp))
        Text(selectedCountLabel, color = template.colors.accent, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        users(Modifier.weight(1f))
    }
}
