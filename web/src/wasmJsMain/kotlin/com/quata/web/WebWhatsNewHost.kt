@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.quata.core.moderation.LegalLinks
import com.quata.core.platform.PreferenceStore
import com.quata.core.ui.components.QuataAboutDialogContent
import com.quata.feature.whatsnew.data.LocalWhatsNewRepository
import com.quata.feature.whatsnew.data.QuataLocalWhatsNewCatalog
import com.quata.feature.whatsnew.data.WhatsNewSeenStateStore
import com.quata.feature.whatsnew.domain.UserReleaseState
import com.quata.feature.whatsnew.domain.WhatsNewRepository
import com.quata.feature.whatsnew.presentation.ReleaseHistoryContent
import com.quata.feature.whatsnew.presentation.ReleaseHistoryStrings
import com.quata.feature.whatsnew.presentation.WhatsNewScreenHost
import com.quata.feature.whatsnew.presentation.WhatsNewStartupAcknowledgementStore
import com.quata.feature.whatsnew.presentation.WhatsNewStartupCoordinator
import com.quata.feature.whatsnew.presentation.WhatsNewStrings

enum class WebWhatsNewDestination { PendingReleases, About, ReleaseHistory }
enum class WebWhatsNewOrigin { Startup, Settings, DeepLink }

internal fun webWhatsNewDestination(route: String): WebWhatsNewDestination? = when (route) {
    "whats-new" -> WebWhatsNewDestination.PendingReleases
    "about" -> WebWhatsNewDestination.About
    "release-history" -> WebWhatsNewDestination.ReleaseHistory
    else -> null
}

internal fun webWhatsNewReturnFragment(origin: WebWhatsNewOrigin): String = when (origin) {
    WebWhatsNewOrigin.Settings -> "settings"
    WebWhatsNewOrigin.Startup, WebWhatsNewOrigin.DeepLink -> ""
}

/** Android release RPCs use Android version codes; Web uses the shared platform-local catalog. */
internal fun webWhatsNewSourceKind(): String = "common-source-controlled-web"

/**
 * A deployment may stamp an explicit Web release code. The checked-in catalog remains the
 * fail-closed product default so a blank optional meta tag cannot make What's New unreachable.
 */
internal fun webWhatsNewInstalledVersionCode(configuredVersionCode: Long?): Long =
    configuredVersionCode
        ?.takeIf { it > 0L }
        ?: QuataLocalWhatsNewCatalog.latestWebVersionCode()

internal fun createWebWhatsNewRepository(): WhatsNewRepository = LocalWhatsNewRepository(
    releases = QuataLocalWhatsNewCatalog.webReleases(),
    store = WebWhatsNewSeenStateStore(),
)

internal fun createWebWhatsNewStartupCoordinator(
    repository: WhatsNewRepository,
    preferences: PreferenceStore,
): WhatsNewStartupCoordinator = WhatsNewStartupCoordinator(
    repository = repository,
    acknowledgementStore = WebWhatsNewStartupAcknowledgementStore(preferences),
)

@Composable
fun WebWhatsNewHost(
    destination: WebWhatsNewDestination,
    repository: WhatsNewRepository,
    installedVersionCode: Long?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val languageTags = remember { browserWhatsNewLanguageTags() }
    when (destination) {
        WebWhatsNewDestination.PendingReleases -> WhatsNewScreenHost(
            repository = repository,
            installedVersionCode = installedVersionCode,
            languageTags = languageTags,
            strings = webWhatsNewStrings(languageTags),
            saveError = webWhatsNewSaveError(languageTags),
            onClose = onBack,
            modifier = modifier,
        )
        WebWhatsNewDestination.About -> QuataAboutDialogContent(
            title = webAboutTitle(languageTags),
            version = webAboutVersion(),
            versionDate = webAboutVersionDate(installedVersionCode),
            body = webAboutBody(languageTags),
            releaseHistoryLabel = webAboutReleaseHistoryLabel(languageTags),
            closeLabel = webReleaseHistoryStrings(languageTags).close,
            onDismiss = onBack,
            onOpenReleaseHistory = { webSetBrowserFragment("release-history") },
            legalLinks = { WebAboutLegalLinks(languageTags) },
        )
        WebWhatsNewDestination.ReleaseHistory -> ReleaseHistoryContent(
            repository = repository,
            languageTags = languageTags,
            strings = webReleaseHistoryStrings(languageTags),
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun WebAboutLegalLinks(languageTags: List<String>) {
    val labels = webAboutLegalLabels(languageTags)
    TextButton(onClick = { webOpenExternalUrl(LegalLinks.Privacy) }) { Text(labels.privacy) }
    TextButton(onClick = { webOpenExternalUrl(LegalLinks.ChildSafety) }) { Text(labels.childSafety) }
}

internal fun webWhatsNewStrings(languageTags: List<String>): WhatsNewStrings = when {
    languageTags.isSpanish() -> WhatsNewStrings("Novedades", "Anterior", "Siguiente", "Continuar", { "Versión $it" }, { "Novedades de $it" })
    languageTags.isFrench() -> WhatsNewStrings("Nouveautés", "Précédent", "Suivant", "Continuer", { "Version $it" }, { "Nouveautés de $it" })
    else -> WhatsNewStrings("What's New", "Previous", "Next", "Continue", { "Version $it" }, { "What's new in $it" })
}

internal fun webWhatsNewSaveError(languageTags: List<String>): String = when {
    languageTags.isSpanish() -> "No se pudo guardar el estado de lectura."
    languageTags.isFrench() -> "Impossible d'enregistrer la progression."
    else -> "Read progress could not be saved."
}

internal fun webReleaseHistoryStrings(languageTags: List<String>): ReleaseHistoryStrings = when {
    languageTags.isSpanish() -> ReleaseHistoryStrings("Cerrar", "Aún no hay novedades publicadas.", "No se pudo cargar el historial de versiones.", "Historial de versiones", "Consulta las novedades de todas las versiones registradas.", "Anterior", "Siguiente", { "Versión $it" }, { "Novedades de $it" })
    languageTags.isFrench() -> ReleaseHistoryStrings("Fermer", "Aucune nouveauté publiée.", "Impossible de charger l'historique des versions.", "Historique des versions", "Consultez les nouveautés de toutes les versions suivies.", "Précédent", "Suivant", { "Version $it" }, { "Nouveautés de $it" })
    else -> ReleaseHistoryStrings("Close", "No releases have been published yet.", "Release history could not be loaded.", "Version history", "Browse the notes for every tracked release.", "Previous", "Next", { "Version $it" }, { "What's new in $it" })
}

private data class WebAboutLegalLabels(val privacy: String, val childSafety: String)

private fun webAboutVersion(): String = "Version ${webDocumentMeta("quata-version-name") ?: "Web"}"

private fun webAboutVersionDate(installedVersionCode: Long?): String =
    installedVersionCode?.takeIf { it > 0 }?.let { "Version code: $it" } ?: "Version code: local"

private fun webAboutTitle(languageTags: List<String>): String = when {
    languageTags.isSpanish() -> "Acerca de Quata"
    languageTags.isFrench() -> "À propos de Quata"
    else -> "About Quata"
}

private fun webAboutReleaseHistoryLabel(languageTags: List<String>): String = when {
    languageTags.isSpanish() -> "Historial de versiones"
    languageTags.isFrench() -> "Historique des versions"
    else -> "Release history"
}

private fun webAboutBody(languageTags: List<String>): String = when {
    languageTags.isSpanish() -> "Feed comunitario, barrios, chats, favoritos, perfiles y contactos SOS en una experiencia integrada."
    languageTags.isFrench() -> "Feed communautaire, quartiers, chats, favoris, profils et contacts SOS dans une experience integree."
    else -> "Community feed, districts, chats, favorites, profiles and SOS contacts in one integrated experience."
}

private fun webAboutLegalLabels(languageTags: List<String>): WebAboutLegalLabels = when {
    languageTags.isSpanish() -> WebAboutLegalLabels("Politica de privacidad", "Seguridad infantil y normas de la comunidad")
    languageTags.isFrench() -> WebAboutLegalLabels("Politique de confidentialite", "Securite des enfants et regles de la communaute")
    else -> WebAboutLegalLabels("Privacy policy", "Child safety and community standards")
}

private class WebWhatsNewSeenStateStore : WhatsNewSeenStateStore {
    override suspend fun read(): Result<UserReleaseState> = runCatching {
        val parts = webLocalStorageGet(WebSeenKey)?.split('|')
        if (parts == null) UserReleaseState(null, null) else {
            require(parts.size == 3 && parts[0] == "v1") { "whats_new_state_invalid" }
            UserReleaseState(parts[1].toLongOrNull(), parts[2].toLongOrNull())
        }
    }

    override suspend fun write(state: UserReleaseState): Result<Unit> = runCatching {
        webLocalStorageSet(WebSeenKey, "v1|${state.lastSeenVersionCode.orEmpty()}|${state.initializedAtVersionCode.orEmpty()}")
    }
}

private class WebWhatsNewStartupAcknowledgementStore(
    private val preferences: PreferenceStore,
) : WhatsNewStartupAcknowledgementStore {
    override suspend fun readAcknowledgedVersionCode(): Result<Long?> = runCatching {
        preferences.getString(WebStartupAcknowledgementKey)?.toLongOrNull()
    }

    override suspend fun writeAcknowledgedVersionCode(versionCode: Long): Result<Unit> = runCatching {
        preferences.putString(WebStartupAcknowledgementKey, versionCode.toString())
    }
}

private fun Long?.orEmpty(): String = this?.toString().orEmpty()
private fun webDocumentMeta(name: String): String? = js("globalThis.document?.querySelector('meta[name=\"' + name + '\"]')?.content || null")
private fun webOpenExternalUrl(url: String): Unit = js("globalThis.open(url, '_blank', 'noopener,noreferrer')")
private fun webSetBrowserFragment(fragment: String): Unit = js("globalThis.location.hash = fragment")
private const val WebSeenKey = "quata.whatsnew.web.state.v1"
private const val WebStartupAcknowledgementKey = "quata.whatsnew.web.startup_ack.v1"
private fun webLocalStorageGet(key: String): String? = js("globalThis.localStorage?.getItem(key) ?? null")
private fun webLocalStorageSet(key: String, value: String): Unit = js("globalThis.localStorage?.setItem(key, value)")
internal fun browserWhatsNewLanguageTags(): List<String> = browserLanguageTag().split(',').map(String::trim).filter(String::isNotEmpty).plus("en").distinct()
private fun browserLanguageTag(): String = js("(globalThis.navigator?.languages && globalThis.navigator.languages.length ? Array.from(globalThis.navigator.languages).join(',') : (globalThis.navigator?.language || 'en'))")
private fun List<String>.isSpanish(): Boolean = any { it.substringBefore('-').substringBefore('_').equals("es", true) }
private fun List<String>.isFrench(): Boolean = any { it.substringBefore('-').substringBefore('_').equals("fr", true) }
