package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.ui.components.QuataAvatarFallback
import com.quata.feature.profile.domain.ProfileViewerRepository
import platform.UIKit.UIViewController

/** Injectable iOS host for a selected community member. It is intentionally read-only. */
class IosMemberProfileHostDependencies(
    val profileId: String,
    val repository: ProfileViewerRepository,
    val onClose: () -> Unit,
    val backLabel: String = "Back",
    val unavailableMessage: String = "This profile is not available.",
)

fun QuataMemberProfileViewController(
    dependencies: IosMemberProfileHostDependencies,
): UIViewController = ComposeUIViewController {
    val viewModel = androidx.compose.runtime.remember(dependencies.profileId, dependencies.repository) {
        ProfileViewerViewModel(dependencies.profileId, dependencies.repository)
    }
    val state by viewModel.uiState.collectAsState()
    DisposableEffect(viewModel) { onDispose { viewModel.close() } }

    QuataTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                state.isLoading -> Text("Loading profile…")
                state.profile != null -> {
                    val profile = requireNotNull(state.profile)
                    QuataAvatarFallback(name = profile.displayName, stableId = profile.id)
                    Text(profile.displayName)
                    if (profile.neighborhood.isNotBlank()) Text(profile.neighborhood)
                    if (profile.isCurrentUser) Text("Your profile")
                }
                state.unavailable -> Text(dependencies.unavailableMessage)
                else -> Text(state.errorMessage ?: dependencies.unavailableMessage)
            }
            Button(onClick = dependencies.onClose) { Text(dependencies.backLabel) }
        }
    }
}
