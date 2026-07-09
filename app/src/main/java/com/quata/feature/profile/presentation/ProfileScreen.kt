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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import com.quata.R
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.session.SessionManager
import com.quata.core.ui.components.AttachmentPreview
import com.quata.core.ui.components.AttachmentViewerDialog
import com.quata.core.ui.components.AvatarLetter
import com.quata.core.ui.components.PhoneInputSection
import com.quata.core.ui.components.QuataCameraDialog
import com.quata.core.ui.components.QuataCameraMode
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.compactButtonMinSize
import com.quata.core.ui.components.rememberCachedRemoteImageRequest
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.postcomposer.imageeditor.QuataImageEditorDialog
import com.quata.feature.postcomposer.imageeditor.QuataImageEditorMode
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.ProfileRepository

private enum class EmergencyTab {
    Contacts,
    Message
}

private enum class ProfileAccountPage {
    Overview,
    Details
}

@Composable
fun ProfileScreen(
    padding: PaddingValues,
    sessionManager: SessionManager,
    repository: ProfileRepository,
    touchFlowEnabled: Boolean,
    onTouchFlowEnabledChange: (Boolean) -> Unit,
    themeMode: QuataThemeMode,
    onThemeModeChange: (QuataThemeMode) -> Unit,
    networkReconnectToken: Long = 0L,
    onFullscreenEditorVisibilityChange: (Boolean) -> Unit = {},
    onLogout: () -> Unit,
    onProfileSaved: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory(repository))
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

    BackHandler(enabled = accountPage == ProfileAccountPage.Details) {
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
                    ProfilePanel(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.profile_touch_flow_setting),
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = touchFlowEnabled,
                                onCheckedChange = onTouchFlowEnabledChange
                            )
                        }
                    }

                    ProfilePanel(contentPadding = PaddingValues(14.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.profile_theme_setting),
                                fontWeight = FontWeight.ExtraBold
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ThemeModeButton(
                                    text = stringResource(R.string.theme_mode_system),
                                    selected = themeMode == QuataThemeMode.System,
                                    onClick = { onThemeModeChange(QuataThemeMode.System) },
                                    modifier = Modifier.weight(1f)
                                )
                                ThemeModeButton(
                                    text = stringResource(R.string.theme_mode_dark),
                                    selected = themeMode == QuataThemeMode.Dark,
                                    onClick = { onThemeModeChange(QuataThemeMode.Dark) },
                                    modifier = Modifier.weight(1f)
                                )
                                ThemeModeButton(
                                    text = stringResource(R.string.theme_mode_light),
                                    selected = themeMode == QuataThemeMode.Light,
                                    onClick = { onThemeModeChange(QuataThemeMode.Light) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    ProfilePanel(contentPadding = PaddingValues(12.dp)) {
                        val profileAvatarUri = profile.avatarUri?.trim()?.takeIf { it.isNotBlank() }
                        val profileAvatarModel = rememberCachedRemoteImageRequest(profileAvatarUri)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(QuataOrange.copy(alpha = 0.2f))
                                    .border(1.dp, QuataOrange.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                    .clickable(enabled = profileAvatarUri != null) {
                                        val avatarUri = profileAvatarUri ?: return@clickable
                                        selectedAvatarPreview = AttachmentPreview(
                                            name = profile.displayName.ifBlank { context.getString(R.string.profile_photo) },
                                            uri = avatarUri,
                                            mimeType = "image/jpeg"
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (profileAvatarUri != null) {
                                    AsyncImage(
                                        model = profileAvatarModel,
                                        contentDescription = stringResource(R.string.profile_photo),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    AvatarLetter(profile.displayName.ifBlank { "Q" })
                                }
                            }
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
                                Text(
                                    stringResource(R.string.profile_photo_hint),
                                    color = template.colors.textSecondary,
                                    fontSize = template.textSizes.caption,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                    ProfileSaveButton(
                        isSaving = state.isSaving,
                        onClick = { viewModel.onEvent(ProfileUiEvent.Save) }
                    )
                    OutlinedButton(
                        onClick = {
                            sessionManager.clearSession()
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
                    ProfileTextField(
                        value = profile.displayName,
                        onValueChange = { viewModel.onEvent(ProfileUiEvent.NameChanged(it)) },
                        label = stringResource(R.string.auth_name)
                    )
                    ProfileTextField(
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
                        phoneLabel = stringResource(R.string.profile_phone)
                    )
                    ProfileTextField(
                        value = state.newPassword,
                        onValueChange = { viewModel.onEvent(ProfileUiEvent.NewPasswordChanged(it)) },
                        label = stringResource(R.string.profile_new_password),
                        isPassword = true
                    )
                    DropdownField(
                        value = profile.selectedSecretQuestion,
                        options = state.secretQuestions,
                        optionLabel = { it.label },
                        onSelected = { viewModel.onEvent(ProfileUiEvent.SecretQuestionChanged(it.value)) },
                        displayText = state.secretQuestions.firstOrNull { it.value == profile.selectedSecretQuestion }?.label.orEmpty()
                    )
                    ProfileTextField(
                        value = state.newSecretAnswer,
                        onValueChange = { viewModel.onEvent(ProfileUiEvent.SecretAnswerChanged(it)) },
                        label = stringResource(R.string.profile_new_secret_answer)
                    )
                    Spacer(Modifier.height(if (isLandscapeLayout) 2.dp else 10.dp))
                    ProfileSaveButton(
                        isSaving = state.isSaving,
                        onClick = { viewModel.onEvent(ProfileUiEvent.Save) }
                    )
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
private fun ProfilePanel(
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable () -> Unit
) {
    val template = quataTheme()
    Surface(
        color = template.colors.surface.copy(alpha = 0.82f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, template.colors.divider.copy(alpha = 0.72f), RoundedCornerShape(20.dp))
    ) {
        Box(Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
private fun ProfileSaveButton(
    isSaving: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !isSaving,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .compactButtonMinSize(),
        colors = ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.Black),
        shape = RoundedCornerShape(9.dp),
        contentPadding = CompactButtonContentPadding
    ) {
        Text(
            if (isSaving) stringResource(R.string.common_saving) else stringResource(R.string.common_save_changes),
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun ThemeModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Surface(
        color = if (selected) template.colors.accent else template.colors.surfaceAlt,
        contentColor = if (selected) template.colors.accentContent else template.colors.textPrimary,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .height(40.dp)
            .border(
                width = 1.dp,
                color = if (selected) template.colors.accent else template.colors.divider,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text(
                text = text,
                fontSize = template.textSizes.caption,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false
) {
    val template = quataTheme()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label) },
        singleLine = !label.contains("respuesta", ignoreCase = true),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .height(CompactTextFieldHeight),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = template.colors.surfaceAlt,
            unfocusedContainerColor = template.colors.surfaceAlt,
            focusedBorderColor = template.colors.accent,
            unfocusedBorderColor = template.colors.inputBorder,
            cursorColor = template.colors.accent
        )
    )
}

@Composable
private fun <T> DropdownField(
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    displayText: String,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(CompactDropdownHeight)
                .border(1.dp, template.colors.inputBorder, RoundedCornerShape(18.dp))
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayText.ifBlank { value },
                    color = template.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                CompactIcon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
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
    val isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape
    val bottomActionHeight = 54.dp
    val bottomActionOffset = if (isLandscapeLayout) 0.dp else 12.dp
    val contentBottomSpace = bottomActionHeight + bottomActionOffset + 18.dp
    var query by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableStateOf(EmergencyTab.Contacts) }
    val visibleUsers = candidates
        .filter { user ->
            query.isBlank() ||
                user.displayName.contains(query, ignoreCase = true) ||
                user.email.contains(query, ignoreCase = true) ||
                user.neighborhood.contains(query, ignoreCase = true) ||
                user.phone.contains(query, ignoreCase = true)
        }
        .sortedWith(
            compareByDescending<EmergencyContactCandidate> { it.id in selectedIds }
                .thenBy { it.displayName.lowercase() }
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
                            Column(Modifier.weight(0.92f)) {
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
                                ProfilePanel(contentPadding = PaddingValues(12.dp)) {
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
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = contentBottomSpace)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CompactIconButton(onClick = onDismiss) {
                                CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                            }
                            Spacer(Modifier.width(6.dp))
                            Surface(color = template.colors.sosSurface, shape = RoundedCornerShape(16.dp)) {
                                Text(stringResource(R.string.common_sos), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.emergency_contacts_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.emergency_contacts_description),
                            color = template.colors.textSecondary,
                            lineHeight = 22.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            EmergencyTabButton(
                                text = stringResource(R.string.emergency_contacts_tab),
                                selected = selectedTab == EmergencyTab.Contacts,
                                onClick = { selectedTab = EmergencyTab.Contacts },
                                modifier = Modifier.weight(1f)
                            )
                            EmergencyTabButton(
                                text = stringResource(R.string.emergency_message_tab),
                                selected = selectedTab == EmergencyTab.Message,
                                onClick = { selectedTab = EmergencyTab.Message },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(10.dp))

                        when (selectedTab) {
                            EmergencyTab.Contacts -> Column(Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text(stringResource(R.string.emergency_search_placeholder)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.emergency_selected_count, selectedIds.size), color = template.colors.accent, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(14.dp))
                            Text(stringResource(R.string.emergency_network_users), fontWeight = FontWeight.ExtraBold)
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
                        EmergencyTab.Message -> Column(Modifier.weight(1f)) {
                            ProfilePanel {
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
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    }
                    Button(
                        onClick = onSave,
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = template.colors.accent, contentColor = template.colors.accentContent),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomActionOffset)
                            .fillMaxWidth()
                            .height(bottomActionHeight)
                    ) {
                        Text(
                            stringResource(if (isSaving) R.string.common_saving else R.string.emergency_save_contacts),
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
        }
    }
}

@Composable
private fun EmergencyTabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Surface(
        color = if (selected) template.colors.accent else template.colors.surfaceAlt,
        contentColor = if (selected) template.colors.accentContent else template.colors.textPrimary,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun EmergencyUserRow(
    user: EmergencyContactCandidate,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val template = quataTheme()
    Surface(
        color = template.colors.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, template.colors.divider, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarLetter(user.displayName, modifier = Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.displayName, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(user.neighborhood, color = template.colors.textSecondary, maxLines = 1)
            }
            OutlinedButton(onClick = onToggle, shape = RoundedCornerShape(14.dp)) {
                Text(if (selected) stringResource(R.string.common_remove) else stringResource(R.string.common_add))
            }
        }
    }
}

private fun Context.hasCapturePermissions(): Boolean =
    hasCameraPermission()

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun capturePermissions(): Array<String> =
    arrayOf(Manifest.permission.CAMERA)
