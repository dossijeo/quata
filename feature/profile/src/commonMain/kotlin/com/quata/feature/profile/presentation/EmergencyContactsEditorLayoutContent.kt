package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared responsive shells for the SOS editor. The host retains focus/IME state, localized
 * strings and platform-backed avatar/input slots while the common presentation layer owns the
 * structural portrait and landscape layouts.
 */
@Composable
fun EmergencyContactsLandscapeEditorLayoutContent(
    topBar: @Composable () -> Unit,
    contacts: @Composable (Modifier) -> Unit,
    message: @Composable (Modifier) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        topBar()
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            contacts(Modifier.weight(1.08f))
            message(Modifier.weight(0.92f))
        }
    }
}

@Composable
fun EmergencyContactsPortraitEditorLayoutContent(
    body: @Composable (Modifier) -> Unit,
    saveAction: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        body(Modifier.weight(1f))
        saveAction()
    }
}
