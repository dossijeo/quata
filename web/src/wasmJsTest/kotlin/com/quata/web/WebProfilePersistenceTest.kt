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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

class WebProfilePersistenceTest {
    @Test
    fun selectsRemoteOnlyForConfiguredAuthenticatedSession() {
        assertEquals(
            WebProfilePersistenceMode.Remote,
            webProfilePersistenceMode(hasRemoteRepository = true, hasConfiguredAuthenticatedSession = true),
        )
        assertEquals(
            WebProfilePersistenceMode.Unavailable,
            webProfilePersistenceMode(hasRemoteRepository = true, hasConfiguredAuthenticatedSession = false),
        )
        assertEquals(
            WebProfilePersistenceMode.Unavailable,
            webProfilePersistenceMode(hasRemoteRepository = false, hasConfiguredAuthenticatedSession = true),
        )
    }

    @Test
    fun unavailableRuntimeDoesNotTouchTheConstructedRemoteRepository() = runTest {
        val gateway = RecordingGateway()
        val repository = WebProfileRepository(
            preferences = RecordingPreferences(),
            contactPicker = UnsupportedContactPicker,
            remoteGateway = gateway,
            remoteSessionProvider = FixedSessionProvider,
            remoteAvailable = { false },
        )

        val result = repository.getProfileEditModel()

        assertTrue(result.isFailure)
        assertEquals("web_profile_remote_session_unavailable", result.exceptionOrNull()?.message)
        assertTrue(gateway.getRequests.isEmpty())
    }

    @Test
    fun mutationActorGateOnlyAcceptsTheAuthenticatedProfile() {
        assertEquals("profile-1", requireWebProfileActor("profile-1", "profile-1"))
        val mismatch = runCatching { requireWebProfileActor("profile-1", "profile-2") }.exceptionOrNull()
        assertEquals("web_profile_actor_mismatch", mismatch?.message)
    }

    @Test
    fun profileMutationHttpFailureNeverLooksLikeSuccess() {
        val failure = runCatching {
            WebPostgrestResult.Failure(WebPostgrestFailureKind.RlsDenied, "denied", 403)
                .requireProfileMutationSuccess("patch")
        }.exceptionOrNull()
        assertEquals("web_profile_patch_rlsdenied_403", failure?.message)
    }

    @Test
    fun recoverySecretRequestCarriesTheRequiredVersionAndAction() {
        val request = webRecoverySecretRequest("  mascota ", "Luna")
        assertEquals(1, request.getValue("version").jsonPrimitive.int)
        assertEquals("update_recovery_secret", request.getValue("action").jsonPrimitive.content)
        assertEquals("mascota", request.getValue("secret_question").jsonPrimitive.content)
        assertEquals("Luna", request.getValue("secret_answer").jsonPrimitive.content)
    }

    @Test
    fun existingRemoteAvatarPassesThroughButANewLocalReferenceFailsClosed() {
        assertEquals("https://cdn.example/avatar.jpg", webProfileAvatarUploadReference(" https://cdn.example/avatar.jpg "))
        assertEquals(null, webProfileAvatarUploadReference("  "))
        val failure = assertFailsWith<UnsupportedOperationException> {
            webProfileAvatarUploadReference("blob:browser-avatar")
        }
        assertEquals("web_profile_avatar_upload_not_available", failure.message)
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
    fun authenticatedGetFailureDoesNotFallBackToAProductDraft() = runTest {
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
    fun authenticatedMutationsUseRemoteGatewayAndNeverWriteLocalDraft() = runTest {
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

        assertTrue(profileResult.isSuccess)
        assertTrue(sosResult.isSuccess)
        assertTrue(gateway.mutationAttempted)
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
