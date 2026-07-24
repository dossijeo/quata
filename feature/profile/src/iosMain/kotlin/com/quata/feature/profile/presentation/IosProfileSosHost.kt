package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.profile.domain.EmergencyContactCandidate
import platform.UIKit.UIViewController

/** iOS composition boundary: repository, contacts, permissions and avatar handling stay in Swift. */
class IosProfileSosHostDependencies(
    val viewModel: ProfileViewModel,
    val strings: EmergencyContactsEditorStrings,
    val isLandscape: Boolean,
    val isImeVisible: Boolean,
    val onAvatarAction: () -> Unit,
    val onRequestContacts: () -> Unit,
    val onRequestPermissions: () -> Unit,
    val onClose: () -> Unit,
)

fun QuataProfileSosViewController(dependencies: IosProfileSosHostDependencies): UIViewController = ComposeUIViewController {
    val state by dependencies.viewModel.uiState.collectAsState()
    val profile = state.profile
    QuataTheme {
        EmergencyContactsDialogContent(
            layoutPadding = PaddingValues(),
            isLandscapeLayout = dependencies.isLandscape,
            isImeVisible = dependencies.isImeVisible,
            candidates = state.emergencyCandidates,
            selectedIds = profile?.emergencyContactIds.orEmpty(),
            message = profile?.emergencyMessage.orEmpty(),
            isSaving = state.isSaving,
            strings = dependencies.strings,
            onMessageChange = { dependencies.viewModel.onEvent(ProfileUiEvent.EmergencyMessageChanged(it)) },
            onToggleContact = { contact -> dependencies.viewModel.onEvent(ProfileUiEvent.EmergencyContactToggled(contact.id)) },
            onDismiss = dependencies.onClose,
            onSave = { dependencies.viewModel.onEvent(ProfileUiEvent.SaveEmergencySettings) },
            slots = EmergencyContactsDialogSlots(
                contactRow = { contact: EmergencyContactCandidate, selected, toggle ->
                    Text(
                        text = "${if (selected) "✓ " else ""}${contact.displayName}",
                        modifier = Modifier.clickable(onClick = toggle),
                    )
                },
                messageInput = { modifier: Modifier, value, onValueChange, _, _ ->
                    OutlinedTextField(value, onValueChange, modifier = modifier)
                },
            ),
        )
    }
}
