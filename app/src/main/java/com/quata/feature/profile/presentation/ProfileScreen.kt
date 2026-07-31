package com.quata.feature.profile.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.quata.R
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.ui.components.AttachmentPreview
import com.quata.core.ui.components.AttachmentViewerDialog
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.postcomposer.imageeditor.QuataImageEditorDialog
import com.quata.feature.postcomposer.imageeditor.QuataImageEditorMode
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.settings.presentation.AppearanceSettingsStrings
import com.quata.core.ui.components.QuataCameraDialog
import com.quata.core.ui.components.QuataCameraMode

/** Android owns only native media/resources. Account UI and state live in commonMain. */
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
    @Suppress("UNUSED_PARAMETER") viewModel: ProfileAndroidViewModel? = null,
) {
    val context = LocalContext.current
    val isLandscape = rememberQuataWindowLayoutInfo().isLandscape
    var photoMenuOpen by rememberSaveable { mutableStateOf(false) }
    var cameraOpen by rememberSaveable { mutableStateOf(false) }
    var editorUri by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<AttachmentPreview?>(null) }
    var avatarChanged by remember { mutableStateOf<((String?) -> Unit)?>(null) }
    val backDispatcher = remember { ProfileBackDispatcher() }
    BackHandler(enabled = backDispatcher.canConsume) { backDispatcher.dispatch() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { editorUri = it }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (context.hasCameraPermission()) cameraOpen = true
        else Toast.makeText(context, context.getString(R.string.profile_camera_permission_photo), Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(editorUri) { onFullscreenEditorVisibilityChange(editorUri != null) }
    DisposableEffect(Unit) { onDispose { onFullscreenEditorVisibilityChange(false) } }

    Box(Modifier.fillMaxSize()) {
        ProfileScreenHost(
            repository = repository,
            strings = androidProfileStrings(context),
            touchFlowEnabled = touchFlowEnabled,
            onTouchFlowEnabledChange = onTouchFlowEnabledChange,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onLogout = onLogout,
            onDeactivateAccount = onDeactivateAccount,
            onDeleteAccountData = onDeleteAccountData,
            refreshKey = networkReconnectToken,
            contentPadding = padding,
            slots = ProfileScreenSlots(
                isLandscapeLayout = { isLandscape },
                avatar = { name, uri -> AvatarImage(
                    name, uri, profileId = profileId,
                    modifier = Modifier.size(76.dp).clickable(enabled = !uri.isNullOrBlank()) {
                        preview = AttachmentPreview(name, uri ?: return@clickable, "image/jpeg")
                    },
                ) },
                avatarActions = { change ->
                    avatarChanged = change
                    OutlinedButton(onClick = { photoMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        CompactIcon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(4.dp)); Text(context.getString(R.string.profile_change_photo))
                    }
                    DropdownMenu(photoMenuOpen, { photoMenuOpen = false }) {
                        DropdownMenuItem(text = { Text(context.getString(R.string.profile_pick_gallery)) }, onClick = {
                            photoMenuOpen = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        DropdownMenuItem(text = { Text(context.getString(R.string.profile_take_photo)) }, onClick = {
                            photoMenuOpen = false
                            if (context.hasCameraPermission()) cameraOpen = true else permission.launch(arrayOf(Manifest.permission.CAMERA))
                        })
                    }
                },
                emergencyContactRow = { user, selected, toggle -> EmergencyUserRowContent(
                    user, selected, context.getString(R.string.common_add), context.getString(R.string.common_remove),
                    avatar = { AvatarImage(user.displayName, user.avatarUrl, profileId = user.id, modifier = Modifier.size(46.dp)) }, onToggle = toggle,
                ) },
                onProfileSaved = onProfileSaved,
                backDispatcher = backDispatcher,
            ),
        )
        preview?.let { AttachmentViewerDialog(it) { preview = null } }
        editorUri?.let { uri -> QuataImageEditorDialog(uri, onDismiss = { editorUri = null }, onEdited = {
            editorUri = null; avatarChanged?.invoke(it.toString())
        }, mode = QuataImageEditorMode.Avatar) }
        if (cameraOpen) QuataCameraDialog(QuataCameraMode.Photo, onDismiss = { cameraOpen = false }, onPhotoCaptured = { uri, _, _ -> cameraOpen = false; editorUri = uri })
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
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    val isLandscape = rememberQuataWindowLayoutInfo().isLandscape
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    BackHandler(true, onDismiss)
    EmergencyContactsDialogContent(
        layoutPadding, isLandscape, imeBottom > 0, candidates, selectedIds, message, isSaving,
        androidProfileStrings(context).emergency,
        onMessageChange, onToggleContact, onDismiss, onSave,
        EmergencyContactsDialogSlots(
            contactRow = { user, selected, toggle -> EmergencyUserRowContent(
                user, selected, stringResource(R.string.common_add), stringResource(R.string.common_remove),
                avatar = { AvatarImage(user.displayName, null, profileId = user.id, modifier = Modifier.size(46.dp)) }, onToggle = toggle,
            ) },
            messageInput = { modifier, value, change, minLines, maxLines -> OutlinedTextField(
                value, change, modifier = modifier, minLines = minLines, maxLines = maxLines ?: Int.MAX_VALUE, shape = RoundedCornerShape(18.dp),
            ) },
        ),
    )
}

private fun Context.hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun androidProfileStrings(context: Context) = ProfileScreenStrings(
    context.getString(R.string.profile_loading), context.getString(R.string.profile_my_data), context.getString(R.string.profile_account_management), context.getString(R.string.profile_account_management_description), context.getString(R.string.profile_configure_emergency_contacts), context.getString(R.string.common_save_changes), context.getString(R.string.common_saving), context.getString(R.string.profile_logout), context.getString(R.string.auth_name), context.getString(R.string.profile_neighborhood), context.getString(R.string.profile_phone), context.getString(R.string.auth_password), context.getString(R.string.profile_new_secret_answer), context.getString(R.string.profile_new_secret_answer), context.getString(R.string.common_back), context.getString(R.string.legal_account_deletion), context.getString(R.string.legal_data_deletion), context.getString(R.string.profile_account_management_description), context.getString(R.string.common_save_changes), context.getString(R.string.common_back),
    AppearanceSettingsStrings(context.getString(R.string.profile_touch_flow_setting), context.getString(R.string.profile_theme_setting), context.getString(R.string.theme_mode_system), context.getString(R.string.theme_mode_dark), context.getString(R.string.theme_mode_light)),
    EmergencyContactsEditorStrings(EmergencyContactsHeaderStrings(context.getString(R.string.common_back), context.getString(R.string.common_sos), context.getString(R.string.emergency_contacts_title), context.getString(R.string.emergency_contacts_description), context.getString(R.string.emergency_contacts_tab), context.getString(R.string.emergency_message_tab)), { context.getString(R.string.emergency_selected_count, it) }, context.getString(R.string.emergency_network_users), context.getString(R.string.emergency_search_placeholder), context.getString(R.string.emergency_message_title), context.getString(R.string.emergency_message_hint), context.getString(R.string.emergency_save_contacts), context.getString(R.string.emergency_save_contacts_short)),
    context.getString(R.string.profile_password_update_unavailable),
)
