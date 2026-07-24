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

            ProfilePageLayoutContent(
                isLandscapeLayout = isLandscapeLayout,
                scrollState = accountScrollState,
                content = {
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

                    ProfileOverviewAccountCardContent(
                        avatar = {
                            val profileAvatarUri = profile.avatarUri?.trim()?.takeIf { it.isNotBlank() }
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
                        },
                        actions = {
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
                    )

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
                    ProfileDetailsFormContent(
                        title = stringResource(R.string.profile_my_data),
                        bottomSpacing = if (isLandscapeLayout) 2.dp else 10.dp,
                        backAction = {
                        CompactIconButton(onClick = { accountPage = ProfileAccountPage.Overview }) {
                            CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                        },
                        fields = {
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
                        },
                        saveAction = {
                    QuataSavingButton(
                        isSaving = state.isSaving,
                        savingText = stringResource(R.string.common_saving),
                        actionText = stringResource(R.string.common_save_changes),
                        onClick = { viewModel.onEvent(ProfileUiEvent.Save) }
                    )
                        }
                    )
                    }

                    ProfileAccountPage.Management -> {
                    ProfileAccountManagementContent(
                        title = stringResource(R.string.profile_account_management),
                        description = stringResource(R.string.profile_account_management_description),
                        descriptionColor = template.colors.textSecondary,
                        actions = listOf(
                            ProfileManagementAction(stringResource(R.string.legal_account_deletion), onDeactivateAccount),
                            ProfileManagementAction(stringResource(R.string.legal_data_deletion), onDeleteAccountData)
                        ),
                        backButton = {
                            CompactIconButton(onClick = { accountPage = ProfileAccountPage.Overview }) {
                                CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                            }
                        }
                    )
                    }
                }
                },
            )
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
    // Landscape keeps both tabs visible and consumes the filtered list directly.
    val visibleUsers = filterEmergencyContactCandidates(
        candidates = candidates,
        selectedIds = selectedIds.toSet(),
        query = query
    )
    BackHandler(enabled = true, onBack = onDismiss)
    EmergencyContactsDialogFrameContent(
        layoutPadding = layoutPadding,
        isLandscapeLayout = isLandscapeLayout
    ) {
                if (isLandscapeLayout) {
                    EmergencyContactsLandscapeEditorLayoutContent(
                        topBar = {
                            EmergencyContactsLandscapeTopBarContent(
                                backLabel = stringResource(R.string.common_back),
                                sosLabel = stringResource(R.string.common_sos),
                                title = stringResource(R.string.emergency_contacts_title),
                                saveLabel = stringResource(
                                    if (isSaving) R.string.common_saving else R.string.emergency_save_contacts_short
                                ),
                                isSaving = isSaving,
                                onDismiss = onDismiss,
                                onSave = onSave
                            )
                        },
                        contacts = { modifier ->
                            EmergencyContactsLandscapeContactsSectionContent(
                                title = stringResource(R.string.emergency_contacts_tab),
                                selectedCountLabel = stringResource(R.string.emergency_selected_count, selectedIds.size),
                                modifier = modifier,
                                searchInput = {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    placeholder = { Text(stringResource(R.string.emergency_search_placeholder)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                },
                                users = { usersModifier ->
                                LazyColumn(
                                    modifier = usersModifier,
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
                            )
                        },
                        message = { modifier ->
                            Column(
                                modifier
                                    .verticalScroll(messageScrollState)
                            ) {
                                EmergencyContactsLandscapeMessageIntroContent(
                                    tabLabel = stringResource(R.string.emergency_message_tab),
                                    description = stringResource(R.string.emergency_contacts_description)
                                )
                                EmergencyContactsLandscapeMessagePanelContent(
                                    title = stringResource(R.string.emergency_message_title),
                                    hint = stringResource(R.string.emergency_message_hint),
                                    input = {
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
                                )
                            }
                        }
                    )
                } else {
                    EmergencyContactsPortraitEditorLayoutContent(
                        body = { modifier ->
                            when (selectedTab) {
                                EmergencyContactsTab.Contacts -> EmergencyContactsSelectionContent(
                            candidates = candidates,
                            selectedIds = selectedIds.toSet(),
                            query = query,
                            onQueryChange = { query = it },
                            listState = contactsListState,
                            showHeader = imeBottom == 0,
                            headerStrings = headerStrings,
                            searchPlaceholder = stringResource(R.string.emergency_search_placeholder),
                            selectedCountLabel = stringResource(R.string.emergency_selected_count, selectedIds.size),
                            networkUsersLabel = stringResource(R.string.emergency_network_users),
                            onTabSelected = { selectedTab = it },
                            onDismiss = onDismiss,
                            userRow = { user, selected ->
                                EmergencyUserRow(
                                    user = user,
                                    selected = selected,
                                    onToggle = { onToggleContact(user) }
                                )
                            },
                            modifier = modifier
                                )
                                EmergencyContactsTab.Message -> EmergencyContactsMessageContent(
                            scrollState = messageScrollState,
                            showHeader = imeBottom == 0,
                            headerStrings = headerStrings,
                            title = stringResource(R.string.emergency_message_title),
                            hint = stringResource(R.string.emergency_message_hint),
                            onTabSelected = { selectedTab = it },
                            onDismiss = onDismiss,
                            messageInput = {
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
                            },
                            modifier = modifier
                                )
                            }
                        },
                        saveAction = {
                            if (imeBottom == 0) {
                                Spacer(Modifier.height(18.dp))
                                EmergencyContactsPortraitSaveButtonContent(
                                    label = stringResource(
                                        if (isSaving) R.string.common_saving else R.string.emergency_save_contacts
                                    ),
                                    isSaving = isSaving,
                                    onSave = onSave
                                )
                                Spacer(Modifier.height(bottomActionOffset))
                            }
                        }
                    )
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
