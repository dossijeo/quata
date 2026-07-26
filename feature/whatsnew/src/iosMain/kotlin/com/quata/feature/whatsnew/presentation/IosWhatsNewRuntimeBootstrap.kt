package com.quata.feature.whatsnew.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.platform.PlatformResult
import com.quata.feature.whatsnew.data.IosWhatsNewSeenStateStore
import com.quata.feature.whatsnew.data.LocalWhatsNewRelease
import com.quata.feature.whatsnew.data.LocalWhatsNewRepository
import com.quata.feature.whatsnew.domain.PendingRelease
import com.quata.feature.whatsnew.domain.WhatsNewRepository
import kotlin.concurrent.Volatile
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

class IosWhatsNewRuntimeBootstrap internal constructor(
    val installedVersionCode: Long,
    val installedVersionName: String,
    val languageTags: List<String>,
    val repository: WhatsNewRepository,
) {
    fun whatsNewStrings(): WhatsNewStrings = iosWhatsNewStrings(languageTags)
    fun releaseHistoryStrings(): ReleaseHistoryStrings = iosReleaseHistoryStrings(languageTags)
}

/**
 * Builds the actual offline iOS runtime from public bundle version metadata and UserDefaults.
 * Missing/unexpanded bundle versions fail closed instead of inventing an installed release.
 */
fun createDefaultIosWhatsNewRuntimeBootstrap(): IosWhatsNewRuntimeBootstrap? = createIosWhatsNewRuntimeBootstrap(
    bundle = NSBundle.mainBundle,
    defaults = NSUserDefaults.standardUserDefaults,
)

/** Injectable variant used by host tests and non-standard bundle/suite composition. */
fun createIosWhatsNewRuntimeBootstrap(
    bundle: NSBundle,
    defaults: NSUserDefaults,
): IosWhatsNewRuntimeBootstrap? {
    val versionCode = bundle.configuredString("CFBundleVersion")?.toLongOrNull()?.takeIf { it > 0 } ?: return null
    val versionName = bundle.configuredString("CFBundleShortVersionString") ?: return null
    val languageTags = NSLocale.preferredLanguages.mapNotNull { it as? String }.ifEmpty { listOf("en") }
    val repository = LocalWhatsNewRepository(
        releases = IosWhatsNewCatalog.releases,
        store = IosWhatsNewSeenStateStore(defaults, IosWhatsNewSeenStateStore.DefaultKey),
    )
    return IosWhatsNewRuntimeBootstrap(versionCode, versionName, languageTags, repository)
}

/** Loads pending local releases and records progress before allowing the host to leave. */
fun QuataIosManagedWhatsNewViewController(
    runtime: IosWhatsNewRuntimeBootstrap,
    onClose: () -> Unit,
): UIViewController = ComposeUIViewController {
    QuataTheme {
        ManagedWhatsNewContent(runtime, onClose)
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

@Composable
private fun ManagedWhatsNewContent(runtime: IosWhatsNewRuntimeBootstrap, onClose: () -> Unit) {
    var state by remember(runtime) { mutableStateOf<IosWhatsNewState>(IosWhatsNewState.Loading) }
    var isCompleting by remember(runtime) { mutableStateOf(false) }
    var saveFailed by remember(runtime) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(runtime) {
        runtime.repository.initializeForNewUser(runtime.installedVersionCode)
            .onFailure { state = IosWhatsNewState.Error }
            .onSuccess {
                state = runtime.repository.getPendingReleases(runtime.installedVersionCode, runtime.languageTags)
                    .fold(
                        onSuccess = { if (it.isEmpty()) IosWhatsNewState.Empty else IosWhatsNewState.Content(it) },
                        onFailure = { IosWhatsNewState.Error },
                    )
            }
    }

    when (val current = state) {
        IosWhatsNewState.Loading -> CenteredWhatsNewMessage { CircularProgressIndicator() }
        IosWhatsNewState.Empty -> LaunchedEffect(Unit) { onClose() }
        IosWhatsNewState.Error -> CenteredWhatsNewMessage { Text(iosCopy(runtime.languageTags).loadError) }
        is IosWhatsNewState.Content -> {
            val finish: () -> Unit = finish@{
                if (isCompleting) return@finish
                isCompleting = true
                saveFailed = false
                scope.launch {
                    runtime.repository.markReleasesSeen(
                        upToVersionCode = current.releases.maxOf(PendingRelease::versionCode),
                        installedVersionCode = runtime.installedVersionCode,
                    ).onSuccess { onClose() }.onFailure {
                        isCompleting = false
                        saveFailed = true
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                WhatsNewContent(
                    releases = current.releases,
                    isCompleting = isCompleting,
                    strings = runtime.whatsNewStrings(),
                    onComplete = finish,
                    onDismiss = finish,
                )
                if (saveFailed) {
                    Text(
                        iosCopy(runtime.languageTags).saveError,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredWhatsNewMessage(content: @Composable () -> Unit) =
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }

private sealed interface IosWhatsNewState {
    data object Loading : IosWhatsNewState
    data object Empty : IosWhatsNewState
    data object Error : IosWhatsNewState
    data class Content(val releases: List<PendingRelease>) : IosWhatsNewState
}

enum class IosWhatsNewRoute { PendingReleases, ReleaseHistory }

fun interface IosWhatsNewRouteHost {
    fun open(route: IosWhatsNewRoute)
}

/** Injectable menu/deep-route boundary; no UIKit navigation or singleton is retained here. */
class IosWhatsNewRouteDispatcher {
    @Volatile private var host: IosWhatsNewRouteHost? = null

    fun attachHost(host: IosWhatsNewRouteHost) { this.host = host }
    fun detachHost(host: IosWhatsNewRouteHost) { if (this.host === host) this.host = null }
    fun openPendingReleases(): PlatformResult<Unit> = dispatch(IosWhatsNewRoute.PendingReleases)
    fun openReleaseHistory(): PlatformResult<Unit> = dispatch(IosWhatsNewRoute.ReleaseHistory)

    private fun dispatch(route: IosWhatsNewRoute): PlatformResult<Unit> {
        val activeHost = host ?: return PlatformResult.Unsupported
        return runCatching { activeHost.open(route); PlatformResult.Success(Unit) }
            .getOrElse { PlatformResult.Failure(it.message) }
    }
}

private object IosWhatsNewCatalog {
    val releases = listOf(
        LocalWhatsNewRelease(
            releaseId = "ios-1.0-1",
            versionCode = 1,
            versionName = "1.0",
            notes = mapOf(
                "es" to "Quata para iOS incorpora las superficies Compose compartidas y una base local de novedades versionada.",
                "en" to "Quata for iOS now includes shared Compose surfaces and a versioned local What's New catalog.",
            ),
        ),
    )
}

private data class IosWhatsNewCopy(val loadError: String, val saveError: String)

private fun iosCopy(tags: List<String>): IosWhatsNewCopy = if (tags.isSpanish()) {
    IosWhatsNewCopy("No se pudieron cargar las novedades.", "No se pudo guardar el estado de lectura.")
} else {
    IosWhatsNewCopy("What's New could not be loaded.", "Read progress could not be saved.")
}

private fun iosWhatsNewStrings(tags: List<String>): WhatsNewStrings = if (tags.isSpanish()) {
    WhatsNewStrings("Novedades", "Anterior", "Siguiente", "Continuar", { "Version $it" }, { "Novedades de $it" })
} else {
    WhatsNewStrings("What's New", "Previous", "Next", "Continue", { "Version $it" }, { "What's new in $it" })
}

private fun iosReleaseHistoryStrings(tags: List<String>): ReleaseHistoryStrings = if (tags.isSpanish()) {
    ReleaseHistoryStrings("Cerrar", "No hay versiones disponibles.", "No se pudo cargar el historial.", "Acerca de Quata", "Historial de versiones", "Anterior", "Siguiente", { "Version $it" }, { "Novedades de $it" })
} else {
    ReleaseHistoryStrings("Close", "No releases are available.", "Release history could not be loaded.", "About Quata", "Release history", "Previous", "Next", { "Version $it" }, { "What's new in $it" })
}

private fun List<String>.isSpanish(): Boolean = any { it.substringBefore('-').equals("es", ignoreCase = true) }

private fun NSBundle.configuredString(key: String): String? =
    objectForInfoDictionaryKey(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() && "$(" !in it }
