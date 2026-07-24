package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Reads image dimensions through the browser decoder without editing or copying pixels. */
class BrowserImageMetadataService : ImageMetadataService {
    override suspend fun read(file: PlatformFile): PlatformResult<ImageMetadata> {
        if (!file.isBrowserImage()) return PlatformResult.Unsupported
        return suspendCoroutine { continuation ->
            browserReadImageMetadata(file.reference) { state, payload ->
                continuation.resume(state.toImageMetadataResult(payload, file.mimeType))
            }
        }
    }
}

private fun PlatformFile.isBrowserImage(): Boolean = mimeType?.startsWith("image/", ignoreCase = true) == true ||
    displayName?.substringAfterLast('.', "")?.lowercase().orEmpty() in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "avif")

private fun String.toImageMetadataResult(payload: String?, sourceMimeType: String?): PlatformResult<ImageMetadata> = when (this) {
    "success" -> payload.toImageMetadataOrNull(sourceMimeType)?.let { PlatformResult.Success(it) }
        ?: PlatformResult.Failure("image_metadata_payload_invalid")
    "unsupported" -> PlatformResult.Unsupported
    else -> PlatformResult.Failure(payload)
}

private fun String?.toImageMetadataOrNull(sourceMimeType: String?): ImageMetadata? = runCatching {
    val objectValue = Json.parseToJsonElement(orEmpty()).jsonObject
    val width = requireNotNull(objectValue["width"]?.jsonPrimitive?.intOrNull).takeIf { it > 0 }
        ?: throw IllegalArgumentException("image_width_invalid")
    val height = requireNotNull(objectValue["height"]?.jsonPrimitive?.intOrNull).takeIf { it > 0 }
        ?: throw IllegalArgumentException("image_height_invalid")
    ImageMetadata(width, height, objectValue["mimeType"]?.jsonPrimitive?.contentOrNull ?: sourceMimeType)
}.getOrNull()

private fun browserReadImageMetadata(reference: String, onResult: (String, String?) -> Unit): Unit = js(
    """
    try {
      if (typeof globalThis.Image !== 'function') { onResult('unsupported', null); return; }
      const image = new globalThis.Image();
      image.onload = () => {
        const width = Number(image.naturalWidth || 0);
        const height = Number(image.naturalHeight || 0);
        if (!width || !height) { onResult('failure', 'image_metadata_dimensions_missing'); return; }
        onResult('success', JSON.stringify({ width, height, mimeType: null }));
      };
      image.onerror = () => onResult('failure', 'image_metadata_decode_failed');
      image.src = reference;
    } catch (error) {
      onResult('failure', error?.message ?? error?.name ?? 'image_metadata_read_failed');
    }
    """,
)
