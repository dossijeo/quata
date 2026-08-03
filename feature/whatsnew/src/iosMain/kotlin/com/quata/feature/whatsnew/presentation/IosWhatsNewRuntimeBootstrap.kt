package com.quata.feature.whatsnew.presentation

import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.platform.PlatformResult
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

    fun whatsNewStrings(): WhatsNewStrings = iosWhatsNewStrings(languageTags)
    fun whatsNewScreenHostStrings(): WhatsNewScreenHostStrings = iosWhatsNewScreenHostStrings(languageTags)
    fun releaseHistoryStrings(): ReleaseHistoryStrings = iosReleaseHistoryStrings(languageTags)

    /** Runs the shared first-version decision without blocking UIKit's public Feed. */
    fun evaluateStartup(onDecision: (Boolean) -> Unit) {
        startupScope.launch {
            val decision = startupCoordinator.evaluate(installedVersionCode, languageTags).getOrNull()
            onDecision(decision == WhatsNewStartupDecision.Show)
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
            strings = runtime.whatsNewScreenHostStrings(),
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

private data class IosWhatsNewCopy(
    val loadError: String,
    val saveError: String,
    val retry: String,
)

private fun iosCopy(tags: List<String>): IosWhatsNewCopy = when {
    tags.isSpanish() -> IosWhatsNewCopy("No se pudieron cargar las novedades.", "No se pudo guardar el estado de lectura.", "Reintentar")
    tags.isFrench() -> IosWhatsNewCopy("Impossible de charger les nouveautés.", "Impossible d'enregistrer la progression.", "Réessayer")
    else -> IosWhatsNewCopy("What's New could not be loaded.", "Read progress could not be saved.", "Retry")
}

private fun iosWhatsNewStrings(tags: List<String>): WhatsNewStrings = when {
    tags.isSpanish() -> WhatsNewStrings("Novedades", "Anterior", "Siguiente", "Continuar", { "Version $it" }, { "Novedades de $it" })
    tags.isFrench() -> WhatsNewStrings("Nouveautés", "Précédent", "Suivant", "Continuer", { "Version $it" }, { "Nouveautés de $it" })
    else -> WhatsNewStrings("What's New", "Previous", "Next", "Continue", { "Version $it" }, { "What's new in $it" })
}

private fun iosWhatsNewScreenHostStrings(tags: List<String>): WhatsNewScreenHostStrings = iosCopy(tags).let { copy ->
    WhatsNewScreenHostStrings(iosWhatsNewStrings(tags), copy.loadError, copy.saveError, copy.retry)
}

private fun iosReleaseHistoryStrings(tags: List<String>): ReleaseHistoryStrings = when {
    tags.isSpanish() -> ReleaseHistoryStrings("Cerrar", "No hay versiones disponibles.", "No se pudo cargar el historial.", "Acerca de Quata", "Historial de versiones", "Anterior", "Siguiente", { "Version $it" }, { "Novedades de $it" })
    tags.isFrench() -> ReleaseHistoryStrings("Fermer", "Aucune nouveauté publiée.", "Impossible de charger l'historique des versions.", "À propos de Quata", "Historique des versions", "Précédent", "Suivant", { "Version $it" }, { "Nouveautés de $it" })
    else -> ReleaseHistoryStrings("Close", "No releases are available.", "Release history could not be loaded.", "About Quata", "Release history", "Previous", "Next", { "Version $it" }, { "What's new in $it" })
}

private fun List<String>.isSpanish(): Boolean = any { it.substringBefore('-').equals("es", ignoreCase = true) }
private fun List<String>.isFrench(): Boolean = any { it.substringBefore('-').equals("fr", ignoreCase = true) }

private fun NSBundle.configuredString(key: String): String? =
    objectForInfoDictionaryKey(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() && "$(" !in it }
