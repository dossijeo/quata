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
import com.quata.core.platform.PlatformPermission
import com.quata.core.platform.PermissionStatus
import com.quata.feature.postcomposer.data.ActorBoundPostComposerRepository
import com.quata.feature.postcomposer.data.FailInsertAfterUploadComposerTransport
import com.quata.feature.postcomposer.data.FailOncePostComposerRepository
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
    val scope = rememberCoroutineScope()
    WebPostComposerHost(
        repository = remember(runtimeConfiguration, authRepository) {
            val transport = WebPostComposerTransport(runtimeConfiguration, authRepository).let { base ->
                if (webPostComposerStorageRollbackEvidenceShouldFailAfterUpload()) {
                    FailInsertAfterUploadComposerTransport(base)
                } else {
                    base
                }
            }
            val real = ActorBoundPostComposerRepository(transport)
            if (webPostComposerProgressRollbackEvidenceShouldFailOnce()) {
                FailOncePostComposerRepository(real)
            } else {
                real
            }
        },
        mediaSlots = WebComposerMediaSlots(
            pickImage = {
                if (webPostComposerPickerEvidenceShouldHandle("gallery-image")) {
                    webPostComposerPickerEvidenceResult("gallery-image")
                } else platformServices.filePicker.pick(
                        FilePickerRequest(listOf("image/*"), source = FilePickerSource.Gallery),
                    ).firstReferenceResult()
            },
            captureImage = {
                if (webPostComposerPickerEvidenceShouldHandle("camera-image")) {
                    webPostComposerPickerEvidenceResult("camera-image")
                } else when (val result = platformServices.cameraCapture.capturePhoto(CameraCaptureRequest("quata-photo.jpg"))) {
                    is PlatformResult.Success -> PlatformResult.Success(result.value.reference)
                    is PlatformResult.Failure -> result
                    PlatformResult.Cancelled -> PlatformResult.Cancelled
                    PlatformResult.Unsupported -> PlatformResult.Unsupported
                }
            },
            pickVideo = {
                if (webPostComposerPickerEvidenceShouldHandle("gallery-video")) {
                    webPostComposerPickerEvidenceResult("gallery-video")
                } else platformServices.filePicker.pick(
                        FilePickerRequest(listOf("video/*"), source = FilePickerSource.Gallery),
                    ).firstReferenceResult()
            },
            captureVideo = {
                if (webPostComposerPickerEvidenceShouldHandle("camera-video")) {
                    webPostComposerPickerEvidenceResult("camera-video")
                } else platformServices.filePicker.pick(
                        FilePickerRequest(listOf("video/*"), source = FilePickerSource.Camera),
                    ).firstReferenceResult()
            },
            editImage = { current ->
                if (webPostComposerImageEditorEvidenceShouldHandle()) {
                    webPostComposerImageEditorEvidenceReference(current)
                } else null
            },
            imageEditor = { source, dismiss, edited ->
                WebPostImageEditor(
                    sourceReference = source,
                    onDismiss = dismiss,
                    onEdited = edited,
                )
            },
            videoEditor = { source, dismiss, edited ->
                WebPostVideoEditor(
                    sourceReference = source,
                    onDismiss = dismiss,
                    onEdited = edited,
                )
            },
            imagePreview = { uri, modifier -> BrowserComposerMediaPreview(uri, false, modifier) },
            videoPreview = { uri, modifier -> BrowserComposerMediaPreview(uri, true, modifier) },
            requestLocation = { resolved ->
                scope.launch {
                    if (platformServices.permissions.status(PlatformPermission.Location) != PermissionStatus.Granted &&
                        platformServices.permissions.request(PlatformPermission.Location) != PermissionStatus.Granted
                    ) return@launch
                    val location = (platformServices.location.currentLocation() as? PlatformResult.Success)?.value
                        ?: return@launch
                    resolved(
                        webComposerCoordinateLabel(location.latitude, location.longitude),
                        location.latitude,
                        location.longitude,
                    )
                }
            },
        ),
        isLandscapeLayout = browserComposerIsLandscape(),
        onBack = onBack,
        onAuthRequired = onAuthRequired,
        onPostCreated = { onBack() },
        canPublish = webComposerCanPublish(authRepository.activeProfileSessionOrNull()),
    )
}

internal fun webComposerCanPublish(session: WebLocalSession?): Boolean = session != null

internal fun webComposerCoordinateLabel(latitude: Double, longitude: Double): String = "$latitude, $longitude"

private fun PlatformResult<List<com.quata.core.platform.PlatformFile>>.firstReferenceResult(): PlatformResult<String> = when (this) {
    is PlatformResult.Success -> value.firstOrNull()?.reference?.takeIf(String::isNotBlank)
        ?.let { PlatformResult.Success(it) }
        ?: PlatformResult.Failure("post_composer_picker_empty")
    is PlatformResult.Failure -> this
    PlatformResult.Cancelled -> PlatformResult.Cancelled
    PlatformResult.Unsupported -> PlatformResult.Unsupported
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

private fun webPostComposerProgressRollbackEvidenceShouldFailOnce(): Boolean = js(
    """
    (() => {
      const local = globalThis.location?.hostname === 'localhost' || globalThis.location?.hostname === '127.0.0.1';
      const params = new URLSearchParams(globalThis.location?.search || '');
      return local &&
        params.get('quata-post-progress-rollback-e2e') === '1' &&
        globalThis.localStorage?.getItem('quata_post_progress_rollback_fail_once') === '1';
    })()
    """,
)

private fun webPostComposerStorageRollbackEvidenceShouldFailAfterUpload(): Boolean = js(
    """
    (() => {
      const local = globalThis.location?.hostname === 'localhost' || globalThis.location?.hostname === '127.0.0.1';
      const params = new URLSearchParams(globalThis.location?.search || '');
      return local &&
        params.get('quata-post-storage-rollback-e2e') === '1' &&
        globalThis.localStorage?.getItem('quata_post_storage_rollback_fail_after_upload') === '1';
    })()
    """,
)

private fun webPostComposerPickerEvidenceShouldHandle(source: String): Boolean = js(
    """
    (() => {
      const params = new URLSearchParams(globalThis.location?.search || '');
      if (params.get('quata-post-picker-camera-e2e') !== '1') return false;
      if (globalThis.localStorage?.getItem('quata_post_composer_picker_e2e_opt_in') !== 'I_ACCEPT_WEB_POST_COMPOSER_PICKER_FIXTURE') return false;
      return globalThis.localStorage?.getItem('quata_post_composer_picker_e2e_source') === source;
    })()
    """,
)

private fun webPostComposerPickerEvidenceReference(source: String): String? = js(
    """
    (() => {
      const params = new URLSearchParams(globalThis.location?.search || '');
      if (params.get('quata-post-picker-camera-e2e') !== '1') return null;
      if (globalThis.localStorage?.getItem('quata_post_composer_picker_e2e_opt_in') !== 'I_ACCEPT_WEB_POST_COMPOSER_PICKER_FIXTURE') return null;
      if (globalThis.localStorage?.getItem('quata_post_composer_picker_e2e_source') !== source) return null;
      const outcome = String(globalThis.localStorage?.getItem('quata_post_composer_picker_e2e_outcome') || 'success').toLowerCase();
      if (outcome !== 'success') return null;
      return globalThis.localStorage?.getItem('quata_post_composer_picker_e2e_reference') || null;
    })()
    """,
)

private fun webPostComposerPickerEvidenceResult(source: String): PlatformResult<String> {
    val outcome = webPostComposerPickerEvidenceOutcome(source)
    return when (outcome) {
        "success" -> webPostComposerPickerEvidenceReference(source)?.takeIf(String::isNotBlank)
            ?.let { PlatformResult.Success(it) }
            ?: PlatformResult.Failure("post_composer_picker_e2e_missing_reference")
        "cancelled" -> PlatformResult.Cancelled
        "failure" -> PlatformResult.Failure(webPostComposerPickerEvidenceReason(source) ?: "post_composer_picker_e2e_failure")
        "unsupported" -> PlatformResult.Unsupported
        else -> PlatformResult.Failure("post_composer_picker_e2e_unknown_outcome")
    }
}

private fun webPostComposerPickerEvidenceOutcome(source: String): String? = js(
    """
    (() => {
      const params = new URLSearchParams(globalThis.location?.search || '');
      if (params.get('quata-post-picker-camera-e2e') !== '1') return null;
      if (globalThis.localStorage?.getItem('quata_post_composer_picker_e2e_opt_in') !== 'I_ACCEPT_WEB_POST_COMPOSER_PICKER_FIXTURE') return null;
      if (globalThis.localStorage?.getItem('quata_post_composer_picker_e2e_source') !== source) return null;
      return String(globalThis.localStorage?.getItem('quata_post_composer_picker_e2e_outcome') || 'success').toLowerCase();
    })()
    """,
)

private fun webPostComposerPickerEvidenceReason(source: String): String? = js(
    """
    (() => {
      if (globalThis.localStorage?.getItem('quata_post_composer_picker_e2e_source') !== source) return null;
      return globalThis.localStorage?.getItem('quata_post_composer_picker_e2e_reason') || null;
    })()
    """,
)

private fun webPostComposerImageEditorEvidenceShouldHandle(): Boolean = js(
    """
    (() => {
      const params = new URLSearchParams(globalThis.location?.search || '');
      if (params.get('quata-post-image-editor-e2e') !== '1') return false;
      return globalThis.localStorage?.getItem('quata_post_composer_image_editor_e2e_opt_in') === 'I_ACCEPT_WEB_POST_COMPOSER_IMAGE_EDITOR_FIXTURE';
    })()
    """,
)

private fun webPostComposerImageEditorEvidenceReference(current: String): String? =
    webPostComposerImageEditorEvidenceOverride() ?: "$current#quata-edited-image"

private fun webPostComposerImageEditorEvidenceOverride(): String? = js(
    """
    (() => {
      const params = new URLSearchParams(globalThis.location?.search || '');
      if (params.get('quata-post-image-editor-e2e') !== '1') return null;
      if (globalThis.localStorage?.getItem('quata_post_composer_image_editor_e2e_opt_in') !== 'I_ACCEPT_WEB_POST_COMPOSER_IMAGE_EDITOR_FIXTURE') return null;
      return globalThis.localStorage?.getItem('quata_post_composer_image_editor_e2e_reference') || null;
    })()
    """,
)
