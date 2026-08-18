package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.platform.ContactPickerService
import com.quata.core.platform.PermissionService
import com.quata.core.platform.PermissionStatus
import com.quata.core.platform.PlatformContact
import com.quata.core.platform.PlatformResult
import com.quata.feature.profile.domain.EmergencyContactCandidate
import platform.UIKit.UIViewController

/**
 * iOS composition boundary for the portable SOS editor.
 *
 * A picked address-book contact is not made into an emergency-contact ID here: those IDs belong
 * to Quata profiles and must be resolved by the authenticated host before the shared candidate
 * list changes. This prevents device-local identifiers or phone numbers being persisted as IDs.
 */
class IosProfileSosHostDependencies(
    val viewModel: ProfileViewModel,
    val strings: EmergencyContactsEditorStrings,
    val isLandscape: Boolean,
    val isImeVisible: Boolean,
    val onAvatarAction: () -> Unit,
    /** Localized host labels for the ContactsUI picker and authorization prompt. */
    val importContactsLabel: String,
    val requestPermissionsLabel: String,
    val contacts: ContactPickerService,
    val permissions: PermissionService,
    /** Resolves explicitly selected native contacts to actual Quata profile candidates. */
    val onContactsPicked: (List<PlatformContact>) -> Unit,
    /** Lets the launcher surface cancellation, unsupported capability or picker failures. */
    val onContactPickerResult: (PlatformResult<List<PlatformContact>>) -> Unit,
    /** Lets the launcher surface the result of the explicit Contacts authorization request. */
    val onContactsPermissionResult: (PermissionStatus) -> Unit,
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
            errorMessage = state.errorMessage,
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
                contactActions = {
                    EmergencyContactsContactActionsContent(
                        strings = dependencies.strings,
                        contacts = dependencies.contacts,
                        permissions = dependencies.permissions,
                        modifier = Modifier.fillMaxWidth(),
                        onContactsPicked = dependencies.onContactsPicked,
                        onContactPickerResult = dependencies.onContactPickerResult,
                        onContactsPermissionResult = dependencies.onContactsPermissionResult,
                    )
                },
            ),
        )
    }
}
