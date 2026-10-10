package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.common.AppDispatchers
import com.quata.core.model.CountryPrefix
import com.quata.feature.profile.domain.ProfileEditConfig
import com.quata.feature.profile.domain.ProfileEditModel
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.profile.domain.ProfileUpdate
import com.quata.feature.profile.domain.UserProfile
import com.quata.feature.settings.presentation.AppearanceSettingsStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class ProfilePrefixRenderedInteractionTest {
    @Test
    fun renderedSelectorOptionAndSavePreserveTheActorPhoneAndSelectedPrefix() = runComposeUiTest {
        val repository = PrefixRepository()
        val scheduler = TestCoroutineScheduler()
        setContent {
            QuataTheme {
                ProfileScreenHost(
                    repository = repository,
                    strings = profileStrings(),
                    touchFlowEnabled = false,
                    onTouchFlowEnabledChange = {},
                    themeMode = QuataThemeMode.System,
                    onThemeModeChange = {},
                    onLogout = {},
                    onDeactivateAccount = {},
                    onDeleteAccountData = {},
                    slots = ProfileScreenSlots(
                        avatar = { _, _ -> Box(androidx.compose.ui.Modifier) },
                        emergencyContactRow = { _, _, _ -> },
                    ),
                    dispatchers = AppDispatchers(default = StandardTestDispatcher(scheduler)),
                )
            }
        }

        scheduler.runCurrent()
        waitUntil { onAllNodesWithTag(ProfileDetailsOpenTestTag).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(ProfileDetailsOpenTestTag).performClick()
        onNodeWithTag(ProfileDetailsCountryCodeButtonTestTag).performClick()
        onNodeWithTag(profileDetailsCountryCodeOptionTestTag("34")).performClick()
        onNodeWithTag(ProfileDetailsSaveTestTag).performClick()

        scheduler.runCurrent()
        waitUntil { repository.saves.size == 1 }
        runOnIdle {
            val saved = repository.saves.single()
            assertEquals("34", saved.countryCode)
            assertEquals("600000000", saved.phone)
            assertEquals("Ada", saved.displayName)
        }
        onNodeWithTag(ProfileDetailsCountryCodeButtonTestTag).assertTextContains("+34")
    }
}

private class PrefixRepository : ProfileRepository {
    private val model = MutableStateFlow(Result.success(editModel("240")))
    val saves = mutableListOf<ProfileUpdate>()

    override fun observeProfileEditModel(): Flow<Result<ProfileEditModel>> = model
    override suspend fun getProfileEditModel(): Result<ProfileEditModel> = model.value
    override suspend fun saveProfile(update: ProfileUpdate): Result<Unit> {
        saves += update
        model.value = Result.success(editModel(update.countryCode))
        return Result.success(Unit)
    }
    override suspend fun saveEmergencySettings(contactIds: List<String>, message: String, messageIsDefault: Boolean) = Result.success(Unit)
    override fun defaultEmergencyMessage(displayName: String) = "Help $displayName"
    override fun changesSavedMessage() = "Saved"
    override fun emergencyContactsSavedMessage() = "SOS saved"

    private fun editModel(code: String) = ProfileEditModel(
        profile = UserProfile(
            displayName = "Ada",
            neighborhood = "Centro",
            countryCode = code,
            phone = "600000000",
            emergencyMessage = "Help Ada",
        ),
        config = ProfileEditConfig(
            countryPrefixes = listOf(CountryPrefix("240", "+240"), CountryPrefix("34", "+34")),
            secretQuestions = emptyList(),
            emergencyCandidates = emptyList(),
        ),
    )
}

private fun profileStrings() = ProfileScreenStrings(
    loading = "Loading",
    myData = "My data",
    management = "Management",
    managementDescription = "Management description",
    configureEmergency = "Emergency",
    saveChanges = "Save",
    saving = "Saving",
    logout = "Logout",
    name = "Name",
    neighborhood = "Neighborhood",
    phone = "Phone",
    newPassword = "Password",
    secretQuestion = "Question",
    newSecretAnswer = "Answer",
    back = "Back",
    deactivate = "Deactivate",
    deleteData = "Delete",
    dangerConfirmation = "Confirm action",
    confirm = "Confirm",
    cancel = "Cancel",
    appearance = AppearanceSettingsStrings("Touch", "Theme", "System", "Dark", "Light"),
    emergency = EmergencyContactsEditorStrings(
        header = EmergencyContactsHeaderStrings("Back", "SOS", "Emergency", "Description", "Contacts", "Message"),
        selectedCount = { "$it selected" },
        networkUsers = "Network",
        importContacts = "Import",
        requestContactsPermission = "Permission",
        contactPickerUnavailable = "Unavailable",
        contactPickerCancelled = "Cancelled",
        contactPickerFailed = "Failed",
        contactsPicked = { "$it picked" },
        contactsPermissionGranted = "Granted",
        contactsPermissionDenied = "Denied",
        contactsPermissionPermanentlyDenied = "Blocked",
        contactsPermissionUnavailable = "Unavailable",
        searchPlaceholder = "Search",
        messageTitle = "Message",
        messageHint = "Hint",
        savePortrait = "Save",
        saveLandscape = "Save",
    ),
    passwordUnavailable = "Unavailable",
    loadingError = "Error",
    retry = "Retry",
)
