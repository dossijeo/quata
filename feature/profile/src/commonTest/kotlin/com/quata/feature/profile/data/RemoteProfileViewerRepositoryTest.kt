package com.quata.feature.profile.data

import com.quata.feature.profile.domain.ProfileViewerResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RemoteProfileViewerRepositoryTest {
    @Test
    fun `member viewer keeps selected id and marks only session owner as current`() = runTest {
        val result = RemoteProfileViewerRepository(
            remote = FakeGateway(ProfileRemoteRecord(id = "member-42", displayName = " Member ", neighborhood = " Centro ")),
            sessions = FakeSessionProvider(ProfileSession(profileId = "self-1", displayName = "Self")),
        ).observeProfile("member-42").first()

        val available = assertIs<ProfileViewerResult.Available>(result)
        assertEquals("member-42", available.profile.id)
        assertEquals("Member", available.profile.displayName)
        assertEquals("Centro", available.profile.neighborhood)
        assertEquals(false, available.profile.isCurrentUser)
    }

    @Test
    fun `unreadable member profile is unavailable rather than fabricated`() = runTest {
        val result = RemoteProfileViewerRepository(
            remote = FakeGateway(null),
            sessions = FakeSessionProvider(ProfileSession(profileId = "self-1", displayName = "Self")),
        ).observeProfile("other-member").first()

        assertEquals(ProfileViewerResult.Unavailable, result)
    }
}

private class FakeSessionProvider(private val session: ProfileSession?) : ProfileSessionProvider {
    override fun currentSession(): ProfileSession? = session
    override fun updateDisplayName(session: ProfileSession, displayName: String) = Unit
}

private class FakeGateway(private val record: ProfileRemoteRecord?) : ProfileRemoteGateway {
    override suspend fun getProfile(profileId: String): ProfileRemoteRecord? = record
    override suspend fun getProfiles(profileIds: Collection<String>): List<ProfileRemoteRecord> = record?.let(::listOf).orEmpty()
    override fun observeProfile(profileId: String): Flow<ProfileRemoteRecord?> = emptyFlow()
    override suspend fun getEmergencyCandidates(): List<ProfileRemoteRecord> = emptyList()
    override fun observeEmergencyCandidates(): Flow<List<ProfileRemoteRecord>> = emptyFlow()
    override suspend fun getEmergencyContactIds(profileId: String, cachePolicy: ProfileCachePolicy): List<String> = emptyList()
    override suspend fun saveProfile(profileId: String, patch: Map<String, String?>) = Unit
    override suspend fun saveEmergencyContacts(profileId: String, contactIds: List<String>) = Unit
}
