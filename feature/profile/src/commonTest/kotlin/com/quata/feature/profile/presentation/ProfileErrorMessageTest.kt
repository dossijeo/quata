package com.quata.feature.profile.presentation

import com.quata.feature.profile.domain.ProfileInvalidPhone
import com.quata.feature.profile.domain.ProfilePhoneCollision
import com.quata.feature.settings.presentation.AppearanceSettingsStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileErrorMessageTest {
    @Test
    fun `stable validation and collision codes use product copy`() {
        val strings = testStrings().copy(invalidPhone = "invalid phone", phoneAlreadyInUse = "collision")
        assertEquals("invalid phone", profileErrorMessage(ProfileInvalidPhone, strings))
        assertEquals("collision", profileErrorMessage(ProfilePhoneCollision, strings))
        assertEquals("remote_failure", profileErrorMessage("remote_failure", strings))
    }

    private fun testStrings() = ProfileScreenStrings(
        loading = "Loading", myData = "Data", management = "Management",
        managementDescription = "Description", configureEmergency = "Emergency",
        saveChanges = "Save", saving = "Saving", logout = "Logout", name = "Name",
        neighborhood = "Neighborhood", phone = "Phone", newPassword = "Password",
        secretQuestion = "Question", newSecretAnswer = "Answer", back = "Back",
        deactivate = "Deactivate", deleteData = "Delete", dangerConfirmation = "Confirm action",
        confirm = "Confirm", cancel = "Cancel",
        appearance = AppearanceSettingsStrings("Touch", "Theme", "System", "Dark", "Light"),
        emergency = EmergencyContactsEditorStrings(
            header = EmergencyContactsHeaderStrings("Back", "SOS", "Emergency", "Description", "Contacts", "Message"),
            selectedCount = { "$it selected" }, networkUsers = "Network", importContacts = "Import",
            requestContactsPermission = "Permission", contactPickerUnavailable = "Unavailable",
            contactPickerCancelled = "Cancelled", contactPickerFailed = "Failed",
            contactsPicked = { "$it picked" }, contactsPermissionGranted = "Granted",
            contactsPermissionDenied = "Denied", contactsPermissionPermanentlyDenied = "Blocked",
            contactsPermissionUnavailable = "Unavailable", searchPlaceholder = "Search",
            messageTitle = "Message", messageHint = "Hint", savePortrait = "Save", saveLandscape = "Save",
        ),
        passwordUnavailable = "Unavailable", loadingError = "Error", retry = "Retry",
    )
}
