package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.localization.QuataLanguage
import com.quata.core.moderation.LegalDocument
import com.quata.core.moderation.iosLegalDocumentFile
import com.quata.core.moderation.iosLegalDocumentPlaceholderFile
import com.quata.core.platform.ContactPickerService
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.DocumentViewerFailureReason
import com.quata.core.platform.DocumentViewerState
import com.quata.core.ui.components.IosRemoteAvatar
import com.quata.core.ui.components.QuataDocumentViewerStatusContent
import com.quata.core.ui.components.QuataAvatarFallback
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.FilePickerSource
import com.quata.core.platform.PermissionService
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.documentViewerOpeningState
import com.quata.core.platform.openWithViewerState
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.settings.presentation.AppearanceSettingsStrings
import com.quata.feature.settings.presentation.SettingsLegalDocumentsSectionContent
import com.quata.feature.settings.presentation.settingsLegalDocumentsStrings
import com.quata.core.ui.components.quataDocumentViewerStatusStrings
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/** Complete iOS Cuenta host. SOS is a dialog inside this common surface, never the route substitute. */
class IosProfileHostDependencies(
    val repository: ProfileRepository,
    val onLogout: () -> Unit,
    val onDeactivateAccount: () -> Unit,
    val onDeleteAccountData: () -> Unit,
    val filePicker: FilePickerService,
    val contacts: ContactPickerService,
    val permissions: PermissionService,
    val touchFlowEnabled: Boolean,
    val onTouchFlowEnabledChange: (Boolean) -> Unit,
    val themeMode: QuataThemeMode,
    val onThemeModeChange: (QuataThemeMode) -> Unit,
    val languageCode: String,
    val documentOpener: DocumentOpenService?,
    val openLegalDocument: (LegalDocument, DocumentOpenService) -> Unit,
)

fun QuataProfileViewController(dependencies: IosProfileHostDependencies): UIViewController = ComposeUIViewController {
    var touchFlowEnabled by remember { mutableStateOf(dependencies.touchFlowEnabled) }
    var themeMode by remember { mutableStateOf(dependencies.themeMode) }
    QuataTheme(mode = themeMode) {
        val isLandscape = rememberQuataWindowLayoutInfo().isLandscape
        val scope = rememberCoroutineScope()
        val language = dependencies.languageCode.toQuataLanguage()
        var documentViewerState by remember { mutableStateOf<DocumentViewerState?>(null) }
        ProfileScreenHost(
            repository = dependencies.repository,
            strings = IosProfileScreenStrings,
            touchFlowEnabled = touchFlowEnabled,
            onTouchFlowEnabledChange = { enabled ->
                touchFlowEnabled = enabled
                dependencies.onTouchFlowEnabledChange(enabled)
            },
            themeMode = themeMode,
            onThemeModeChange = { mode ->
                themeMode = mode
                dependencies.onThemeModeChange(mode)
            },
            onLogout = dependencies.onLogout,
            onDeactivateAccount = dependencies.onDeactivateAccount,
            onDeleteAccountData = dependencies.onDeleteAccountData,
            slots = ProfileScreenSlots(
                isLandscapeLayout = { isLandscape },
                avatar = { name, avatarUrl -> IosRemoteAvatar(name, name, avatarUrl, Modifier.size(56.dp)) },
                avatarActions = { onAvatarChanged ->
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                when (val result = dependencies.filePicker.pick(
                                    FilePickerRequest(listOf("image/*"), allowMultiple = false, source = FilePickerSource.Gallery),
                                )) {
                                    is PlatformResult.Success -> result.value.firstOrNull()?.reference?.let(onAvatarChanged)
                                    PlatformResult.Cancelled, PlatformResult.Unsupported, is PlatformResult.Failure -> Unit
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Change photo") }
                },
                emergencyContactRow = { contact: EmergencyContactCandidate, selected, toggle ->
                    EmergencyUserRowContent(
                        user = contact,
                        selected = selected,
                        addLabel = "Add",
                        removeLabel = "Remove",
                        avatar = { IosRemoteAvatar(contact.displayName, contact.id, contact.avatarUrl, Modifier.size(46.dp)) },
                        onToggle = toggle,
                    )
                },
                emergencyContactActions = {
                    EmergencyContactsContactActionsContent(
                        strings = IosProfileScreenStrings.emergency,
                        contacts = dependencies.contacts,
                        permissions = dependencies.permissions,
                    )
                },
                legalDocuments = {
                    dependencies.documentOpener?.let { opener ->
                        SettingsLegalDocumentsSectionContent(
                            language = language,
                            strings = settingsLegalDocumentsStrings(language),
                            onOpenDocument = { document ->
                                scope.launch {
                                    val file = iosLegalDocumentFile(document, language)
                                    if (file == null) {
                                        val placeholder = iosLegalDocumentPlaceholderFile(document, language)
                                        documentViewerState = DocumentViewerState.Failed(
                                            file = placeholder,
                                            descriptor = documentViewerOpeningState(placeholder).descriptor,
                                            reason = DocumentViewerFailureReason.PlatformUnsupported,
                                        )
                                    } else {
                                        documentViewerState = documentViewerOpeningState(file)
                                        documentViewerState = opener.openWithViewerState(file).completed
                                    }
                                }
                            },
                        )
                    }
                },
            ),
        )
        QuataDocumentViewerStatusContent(
            state = documentViewerState,
            strings = quataDocumentViewerStatusStrings(language),
            onDismiss = { documentViewerState = null },
        )
    }
}

private fun String.toQuataLanguage(): QuataLanguage = when {
    lowercase().startsWith("es") -> QuataLanguage.Spanish
    lowercase().startsWith("fr") -> QuataLanguage.French
    else -> QuataLanguage.English
}

private val IosProfileScreenStrings = ProfileScreenStrings(
    loading = "Loading profile…", myData = "My data", management = "Account management",
    managementDescription = "Manage sensitive options for your account.",
    configureEmergency = "Configure emergency contacts", saveChanges = "Save changes", saving = "Saving…",
    logout = "Log out", name = "Name", neighborhood = "Neighborhood", phone = "Phone", newPassword = "New password",
    secretQuestion = "Secret question", newSecretAnswer = "New secret answer",
    back = "Back", deactivate = "Deactivate account", deleteData = "Request data deletion",
    dangerConfirmation = "This action needs a final confirmation and is not performed during QA.", confirm = "Continue", cancel = "Cancel",
    appearance = AppearanceSettingsStrings("Touch Flow", "Theme", "System", "Dark", "Light"),
    emergency = IosEmergencyContactsEditorStrings,
    passwordUnavailable = "Change your password from Forgot my password until authenticated password updates are available.",
    loadingError = "Could not load your profile.",
    retry = "Retry",
)
