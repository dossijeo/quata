package com.quata.feature.whatsnew.data

enum class LocalWhatsNewPlatform { Web, Ios }

/**
 * Source-controlled release notes for clients whose version numbers are independent from Android.
 *
 * Android deliberately keeps its published Supabase-backed release repository. The existing
 * `quata_*android_releases` RPCs use Android version codes and therefore cannot truthfully describe
 * a Web or iOS installation. Web and iOS share this catalog contract while retaining separate
 * entries and platform-owned seen-state stores.
 */
object QuataLocalWhatsNewCatalog {
    fun releases(platform: LocalWhatsNewPlatform): List<LocalWhatsNewRelease> = when (platform) {
        LocalWhatsNewPlatform.Web -> WebReleases
        LocalWhatsNewPlatform.Ios -> IosReleases
    }

    fun latestVersionCode(platform: LocalWhatsNewPlatform): Long =
        releases(platform).maxOf(LocalWhatsNewRelease::versionCode)

    private val WebReleases = listOf(
        LocalWhatsNewRelease(
            releaseId = "web-1.0-1",
            versionCode = 1,
            versionName = "1.0",
            notes = mapOf(
                "es" to "Qüata Web incorpora Novedades con la pantalla Compose compartida, historial de versiones y progreso de lectura local.",
                "en" to "Qüata Web now includes What's New with the shared Compose screen, release history, and local read progress.",
                "fr" to "Qüata Web intègre désormais les nouveautés avec l'écran Compose partagé, l'historique des versions et la progression de lecture locale.",
            ),
        ),
    )

    private val IosReleases = listOf(
        LocalWhatsNewRelease(
            releaseId = "ios-1.0-1",
            versionCode = 1,
            versionName = "1.0",
            notes = mapOf(
                "es" to "Qüata para iOS incorpora las superficies Compose compartidas y un catálogo local de novedades versionado.",
                "en" to "Qüata for iOS now includes shared Compose surfaces and a versioned local What's New catalog.",
                "fr" to "Qüata pour iOS intègre désormais les surfaces Compose partagées et un catalogue local de nouveautés versionné.",
            ),
        ),
    )
}
