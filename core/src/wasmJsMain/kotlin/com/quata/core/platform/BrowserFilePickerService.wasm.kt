package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Browser file picker backed by a transient `<input type="file">`. */
class BrowserFilePickerService : FilePickerService {
    override suspend fun pickFiles(
        acceptedMimeTypes: List<String>,
        allowMultiple: Boolean
    ): PlatformResult<List<PlatformFile>> = pick(
        FilePickerRequest(
            acceptedMimeTypes = acceptedMimeTypes,
            allowMultiple = allowMultiple,
            source = FilePickerSource.Documents,
        ),
    )

    /** Uses the browser's real file/gallery chooser or capture control when the UA supports it. */
    override suspend fun pick(request: FilePickerRequest): PlatformResult<List<PlatformFile>> = suspendCoroutine { continuation ->
        val acceptedMimeTypes = request.browserAcceptedMimeTypes()
        browserPickFiles(
            accept = acceptedMimeTypes.joinToString(","),
            allowMultiple = request.allowMultiple && request.source != FilePickerSource.Camera,
            capture = request.source == FilePickerSource.Camera,
        ) { result ->
            continuation.resume(
                when (result) {
                    BrowserPickerUnsupported -> PlatformResult.Unsupported
                    null -> PlatformResult.Cancelled
                    else -> PlatformResult.Success(result.toPlatformFiles())
                }
            )
        }
    }
}

/** Keeps browser gallery/camera defaults aligned with Android's visual-media request. */
private fun FilePickerRequest.browserAcceptedMimeTypes(): List<String> {
    val explicit = acceptedMimeTypes.filter { it.isNotBlank() }.distinct()
    if (explicit.isNotEmpty()) return explicit
    return when (source) {
        FilePickerSource.Gallery,
        FilePickerSource.Camera -> listOf("image/*")
        FilePickerSource.Documents -> emptyList()
    }
}

private fun String.toPlatformFiles(): List<PlatformFile> = runCatching {
    Json.parseToJsonElement(this).jsonArray.mapNotNull { element ->
        val file = element.jsonObject
        val reference = file["reference"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        PlatformFile(
            reference = reference,
            displayName = file["displayName"]?.jsonPrimitive?.contentOrNull,
            mimeType = file["mimeType"]?.jsonPrimitive?.contentOrNull,
            sizeBytes = file["sizeBytes"]?.jsonPrimitive?.longOrNull
        )
    }
}.getOrElse { emptyList() }

private fun browserPickFiles(
    accept: String,
    allowMultiple: Boolean,
    capture: Boolean,
    onResult: (String?) -> Unit
) {
    js(
        """
        try {
          const document = globalThis.document;
          if (!document || typeof document.createElement !== 'function') {
            onResult('unsupported');
          } else {
            const input = document.createElement('input');
            input.type = 'file';
            input.accept = accept;
            input.multiple = allowMultiple;
            if (capture) input.setAttribute('capture', 'environment');
            input.style.display = 'none';
            let completed = false;
            const onWindowFocus = () => {
              // Some desktop browsers do not dispatch `cancel` for a file input. Once their
              // chooser returns focus, an empty selection is the only reliable cancellation cue.
              globalThis.setTimeout(() => {
                if (!completed && !(input.files && input.files.length)) finish(null);
              }, 0);
            };
            const finish = (value) => {
              if (completed) return;
              completed = true;
              globalThis.removeEventListener?.('focus', onWindowFocus);
              input.remove();
              onResult(value);
            };
            input.addEventListener('change', () => {
              const files = Array.from(input.files || []);
              if (files.length === 0) {
                finish(null);
              } else {
                finish(JSON.stringify(files.map((file) => ({
                  reference: globalThis.URL.createObjectURL(file),
                  displayName: file.name || null,
                  mimeType: file.type || null,
                  sizeBytes: file.size
                }))));
              }
            }, { once: true });
            input.addEventListener('cancel', () => finish(null), { once: true });
            document.body?.appendChild(input);
            globalThis.addEventListener?.('focus', onWindowFocus, { once: true });
            input.click();
          }
        } catch (_) {
          onResult('unsupported');
        }
        """
    )
}

private const val BrowserPickerUnsupported = "unsupported"
