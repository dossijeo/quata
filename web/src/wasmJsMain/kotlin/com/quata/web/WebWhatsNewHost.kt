@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.quata.feature.whatsnew.domain.PendingRelease
import com.quata.feature.whatsnew.domain.WhatsNewRepository
import com.quata.feature.whatsnew.presentation.ReleaseHistoryContent
import com.quata.feature.whatsnew.presentation.ReleaseHistoryStrings
import com.quata.feature.whatsnew.presentation.WhatsNewContent
import com.quata.feature.whatsnew.presentation.WhatsNewStrings
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Browser launcher for the shared What's New and release-history composables.
 *
 * A deployment can provide `quata-release-version-code`; without it the route remains useful as
 * an authenticated About/history view, but deliberately does not guess the version installed by
 * the user. The three RPCs are the same RLS-protected release contracts used by Android.
 */
@Composable
fun WebWhatsNewHost(
    repository: WhatsNewRepository,
    installedVersionCode: Long?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val languageTags = remember { browserLanguageTags() }
    if (installedVersionCode == null) {
        ReleaseHistoryContent(
            repository = repository,
            languageTags = languageTags,
            strings = webReleaseHistoryStrings,
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    var state by remember(repository, installedVersionCode, languageTags) {
        mutableStateOf<WebWhatsNewState>(WebWhatsNewState.Loading)
    }
    var isCompleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(repository, installedVersionCode, languageTags) {
        state = repository.getPendingReleases(installedVersionCode, languageTags).fold(
            onSuccess = { releases -> if (releases.isEmpty()) WebWhatsNewState.Empty else WebWhatsNewState.Content(releases) },
            onFailure = { WebWhatsNewState.Error },
        )
    }
    when (val current = state) {
        WebWhatsNewState.Loading -> WebWhatsNewLoading(modifier)
        WebWhatsNewState.Empty -> ReleaseHistoryContent(repository, languageTags, webReleaseHistoryStrings, onBack, modifier)
        WebWhatsNewState.Error -> WebWhatsNewError(modifier)
        is WebWhatsNewState.Content -> WhatsNewContent(
            releases = current.releases,
            isCompleting = isCompleting,
            strings = webWhatsNewStrings,
            onDismiss = onBack,
            onComplete = {
                if (isCompleting) return@WhatsNewContent
                scope.launch {
                    isCompleting = true
                    val result = repository.markReleasesSeen(
                        upToVersionCode = current.releases.maxOf(PendingRelease::versionCode),
                        installedVersionCode = installedVersionCode,
                    )
                    isCompleting = false
                    if (result.isSuccess) onBack() else state = WebWhatsNewState.Error
                }
            },
            modifier = modifier,
        )
    }
}

private sealed interface WebWhatsNewState {
    data object Loading : WebWhatsNewState
    data object Empty : WebWhatsNewState
    data object Error : WebWhatsNewState
    data class Content(val releases: List<PendingRelease>) : WebWhatsNewState
}

@Composable
private fun WebWhatsNewLoading(modifier: Modifier) =
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

@Composable
private fun WebWhatsNewError(modifier: Modifier) =
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No se pudieron cargar las novedades.")
    }

private val webWhatsNewStrings = WhatsNewStrings(
    title = "Novedades",
    previous = "Anterior",
    next = "Siguiente",
    continueLabel = "Continuar",
    version = { version -> "Version $version" },
    versionHeading = { version -> "Novedades de $version" },
)

private val webReleaseHistoryStrings = ReleaseHistoryStrings(
    close = "Cerrar",
    empty = "Aun no hay novedades publicadas.",
    error = "No se pudo cargar el historial de versiones.",
    title = "Acerca de Quata",
    subtitle = "Historial de versiones",
    previous = "Anterior",
    next = "Siguiente",
    version = { version -> "Version $version" },
    versionHeading = { version -> "Novedades de $version" },
)

class WebWhatsNewRepository(
    private val rpcClient: WebPostgrestRpcClient,
) : WhatsNewRepository {
    override suspend fun getPendingReleases(installedVersionCode: Long, languageTags: List<String>): Result<List<PendingRelease>> =
        releases("quata_pending_android_releases", buildJsonObject {
            put("p_installed_version_code", installedVersionCode)
            put("p_track", WebReleaseTrack)
        }, languageTags).map { it.sortedBy(PendingRelease::versionCode) }

    override suspend fun getReleaseHistory(languageTags: List<String>): Result<List<PendingRelease>> =
        releases("quata_android_release_history", buildJsonObject { put("p_track", WebReleaseTrack) }, languageTags)
            .map { it.sortedByDescending(PendingRelease::versionCode) }

    override suspend fun initializeForNewUser(installedVersionCode: Long): Result<Unit> =
        getPendingReleases(installedVersionCode, browserLanguageTags()).map { Unit }

    override suspend fun markReleasesSeen(upToVersionCode: Long, installedVersionCode: Long): Result<Unit> = runCatching {
        when (val result = rpcClient.post("quata_mark_android_releases_seen", buildJsonObject {
            put("p_up_to_version_code", upToVersionCode)
            put("p_installed_version_code", installedVersionCode)
        }.toString())) {
            is WebPostgrestResult.Success -> Unit
            is WebPostgrestResult.Failure -> throw WebPostgrestReadException(result)
        }
    }

    private suspend fun releases(function: String, body: JsonObject, languageTags: List<String>): Result<List<PendingRelease>> = runCatching {
        val result = rpcClient.post(function, body.toString())
        val payload = when (result) {
            is WebPostgrestResult.Success -> Json.parseToJsonElement(result.body).jsonArray
            is WebPostgrestResult.Failure -> throw WebPostgrestReadException(result)
        }
        payload.mapNotNull { it.jsonObject.toPendingRelease(languageTags) }
            .distinctBy(PendingRelease::versionCode)
    }
}

private fun JsonObject.toPendingRelease(languageTags: List<String>): PendingRelease? {
    val notes = this["notes"]?.jsonObject
        ?.mapValues { (_, note) -> note.jsonPrimitive.contentOrNull?.trim().orEmpty() }
        ?.filterValues(String::isNotEmpty)
        .orEmpty()
    val note = notes.resolveFor(languageTags) ?: return null
    val releaseId = this["release_id"]?.jsonPrimitive?.contentOrNull ?: return null
    val versionCode = this["version_code"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return null
    val availableTags = this["available_language_tags"]?.jsonArray
        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
        ?.toSet()
        ?.ifEmpty { notes.keys }
        ?: notes.keys
    return PendingRelease(releaseId, versionCode, this["version_name"]?.jsonPrimitive?.contentOrNull, note, availableTags)
}

private fun Map<String, String>.resolveFor(languageTags: List<String>): String? {
    languageTags.firstNotNullOfOrNull { requested -> entries.firstOrNull { it.key.equals(requested, true) }?.value }?.let { return it }
    languageTags.map { it.substringBefore('-') }.firstNotNullOfOrNull { language ->
        entries.firstOrNull { it.key.substringBefore('-').equals(language, true) }?.value
    }?.let { return it }
    entries.firstOrNull { it.key.substringBefore('-').equals("en", true) }?.value?.let { return it }
    return entries.sortedBy { it.key.lowercase() }.firstOrNull()?.value
}

private fun browserLanguageTags(): List<String> = listOf(browserLanguageTag(), "en").distinct()

private fun browserLanguageTag(): String = js("globalThis.navigator?.language || 'es'")

private const val WebReleaseTrack = "production"
