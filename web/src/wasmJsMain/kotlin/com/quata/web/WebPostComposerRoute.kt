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
) {
    val scope = rememberCoroutineScope()
    val repository = remember(runtimeConfiguration, authRepository) {
        WebPostComposerRepository(
            configuration = runtimeConfiguration,
            authRepository = authRepository,
            client = WebPostgrestClient(runtimeConfiguration, authRepository),
        )
    }
    WebPostComposerHost(
        repository = repository,
        mediaSlots = WebComposerMediaSlots(
            imageGallery = { modifier, onSelected ->
                BrowserPickerButton("Elegir imagen", modifier) {
                    platformServices.filePicker.pick(
                        FilePickerRequest(listOf("image/*"), source = FilePickerSource.Gallery),
                    ).firstReferenceOrNull()?.let(onSelected)
                }
            },
            imageCamera = { modifier, onSelected ->
                BrowserPickerButton("Tomar foto", modifier) {
                    when (val result = platformServices.cameraCapture.capturePhoto(CameraCaptureRequest("quata-photo.jpg"))) {
                        is PlatformResult.Success -> onSelected(result.value.reference)
                        else -> Unit
                    }
                }
            },
            videoGallery = { modifier, onSelected ->
                BrowserPickerButton("Elegir v\u00eddeo", modifier) {
                    platformServices.filePicker.pick(
                        FilePickerRequest(listOf("video/*"), source = FilePickerSource.Gallery),
                    ).firstReferenceOrNull()?.let(onSelected)
                }
            },
            videoCamera = { modifier, onSelected ->
                BrowserPickerButton("Grabar v\u00eddeo", modifier) {
                    platformServices.filePicker.pick(
                        FilePickerRequest(listOf("video/*"), source = FilePickerSource.Camera),
                    ).firstReferenceOrNull()?.let(onSelected)
                }
            },
            preview = { uri, isVideo, modifier -> BrowserComposerMediaPreview(uri, isVideo, modifier) },
        ),
        isLandscapeLayout = browserComposerIsLandscape(),
    )
}

@Composable
private fun BrowserPickerButton(label: String, modifier: Modifier, select: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    Button(onClick = { scope.launch { select() } }, modifier = modifier) { Text(label) }
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
