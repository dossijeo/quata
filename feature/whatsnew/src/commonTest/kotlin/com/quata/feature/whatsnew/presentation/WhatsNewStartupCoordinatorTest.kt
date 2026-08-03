package com.quata.feature.whatsnew.presentation

import com.quata.feature.whatsnew.domain.PendingRelease
import com.quata.feature.whatsnew.domain.WhatsNewRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatsNewStartupCoordinatorTest {
    @Test
    fun emptyCatalogAcknowledgesInstalledVersion() = kotlinx.coroutines.test.runTest {
        val acknowledgements = MemoryAcknowledgements()
        val decision = WhatsNewStartupCoordinator(EmptyRepository, acknowledgements).evaluate(12, listOf("es"))

        assertEquals(WhatsNewStartupDecision.Skip, decision.getOrThrow())
        assertEquals(12, acknowledgements.value)
    }

    @Test
    fun pendingCatalogRequestsStartupWithoutAcknowledgingBeforeTheScreenCloses() = kotlinx.coroutines.test.runTest {
        val acknowledgements = MemoryAcknowledgements()
        val decision = WhatsNewStartupCoordinator(PendingRepository, acknowledgements).evaluate(12, listOf("es"))

        assertEquals(WhatsNewStartupDecision.Show, decision.getOrThrow())
        assertNull(acknowledgements.value)
    }

    @Test
    fun explicitCloseAcknowledgesTheInstalledVersion() = kotlinx.coroutines.test.runTest {
        val acknowledgements = MemoryAcknowledgements()
        WhatsNewStartupCoordinator(PendingRepository, acknowledgements).acknowledge(12).getOrThrow()

        assertEquals(12, acknowledgements.value)
    }

    @Test
    fun acknowledgementReadFailureFailsClosed() = kotlinx.coroutines.test.runTest {
        val coordinator = WhatsNewStartupCoordinator(PendingRepository, FailingAcknowledgements)

        assertTrue(coordinator.evaluate(12, listOf("es")).isFailure)
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

private object PendingRepository : WhatsNewRepository {
    private val release = PendingRelease(
        releaseId = "ios-12",
        versionCode = 12,
        versionName = "1.2",
        localizedNote = "Pantalla compartida",
        availableLanguageTags = setOf("es"),
    )

    override suspend fun getPendingReleases(installedVersionCode: Long, languageTags: List<String>): Result<List<PendingRelease>> = Result.success(listOf(release))
    override suspend fun getReleaseHistory(languageTags: List<String>): Result<List<PendingRelease>> = Result.success(listOf(release))
    override suspend fun initializeForNewUser(installedVersionCode: Long): Result<Unit> = Result.success(Unit)
    override suspend fun markReleasesSeen(upToVersionCode: Long, installedVersionCode: Long): Result<Unit> = Result.success(Unit)
}

private object FailingAcknowledgements : WhatsNewStartupAcknowledgementStore {
    override suspend fun readAcknowledgedVersionCode(): Result<Long?> = Result.failure(IllegalStateException("read_failed"))
    override suspend fun writeAcknowledgedVersionCode(versionCode: Long): Result<Unit> = Result.failure(IllegalStateException("write_failed"))
}
