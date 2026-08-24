package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.QuataSavingButton
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.settings.presentation.AppearanceSettingsSectionContent
import com.quata.feature.settings.presentation.AppearanceSettingsStrings

const val ProfileAvatarChangeTestTag = "profile.avatar.change"
const val ProfileAvatarGalleryTestTag = "profile.avatar.gallery"
const val ProfileAvatarCameraTestTag = "profile.avatar.camera"
const val ProfileSaveChangesTestTag = "profile.save"

/** The only product account surface. Platform hosts supply native integrations through [ProfileScreenSlots]. */
@Composable
fun ProfileScreenHost(
    repository: ProfileRepository,
    strings: ProfileScreenStrings,
    touchFlowEnabled: Boolean,
    onTouchFlowEnabledChange: (Boolean) -> Unit,
    themeMode: QuataThemeMode,
    onThemeModeChange: (QuataThemeMode) -> Unit,
    onLogout: () -> Unit,
    onDeactivateAccount: () -> Unit,
    onDeleteAccountData: () -> Unit,
    slots: ProfileScreenSlots,
    refreshKey: Long = 0L,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(repository) { ProfileViewModel(repository) }
    val state by viewModel.uiState.collectAsState()
    var page by rememberSaveable { mutableStateOf(ProfileAccountPage.Overview) }
    var showSos by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf<ProfileDangerousAction?>(null) }
    DisposableEffect(viewModel) { onDispose { viewModel.close() } }
    LaunchedEffect(refreshKey) { if (refreshKey != 0L) viewModel.onEvent(ProfileUiEvent.Refresh) }
    LaunchedEffect(state.successMessage) {
        if (state.successMessageTriggersProfileSaved) slots.onProfileSaved()
    }
    LaunchedEffect(state.emergencySettingsSaved) {
        if (state.emergencySettingsSaved) {
            showSos = false
            viewModel.onEvent(ProfileUiEvent.ClearMessages)
        }
    }
    SideEffect {
        slots.backDispatcher?.setHandler(if (showSos || page != ProfileAccountPage.Overview) {
            {
            if (showSos) {
                showSos = false
                viewModel.onEvent(ProfileUiEvent.ClearMessages)
            } else if (page != ProfileAccountPage.Overview) {
                page = ProfileAccountPage.Overview
            }
            }
        } else null)
    }
    DisposableEffect(slots.backDispatcher) { onDispose { slots.backDispatcher?.clearHandler() } }

    Box(modifier.fillMaxSize()) {
        val profile = state.profile
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(strings.loading)
            }
        } else if (profile == null) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(strings.loadingError)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { viewModel.onEvent(ProfileUiEvent.Refresh) }) {
                    Text(strings.retry)
                }
            }
        } else {
            ProfilePageLayoutContent(
                isLandscapeLayout = slots.isLandscapeLayout(),
                scrollState = rememberScrollState(),
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                content = {
                when (page) {
                    ProfileAccountPage.Overview -> ProfileOverviewContent(
                        state = state,
                        strings = strings,
                        touchFlowEnabled = touchFlowEnabled,
                        onTouchFlowEnabledChange = onTouchFlowEnabledChange,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        slots = slots,
                        onDetails = { page = ProfileAccountPage.Details },
                        onManagement = { page = ProfileAccountPage.Management },
                        onSos = { showSos = true },
                        onSave = { viewModel.onEvent(ProfileUiEvent.Save) },
                        onAvatarChanged = { viewModel.onEvent(ProfileUiEvent.AvatarChanged(it)) },
                        onLogout = onLogout,
                    )
                    ProfileAccountPage.Details -> ProfileDetailsContent(
                        state = state,
                        strings = strings,
                        onBack = { page = ProfileAccountPage.Overview },
                        onEvent = viewModel::onEvent,
                    )
                    ProfileAccountPage.Management -> ProfileManagementContent(
                        strings = strings,
                        onBack = { page = ProfileAccountPage.Overview },
                        onDeactivate = { confirmation = ProfileDangerousAction.Deactivate },
                        onDelete = { confirmation = ProfileDangerousAction.DeleteData },
                    )
                }
                state.errorMessage?.let { Text(it, color = Color.Red) }
                state.successMessage?.let { Text(it, color = quataTheme().colors.textSecondary) }
                },
            )
        }
        slots.sosE2eBridge?.invoke(
            {
                showSos = true
            },
            {
                showSos = false
                viewModel.onEvent(ProfileUiEvent.ClearMessages)
            },
            { count ->
                val desiredIds = state.emergencyCandidates.take(count).map { it.id }.toSet()
                val selectedIds = profile?.emergencyContactIds.orEmpty().toSet()
                state.emergencyCandidates
                    .filter { it.id in selectedIds && it.id !in desiredIds }
                    .forEach { viewModel.onEvent(ProfileUiEvent.EmergencyContactToggled(it.id)) }
                state.emergencyCandidates
                    .filter { it.id !in selectedIds && it.id in desiredIds }
                    .forEach { viewModel.onEvent(ProfileUiEvent.EmergencyContactToggled(it.id)) }
            },
        )
        slots.accountE2eBridge?.invoke {
            viewModel.onEvent(ProfileUiEvent.Save)
        }
        SideEffect {
            if (showSos) {
                slots.onSosSelectionChanged(
                    profile?.emergencyContactIds.orEmpty().distinct().size,
                    state.emergencyCandidates.size,
                )
                slots.onSosErrorChanged(state.errorMessage)
            } else {
                slots.onSosTabChanged(null)
                slots.onSosSelectionChanged(0, state.emergencyCandidates.size)
                slots.onSosErrorChanged(null)
            }
        }
        if (showSos && profile != null) {
            EmergencyContactsDialogContent(
                layoutPadding = PaddingValues(),
                isLandscapeLayout = slots.isLandscapeLayout(),
                isImeVisible = slots.isImeVisible(),
                candidates = state.emergencyCandidates,
                selectedIds = profile.emergencyContactIds,
                message = profile.emergencyMessage,
                isSaving = state.isSaving,
                errorMessage = state.errorMessage,
                strings = strings.emergency,
                onMessageChange = { viewModel.onEvent(ProfileUiEvent.EmergencyMessageChanged(it)) },
                onToggleContact = { viewModel.onEvent(ProfileUiEvent.EmergencyContactToggled(it.id)) },
                onDismiss = { showSos = false; viewModel.onEvent(ProfileUiEvent.ClearMessages) },
                onSave = { viewModel.onEvent(ProfileUiEvent.SaveEmergencySettings) },
                slots = EmergencyContactsDialogSlots(
                    contactRow = { contact, selected, toggle -> slots.emergencyContactRow(contact, selected, toggle) },
                    messageInput = { fieldModifier, value, change, minLines, maxLines ->
                        OutlinedTextField(value = value, onValueChange = change, modifier = fieldModifier, minLines = minLines, maxLines = maxLines ?: Int.MAX_VALUE)
                    },
                    contactActions = slots.emergencyContactActions,
                    onTabChanged = slots.onSosTabChanged,
                ),
            )
        }
        confirmation?.let { action ->
            AlertDialog(
                onDismissRequest = { confirmation = null },
                title = { Text(if (action == ProfileDangerousAction.Deactivate) strings.deactivate else strings.deleteData) },
                text = { Text(strings.dangerConfirmation) },
                confirmButton = { Button(onClick = { confirmation = null; if (action == ProfileDangerousAction.Deactivate) onDeactivateAccount() else onDeleteAccountData() }) { Text(strings.confirm) } },
                dismissButton = { OutlinedButton(onClick = { confirmation = null }) { Text(strings.cancel) } },
            )
        }
    }
}

@Composable
private fun ProfileOverviewContent(
    state: ProfileUiState,
    strings: ProfileScreenStrings,
    touchFlowEnabled: Boolean,
    onTouchFlowEnabledChange: (Boolean) -> Unit,
    themeMode: QuataThemeMode,
    onThemeModeChange: (QuataThemeMode) -> Unit,
    slots: ProfileScreenSlots,
    onDetails: () -> Unit,
    onManagement: () -> Unit,
    onSos: () -> Unit,
    onSave: () -> Unit,
    onAvatarChanged: (String?) -> Unit,
    onLogout: () -> Unit,
) {
    val profile = requireNotNull(state.profile)
    AppearanceSettingsSectionContent(touchFlowEnabled, themeMode, strings.appearance, onTouchFlowEnabledChange, onThemeModeChange)
    ProfileOverviewAccountCardContent(
        avatar = { slots.avatar(profile.displayName, profile.avatarUri) },
        actions = {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                slots.avatarActions { uri -> onAvatarChanged(uri) }
                OutlinedButton(onClick = onDetails, modifier = Modifier.fillMaxWidth()) { Text(strings.myData, fontWeight = FontWeight.ExtraBold) }
                OutlinedButton(onClick = onManagement, modifier = Modifier.fillMaxWidth()) { Text(strings.management, fontWeight = FontWeight.ExtraBold) }
            }
        },
    )
    EmergencyContactsSettingsActionContent(strings.configureEmergency, profile.emergencyContactIds.size, onClick = onSos)
    slots.legalDocuments?.invoke()
    QuataSavingButton(
        state.isSaving,
        strings.saving,
        strings.saveChanges,
        onClick = onSave,
        modifier = Modifier.testTag(ProfileSaveChangesTestTag),
        semanticDescription = ProfileSaveChangesTestTag,
    )
    OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text(strings.logout, fontWeight = FontWeight.ExtraBold) }
}

@Composable
private fun ProfileDetailsContent(state: ProfileUiState, strings: ProfileScreenStrings, onBack: () -> Unit, onEvent: (ProfileUiEvent) -> Unit) {
    val profile = requireNotNull(state.profile)
    ProfileDetailsFormContent(
        title = strings.myData,
        bottomSpacing = 10.dp,
        backAction = { CompactIconButton(onClick = onBack) { CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, strings.back) } },
        fields = {
            ProfileTextField(profile.displayName, strings.name) { onEvent(ProfileUiEvent.NameChanged(it)) }
            ProfileTextField(profile.neighborhood, strings.neighborhood) { onEvent(ProfileUiEvent.NeighborhoodChanged(it)) }
            ProfilePrefixAndPhone(state, profile.countryCode, profile.phone, strings, onEvent)
            Text(strings.passwordUnavailable, color = quataTheme().colors.textSecondary)
            ProfileSecretQuestion(state, profile.selectedSecretQuestion, strings) { onEvent(ProfileUiEvent.SecretQuestionChanged(it)) }
            ProfileTextField(state.newSecretAnswer, strings.newSecretAnswer) { onEvent(ProfileUiEvent.SecretAnswerChanged(it)) }
        },
        saveAction = { QuataSavingButton(state.isSaving, strings.saving, strings.saveChanges, onClick = { onEvent(ProfileUiEvent.Save) }) },
    )
}

@Composable private fun ProfileTextField(value: String, label: String, password: Boolean = false, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)

@Composable
private fun ProfilePrefixAndPhone(state: ProfileUiState, code: String, phone: String, strings: ProfileScreenStrings, onEvent: (ProfileUiEvent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(.43f)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("+$code"); CompactIcon(Icons.Filled.ArrowDropDown, null) }
            DropdownMenu(expanded, { expanded = false }) { state.countryPrefixes.forEach { prefix -> DropdownMenuItem(text = { Text(prefix.label) }, onClick = { expanded = false; onEvent(ProfileUiEvent.CountryCodeChanged(prefix.code)) }) } }
        }
        Box(Modifier.weight(.57f)) { ProfileTextField(phone, strings.phone) { onEvent(ProfileUiEvent.PhoneChanged(it)) } }
    }
}

@Composable
private fun ProfileSecretQuestion(state: ProfileUiState, selected: String, strings: ProfileScreenStrings, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(state.secretQuestions.firstOrNull { it.value == selected }?.label ?: strings.secretQuestion); Spacer(Modifier.width(4.dp)); CompactIcon(Icons.Filled.ArrowDropDown, null) }
        DropdownMenu(expanded, { expanded = false }) { state.secretQuestions.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { expanded = false; onChange(option.value) }) } }
    }
}

@Composable
private fun ProfileManagementContent(strings: ProfileScreenStrings, onBack: () -> Unit, onDeactivate: () -> Unit, onDelete: () -> Unit) =
    ProfileAccountManagementContent(strings.management, strings.managementDescription, quataTheme().colors.textSecondary, listOf(ProfileManagementAction(strings.deactivate, onDeactivate), ProfileManagementAction(strings.deleteData, onDelete)), backButton = { CompactIconButton(onClick = onBack) { CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, strings.back) } })

private enum class ProfileAccountPage { Overview, Details, Management }
private enum class ProfileDangerousAction { Deactivate, DeleteData }

data class ProfileScreenStrings(
    val loading: String, val myData: String, val management: String, val managementDescription: String,
    val configureEmergency: String, val saveChanges: String, val saving: String, val logout: String,
    val name: String, val neighborhood: String, val phone: String, val newPassword: String,
    val secretQuestion: String, val newSecretAnswer: String, val back: String, val deactivate: String,
    val deleteData: String, val dangerConfirmation: String, val confirm: String, val cancel: String,
    val appearance: AppearanceSettingsStrings, val emergency: EmergencyContactsEditorStrings,
    val passwordUnavailable: String,
    val loadingError: String,
    val retry: String,
)

data class ProfileScreenSlots(
    val isLandscapeLayout: () -> Boolean = { false },
    val isImeVisible: () -> Boolean = { false },
    val avatar: @Composable (displayName: String, avatarUri: String?) -> Unit,
    val avatarActions: @Composable ((String?) -> Unit) -> Unit = {},
    val emergencyContactRow: @Composable (EmergencyContactCandidate, Boolean, () -> Unit) -> Unit,
    val emergencyContactActions: @Composable (() -> Unit)? = null,
    val legalDocuments: (@Composable () -> Unit)? = null,
    val onProfileSaved: () -> Unit = {},
    val onBackFromOverview: () -> Unit = {},
    val backDispatcher: ProfileBackDispatcher? = null,
    val sosE2eBridge: (@Composable (openSos: () -> Unit, closeSos: () -> Unit, selectFirstContacts: (Int) -> Unit) -> Unit)? = null,
    val accountE2eBridge: (@Composable (saveProfile: () -> Unit) -> Unit)? = null,
    val onSosTabChanged: (EmergencyContactsTab?) -> Unit = {},
    val onSosSelectionChanged: (selectedCount: Int, candidateCount: Int) -> Unit = { _, _ -> },
    val onSosErrorChanged: (String?) -> Unit = {},
)

/** Platform-neutral back bridge. Hosts install their native back callback as a thin adapter. */
class ProfileBackDispatcher {
    private var handler: (() -> Unit)? = null
    var canConsume: Boolean = false
        private set
    fun setHandler(value: (() -> Unit)?) { handler = value; canConsume = value != null }
    fun clearHandler() { handler = null; canConsume = false }
    fun dispatch() { handler?.invoke() }
}
