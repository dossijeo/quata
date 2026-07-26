package com.quata.feature.whatsnew.data

import com.quata.feature.whatsnew.domain.UserReleaseState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LocalWhatsNewRepositoryTest {
    @Test
    fun pendingCatalogIsBoundedByInstalledAndSeenVersions() = runTest {
        val store = MemorySeenStore(UserReleaseState(lastSeenVersionCode = 1, initializedAtVersionCode = 1))
        val repository = LocalWhatsNewRepository(catalog(), store)

        val pending = repository.getPendingReleases(installedVersionCode = 2, languageTags = listOf("es-ES")).getOrThrow()

        assertEquals(listOf(2L), pending.map { it.versionCode })
        assertEquals("Segunda version", pending.single().localizedNote)
    }

    @Test
    fun localizationFallsBackToLanguageThenEnglish() = runTest {
        val repository = LocalWhatsNewRepository(catalog(), MemorySeenStore(UserReleaseState(null, null)))

        assertEquals("Segunda version", repository.getReleaseHistory(listOf("es-MX")).getOrThrow().first().localizedNote)
        assertEquals("Second release", repository.getReleaseHistory(listOf("fr-FR")).getOrThrow().first().localizedNote)
    }

    @Test
    fun seenStateIsMonotonicAndRejectsUninstalledVersions() = runTest {
        val store = MemorySeenStore(UserReleaseState(lastSeenVersionCode = 2, initializedAtVersionCode = 1))
        val repository = LocalWhatsNewRepository(catalog(), store)

        repository.markReleasesSeen(upToVersionCode = 1, installedVersionCode = 2).getOrThrow()
        assertEquals(2, store.state.lastSeenVersionCode)

        assertFailsWith<IllegalArgumentException> {
            repository.markReleasesSeen(upToVersionCode = 3, installedVersionCode = 2).getOrThrow()
        }
    }

    @Test
    fun failedStoreWriteDoesNotReportSeen() = runTest {
        val store = MemorySeenStore(UserReleaseState(null, null), failWrites = true)
        val repository = LocalWhatsNewRepository(catalog(), store)

        assertTrue(repository.markReleasesSeen(1, 1).isFailure)
        assertEquals(null, store.state.lastSeenVersionCode)
    }

    private fun catalog() = listOf(
        LocalWhatsNewRelease("ios-1", 1, "1.0", mapOf("en" to "First release")),
        LocalWhatsNewRelease("ios-2", 2, "1.1", mapOf("es" to "Segunda version", "en" to "Second release")),
        LocalWhatsNewRelease("future", 3, "2.0", mapOf("en" to "Future")),
    )
}

private class MemorySeenStore(
    var state: UserReleaseState,
    private val failWrites: Boolean = false,
) : WhatsNewSeenStateStore {
    override suspend fun read(): Result<UserReleaseState> = Result.success(state)

    override suspend fun write(state: UserReleaseState): Result<Unit> {
        if (failWrites) return Result.failure(IllegalStateException("write_failed"))
        this.state = state
        return Result.success(Unit)
    }
}
