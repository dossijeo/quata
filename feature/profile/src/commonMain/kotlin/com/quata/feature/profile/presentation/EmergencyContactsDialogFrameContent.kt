package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.ui.components.QuataScreen

/**
 * Shared full-screen frame for the SOS contact editor. Platform hosts keep their IME, contacts
 * and permission-bound content in the slot while sharing viewport padding and responsive chrome.
 */
@Composable
fun EmergencyContactsDialogFrameContent(
    layoutPadding: PaddingValues,
    isLandscapeLayout: Boolean,
    content: @Composable () -> Unit
) {
    QuataScreen(padding = layoutPadding) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (isLandscapeLayout) 16.dp else 18.dp,
                    end = if (isLandscapeLayout) 16.dp else 18.dp
                )
                .padding(
                    top = if (isLandscapeLayout) 10.dp else 14.dp,
                    bottom = if (isLandscapeLayout) 10.dp else 0.dp
                )
        ) {
            content()
        }
    }
}
