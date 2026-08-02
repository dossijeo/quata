package com.quata.feature.whatsnew.presentation

import com.quata.feature.whatsnew.domain.PendingRelease
import com.quata.feature.whatsnew.domain.WhatsNewRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class WhatsNewStartupCoordinatorTest {
    @Test
    fun emptyCatalogAcknowledgesInstalledVersion() = kotlinx.coroutines.test.runTest {
        val acknowledgements = MemoryAcknowledgements()
        val decision = WhatsNewStartupCoordinator(EmptyRepository, acknowledgements).evaluate(12, listOf("es"))

        assertEquals(WhatsNewStartupDecision.Skip, decision.getOrThrow())
        assertEquals(12, acknowledgements.value)
    }
}

private class MemoryAcknowledgements : WhatsNewStartupAcknowledgementStore {
    var value: Long? = null
    override suspend fun readAcknowledgedVersionCode(): Result<Long?> = Result.success(value)
    override suspend fun writeAcknowledgedVersionCode(versionCode: Long): Result<Unit> = Result.success(Unit.also { value = versionCode })
}

private object EmptyRepository : WhatsNewRepository {
    override suspend fun getPendingReleases(installedVersionCode: Long, languageTags: List<String>): Result<List<PendingRelease>> = Result.success(emptyList())
    override suspend fun getReleaseHistory(languageTags: List<String>): Result<List<PendingRelease>> = Result.success(emptyList())
    override suspend fun initializeForNewUser(installedVersionCode: Long): Result<Unit> = Result.success(Unit)
    override suspend fun markReleasesSeen(upToVersionCode: Long, installedVersionCode: Long): Result<Unit> = Result.success(Unit)
}
