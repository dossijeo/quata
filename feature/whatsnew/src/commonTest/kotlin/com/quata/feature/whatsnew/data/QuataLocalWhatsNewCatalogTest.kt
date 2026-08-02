package com.quata.feature.whatsnew.data

import com.quata.feature.whatsnew.domain.UserReleaseState
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class QuataLocalWhatsNewCatalogTest {
    @Test
    fun webAndIosCatalogsAreRealVersionedAndPlatformSpecific() {
        val web = QuataLocalWhatsNewCatalog.releases(LocalWhatsNewPlatform.Web)
        val ios = QuataLocalWhatsNewCatalog.releases(LocalWhatsNewPlatform.Ios)

        assertTrue(web.isNotEmpty())
        assertTrue(ios.isNotEmpty())
        assertTrue(web.all { it.releaseId.startsWith("web-") && it.versionCode > 0 })
        assertTrue(ios.all { it.releaseId.startsWith("ios-") && it.versionCode > 0 })
        assertNotEquals(web.map { it.releaseId }, ios.map { it.releaseId })
        assertTrue(QuataLocalWhatsNewCatalog.latestVersionCode(LocalWhatsNewPlatform.Web) > 0)
        assertTrue(QuataLocalWhatsNewCatalog.latestVersionCode(LocalWhatsNewPlatform.Ios) > 0)
    }

    @Test
    fun everyPlatformCatalogResolvesSpanishEnglishAndFrench() = runTest {
        LocalWhatsNewPlatform.entries.forEach { platform ->
            val repository = LocalWhatsNewRepository(
                QuataLocalWhatsNewCatalog.releases(platform),
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
