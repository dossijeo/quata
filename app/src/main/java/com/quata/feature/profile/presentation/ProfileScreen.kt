package com.quata.feature.profile.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.quata.core.ui.components.CompactButtonContentPadding
import com.quata.core.ui.components.CompactDropdownHeight
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.CompactTextFieldHeight
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.session.SessionManager
import com.quata.core.ui.components.AttachmentPreview
import com.quata.core.ui.components.AttachmentViewerDialog
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.PhoneInputSection
import com.quata.core.ui.components.QuataDropdownField
import com.quata.core.ui.components.QuataPanel
import com.quata.core.ui.components.QuataCameraDialog
import com.quata.core.ui.components.QuataCameraMode
import com.quata.core.ui.components.QuataSavingButton
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.QuataTextField
import com.quata.core.ui.components.compactButtonMinSize
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.postcomposer.imageeditor.QuataImageEditorDialog
import com.quata.feature.postcomposer.imageeditor.QuataImageEditorMode
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.settings.presentation.AppearanceSettingsControls
import com.quata.feature.settings.presentation.AppearanceSettingsStrings

private enum class ProfileAccountPage {
    Overview,
    Details,
    Management
}

@Composable
fun ProfileScreen(
    padding: PaddingValues,
    repository: ProfileRepository,
    profileId: String,
    touchFlowEnabled: Boolean,
    onTouchFlowEnabledChange: (Boolean) -> Unit,
    themeMode: QuataThemeMode,
    onThemeModeChange: (QuataThemeMode) -> Unit,
    networkReconnectToken: Long = 0L,
    onFullscreenEditorVisibilityChange: (Boolean) -> Unit = {},
    onLogout: () -> Unit,
    onDeactivateAccount: () -> Unit,
    onDeleteAccountData: () -> Unit,
    onProfileSaved: () -> Unit,
    viewModel: ProfileAndroidViewModel = viewModel(factory = ProfileAndroidViewModel.Factory(repository))
) {
    val template = quataTheme()
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var isEmergencyDialogOpen by rememberSaveable { mutableStateOf(false) }
    var isPhotoMenuOpen by rememberSaveable { mutableStateOf(false) }
    var accountPage by rememberSaveable { mutableStateOf(ProfileAccountPage.Overview) }
    var isProfileCameraOpen by rememberSaveable { mutableStateOf(false) }
    var pendingAvatarEditorUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAvatarPreview by remember { mutableStateOf<AttachmentPreview?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        pendingAvatarEditorUri = uri
    }

    fun launchProfilePhotoCapture() {
        isProfileCameraOpen = true
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (context.hasCapturePermissions()) {
            launchProfilePhotoCapture()
        } else {
            Toast.makeText(context, context.getString(R.string.profile_camera_permission_photo), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(networkReconnectToken) {
        if (networkReconnectToken != 0L) {
            viewModel.onEvent(ProfileUiEvent.Refresh)
        }
    }

    LaunchedEffect(pendingAvatarEditorUri) {
        onFullscreenEditorVisibilityChange(pendingAvatarEditorUri != null)
    }

    DisposableEffect(Unit) {
        onDispose { onFullscreenEditorVisibilityChange(false) }
    }

    BackHandler(enabled = accountPage != ProfileAccountPage.Overview) {
        accountPage = ProfileAccountPage.Overview
    }

    LaunchedEffect(state.successMessage) {
        val message = state.successMessage ?: return@LaunchedEffect
        val shouldNotifyProfileSaved = state.successMessageTriggersProfileSaved
        val shouldCloseEmergencyDialog = state.emergencySettingsSaved
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        if (shouldCloseEmergencyDialog) {
            isEmergencyDialogOpen = false
        }
        viewModel.onEvent(ProfileUiEvent.ClearMessages)
        if (shouldNotifyProfileSaved) {
            onProfileSaved()
        }
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.onEvent(ProfileUiEvent.ClearMessages)
    }

    Box(Modifier.fillMaxSize()) {
        QuataScreen(padding) {
            val profile = state.profile
            val isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape
            val accountScrollState = rememberScrollState(accountPage.ordinal)
            if (state.isLoading || profile == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.profile_loading), color = template.colors.textSecondary)
                }
                return@QuataScreen
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(accountScrollState)
                    .padding(
                        start = if (isLandscapeLayout) 8.dp else 14.dp,
                        top = if (isLandscapeLayout) 10.dp else 12.dp,
                        end = 14.dp,
                        bottom = 12.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (accountPage) {
                    ProfileAccountPage.Overview -> {
                    QuataPanel(contentPadding = PaddingValues(14.dp)) {
                        AppearanceSettingsControls(
                            touchFlowEnabled = touchFlowEnabled,
                            themeMode = themeMode,
                            strings = AppearanceSettingsStrings(
                                touchFlow = stringResource(R.string.profile_touch_flow_setting),
                                theme = stringResource(R.string.profile_theme_setting),
                                system = stringResource(R.string.theme_mode_system),
                                dark = stringResource(R.string.theme_mode_dark),
                                light = stringResource(R.string.theme_mode_light)
                            ),
                            onTouchFlowEnabledChange = onTouchFlowEnabledChange,
                            onThemeModeChange = onThemeModeChange
                        )
                    }

                    QuataPanel(contentPadding = PaddingValues(12.dp)) {
                        val profileAvatarUri = profile.avatarUri?.trim()?.takeIf { it.isNotBlank() }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarImage(
                                name = profile.displayName.ifBlank { "Q" },
                                avatarUrl = profileAvatarUri,
                                profileId = profileId,
                                modifier = Modifier
                                    .size(76.dp)
                                    .clickable(enabled = profileAvatarUri != null) {
                                        val avatarUri = profileAvatarUri ?: return@clickable
                                        selectedAvatarPreview = AttachmentPreview(
                                            name = profile.displayName.ifBlank { context.getString(R.string.profile_photo) },
                                            uri = avatarUri,
                                            mimeType = "image/jpeg"
                                        )
                                    }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { isPhotoMenuOpen = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .compactButtonMinSize(),
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = CompactButtonContentPadding
                                ) {
                                    CompactIcon(Icons.Filled.PhotoCamera, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.profile_change_photo))
                                }
                                DropdownMenu(
                                    expanded = isPhotoMenuOpen,
                                    onDismissRequest = { isPhotoMenuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.profile_pick_gallery)) },
                                        leadingIcon = { CompactIcon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                                        onClick = {
                                            isPhotoMenuOpen = false
                                            photoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.profile_take_photo)) },
                                        leadingIcon = { CompactIcon(Icons.Filled.PhotoCamera, contentDescription = null) },
                                        onClick = {
                                            isPhotoMenuOpen = false
                                            if (context.hasCapturePermissions()) {
                                                launchProfilePhotoCapture()
                                            } else {
                                                cameraPermissionLauncher.launch(capturePermissions())
                                            }
                                        }
                                    )
                                }
                                OutlinedButton(
                                    onClick = { accountPage = ProfileAccountPage.Details },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .compactButtonMinSize(),
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = CompactButtonContentPadding
                                ) {
                                    Text(stringResource(R.string.profile_my_data), fontWeight = FontWeight.ExtraBold)
                                }
                                OutlinedButton(
                                    onClick = { accountPage = ProfileAccountPage.Management },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .compactButtonMinSize(),
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = CompactButtonContentPadding
                                ) {
                                    Text(stringResource(R.string.profile_account_management), fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { isEmergencyDialogOpen = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .compactButtonMinSize(),
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = CompactButtonContentPadding
                    ) {
                        Text(stringResource(R.string.profile_configure_emergency_contacts), fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.weight(1f))
                        Text("${profile.emergencyContactIds.size}/5")
                    }
                    Spacer(Modifier.height(if (isLandscapeLayout) 2.dp else 10.dp))
                    QuataSavingButton(
                        isSaving = state.isSaving,
                        savingText = stringResource(R.string.common_saving),
                        actionText = stringResource(R.string.common_save_changes),
                        onClick = { viewModel.onEvent(ProfileUiEvent.Save) }
                    )
                    OutlinedButton(
                        onClick = {
                            onLogout()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .compactButtonMinSize(),
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = CompactButtonContentPadding
                    ) {
                        Text(stringResource(R.string.profile_logout), fontWeight = FontWeight.ExtraBold)
                    }
                    }

                    ProfileAccountPage.Details -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactIconButton(onClick = { accountPage = ProfileAccountPage.Overview }) {
                            CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.profile_my_data),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    QuataTextField(
                        value = profile.displayName,
                        onValueChange = { viewModel.onEvent(ProfileUiEvent.NameChanged(it)) },
                        label = stringResource(R.string.auth_name)
                    )
                    QuataTextField(
                        value = profile.neighborhood,
                        onValueChange = { viewModel.onEvent(ProfileUiEvent.NeighborhoodChanged(it)) },
                        label = stringResource(R.string.profile_neighborhood)
                    )
                    PhoneInputSection(
                        prefixes = state.countryPrefixes,
                        selectedPrefix = profile.countryCode,
                        onPrefixChange = { viewModel.onEvent(ProfileUiEvent.CountryCodeChanged(it)) },
                        phone = profile.phone,
                        onPhoneChange = { viewModel.onEvent(ProfileUiEvent.PhoneChanged(it)) },
                        phoneLabel = stringResource(R.string.profile_phone),
                        searchPlaceholder = stringResource(R.string.profile_search_prefix)
                    )
                    QuataTextField(
                        value = state.newPassword,
                        onValueChange = { viewModel.onEvent(ProfileUiEvent.NewPasswordChanged(it)) },
                        label = stringResource(R.string.profile_new_password),
                        isPassword = true
                    )
                    QuataDropdownField(
                        value = profile.selectedSecretQuestion,
                        options = state.secretQuestions,
                        optionLabel = { it.label },
                        onSelected = { viewModel.onEvent(ProfileUiEvent.SecretQuestionChanged(it.value)) },
                        displayText = state.secretQuestions.firstOrNull { it.value == profile.selectedSecretQuestion }?.label.orEmpty()
                    )
                    QuataTextField(
                        value = state.newSecretAnswer,
                        onValueChange = { viewModel.onEvent(ProfileUiEvent.SecretAnswerChanged(it)) },
                        label = stringResource(R.string.profile_new_secret_answer)
                    )
                    Spacer(Modifier.height(if (isLandscapeLayout) 2.dp else 10.dp))
                    QuataSavingButton(
                        isSaving = state.isSaving,
                        savingText = stringResource(R.string.common_saving),
                        actionText = stringResource(R.string.common_save_changes),
                        onClick = { viewModel.onEvent(ProfileUiEvent.Save) }
                    )
                    }

                    ProfileAccountPage.Management -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactIconButton(onClick = { accountPage = ProfileAccountPage.Overview }) {
                            CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.profile_account_management),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = stringResource(R.string.profile_account_management_description),
                        color = template.colors.textSecondary
                    )
                    OutlinedButton(
                        onClick = onDeactivateAccount,
                        modifier = Modifier.fillMaxWidth().compactButtonMinSize(),
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = CompactButtonContentPadding
                    ) {
                        Text(stringResource(R.string.legal_account_deletion))
                    }
                    OutlinedButton(
                        onClick = onDeleteAccountData,
                        modifier = Modifier.fillMaxWidth().compactButtonMinSize(),
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = CompactButtonContentPadding
                    ) {
                        Text(stringResource(R.string.legal_data_deletion))
                    }
                    }
                }
            }
        }

        val profile = state.profile
        if (isEmergencyDialogOpen && profile != null) {
            EmergencyContactsDialog(
                layoutPadding = padding,
                candidates = state.emergencyCandidates,
                selectedIds = profile.emergencyContactIds,
                message = profile.emergencyMessage,
                isSaving = state.isSaving,
                onMessageChange = { viewModel.onEvent(ProfileUiEvent.EmergencyMessageChanged(it)) },
                onToggleContact = { viewModel.onEvent(ProfileUiEvent.EmergencyContactToggled(it.id)) },
                onDismiss = { isEmergencyDialogOpen = false },
                onSave = {
                    viewModel.onEvent(ProfileUiEvent.SaveEmergencySettings)
                }
            )
        }
        selectedAvatarPreview?.let { avatar ->
            AttachmentViewerDialog(
                attachment = avatar,
                onDismiss = { selectedAvatarPreview = null }
            )
        }
        pendingAvatarEditorUri?.let { avatarUri ->
            QuataImageEditorDialog(
                imageUri = avatarUri,
                mode = QuataImageEditorMode.Avatar,
                onDismiss = { pendingAvatarEditorUri = null },
                onEdited = { editedUri ->
                    pendingAvatarEditorUri = null
                    viewModel.onEvent(ProfileUiEvent.AvatarChanged(editedUri.toString()))
                }
            )
        }
        if (isProfileCameraOpen) {
            QuataCameraDialog(
                mode = QuataCameraMode.Photo,
                onDismiss = { isProfileCameraOpen = false },
                onPhotoCaptured = { uri, _, _ ->
                    isProfileCameraOpen = false
                    pendingAvatarEditorUri = uri
                }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun EmergencyContactsDialog(
    layoutPadding: PaddingValues = PaddingValues(),
    candidates: List<EmergencyContactCandidate>,
    selectedIds: List<String>,
    message: String,
    isSaving: Boolean,
    onMessageChange: (String) -> Unit,
    onToggleContact: (EmergencyContactCandidate) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val template = quataTheme()
    val headerStrings = EmergencyContactsHeaderStrings(
        back = stringResource(R.string.common_back),
        sos = stringResource(R.string.common_sos),
        title = stringResource(R.string.emergency_contacts_title),
        description = stringResource(R.string.emergency_contacts_description),
        contactsTab = stringResource(R.string.emergency_contacts_tab),
        messageTab = stringResource(R.string.emergency_message_tab)
    )
    val isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape
    val bottomActionHeight = 54.dp
    val bottomActionOffset = if (isLandscapeLayout) 0.dp else 12.dp
    var query by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableStateOf(EmergencyContactsTab.Contacts) }
    // These states are used only while the IME reduces the available viewport.
    // They preserve the unchanged layout and position at rest.
    val messageScrollState = rememberScrollState()
    val contactsListState = rememberLazyListState()
    val messageBringIntoViewRequester = remember { BringIntoViewRequester() }
    var isMessageFocused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(isMessageFocused, imeBottom) {
        if (isMessageFocused && imeBottom > 0) {
            messageBringIntoViewRequester.bringIntoView()
        }
    }
    LaunchedEffect(imeBottom, selectedTab) {
        if (imeBottom == 0 && selectedTab == EmergencyContactsTab.Contacts) {
            contactsListState.scrollToItem(0)
        }
    }
    val visibleUsers = filterEmergencyContactCandidates(
        candidates = candidates,
        selectedIds = selectedIds.toSet(),
        query = query
    )

    BackHandler(enabled = true, onBack = onDismiss)
    QuataScreen(padding = layoutPadding) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (isLandscapeLayout) 16.dp else 18.dp,
                    end = if (isLandscapeLayout) 16.dp else 18.dp
                )
                .padding(top = if (isLandscapeLayout) 10.dp else 14.dp, bottom = if (isLandscapeLayout) 10.dp else 0.dp)
        ) {
                if (isLandscapeLayout) {
                    Column(Modifier.fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CompactIconButton(onClick = onDismiss) {
                                CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                            }
                            Spacer(Modifier.width(6.dp))
                            Surface(color = template.colors.sosSurface, shape = RoundedCornerShape(16.dp)) {
                                Text(stringResource(R.string.common_sos), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.emergency_contacts_title), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = onSave,
                                enabled = !isSaving,
                                colors = ButtonDefaults.buttonColors(containerColor = template.colors.accent, contentColor = template.colors.accentContent),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .height(42.dp)
                                    .width(196.dp)
                            ) {
                                Text(
                                    stringResource(
                                        if (isSaving) R.string.common_saving else R.string.emergency_save_contacts_short
                                    ),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(Modifier.weight(1.08f)) {
                                Text(stringResource(R.string.emergency_contacts_tab), fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    placeholder = { Text(stringResource(R.string.emergency_search_placeholder)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.emergency_selected_count, selectedIds.size), color = template.colors.accent, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(visibleUsers) { user ->
                                        EmergencyUserRow(
                                            user = user,
                                            selected = user.id in selectedIds,
                                            onToggle = { onToggleContact(user) }
                                        )
                                    }
                                }
                            }
                            Column(
                                Modifier
                                    .weight(0.92f)
                                    .verticalScroll(messageScrollState)
                            ) {
                                Text(stringResource(R.string.emergency_message_tab), fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.emergency_contacts_description),
                                    color = template.colors.textSecondary,
                                    lineHeight = 18.sp,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(8.dp))
                                QuataPanel(contentPadding = PaddingValues(12.dp)) {
                                    Column {
                                        Text(stringResource(R.string.emergency_message_title), fontWeight = FontWeight.ExtraBold)
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            stringResource(R.string.emergency_message_hint),
                                            color = template.colors.textSecondary,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = message,
                                            onValueChange = onMessageChange,
                                            minLines = 4,
                                            maxLines = 5,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .bringIntoViewRequester(messageBringIntoViewRequester)
                                                .onFocusChanged { isMessageFocused = it.isFocused },
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        EmergencyContactsTab.Contacts -> LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            state = contactsListState,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                if (imeBottom == 0) {
                                    EmergencyContactsHeaderContent(
                                        selectedTab = selectedTab,
                                        strings = headerStrings,
                                        onTabSelected = { selectedTab = it },
                                        onDismiss = onDismiss
                                    )
                                    Spacer(Modifier.height(10.dp))
                                }
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    placeholder = { Text(stringResource(R.string.emergency_search_placeholder)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    stringResource(R.string.emergency_selected_count, selectedIds.size),
                                    color = template.colors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(stringResource(R.string.emergency_network_users), fontWeight = FontWeight.ExtraBold)
                            }
                            items(visibleUsers, key = { it.id }) { user ->
                                EmergencyUserRow(
                                    user = user,
                                    selected = user.id in selectedIds,
                                    onToggle = { onToggleContact(user) }
                                )
                            }
                        }
                        EmergencyContactsTab.Message -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(messageScrollState)
                        ) {
                            if (imeBottom == 0) {
                                EmergencyContactsHeaderContent(
                                    selectedTab = selectedTab,
                                    strings = headerStrings,
                                    onTabSelected = { selectedTab = it },
                                    onDismiss = onDismiss
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                            QuataPanel {
                                Column {
                                    Text(stringResource(R.string.emergency_message_title), fontWeight = FontWeight.ExtraBold)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.emergency_message_hint),
                                        color = template.colors.textSecondary
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = message,
                                        onValueChange = onMessageChange,
                                        minLines = 8,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .bringIntoViewRequester(messageBringIntoViewRequester)
                                            .onFocusChanged { isMessageFocused = it.isFocused },
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (imeBottom == 0) {
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = onSave,
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = template.colors.accent, contentColor = template.colors.accentContent),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(bottomActionHeight)
                        ) {
                            Text(
                                stringResource(if (isSaving) R.string.common_saving else R.string.emergency_save_contacts),
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(Modifier.height(bottomActionOffset))
                    }
                    }
                }
        }
    }
}

@Composable
private fun EmergencyUserRow(
    user: EmergencyContactCandidate,
    selected: Boolean,
    onToggle: () -> Unit
) {
    EmergencyUserRowContent(
        user = user,
        selected = selected,
        addLabel = stringResource(R.string.common_add),
        removeLabel = stringResource(R.string.common_remove),
        avatar = {
            AvatarImage(
                name = user.displayName,
                avatarUrl = null,
                profileId = user.id,
                modifier = Modifier.size(46.dp)
            )
        },
        onToggle = onToggle
    )
}

private fun Context.hasCapturePermissions(): Boolean =
    hasCameraPermission()

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun capturePermissions(): Array<String> =
    arrayOf(Manifest.permission.CAMERA)
