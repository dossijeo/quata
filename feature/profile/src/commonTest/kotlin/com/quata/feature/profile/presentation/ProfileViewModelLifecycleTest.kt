package com.quata.feature.profile.presentation

import com.quata.core.common.AppDispatchers
import com.quata.feature.profile.domain.ProfileEditConfig
import com.quata.feature.profile.domain.ProfileEditModel
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.profile.domain.ProfileUpdate
import com.quata.feature.profile.domain.UserProfile
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelLifecycleTest {
    @Test
    fun never_emitting_repository_is_cancelled_when_host_closes() = runTest {
        var cancelled = false
        val repository = RecordingRepository(stream = {
            flow {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        })
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = ProfileViewModel(repository, AppDispatchers(dispatcher, dispatcher, dispatcher))
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.close()
        runCurrent()

        assertTrue(cancelled)
    }

    @Test
    fun terminal_initial_failure_is_visible_and_explicit_retry_reconnects() = runTest {
        val model = profileModel()
        val repository = RecordingRepository(stream = { attempt ->
            if (attempt == 1) flowOf(Result.failure(IllegalStateException("ios_profile_session_timeout")))
            else flowOf(Result.success(model))
        })
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = ProfileViewModel(repository, AppDispatchers(dispatcher, dispatcher, dispatcher))
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.profile)
        assertEquals("ios_profile_session_timeout", viewModel.uiState.value.errorMessage)

        viewModel.onEvent(ProfileUiEvent.Refresh)
        runCurrent()

        assertEquals(2, repository.attempts)
        assertEquals("Profile", viewModel.uiState.value.profile?.displayName)
        assertNull(viewModel.uiState.value.errorMessage)
        viewModel.close()
    }

    @Test
    fun sos_save_failure_keeps_editor_state_open_for_retry_without_success() = runTest {
        val repository = RecordingRepository(
            stream = { flowOf(Result.success(profileModel())) },
            emergencySaveResult = Result.failure(IllegalStateException("remote_sos_save_failed")),
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = ProfileViewModel(repository, AppDispatchers(dispatcher, dispatcher, dispatcher))
        runCurrent()

        viewModel.onEvent(ProfileUiEvent.EmergencyContactToggled("contact-1"))
        viewModel.onEvent(ProfileUiEvent.SaveEmergencySettings)
        runCurrent()

        assertEquals(listOf("contact-1"), repository.savedEmergencyContactIds.single())
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals("remote_sos_save_failed", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.emergencySettingsSaved)
        assertNull(viewModel.uiState.value.successMessage)
        assertEquals(listOf("contact-1"), viewModel.uiState.value.profile?.emergencyContactIds)
        viewModel.close()
    }

    private class RecordingRepository(
        private val stream: (Int) -> Flow<Result<ProfileEditModel>>,
        private val emergencySaveResult: Result<Unit> = Result.success(Unit),
    ) : ProfileRepository {
        var attempts = 0
        val savedEmergencyContactIds = mutableListOf<List<String>>()
        override fun observeProfileEditModel(): Flow<Result<ProfileEditModel>> = stream(++attempts)
        override suspend fun getProfileEditModel() = error("unused")
        override suspend fun saveProfile(update: ProfileUpdate) = Result.success(Unit)
        override suspend fun saveEmergencySettings(contactIds: List<String>, message: String, messageIsDefault: Boolean): Result<Unit> {
            savedEmergencyContactIds += contactIds
            return emergencySaveResult
        }
        override fun defaultEmergencyMessage(displayName: String) = "Emergency $displayName"
        override fun changesSavedMessage() = "Saved"
        override fun emergencyContactsSavedMessage() = "SOS saved"
    }

    private fun profileModel() = ProfileEditModel(
        profile = UserProfile(
            displayName = "Profile",
            neighborhood = "Centro",
            countryCode = "240",
            phone = "600000000",
            emergencyMessage = "Help",
        ),
        config = ProfileEditConfig(emptyList(), emptyList(), emptyList()),
    )
}
