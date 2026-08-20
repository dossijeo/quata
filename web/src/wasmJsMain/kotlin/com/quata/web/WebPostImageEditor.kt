@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.quata.feature.postcomposer.imageeditor.ImageEditorPostOutputSpec
import com.quata.feature.postcomposer.imageeditor.PostImageEditorDialogContent
import com.quata.feature.postcomposer.imageeditor.PostImageEditorGeometry
import com.quata.feature.postcomposer.imageeditor.PostImageEditorTransform
import com.quata.feature.postcomposer.imageeditor.postImageEditorGeometry
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

@Composable
internal fun WebPostImageEditor(
    sourceReference: String,
    onDismiss: () -> Unit,
    onEdited: (String) -> Unit,
) {
    var transform by remember(sourceReference) { mutableStateOf(PostImageEditorTransform.Default) }
    var frameSize by remember(sourceReference) { mutableStateOf<Pair<Int, Int>?>(null) }
    val imageState = rememberBrowserCanvasImage(sourceReference)
    val geometry = (imageState as? BrowserCanvasImageState.Ready)
        ?.takeIf { frameSize != null }
        ?.let { ready ->
            postImageEditorGeometry(
                sourceWidth = ready.bitmap.width,
                sourceHeight = ready.bitmap.height,
                transform = transform,
                outputSpec = ImageEditorPostOutputSpec,
            )
        }
    val scope = rememberCoroutineScope()
    var saveInProgress by remember(sourceReference) { mutableStateOf(false) }
    fun requestSave(cropToOutputAspect: Boolean) {
        if (saveInProgress) return
        saveInProgress = true
        webPostImageEditorRecordExportStarted()
        scope.launch {
            runCatching { webPostImageEditorExportJpeg(sourceReference, transform, cropToOutputAspect) }
                .onSuccess(onEdited)
                .onFailure {
                    webPostImageEditorRecordExportFailure(it.message ?: "web_post_image_editor_export_failed")
                    saveInProgress = false
                }
        }
    }
    DisposableEffect(sourceReference, transform, saveInProgress, onDismiss, onEdited) {
        val uninstall = installWebPostImageEditorE2eBridge(
            rotate = { transform = transform.rotateClockwise() },
            reset = { transform = PostImageEditorTransform.Default },
            save = { requestSave(true) },
            dismiss = onDismiss,
        )
        onDispose { uninstall() }
    }

    PostImageEditorDialogContent(
        transform = transform,
        geometry = geometry,
        outputSpec = ImageEditorPostOutputSpec,
        onTransformChange = { transform = it },
        onDismiss = onDismiss,
        onSave = { cropToOutputAspect -> requestSave(cropToOutputAspect) },
        preview = { currentTransform, currentGeometry, cropPanelOpen, cropApplied, modifier ->
            PostImageEditorCanvasPreview(
                imageState = imageState,
                geometry = currentGeometry,
                transform = currentTransform,
                cropToOutputAspect = cropPanelOpen || cropApplied,
                modifier = modifier.onSizeChanged { frameSize = it.width to it.height },
            )
        },
    )
}

@Composable
private fun PostImageEditorCanvasPreview(
    imageState: BrowserCanvasImageState,
    geometry: PostImageEditorGeometry?,
    transform: PostImageEditorTransform,
    cropToOutputAspect: Boolean,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val ready = imageState as? BrowserCanvasImageState.Ready ?: return@Canvas
        val current = geometry
        val turns = ((transform.quarterTurns % 4) + 4) % 4
        val rotatedWidth = if (turns % 2 == 0) ready.bitmap.width else ready.bitmap.height
        val rotatedHeight = if (turns % 2 == 0) ready.bitmap.height else ready.bitmap.width
        val fitScale = minOf(size.width / rotatedWidth.toFloat(), size.height / rotatedHeight.toFloat())
        val cropScale = minOf(size.width / ImageEditorPostOutputSpec.width, size.height / ImageEditorPostOutputSpec.height)
        val drawWidth = if (cropToOutputAspect && current != null) current.sourceDrawnWidth * cropScale else ready.bitmap.width * fitScale
        val drawHeight = if (cropToOutputAspect && current != null) current.sourceDrawnHeight * cropScale else ready.bitmap.height * fitScale
        val maxPanX = if (cropToOutputAspect && current != null) current.maxPanX * cropScale else 0f
        val maxPanY = if (cropToOutputAspect && current != null) current.maxPanY * cropScale else 0f
        withTransform({
            translate(
                left = size.width / 2f + transform.panX * maxPanX,
                top = size.height / 2f + transform.panY * maxPanY,
            )
            rotate(degrees = transform.quarterTurns * 90f)
        }) {
            drawImage(
                image = ready.bitmap,
                dstOffset = IntOffset(-drawWidth.roundToInt() / 2, -drawHeight.roundToInt() / 2),
                dstSize = IntSize(drawWidth.roundToInt(), drawHeight.roundToInt()),
            )
        }
    }
}

internal suspend fun webPostImageEditorExportJpeg(
    reference: String,
    transform: PostImageEditorTransform,
    cropToOutputAspect: Boolean = true,
): String = suspendCoroutine { continuation ->
    webPostImageEditorExportJpegJs(
        reference = reference,
        zoom = transform.zoom,
        panX = transform.panX,
        panY = transform.panY,
        quarterTurns = transform.quarterTurns,
        cropToOutputAspect = cropToOutputAspect,
        onSuccess = { payload ->
            runCatching {
                Json.parseToJsonElement(payload).jsonObject["reference"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.startsWith("blob:") }
                    ?: error("web_post_image_editor_reference_invalid")
            }.onSuccess { webPostImageEditorRecordExportSuccess(it) }
                .fold(continuation::resume, { continuation.resumeWith(Result.failure(it)) })
        },
        onFailure = { continuation.resumeWith(Result.failure(IllegalStateException(it))) },
    )
}

internal fun installWebPostImageEditorE2eBridge(
    rotate: () -> Unit,
    reset: () -> Unit,
    save: () -> Unit,
    dismiss: () -> Unit,
): () -> Unit = installPostImageEditorBridgeWhenAllowed(rotate, reset, save, dismiss)

@JsFun(
    """(rotate, reset, save, dismiss) => {
      const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
      const params = new URLSearchParams(location?.search || '');
      const optedIn = params.get('quata-post-image-editor-e2e') === '1' ||
        params.get('quata-post-publish-e2e') === '1' ||
        globalThis.sessionStorage?.getItem('quata.post_publish.e2e') === '1';
      if (!local || !optedIn) return () => {};
      const bridge = Object.freeze({
        version: 1,
        rotate: () => rotate(),
        reset: () => reset(),
        save: () => save(),
        dismiss: () => dismiss(),
      });
      globalThis.__quataPostImageEditorE2eProduct = bridge;
      globalThis.document?.documentElement?.setAttribute('data-quata-post-image-editor-e2e', 'ready');
      return () => {
        if (globalThis.__quataPostImageEditorE2eProduct === bridge) delete globalThis.__quataPostImageEditorE2eProduct;
        globalThis.document?.documentElement?.removeAttribute('data-quata-post-image-editor-e2e');
      };
    }""",
)
private external fun installPostImageEditorBridgeWhenAllowed(
    rotate: () -> Unit,
    reset: () -> Unit,
    save: () -> Unit,
    dismiss: () -> Unit,
): () -> Unit

private fun webPostImageEditorRecordExportStarted(): Unit = js(
    """(() => { globalThis.__quataPostImageEditorExport = { status: 'started' }; })()""",
)

private fun webPostImageEditorRecordExportSuccess(reference: String): Unit = js(
    """(() => { globalThis.__quataPostImageEditorExport = { status: 'success', reference: String(reference).slice(0, 80) }; })()""",
)

private fun webPostImageEditorRecordExportFailure(message: String): Unit = js(
    """(() => { globalThis.__quataPostImageEditorExport = { status: 'failed', message: String(message).slice(0, 160) }; })()""",
)

private fun webPostImageEditorExportJpegJs(
    reference: String,
    zoom: Float,
    panX: Float,
    panY: Float,
    quarterTurns: Int,
    cropToOutputAspect: Boolean,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    (() => {
      try {
        if (typeof fetch !== 'function' || !globalThis.document?.createElement || !globalThis.URL?.createObjectURL) {
          onFailure('web_post_image_editor_canvas_unsupported'); return;
        }
        const loadImageFromUrl = url => new Promise((resolve, reject) => {
          const image = new Image();
          image.onload = () => resolve(image);
          image.onerror = () => reject(Error('web_post_image_editor_decode_failed'));
          image.src = url;
        });
        const sourcePromise = String(reference).startsWith('data:') || String(reference).startsWith('blob:')
          ? loadImageFromUrl(reference)
          : fetch(reference).then(response => {
              if (!response.ok) throw Error('web_post_image_editor_source_' + response.status);
              return response.blob();
            }).then(source => new Promise((resolve, reject) => {
              const sourceUrl = globalThis.URL.createObjectURL(source);
              loadImageFromUrl(sourceUrl)
                .then(image => { globalThis.URL.revokeObjectURL(sourceUrl); resolve(image); })
                .catch(error => { globalThis.URL.revokeObjectURL(sourceUrl); reject(error); });
            }));
        sourcePromise.then(image => {
          const width = Number(image.naturalWidth || image.width || 0);
          const height = Number(image.naturalHeight || image.height || 0);
          if (!width || !height) throw Error('web_post_image_editor_dimensions_invalid');
          const turns = ((Number(quarterTurns) % 4) + 4) % 4;
          const shouldCrop = Boolean(cropToOutputAspect);
          const outputWidth = shouldCrop ? 1080 : (turns % 2 === 0 ? width : height);
          const outputHeight = shouldCrop ? 1920 : (turns % 2 === 0 ? height : width);
          const canvas = globalThis.document.createElement('canvas');
          canvas.width = outputWidth; canvas.height = outputHeight;
          const context = canvas.getContext('2d');
          if (!context) throw Error('web_post_image_editor_canvas_context_unavailable');
          const scale = (shouldCrop ? Math.max(outputWidth / width, outputHeight / height) * Math.min(4, Math.max(1, Number(zoom) || 1)) : 1);
          const sourceDrawnWidth = width * scale;
          const sourceDrawnHeight = height * scale;
          const outputDrawnWidth = turns % 2 === 0 ? sourceDrawnWidth : sourceDrawnHeight;
          const outputDrawnHeight = turns % 2 === 0 ? sourceDrawnHeight : sourceDrawnWidth;
          const maxPanX = shouldCrop ? Math.max(0, (outputDrawnWidth - outputWidth) / 2) : 0;
          const maxPanY = shouldCrop ? Math.max(0, (outputDrawnHeight - outputHeight) / 2) : 0;
          context.fillStyle = '#000000';
          context.fillRect(0, 0, outputWidth, outputHeight);
          context.save();
          context.translate(
            outputWidth / 2 + Math.max(-1, Math.min(1, Number(panX) || 0)) * maxPanX,
            outputHeight / 2 + Math.max(-1, Math.min(1, Number(panY) || 0)) * maxPanY
          );
          context.rotate(turns * Math.PI / 2);
          context.drawImage(image, -sourceDrawnWidth / 2, -sourceDrawnHeight / 2, sourceDrawnWidth, sourceDrawnHeight);
          context.restore();
          canvas.toBlob(blob => {
            if (!blob || !blob.size) { onFailure('web_post_image_editor_encode_failed'); return; }
            onSuccess(JSON.stringify({ reference: globalThis.URL.createObjectURL(blob), mimeType: 'image/jpeg' }));
          }, 'image/jpeg', 0.92);
        }).catch(error => onFailure(error?.message || 'web_post_image_editor_export_failed'));
      } catch (error) { onFailure(error?.message || 'web_post_image_editor_export_failed'); }
    })()
    """,
)
