package com.quata.feature.profile.presentation

/**
 * Platform-neutral selection policy for the SOS editor.
 *
 * The contacts source, permission request and row avatar remain host concerns. This keeps the
 * state transition used by the responsive Compose editor in commonMain and preserves the
 * existing maximum of five emergency contacts.
 */
fun toggleEmergencyContactSelection(
    selectedIds: List<String>,
    contactId: String,
): List<String> {
    val selected = selectedIds.distinct().take(MaxEmergencyContactSelection).toMutableList()
    if (contactId in selected) {
        selected.remove(contactId)
    } else if (selected.size < MaxEmergencyContactSelection) {
        selected += contactId
    }
    return selected
}

const val MaxEmergencyContactSelection = 5
