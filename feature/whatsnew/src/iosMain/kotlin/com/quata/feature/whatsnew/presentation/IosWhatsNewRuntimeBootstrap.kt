package com.quata.feature.whatsnew.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.localization.QuataLanguage
import com.quata.core.moderation.LegalDocument
import com.quata.core.moderation.iosLegalDocumentFile
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.ui.components.QuataLegalDocumentLinksContent
import com.quata.feature.whatsnew.data.IosWhatsNewSeenStateStore
import com.quata.feature.whatsnew.data.LocalWhatsNewRepository
import com.quata.feature.whatsnew.data.QuataLocalWhatsNewCatalog
import com.quata.feature.whatsnew.domain.WhatsNewRepository
import kotlin.concurrent.Volatile
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

class IosWhatsNewRuntimeBootstrap internal constructor(
    val installedVersionCode: Long,
    val installedVersionName: String,
    val languageTags: List<String>,
    val repository: WhatsNewRepository,
    private val startupCoordinator: WhatsNewStartupCoordinator,
) {
    private val startupScope = MainScope()

    fun releaseHistoryStrings(): ReleaseHistoryStrings = iosReleaseHistoryStrings(languageTags)

    /** Runs the shared first-version decision without blocking UIKit's public Feed. */
    fun evaluateStartup(onDecision: (Boolean) -> Unit) {
        startupScope.launch {
            val decision = startupCoordinator.evaluate(installedVersionCode, languageTags).getOrNull()
            onDecision(decision == true)
        }
    }

    /** Persists the installed-version acknowledgement before UIKit returns to Feed. */
    fun acknowledgeStartup(onComplete: () -> Unit) {
        startupScope.launch {
            startupCoordinator.acknowledge(installedVersionCode)
            onComplete()
        }
    }
}

/**
 * Builds the actual offline iOS runtime from public bundle version metadata and UserDefaults.
 * Missing/unexpanded bundle versions fail closed instead of inventing an installed release.
 */
fun createDefaultIosWhatsNewRuntimeBootstrap(): IosWhatsNewRuntimeBootstrap? = createIosWhatsNewRuntimeBootstrap(
    bundle = NSBundle.mainBundle,
    defaults = NSUserDefaults.standardUserDefaults,
    languageTag = null,
)

/** Swift supplies the sanitized preferred language; invalid input deliberately falls back to English. */
fun createDefaultIosWhatsNewRuntimeBootstrap(languageTag: String?): IosWhatsNewRuntimeBootstrap? = createIosWhatsNewRuntimeBootstrap(
    bundle = NSBundle.mainBundle,
    defaults = NSUserDefaults.standardUserDefaults,
    languageTag = languageTag,
)

/** Injectable variant used by host tests and non-standard bundle/suite composition. */
fun createIosWhatsNewRuntimeBootstrap(
    bundle: NSBundle,
    defaults: NSUserDefaults,
    languageTag: String? = null,
): IosWhatsNewRuntimeBootstrap? {
    val versionCode = bundle.configuredString("CFBundleVersion")?.toLongOrNull()?.takeIf { it > 0 } ?: return null
    val versionName = bundle.configuredString("CFBundleShortVersionString") ?: return null
    val languageTags = languageTag
        ?.trim()
        ?.takeIf { LanguageTagPattern.matches(it) }
        ?.let(::listOf)
        ?: listOf("en")
    val repository = LocalWhatsNewRepository(
        releases = QuataLocalWhatsNewCatalog.iosReleases(),
        store = IosWhatsNewSeenStateStore(defaults, IosWhatsNewSeenStateStore.DefaultKey),
    )
    val startupCoordinator = WhatsNewStartupCoordinator(
        repository = repository,
        acknowledgementStore = IosWhatsNewStartupAcknowledgementStore(defaults),
    )
    return IosWhatsNewRuntimeBootstrap(versionCode, versionName, languageTags, repository, startupCoordinator)
}

private val LanguageTagPattern = Regex("^[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})*$")

/** Loads pending local releases and records progress before allowing the host to leave. */
fun QuataIosManagedWhatsNewViewController(
    runtime: IosWhatsNewRuntimeBootstrap,
    onClose: () -> Unit,
): UIViewController = ComposeUIViewController {
    QuataTheme {
        WhatsNewScreenHost(
            repository = runtime.repository,
            installedVersionCode = runtime.installedVersionCode,
            languageTags = runtime.languageTags,
            strings = iosWhatsNewStrings(runtime.languageTags),
            saveError = iosWhatsNewSaveError(runtime.languageTags),
            onClose = onClose,
        )
    }
}

/** Menu/About destination: complete local history, independent of seen state. */
fun QuataIosReleaseHistoryViewController(
    runtime: IosWhatsNewRuntimeBootstrap,
    onClose: () -> Unit,
): UIViewController = QuataReleaseHistoryViewController(
    IosReleaseHistoryHostDependencies(
        repository = runtime.repository,
        languageTags = runtime.languageTags,
        strings = runtime.releaseHistoryStrings(),
        onBack = onClose,
    ),
)

/** Menu/About destination: shared About dialog with a real path to release history. */
fun QuataIosAboutViewController(
    runtime: IosWhatsNewRuntimeBootstrap,
    documentOpener: DocumentOpenService,
    onClose: () -> Unit,
    onOpenReleaseHistory: () -> Unit,
): UIViewController = QuataAboutViewController(
    IosAboutHostDependencies(
        title = iosAboutTitle(runtime.languageTags),
        version = iosAboutVersion(runtime),
        versionDate = iosAboutVersionDate(runtime),
        body = iosAboutBody(runtime.languageTags),
        releaseHistoryLabel = iosAboutReleaseHistoryLabel(runtime.languageTags),
        closeLabel = runtime.releaseHistoryStrings().close,
        onDismiss = onClose,
        onOpenReleaseHistory = onOpenReleaseHistory,
        legalLinks = { IosAboutLegalLinks(runtime.languageTags, documentOpener) },
    ),
)

/** iOS UI evidence fixture: real shared About content with a recording document opener. */
fun QuataIosAboutLegalEvidenceViewController(
    runtime: IosWhatsNewRuntimeBootstrap,
    onOpened: (String) -> Unit,
    onClose: () -> Unit,
    onOpenReleaseHistory: () -> Unit,
): UIViewController = QuataIosAboutViewController(
    runtime = runtime,
    documentOpener = RecordingIosLegalDocumentOpenService(onOpened),
    onClose = onClose,
    onOpenReleaseHistory = onOpenReleaseHistory,
)

private class RecordingIosLegalDocumentOpenService(
    private val onOpened: (String) -> Unit,
) : DocumentOpenService {
    override suspend fun open(file: PlatformFile): PlatformResult<Unit> {
        onOpened(file.displayName.orEmpty())
        return PlatformResult.Success(Unit)
    }
}

enum class IosWhatsNewRoute { PendingReleases, ReleaseHistory }

fun interface IosWhatsNewRouteHost {
    fun open(route: IosWhatsNewRoute)
}

/** Injectable menu/deep-route boundary; no UIKit navigation or singleton is retained here. */
class IosWhatsNewRouteDispatcher {
    @Volatile private var host: IosWhatsNewRouteHost? = null

    fun attachHost(host: IosWhatsNewRouteHost) { this.host = host }
    // Kotlin/Native protocol wrappers do not preserve Swift object identity across calls, so a
    // parameterized identity check can retain a detached UIKit host. This dispatcher owns only
    // one attachment; teardown therefore always clears it explicitly.
    fun detachHost() { host = null }
    fun openPendingReleases(): PlatformResult<Unit> = dispatch(IosWhatsNewRoute.PendingReleases)
    fun openReleaseHistory(): PlatformResult<Unit> = dispatch(IosWhatsNewRoute.ReleaseHistory)

    private fun dispatch(route: IosWhatsNewRoute): PlatformResult<Unit> {
        val activeHost = host ?: return PlatformResult.Unsupported
        return runCatching { activeHost.open(route); PlatformResult.Success(Unit) }
            .getOrElse { PlatformResult.Failure(it.message) }
    }
}

private class IosWhatsNewStartupAcknowledgementStore(
    private val defaults: NSUserDefaults,
) : WhatsNewStartupAcknowledgementStore {
    override suspend fun readAcknowledgedVersionCode(): Result<Long?> = Result.success(
        if (defaults.objectForKey(Key) == null) null else defaults.integerForKey(Key),
    )

    override suspend fun writeAcknowledgedVersionCode(versionCode: Long): Result<Unit> = runCatching {
        defaults.setInteger(versionCode, forKey = Key)
    }

    private companion object {
        const val Key = "quata.whatsnew.startup.acknowledged_version"
    }
}

private fun iosWhatsNewSaveError(tags: List<String>): String = when {
    tags.isSpanish() -> "No se pudo guardar el estado de lectura."
    tags.isFrench() -> "Impossible d'enregistrer la progression."
    else -> "Read progress could not be saved."
}

private fun iosWhatsNewStrings(tags: List<String>): WhatsNewStrings = when {
    tags.isSpanish() -> WhatsNewStrings("Novedades", "Anterior", "Siguiente", "Continuar", { "Version $it" }, { "Novedades de $it" })
    tags.isFrench() -> WhatsNewStrings("Nouveautés", "Précédent", "Suivant", "Continuer", { "Version $it" }, { "Nouveautés de $it" })
    else -> WhatsNewStrings("What's New", "Previous", "Next", "Continue", { "Version $it" }, { "What's new in $it" })
}

private fun iosReleaseHistoryStrings(tags: List<String>): ReleaseHistoryStrings = when {
    tags.isSpanish() -> ReleaseHistoryStrings("Cerrar", "No hay versiones disponibles.", "No se pudo cargar el historial.", "Historial de versiones", "Consulta las novedades de todas las versiones registradas.", "Anterior", "Siguiente", { "Version $it" }, { "Novedades de $it" })
    tags.isFrench() -> ReleaseHistoryStrings("Fermer", "Aucune nouveauté publiée.", "Impossible de charger l'historique des versions.", "Historique des versions", "Consultez les nouveautés de toutes les versions suivies.", "Précédent", "Suivant", { "Version $it" }, { "Nouveautés de $it" })
    else -> ReleaseHistoryStrings("Close", "No releases are available.", "Release history could not be loaded.", "Version history", "Browse the notes for every tracked release.", "Previous", "Next", { "Version $it" }, { "What's new in $it" })
}

@Composable
private fun IosAboutLegalLinks(tags: List<String>, documentOpener: DocumentOpenService) {
    val language = tags.toQuataLanguage()
    val scope = rememberCoroutineScope()
    QuataLegalDocumentLinksContent(
        language = language,
        onOpenDocument = { document ->
            scope.launch {
                iosLegalDocumentFile(document, language)?.let { documentOpener.open(it) }
            }
        },
    )
}

fun openIosLegalDocumentForSettings(
    runtime: IosWhatsNewRuntimeBootstrap,
    document: LegalDocument,
    documentOpener: DocumentOpenService,
): PlatformResult<Unit> {
    val language = runtime.languageTags.toQuataLanguage()
    val file = iosLegalDocumentFile(document, language) ?: return PlatformResult.Unsupported
    MainScope().launch { documentOpener.open(file) }
    return PlatformResult.Success(Unit)
}

private fun iosAboutVersion(runtime: IosWhatsNewRuntimeBootstrap): String = when {
    runtime.languageTags.isSpanish() -> "Version ${runtime.installedVersionName}"
    runtime.languageTags.isFrench() -> "Version ${runtime.installedVersionName}"
    else -> "Version ${runtime.installedVersionName}"
}

private fun iosAboutVersionDate(runtime: IosWhatsNewRuntimeBootstrap): String = when {
    runtime.languageTags.isSpanish() -> "Codigo de version: ${runtime.installedVersionCode}"
    runtime.languageTags.isFrench() -> "Code de version : ${runtime.installedVersionCode}"
    else -> "Version code: ${runtime.installedVersionCode}"
}

private fun iosAboutTitle(tags: List<String>): String = when {
    tags.isSpanish() -> "Acerca de Quata"
    tags.isFrench() -> "À propos de Quata"
    else -> "About Quata"
}

private fun iosAboutReleaseHistoryLabel(tags: List<String>): String = when {
    tags.isSpanish() -> "Historial de versiones"
    tags.isFrench() -> "Historique des versions"
    else -> "Release history"
}

private fun iosAboutBody(tags: List<String>): String = when {
    tags.isSpanish() -> "Feed comunitario, barrios, chats, favoritos, perfiles y contactos SOS en una experiencia integrada."
    tags.isFrench() -> "Feed communautaire, quartiers, chats, favoris, profils et contacts SOS dans une experience integree."
    else -> "Community feed, districts, chats, favorites, profiles and SOS contacts in one integrated experience."
}

private fun List<String>.isSpanish(): Boolean = any { it.substringBefore('-').equals("es", ignoreCase = true) }
private fun List<String>.isFrench(): Boolean = any { it.substringBefore('-').equals("fr", ignoreCase = true) }
private fun List<String>.toQuataLanguage(): QuataLanguage = when {
    isSpanish() -> QuataLanguage.Spanish
    isFrench() -> QuataLanguage.French
    else -> QuataLanguage.English
}

private fun NSBundle.configuredString(key: String): String? =
    objectForInfoDictionaryKey(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() && "$(" !in it }
