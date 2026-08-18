package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.platform.ContactPickerService
import com.quata.core.platform.PermissionService
import com.quata.core.platform.PermissionStatus
import com.quata.core.platform.PlatformContact
import com.quata.core.platform.PlatformPermission
import com.quata.core.platform.PlatformResult
import com.quata.core.ui.components.CompactIcon
import kotlinx.coroutines.launch

const val ProfileSosContactActionsTestTag = "profile.sos.contacts.actions"
const val ProfileSosContactImportTestTag = "profile.sos.contacts.import"
const val ProfileSosContactPermissionTestTag = "profile.sos.contacts.permission"
const val ProfileSosContactStatusTestTag = "profile.sos.contacts.status"

@Composable
fun EmergencyContactsContactActionsContent(
    strings: EmergencyContactsEditorStrings,
    contacts: ContactPickerService,
    permissions: PermissionService,
    modifier: Modifier = Modifier,
    onContactsPicked: (List<PlatformContact>) -> Unit = {},
    onContactPickerResult: (PlatformResult<List<PlatformContact>>) -> Unit = {},
    onContactsPermissionResult: (PermissionStatus) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                testTag = ProfileSosContactActionsTestTag
                contentDescription = ProfileSosContactActionsTestTag
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                scope.launch {
                    when (val result = contacts.pickContacts()) {
                        is PlatformResult.Success -> {
                            onContactsPicked(result.value)
                            statusMessage = strings.contactsPicked(result.value.size)
                        }
                        PlatformResult.Cancelled -> {
                            statusMessage = strings.contactPickerCancelled
                            onContactPickerResult(result)
                        }
                        PlatformResult.Unsupported -> {
                            statusMessage = strings.contactPickerUnavailable
                            onContactPickerResult(result)
                        }
                        is PlatformResult.Failure -> {
                            statusMessage = result.reason?.takeIf(String::isNotBlank) ?: strings.contactPickerFailed
                            onContactPickerResult(result)
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    testTag = ProfileSosContactImportTestTag
                    contentDescription = ProfileSosContactImportTestTag
                },
        ) {
            CompactIcon(Icons.Filled.Contacts, null)
            Spacer(Modifier.width(6.dp))
            Text(strings.importContacts)
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val status = permissions.request(PlatformPermission.Contacts)
                    onContactsPermissionResult(status)
                    statusMessage = strings.contactsPermissionMessage(status)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    testTag = ProfileSosContactPermissionTestTag
                    contentDescription = ProfileSosContactPermissionTestTag
                },
        ) {
            CompactIcon(Icons.Filled.LockOpen, null)
            Spacer(Modifier.width(6.dp))
            Text(strings.requestContactsPermission)
        }
        statusMessage?.takeIf(String::isNotBlank)?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics {
                    testTag = ProfileSosContactStatusTestTag
                    contentDescription = ProfileSosContactStatusTestTag
                },
            )
        }
    }
}

fun EmergencyContactsEditorStrings.contactsPermissionMessage(status: PermissionStatus): String = when (status) {
    PermissionStatus.Granted -> contactsPermissionGranted
    PermissionStatus.Denied -> contactsPermissionDenied
    PermissionStatus.PermanentlyDenied -> contactsPermissionPermanentlyDenied
    PermissionStatus.Unavailable -> contactsPermissionUnavailable
}
