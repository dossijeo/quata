@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.WebElementView
import com.quata.core.platform.CameraCaptureRequest
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerSource
import com.quata.core.platform.PlatformResult
import com.quata.feature.postcomposer.data.ActorBoundPostComposerRepository
import kotlinx.browser.document
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLVideoElement

/** Browser launcher composition for the common Composer route (`#composer`). */
@Composable
fun WebPostComposerRoute(
    platformServices: WebPlatformServices,
    runtimeConfiguration: WebRuntimeConfiguration,
    authRepository: WebAuthRepository,
    onBack: () -> Unit,
    onAuthRequired: () -> Unit,
) {
    WebPostComposerHost(
        repository = remember(runtimeConfiguration, authRepository) {
            ActorBoundPostComposerRepository(WebPostComposerTransport(runtimeConfiguration, authRepository))
        },
        mediaSlots = WebComposerMediaSlots(
            pickImage = {
                platformServices.filePicker.pick(
                        FilePickerRequest(listOf("image/*"), source = FilePickerSource.Gallery),
                    ).firstReferenceOrNull()
            },
            captureImage = {
                when (val result = platformServices.cameraCapture.capturePhoto(CameraCaptureRequest("quata-photo.jpg"))) {
                    is PlatformResult.Success -> result.value.reference
                    else -> null
                }
            },
            pickVideo = {
                platformServices.filePicker.pick(
                        FilePickerRequest(listOf("video/*"), source = FilePickerSource.Gallery),
                    ).firstReferenceOrNull()
            },
            captureVideo = {
                platformServices.filePicker.pick(
                        FilePickerRequest(listOf("video/*"), source = FilePickerSource.Camera),
                    ).firstReferenceOrNull()
            },
            imagePreview = { uri, modifier -> BrowserComposerMediaPreview(uri, false, modifier) },
            videoPreview = { uri, modifier -> BrowserComposerMediaPreview(uri, true, modifier) },
        ),
        isLandscapeLayout = browserComposerIsLandscape(),
        onBack = onBack,
        onAuthRequired = onAuthRequired,
        onPostCreated = { onBack() },
    )
}

private fun PlatformResult<List<com.quata.core.platform.PlatformFile>>.firstReferenceOrNull(): String? = when (this) {
    is PlatformResult.Success -> value.firstOrNull()?.reference
    is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported -> null
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BrowserComposerMediaPreview(uri: String?, isVideo: Boolean, modifier: Modifier) {
    if (uri.isNullOrBlank()) {
        Text("Selecciona un archivo para previsualizarlo.", style = MaterialTheme.typography.bodySmall, modifier = modifier)
        return
    }
    if (isVideo) {
        WebElementView(
            factory = { (document.createElement("video") as HTMLVideoElement).apply { controls = true; preload = "metadata"; style.width = "100%" } },
            update = { it.src = uri },
            modifier = modifier.fillMaxWidth().heightIn(min = 180.dp),
        )
    } else {
        WebElementView(
            factory = { (document.createElement("img") as HTMLImageElement).apply { alt = "Vista previa de imagen"; style.width = "100%"; style.objectFit = "contain" } },
            update = { it.src = uri },
            modifier = modifier.fillMaxWidth().heightIn(min = 180.dp),
        )
    }
}

private fun browserComposerIsLandscape(): Boolean = js("Boolean(globalThis.matchMedia?.('(orientation: landscape)').matches)")
