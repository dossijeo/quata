@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Browser file picker backed by a transient `<input type="file">`.
 *
 * Successful results own Blob URLs until the host has finished uploading and rendering them.
 * [release] only revokes URLs issued by this picker instance, so it is safe to expose to a
 * composition root without granting it the ability to invalidate remote or another service's URL.
 */
class BrowserFilePickerService : FilePickerService, FilePickerReferenceReleaser {
    private val issuedReferences = mutableSetOf<String>()

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
    override suspend fun pick(request: FilePickerRequest): PlatformResult<List<PlatformFile>> {
        browserChatAttachmentPickerE2eOutcome(request.source)?.let { return it }
        return suspendCoroutine { continuation ->
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
                        else -> PlatformResult.Success(result.toPlatformFiles()).also(::trackIssuedReferences)
                    }
                )
            }
        }
    }

    override suspend fun release(file: PlatformFile): PlatformResult<Unit> {
        val reference = file.reference
        if (!reference.startsWith("blob:", ignoreCase = true) || !issuedReferences.remove(reference)) {
            return PlatformResult.Failure("web_picker_file_not_owned")
        }
        browserRevokePickedFileUrl(reference)
        return PlatformResult.Success(Unit)
    }

    private fun trackIssuedReferences(result: PlatformResult<List<PlatformFile>>) {
        (result as? PlatformResult.Success<List<PlatformFile>>)?.value
            ?.asSequence()
            ?.map(PlatformFile::reference)
            ?.filter { it.startsWith("blob:", ignoreCase = true) }
            ?.forEach(issuedReferences::add)
    }
}

private fun browserChatAttachmentPickerE2eOutcome(source: FilePickerSource): PlatformResult<List<PlatformFile>>? {
    val outcome = browserChatAttachmentPickerE2eOutcomeValue(source.browserChatEvidenceSourceName()) ?: return null
    return when {
        outcome == "cancelled" -> PlatformResult.Cancelled
        outcome == "unsupported" -> PlatformResult.Unsupported
        outcome.startsWith("failure:") -> PlatformResult.Failure(outcome.removePrefix("failure:").ifBlank { "attachment_picker_e2e_failure" })
        outcome == "failure" -> PlatformResult.Failure("attachment_picker_e2e_failure")
        else -> null
    }
}

private fun FilePickerSource.browserChatEvidenceSourceName(): String = when (this) {
    FilePickerSource.Documents -> "document"
    FilePickerSource.Gallery -> "gallery"
    FilePickerSource.Camera -> "camera"
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

private fun browserRevokePickedFileUrl(reference: String): Unit = js(
    """
    (() => { if (reference.startsWith('blob:')) globalThis.URL?.revokeObjectURL?.(reference); })()
    """,
)

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

private fun browserChatAttachmentPickerE2eOutcomeValue(source: String): String? = js(
    """
    (() => {
      const fixture = globalThis.__quataChatAttachmentPickerE2E;
      if (!fixture || fixture.optIn !== 'I_ACCEPT_WEB_CHAT_ATTACHMENT_PICKER_OUTCOME') return null;
      if (fixture.source && fixture.source !== source) return null;
      const outcome = String(fixture.outcome || 'success').toLowerCase();
      if (outcome === 'failure') return 'failure:' + String(fixture.reason || 'attachment_picker_e2e_failure');
      if (outcome === 'cancelled' || outcome === 'unsupported') return outcome;
      return null;
    })()
    """,
)
