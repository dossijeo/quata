package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.quata.core.platform.CameraCaptureRequest
import com.quata.core.platform.CameraCaptureService
import com.quata.core.platform.ContactPickerService
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.DocumentViewerFailureReason
import com.quata.core.platform.DocumentViewerState
import com.quata.core.platform.PlatformFile
import com.quata.core.ui.components.IosRemoteAvatar
import com.quata.core.ui.components.QuataDocumentViewerStatusContent
import com.quata.core.ui.components.CompactIcon
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
import com.quata.feature.postcomposer.presentation.IosAvatarImageEditor
import com.quata.feature.settings.presentation.AppearanceSettingsStrings
import com.quata.feature.settings.presentation.SettingsLegalDocumentsSectionContent
import com.quata.feature.settings.presentation.settingsLegalDocumentsStrings
import com.quata.core.ui.components.quataDocumentViewerStatusStrings
import kotlinx.coroutines.launch
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.UIKit.UIViewController

/** Complete iOS Cuenta host. SOS is a dialog inside this common surface, never the route substitute. */
class IosProfileHostDependencies(
    val repository: ProfileRepository,
    val onLogout: () -> Unit,
    val onDeactivateAccount: () -> Unit,
    val onDeleteAccountData: () -> Unit,
    val filePicker: FilePickerService,
    val cameraCapture: CameraCaptureService,
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
                avatarActions = { onAvatarChanged -> IosProfileAvatarActions(dependencies, onAvatarChanged) },
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

@Composable
private fun IosProfileAvatarActions(
    dependencies: IosProfileHostDependencies,
    onAvatarChanged: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val filePicker = remember(dependencies.filePicker) {
        IosProfileAvatarEvidenceFilePicker.wrapIfRequested(dependencies.filePicker)
    }
    var menuOpen by remember { mutableStateOf(false) }
    var editorFile by remember { mutableStateOf<PlatformFile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun openEditor(result: PlatformResult<List<PlatformFile>>) {
        when (result) {
            is PlatformResult.Success -> {
                editorFile = result.value.firstOrNull()
                error = if (editorFile == null) "No photo selected." else null
            }
            PlatformResult.Cancelled -> Unit
            PlatformResult.Unsupported -> error = "Photo source is not available on this device."
            is PlatformResult.Failure -> error = "Could not select the photo."
        }
    }

    suspend fun openCameraEditor(result: PlatformResult<PlatformFile>) {
        when (result) {
            is PlatformResult.Success -> {
                editorFile = result.value
                error = null
            }
            PlatformResult.Cancelled -> Unit
            PlatformResult.Unsupported -> error = "Camera is not available on this device."
            is PlatformResult.Failure -> error = "Could not capture the photo."
        }
    }

    Column {
        OutlinedButton(
            onClick = { menuOpen = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ProfileAvatarChangeTestTag)
                .semantics { contentDescription = ProfileAvatarChangeTestTag },
        ) {
            CompactIcon(Icons.Filled.PhotoCamera, null)
            Spacer(Modifier.width(4.dp))
            Text("Change photo")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Choose from gallery") },
                modifier = Modifier
                    .testTag(ProfileAvatarGalleryTestTag)
                    .semantics { contentDescription = ProfileAvatarGalleryTestTag },
                leadingIcon = { CompactIcon(Icons.Filled.PermMedia, null) },
                onClick = {
                    menuOpen = false
                    scope.launch {
                        openEditor(
                            filePicker.pick(
                                FilePickerRequest(listOf("image/*"), allowMultiple = false, source = FilePickerSource.Gallery),
                            ),
                        )
                    }
                },
            )
            DropdownMenuItem(
                text = { Text("Take photo") },
                modifier = Modifier
                    .testTag(ProfileAvatarCameraTestTag)
                    .semantics { contentDescription = ProfileAvatarCameraTestTag },
                leadingIcon = { CompactIcon(Icons.Filled.PhotoCamera, null) },
                onClick = {
                    menuOpen = false
                    scope.launch {
                        openCameraEditor(dependencies.cameraCapture.capturePhoto(CameraCaptureRequest("quata-avatar.jpg")))
                    }
                },
            )
        }
        error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
    }
    editorFile?.let { file ->
        IosAvatarImageEditor(
            source = file,
            onDismiss = { editorFile = null },
            onEdited = { edited ->
                editorFile = null
                onAvatarChanged(edited.reference)
            },
        )
    }
}

private const val AccountAvatarPickerFixtureOptIn = "I_ACCEPT_IOS_ACCOUNT_AVATAR_PICKER_FIXTURE"

private class IosProfileAvatarEvidenceFilePicker(
    private val delegate: FilePickerService,
) : FilePickerService {
    override suspend fun pickFiles(
        acceptedMimeTypes: List<String>,
        allowMultiple: Boolean,
    ): PlatformResult<List<PlatformFile>> =
        delegate.pickFiles(acceptedMimeTypes, allowMultiple)

    override suspend fun pick(request: FilePickerRequest): PlatformResult<List<PlatformFile>> {
        iosProfileAvatarEvidencePickedFile(request.source)?.let { return PlatformResult.Success(listOf(it)) }
        return delegate.pick(request)
    }

    companion object {
        fun wrapIfRequested(delegate: FilePickerService): FilePickerService =
            if (iosProfileAvatarEvidenceFixtureOptedIn()) IosProfileAvatarEvidenceFilePicker(delegate) else delegate
    }
}

private fun iosProfileAvatarEvidencePickedFile(source: FilePickerSource): PlatformFile? {
    if (source != FilePickerSource.Gallery) return null
    val environment = NSProcessInfo.processInfo.environment
    if (!iosProfileAvatarEvidenceFixtureOptedIn(environment)) return null
    val path = environment.iosProfileAvatarFixtureValue("QUATA_IOS_ACCOUNT_AVATAR_PICKER_PATH")
        ?.takeIf(String::isNotBlank)
        ?: return null
    val reference = if (path.startsWith("file://")) path else NSURL.fileURLWithPath(path).absoluteString ?: path
    val name = environment.iosProfileAvatarFixtureValue("QUATA_IOS_ACCOUNT_AVATAR_PICKER_NAME")
        ?: path.substringAfterLast('/').ifBlank { "account-avatar-fixture.png" }
    val mimeType = environment.iosProfileAvatarFixtureValue("QUATA_IOS_ACCOUNT_AVATAR_PICKER_MIME")
        ?: "image/png"
    return PlatformFile(reference = reference, displayName = name, mimeType = mimeType)
}

private fun iosProfileAvatarEvidenceFixtureOptedIn(
    environment: Map<Any?, *> = NSProcessInfo.processInfo.environment,
): Boolean =
    environment.iosProfileAvatarFixtureValue("QUATA_IOS_ACCOUNT_AVATAR_PICKER_FIXTURE_OPT_IN") == AccountAvatarPickerFixtureOptIn

private fun Map<Any?, *>.iosProfileAvatarFixtureValue(key: String): String? =
    this[key]?.toString()?.takeIf(String::isNotBlank)

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
