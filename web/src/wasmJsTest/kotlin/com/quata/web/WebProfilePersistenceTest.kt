package com.quata.web

import com.quata.core.platform.ContactPickerService
import com.quata.core.platform.PlatformContact
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.PreferenceStore
import com.quata.feature.profile.data.ProfileCachePolicy
import com.quata.feature.profile.data.ProfileRemoteGateway
import com.quata.feature.profile.data.ProfileRemoteRecord
import com.quata.feature.profile.data.ProfileSession
import com.quata.feature.profile.data.ProfileSessionProvider
import com.quata.feature.profile.domain.ProfileUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebProfilePersistenceTest {
    @Test
    fun selectsRemoteReadOnlyForConfiguredAuthenticatedSession() {
        assertEquals(
            WebProfilePersistenceMode.Remote,
            webProfilePersistenceMode(hasRemoteRepository = true, hasConfiguredAuthenticatedSession = true),
        )
        assertEquals(
            WebProfilePersistenceMode.OfflineDraft,
            webProfilePersistenceMode(hasRemoteRepository = true, hasConfiguredAuthenticatedSession = false),
        )
        assertEquals(
            WebProfilePersistenceMode.OfflineDraft,
            webProfilePersistenceMode(hasRemoteRepository = false, hasConfiguredAuthenticatedSession = true),
        )
    }

    @Test
    fun authenticatedGetUsesRemoteRecordsAndNeverWritesLocalDraft() = runTest {
        val gateway = RecordingGateway()
        val preferences = RecordingPreferences()
        val repository = WebProfileRepository(
            preferences = preferences,
            contactPicker = UnsupportedContactPicker,
            remoteGateway = gateway,
            remoteSessionProvider = FixedSessionProvider,
            remoteAvailable = { true },
        )

        val model = repository.getProfileEditModel().getOrThrow()

        assertEquals("Remoto", model.profile.displayName)
        assertEquals(listOf("sos-1"), model.profile.emergencyContactIds)
        assertEquals(listOf("profile-1"), gateway.getRequests)
        assertEquals(1, gateway.emergencyCandidatesRequests)
        assertTrue(preferences.writes.isEmpty())
        assertEquals(WebProfilePersistenceMode.Remote, repository.persistenceMode())
    }

    @Test
    fun authenticatedGetFailureDoesNotFallBackToOfflineDraft() = runTest {
        val preferences = RecordingPreferences().also { it.values["web.profile.display_name"] = "Borrador" }
        val repository = WebProfileRepository(
            preferences = preferences,
            contactPicker = UnsupportedContactPicker,
            remoteGateway = RecordingGateway(failReads = true),
            remoteSessionProvider = FixedSessionProvider,
            remoteAvailable = { true },
        )

        val result = repository.getProfileEditModel()

        assertTrue(result.isFailure)
        assertEquals("remote_get_failed", result.exceptionOrNull()?.message)
        assertTrue(preferences.writes.isEmpty())
        assertEquals(WebProfilePersistenceMode.Remote, repository.persistenceMode())
    }

    @Test
    fun authenticatedMutationsFailClosedWithoutPatchPostDeleteOrLocalWrites() = runTest {
        val gateway = RecordingGateway()
        val preferences = RecordingPreferences()
        val repository = WebProfileRepository(
            preferences = preferences,
            contactPicker = UnsupportedContactPicker,
            remoteGateway = gateway,
            remoteSessionProvider = FixedSessionProvider,
            remoteAvailable = { true },
        )

        val profileResult = repository.saveProfile(
            ProfileUpdate(
                displayName = "Nuevo",
                neighborhood = "Centro",
                countryCode = "240",
                phone = "600",
                avatarUri = null,
                newPassword = "",
                secretQuestion = "",
                secretAnswer = "",
                emergencyContactIds = emptyList(),
                emergencyMessage = "",
                emergencyMessageIsDefault = true,
            ),
        )
        val sosResult = repository.saveEmergencySettings(listOf("sos-1"), "Ayuda", false)

        assertEquals("web_profile_mutation_contract_unverified", profileResult.exceptionOrNull()?.message)
        assertEquals("web_profile_mutation_contract_unverified", sosResult.exceptionOrNull()?.message)
        assertFalse(gateway.mutationAttempted)
        assertTrue(preferences.writes.isEmpty())
    }

    private class RecordingGateway(private val failReads: Boolean = false) : ProfileRemoteGateway {
        val getRequests = mutableListOf<String>()
        var emergencyCandidatesRequests = 0
        var mutationAttempted = false
        override suspend fun getProfile(profileId: String): ProfileRemoteRecord? {
            getRequests += profileId
            if (failReads) error("remote_get_failed")
            return ProfileRemoteRecord(id = profileId, displayName = "Remoto", neighborhood = "Malabo", countryCode = "240", phoneLocal = "600")
        }
        override suspend fun getProfiles(profileIds: Collection<String>): List<ProfileRemoteRecord> {
            getRequests += profileIds
            return profileIds.map { ProfileRemoteRecord(id = it, displayName = "Contacto") }
        }
        override fun observeProfile(profileId: String): Flow<ProfileRemoteRecord?> = flowOf(getProfileForFlow(profileId))
        override suspend fun getEmergencyCandidates(): List<ProfileRemoteRecord> {
            emergencyCandidatesRequests++
            if (failReads) error("remote_get_failed")
            return listOf(ProfileRemoteRecord(id = "sos-1", displayName = "SOS remoto"))
        }
        override fun observeEmergencyCandidates(): Flow<List<ProfileRemoteRecord>> = flowOf(emptyList())
        override suspend fun getEmergencyContactIds(profileId: String, cachePolicy: ProfileCachePolicy): List<String> {
            if (failReads) error("remote_get_failed")
            return listOf("sos-1")
        }
        override suspend fun saveProfile(profileId: String, patch: Map<String, String?>) { mutationAttempted = true }
        override suspend fun saveEmergencyContacts(profileId: String, contactIds: List<String>) { mutationAttempted = true }
        private fun getProfileForFlow(profileId: String): ProfileRemoteRecord? =
            if (failReads) null else ProfileRemoteRecord(id = profileId, displayName = "Remoto")
    }

    private class RecordingPreferences : PreferenceStore {
        val values = mutableMapOf<String, String>()
        val writes = mutableListOf<String>()
        override suspend fun getString(key: String): String? = values[key]
        override suspend fun putString(key: String, value: String) { writes += key; values[key] = value }
        override suspend fun remove(key: String) { writes += key; values.remove(key) }
    }

    private object FixedSessionProvider : ProfileSessionProvider {
        override fun currentSession(): ProfileSession = ProfileSession("profile-1", "Sesión")
        override fun updateDisplayName(session: ProfileSession, displayName: String) = Unit
    }

    private object UnsupportedContactPicker : ContactPickerService {
        override suspend fun pickContacts(): PlatformResult<List<PlatformContact>> = PlatformResult.Unsupported
    }
}
