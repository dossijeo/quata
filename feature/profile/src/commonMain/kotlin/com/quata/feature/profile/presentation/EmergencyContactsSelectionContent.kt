package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.feature.profile.domain.EmergencyContactCandidate

/**
 * Portable contacts-tab body for the SOS editor. Platform hosts provide the avatar-backed
 * row, while filtering, list structure and the common header stay shared.
 */
@Composable
fun EmergencyContactsSelectionContent(
    candidates: List<EmergencyContactCandidate>,
    selectedIds: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    showHeader: Boolean,
    headerStrings: EmergencyContactsHeaderStrings,
    searchPlaceholder: String,
    selectedCountLabel: String,
    networkUsersLabel: String,
    onTabSelected: (EmergencyContactsTab) -> Unit,
    onDismiss: () -> Unit,
    userRow: @Composable (EmergencyContactCandidate, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    val visibleUsers = filterEmergencyContactCandidates(candidates, selectedIds, query)
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            if (showHeader) {
                EmergencyContactsHeaderContent(
                    selectedTab = EmergencyContactsTab.Contacts,
                    strings = headerStrings,
                    onTabSelected = onTabSelected,
                    onDismiss = onDismiss
                )
                Spacer(Modifier.height(10.dp))
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(searchPlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                selectedCountLabel,
                color = template.colors.accent,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))
            Text(networkUsersLabel, fontWeight = FontWeight.ExtraBold)
        }
        items(visibleUsers, key = { it.id }) { user ->
            userRow(user, user.id in selectedIds)
        }
    }
}
