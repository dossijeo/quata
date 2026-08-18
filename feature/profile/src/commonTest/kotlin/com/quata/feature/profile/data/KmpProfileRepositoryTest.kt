package com.quata.feature.profile.data

import com.quata.core.model.CountryPrefix
import com.quata.feature.profile.domain.SecretQuestionOption
import com.quata.feature.profile.domain.ProfileUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KmpProfileRepositoryTest {
    @Test
    fun `remote patch preserves canonical and compatibility fields`() {
        val patch = ProfileUpdate(
            displayName = "Ada",
            neighborhood = "Centro",
            countryCode = "+240",
            phone = " 555 123 ",
            avatarUri = "  https://cdn.example/avatar.jpg ",
            newPassword = "",
            secretQuestion = "pet",
            secretAnswer = "Luna",
            emergencyContactIds = emptyList(),
            emergencyMessage = "SOS",
            emergencyMessageIsDefault = false
        ).toRemotePatch()

        assertEquals("+240555123", patch["phone"])
        assertEquals("Ada", patch["display_name"])
        assertEquals("Ada", patch["nombre"])
        assertEquals("https://cdn.example/avatar.jpg", patch["avatar_url"])
        assertFalse(patch.containsKey("secret_question"))
        assertFalse(patch.containsKey("secret_answer"))
    }

    @Test
    fun `remote profile normalizes phone and prefers custom emergency message`() {
        val profile = ProfileRemoteRecord(
            id = "profile-1",
            displayName = " Ada ",
            countryCode = "+240",
            phoneLocal = "555 123",
            avatarUrl = "  "
        ).toUserProfile(
            fallbackName = "Fallback",
            emergencyContactIds = listOf("contact"),
            storedMessage = StoredProfileEmergencyMessage("Ayuda", isDefault = false),
            defaultMessage = { "Default $it" }
        )

        assertEquals("Ada", profile.displayName)
        assertEquals("240", profile.countryCode)
        assertEquals("555123", profile.phone)
        assertEquals("Ayuda", profile.emergencyMessage)
        assertNull(profile.avatarUri)
    }

    @Test
    fun `emergency contacts are trimmed deduplicated and limited`() {
        assertEquals(
            listOf("a", "b", "c", "d", "e"),
            normalizeEmergencyContactIds(listOf(" a ", "", "a", "b", "c", "d", "e", "f"))
        )
    }

    @Test
    fun `profile password cannot report success without an atomic bridge contract`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            requireProfilePasswordUpdateSupported("NewPassword7")
        }
        assertEquals("profile_password_update_unavailable", failure.message)
        requireProfilePasswordUpdateSupported("")
    }

    @Test
    fun `password rejection happens before any remote mutation boundary`() {
        val calls = mutableListOf<String>()
        val rejected = runCatching {
            requireProfilePasswordUpdateSupported("NewPassword7")
            calls += "remote_mutation"
        }.exceptionOrNull()
        assertEquals("profile_password_update_unavailable", rejected?.message)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `sos candidate retains its remote avatar for the shared row slot`() {
        val candidate = ProfileRemoteRecord(
            id = "sos-1",
            displayName = "SOS",
            avatarUrl = "https://cdn.example/sos.jpg",
        ).toEmergencyCandidate()
        assertEquals("https://cdn.example/sos.jpg", candidate.avatarUrl)
    }

    @Test
    fun `failed remote emergency save does not overwrite local SOS cache`() = runTest {
        val remote = FailingEmergencyRemoteGateway()
        val contactsStore = RecordingEmergencyContactsStore()
        val messageStore = RecordingEmergencyMessageStore()
        val repository = KmpProfileRepository(
            remote = remote,
            sessions = StaticProfileSessionProvider(ProfileSession("profile-1", "Ada")),
            avatarUploader = object : ProfileAvatarUploader {
                override suspend fun uploadIfNeeded(profileId: String, avatarUri: String?) = avatarUri
            },
            emergencyMessages = messageStore,
            emergencyContacts = contactsStore,
            catalog = TestProfileCatalog,
        )

        val result = repository.saveEmergencySettings(
            contactIds = listOf(" contact-a ", "contact-b", "contact-a"),
            message = "Help",
            messageIsDefault = false,
        )

        assertTrue(result.isFailure)
        assertEquals("remote_sos_save_failed", result.exceptionOrNull()?.message)
        assertEquals(listOf("contact-a", "contact-b"), remote.lastSavedContactIds)
        assertTrue(contactsStore.saved.isEmpty())
        assertTrue(messageStore.saved.isEmpty())
    }
}

private class FailingEmergencyRemoteGateway : ProfileRemoteGateway {
    var lastSavedContactIds: List<String> = emptyList()
    override suspend fun getProfile(profileId: String): ProfileRemoteRecord? = null
    override suspend fun getProfiles(profileIds: Collection<String>): List<ProfileRemoteRecord> = emptyList()
    override fun observeProfile(profileId: String): Flow<ProfileRemoteRecord?> = flowOf(null)
    override suspend fun getEmergencyCandidates(): List<ProfileRemoteRecord> = emptyList()
    override fun observeEmergencyCandidates(): Flow<List<ProfileRemoteRecord>> = flowOf(emptyList())
    override suspend fun getEmergencyContactIds(profileId: String, cachePolicy: ProfileCachePolicy): List<String> = emptyList()
    override suspend fun saveProfile(profileId: String, patch: Map<String, String?>) = Unit
    override suspend fun saveEmergencyContacts(profileId: String, contactIds: List<String>) {
        lastSavedContactIds = contactIds
        error("remote_sos_save_failed")
    }
}

private class StaticProfileSessionProvider(private val session: ProfileSession?) : ProfileSessionProvider {
    override fun currentSession(): ProfileSession? = session
    override fun updateDisplayName(session: ProfileSession, displayName: String) = Unit
}

private class RecordingEmergencyContactsStore : ProfileEmergencyContactsStore {
    val saved = mutableListOf<List<String>>()
    override suspend fun get(profileId: String): List<String> = emptyList()
    override suspend fun save(profileId: String, contactIds: List<String>) {
        saved += contactIds
    }
}

private class RecordingEmergencyMessageStore : ProfileEmergencyMessageStore {
    val saved = mutableListOf<StoredProfileEmergencyMessage>()
    override suspend fun get(profileId: String): StoredProfileEmergencyMessage? = null
    override suspend fun save(profileId: String, message: String, isDefault: Boolean) {
        saved += StoredProfileEmergencyMessage(message, isDefault)
    }
}

private object TestProfileCatalog : ProfilePresentationCatalog {
    override fun countryPrefixes(): List<CountryPrefix> = emptyList()
    override fun secretQuestions(): List<SecretQuestionOption> = emptyList()
    override fun fallbackUserName(): String = "Usuario"
    override fun defaultEmergencyMessage(displayName: String): String = "SOS $displayName"
    override fun changesSavedMessage(): String = "Saved"
    override fun emergencyContactsSavedMessage(): String = "SOS saved"
}
