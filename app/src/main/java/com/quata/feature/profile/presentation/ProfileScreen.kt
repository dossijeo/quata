package com.quata.feature.profile.presentation

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataSurface
import com.quata.core.session.SessionManager
import com.quata.core.ui.components.AvatarLetter
import com.quata.core.ui.components.QuataScreen
import com.quata.feature.profile.domain.CountryPrefix
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.ProfileRepository

private enum class EmergencyTab {
    Contacts,
    Message
}

@Composable
fun ProfileScreen(
    padding: PaddingValues,
    sessionManager: SessionManager,
    repository: ProfileRepository,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory(repository))
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var isEmergencyDialogOpen by rememberSaveable { mutableStateOf(false) }
    var isPhotoMenuOpen by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onEvent(ProfileUiEvent.AvatarChanged(uri?.toString()))
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) viewModel.onEvent(ProfileUiEvent.AvatarChanged(pendingCameraUri?.toString()))
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pendingCameraUri?.let { cameraLauncher.launch(it) }
        } else {
            Toast.makeText(context, context.getString(R.string.profile_camera_permission_photo), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.successMessage) {
        val message = state.successMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.onEvent(ProfileUiEvent.ClearMessages)
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.onEvent(ProfileUiEvent.ClearMessages)
    }

    QuataScreen(padding) {
        val profile = state.profile
        if (state.isLoading || profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.profile_loading), color = Color.White.copy(alpha = 0.72f))
            }
            return@QuataScreen
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.profile_account_label), color = Color.White.copy(alpha = 0.78f), letterSpacing = 2.sp)
            Text(stringResource(R.string.profile_edit_title), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                stringResource(R.string.profile_edit_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 23.sp
            )

            ProfilePanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(QuataOrange.copy(alpha = 0.2f))
                            .border(1.dp, QuataOrange.copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profile.avatarUri != null) {
                            AsyncImage(
                                model = profile.avatarUri,
                                contentDescription = stringResource(R.string.profile_photo),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AvatarLetter(profile.displayName.ifBlank { "Q" })
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { isPhotoMenuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.profile_change_photo))
                        }
                        DropdownMenu(
                            expanded = isPhotoMenuOpen,
                            onDismissRequest = { isPhotoMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.profile_pick_gallery)) },
                                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                                onClick = {
                                    isPhotoMenuOpen = false
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.profile_take_photo)) },
                                leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                                onClick = {
                                    isPhotoMenuOpen = false
                                    val uri = context.createProfileImageUri()
                                    pendingCameraUri = uri
                                    if (uri != null) {
                                        if (context.hasCameraPermission()) {
                                            cameraLauncher.launch(uri)
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.profile_photo_hint), color = Color.White.copy(alpha = 0.66f))
                    }
                }
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
            PhoneSection(
                prefixes = state.countryPrefixes,
                selectedPrefix = profile.countryCode,
                onPrefixChange = { viewModel.onEvent(ProfileUiEvent.CountryCodeChanged(it)) },
                phone = profile.phone,
                onPhoneChange = { viewModel.onEvent(ProfileUiEvent.PhoneChanged(it)) }
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

            OutlinedButton(
                onClick = { isEmergencyDialogOpen = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(stringResource(R.string.profile_configure_emergency_contacts), fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text("${profile.emergencyContactIds.size}/5")
            }
            Button(
                onClick = { viewModel.onEvent(ProfileUiEvent.Save) },
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.Black),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(if (state.isSaving) stringResource(R.string.common_saving) else stringResource(R.string.common_save_changes), fontWeight = FontWeight.ExtraBold)
            }
            OutlinedButton(
                onClick = {
                    sessionManager.clearSession()
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(stringResource(R.string.profile_logout), fontWeight = FontWeight.ExtraBold)
            }
        }
    }

    val profile = state.profile
    if (isEmergencyDialogOpen && profile != null) {
        EmergencyContactsDialog(
            candidates = state.emergencyCandidates,
            selectedIds = profile.emergencyContactIds,
            message = profile.emergencyMessage,
            onMessageChange = { viewModel.onEvent(ProfileUiEvent.EmergencyMessageChanged(it)) },
            onToggleContact = { viewModel.onEvent(ProfileUiEvent.EmergencyContactToggled(it.id)) },
            onDismiss = { isEmergencyDialogOpen = false },
            onSave = {
                Toast.makeText(context, context.getString(R.string.profile_emergency_contacts_updated), Toast.LENGTH_SHORT).show()
                viewModel.onEvent(ProfileUiEvent.Save)
                isEmergencyDialogOpen = false
            }
        )
    }
}

@Composable
private fun ProfilePanel(content: @Composable () -> Unit) {
    Surface(
        color = QuataSurface.copy(alpha = 0.54f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
    ) {
        Box(Modifier.padding(16.dp)) {
            content()
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label) },
        singleLine = !label.contains("respuesta", ignoreCase = true),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun PhoneSection(
    prefixes: List<CountryPrefix>,
    selectedPrefix: String,
    onPrefixChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        PrefixDropdownField(
            value = selectedPrefix,
            options = prefixes,
            onSelected = { onPrefixChange(it.code) },
            displayText = "+$selectedPrefix",
            modifier = Modifier.weight(0.38f)
        )
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            placeholder = { Text(stringResource(R.string.profile_phone)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.weight(0.62f),
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun PrefixDropdownField(
    value: String,
    options: List<CountryPrefix>,
    onSelected: (CountryPrefix) -> Unit,
    displayText: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val filteredOptions = remember(options, query) {
        if (query.isBlank()) {
            options
        } else {
            options.filter { option ->
                option.code.contains(query, ignoreCase = true) ||
                    option.label.contains(query, ignoreCase = true)
            }
        }
    }

    Box(modifier) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayText.ifBlank { value },
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 380.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.profile_search_prefix)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(14.dp)
            )
            filteredOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                        query = ""
                    }
                )
            }
        }
    }
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
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayText.ifBlank { value },
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
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
    candidates: List<EmergencyContactCandidate>,
    selectedIds: List<String>,
    message: String,
    onMessageChange: (String) -> Unit,
    onToggleContact: (EmergencyContactCandidate) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val bottomActionHeight = 54.dp
    val bottomActionOffset = 78.dp
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF111827),
            contentColor = Color.White,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 18.dp)
                    .padding(top = 14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = contentBottomSpace)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                        Spacer(Modifier.width(6.dp))
                        Surface(color = Color(0xFF5B2730), shape = RoundedCornerShape(16.dp)) {
                            Text(stringResource(R.string.common_sos), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.emergency_contacts_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.emergency_contacts_description),
                        color = Color.White.copy(alpha = 0.68f),
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
                            Text(stringResource(R.string.emergency_selected_count, selectedIds.size), color = QuataOrange, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(14.dp))
                            Text(stringResource(R.string.emergency_network_users), fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(8.dp))
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(visibleUsers, key = { it.id }) { user ->
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
                                        color = Color.White.copy(alpha = 0.66f)
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
                    colors = ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.Black),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomActionOffset)
                        .fillMaxWidth()
                        .height(bottomActionHeight)
                ) {
                    Text(stringResource(R.string.emergency_save_contacts), fontWeight = FontWeight.ExtraBold)
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
    Surface(
        color = if (selected) QuataOrange else QuataSurface.copy(alpha = 0.5f),
        contentColor = if (selected) Color.Black else Color.White,
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
    Surface(
        color = QuataSurface.copy(alpha = 0.45f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarLetter(user.displayName, modifier = Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.displayName, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(user.neighborhood, color = Color.White.copy(alpha = 0.58f), maxLines = 1)
            }
            OutlinedButton(onClick = onToggle, shape = RoundedCornerShape(14.dp)) {
                Text(if (selected) stringResource(R.string.common_remove) else stringResource(R.string.common_add))
            }
        }
    }
}

private fun Context.createProfileImageUri(): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "quata_profile_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
    }
    return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
