package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.ui.components.IosRemoteAvatar
import com.quata.core.ui.components.QuataAvatarFallback
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.settings.presentation.AppearanceSettingsStrings
import platform.UIKit.UIViewController

/** Complete iOS Cuenta host. SOS is a dialog inside this common surface, never the route substitute. */
class IosProfileHostDependencies(
    val repository: ProfileRepository,
    val onLogout: () -> Unit,
    val onDeactivateAccount: () -> Unit,
    val onDeleteAccountData: () -> Unit,
    val touchFlowEnabled: Boolean = false,
    val onTouchFlowEnabledChange: (Boolean) -> Unit = {},
    val themeMode: QuataThemeMode = QuataThemeMode.System,
    val onThemeModeChange: (QuataThemeMode) -> Unit = {},
)

fun QuataProfileViewController(dependencies: IosProfileHostDependencies): UIViewController = ComposeUIViewController {
    QuataTheme {
        val isLandscape = rememberQuataWindowLayoutInfo().isLandscape
        ProfileScreenHost(
            repository = dependencies.repository,
            strings = IosProfileScreenStrings,
            touchFlowEnabled = dependencies.touchFlowEnabled,
            onTouchFlowEnabledChange = dependencies.onTouchFlowEnabledChange,
            themeMode = dependencies.themeMode,
            onThemeModeChange = dependencies.onThemeModeChange,
            onLogout = dependencies.onLogout,
            onDeactivateAccount = dependencies.onDeactivateAccount,
            onDeleteAccountData = dependencies.onDeleteAccountData,
            slots = ProfileScreenSlots(
                isLandscapeLayout = { isLandscape },
                avatar = { name, avatarUrl -> IosRemoteAvatar(name, name, avatarUrl, Modifier.size(56.dp)) },
                avatarActions = { _ -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Change photo (upload pending)") } },
                emergencyContactRow = { contact: EmergencyContactCandidate, selected, toggle ->
                    EmergencyUserRowContent(
                        user = contact,
                        selected = selected,
                        addLabel = "Add",
                        removeLabel = "Remove",
                        avatar = { QuataAvatarFallback(contact.displayName, contact.id) },
                        onToggle = toggle,
                    )
                },
            ),
        )
    }
}

private val IosProfileScreenStrings = ProfileScreenStrings(
    loading = "Loading profile…", myData = "My data", management = "Account management",
    managementDescription = "Manage sensitive options for your account.",
    configureEmergency = "Configure emergency contacts", saveChanges = "Save changes", saving = "Saving…",
    logout = "Log out", name = "Name", neighborhood = "Neighborhood", phone = "Phone",
    secretQuestion = "Secret question", newSecretAnswer = "New secret answer",
    back = "Back", deactivate = "Deactivate account", deleteData = "Request data deletion",
    dangerConfirmation = "This action needs a final confirmation and is not performed during QA.", confirm = "Continue", cancel = "Cancel",
    appearance = AppearanceSettingsStrings("Touch Flow", "Theme", "System", "Dark", "Light"),
    emergency = IosEmergencyContactsEditorStrings,
)
