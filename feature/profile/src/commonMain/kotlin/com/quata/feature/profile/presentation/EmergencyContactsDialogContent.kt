package com.quata.feature.profile.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quata.feature.profile.domain.EmergencyContactCandidate

/**
 * Platform-owned pieces of the SOS editor. Contacts discovery/permission state stays above this
 * boundary; the portable dialog owns filtering, selection, responsive layout and save feedback.
 */
class EmergencyContactsDialogSlots(
    val contactRow: @Composable (EmergencyContactCandidate, Boolean, () -> Unit) -> Unit,
    val messageInput: @Composable (Modifier, String, (String) -> Unit, Int, Int?) -> Unit,
)

/**
 * Complete portable SOS contacts dialog body for portrait and landscape hosts.
 *
 * The host supplies localized [strings], IME/layout signals and platform-dependent avatar/input
 * slots. Permission prompts, contact launchers and navigation callbacks intentionally remain
 * outside commonMain and feed this content through [candidates] and callbacks.
 */
@Composable
fun EmergencyContactsDialogContent(
    layoutPadding: androidx.compose.foundation.layout.PaddingValues,
    isLandscapeLayout: Boolean,
    isImeVisible: Boolean,
    candidates: List<EmergencyContactCandidate>,
    selectedIds: List<String>,
    message: String,
    isSaving: Boolean,
    strings: EmergencyContactsEditorStrings,
    onMessageChange: (String) -> Unit,
    onToggleContact: (EmergencyContactCandidate) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    slots: EmergencyContactsDialogSlots,
) {
    EmergencyContactsEditorContent(
        layoutPadding = layoutPadding,
        isLandscapeLayout = isLandscapeLayout,
        isImeVisible = isImeVisible,
        candidates = candidates,
        selectedIds = selectedIds,
        message = message,
        isSaving = isSaving,
        strings = strings,
        onMessageChange = onMessageChange,
        onToggleContact = onToggleContact,
        onDismiss = onDismiss,
        onSave = onSave,
        userRow = slots.contactRow,
        messageInput = slots.messageInput,
    )
}
