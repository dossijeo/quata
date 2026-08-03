package com.quata.feature.whatsnew.data

import com.quata.feature.whatsnew.domain.UserReleaseState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class QuataLocalWhatsNewCatalogTest {
    @Test
    fun webAndIosCatalogsAreRealVersionedAndPlatformSpecific() {
        val web = QuataLocalWhatsNewCatalog.webReleases()
        val ios = QuataLocalWhatsNewCatalog.iosReleases()

        assertTrue(web.isNotEmpty())
        assertTrue(ios.isNotEmpty())
        assertTrue(web.all { it.releaseId.startsWith("web-") && it.versionCode > 0 })
        assertTrue(ios.all { it.releaseId.startsWith("ios-") && it.versionCode > 0 })
        assertTrue(web.all { release -> release.notes.isNotEmpty() && release.notes.values.all(String::isNotBlank) })
        assertTrue(ios.all { release -> release.notes.isNotEmpty() && release.notes.values.all(String::isNotBlank) })
        assertEquals(web.map(LocalWhatsNewRelease::versionCode).distinct(), web.map(LocalWhatsNewRelease::versionCode))
        assertEquals(ios.map(LocalWhatsNewRelease::versionCode).distinct(), ios.map(LocalWhatsNewRelease::versionCode))
        assertNotEquals(web.map { it.releaseId }, ios.map { it.releaseId })
        assertTrue(QuataLocalWhatsNewCatalog.latestWebVersionCode() > 0)
        assertTrue(ios.maxOf(LocalWhatsNewRelease::versionCode) > 0)
    }

    @Test
    fun everyPlatformCatalogResolvesSpanishEnglishAndFrench() = runTest {
        listOf(QuataLocalWhatsNewCatalog.webReleases(), QuataLocalWhatsNewCatalog.iosReleases()).forEach { releases ->
            val repository = LocalWhatsNewRepository(
                releases,
                CatalogSeenStateStore,
            )

            listOf("es", "en", "fr").forEach { language ->
                val history = repository.getReleaseHistory(listOf(language)).getOrThrow()
                assertTrue(history.isNotEmpty())
                assertTrue(history.all { it.localizedNote.isNotBlank() })
                assertTrue(history.all { language in it.availableLanguageTags })
            }
        }
    }
}

private object CatalogSeenStateStore : WhatsNewSeenStateStore {
    override suspend fun read(): Result<UserReleaseState> = Result.success(UserReleaseState(null, null))
    override suspend fun write(state: UserReleaseState): Result<Unit> = Result.success(Unit)
}
