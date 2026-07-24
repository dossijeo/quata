package com.quata.feature.auth.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Platform-neutral authenticated shell. Hosts own session state and logout transport while the
 * shared layer keeps the content viewport and logout affordance consistent.
 */
@Composable
fun AuthSessionShellContent(
    isLoggingOut: Boolean,
    logoutLabel: String,
    loggingOutLabel: String,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        content()
        Button(
            enabled = !isLoggingOut,
            onClick = onLogout,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
        ) {
            Text(if (isLoggingOut) loggingOutLabel else logoutLabel)
        }
    }
}
