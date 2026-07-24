package com.quata.web

import com.quata.core.platform.PlatformFile
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Browser-only Storage adapter. The shared Chat feature only deals with [PlatformFile] references;
 * resolving the browser Blob and sending it to Supabase Storage belongs at this boundary.
 */
class WebChatAttachmentUploader(
    private val configuration: WebRuntimeConfiguration,
    private val authRepository: WebAuthRepository,
) {
    suspend fun upload(profileId: String, file: PlatformFile): UploadedWebChatAttachment {
        val baseUrl = configuration.supabaseUrl?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("supabase_url_missing")
        val apiKey = configuration.supabasePublishableKey?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("supabase_publishable_key_missing")
        val credentials = authRepository.currentWebPushCredentials()
            ?: throw IllegalStateException("web_session_missing")
        val name = file.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: "attachment"
        val extension = name.substringAfterLast('.', "").cleanExtension()
            .ifBlank { file.mimeType.defaultExtension() }
            .ifBlank { "bin" }
        val safeStem = name.substringBeforeLast('.', name)
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_', '.', '-')
            .take(80)
            .ifBlank { "attachment" }
        val path = "$profileId/chat/${browserAttachmentTimestamp()}-${browserAttachmentRandomToken()}-$safeStem.$extension"
        val mimeType = file.mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
        val result = browserUploadChatAttachment(
            sourceReference = file.reference,
            uploadUrl = "$baseUrl/storage/v1/object/chat-attachments/${path.encodeStoragePath()}",
            apiKey = apiKey,
            accessToken = credentials.accessToken,
            mimeType = mimeType,
        )
        return UploadedWebChatAttachment(
            storagePath = path,
            publicUrl = "$baseUrl/storage/v1/object/public/chat-attachments/${path.encodeStoragePath()}",
            mimeType = result.mimeType ?: mimeType,
            sizeBytes = result.sizeBytes ?: file.sizeBytes,
            name = name,
            extension = extension,
        )
    }
}

data class UploadedWebChatAttachment(
    val storagePath: String,
    val publicUrl: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val name: String,
    val extension: String,
)

private data class BrowserUploadResult(val mimeType: String?, val sizeBytes: Long?)

private suspend fun browserUploadChatAttachment(
    sourceReference: String,
    uploadUrl: String,
    apiKey: String,
    accessToken: String,
    mimeType: String,
): BrowserUploadResult = suspendCoroutine { continuation ->
    browserUploadChatAttachmentRequest(
        sourceReference = sourceReference,
        uploadUrl = uploadUrl,
        apiKey = apiKey,
        accessToken = accessToken,
        mimeType = mimeType,
        onSuccess = { payload ->
            runCatching {
                val objectPayload = Json.parseToJsonElement(payload).jsonObject
                BrowserUploadResult(
                    mimeType = objectPayload["mimeType"]?.jsonPrimitive?.contentOrNull,
                    sizeBytes = objectPayload["sizeBytes"]?.jsonPrimitive?.longOrNull,
                )
            }.onSuccess(continuation::resume)
                .onFailure { error -> continuation.resumeWith(Result.failure(error)) }
        },
        onFailure = { reason -> continuation.resumeWith(Result.failure(IllegalStateException(reason))) },
    )
}

private fun browserUploadChatAttachmentRequest(
    sourceReference: String,
    uploadUrl: String,
    apiKey: String,
    accessToken: String,
    mimeType: String,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    if (typeof globalThis.fetch !== 'function') {
      onFailure('web_chat_attachment_fetch_unsupported');
      return;
    }
    globalThis.fetch(sourceReference).then(async (sourceResponse) => {
      if (!sourceResponse.ok) throw new Error(`web_chat_attachment_source_${'$'}{sourceResponse.status}`);
      const blob = await sourceResponse.blob();
      const response = await globalThis.fetch(uploadUrl, {
        method: 'POST',
        headers: {
          apikey: apiKey,
          Authorization: `Bearer ${'$'}{accessToken}`,
          'Content-Type': blob.type || mimeType,
          'x-upsert': 'false',
        },
        body: blob,
      });
      if (!response.ok) throw new Error(`web_chat_attachment_upload_${'$'}{response.status}`);
      onSuccess(JSON.stringify({
        mimeType: blob.type || mimeType || null,
        sizeBytes: Number.isFinite(blob.size) ? Math.trunc(blob.size) : null,
      }));
    }).catch((error) => onFailure(error?.message ?? error?.name ?? 'web_chat_attachment_upload_failed'));
    """,
)

private fun String.cleanExtension(): String = lowercase()
    .filter { it.isLetterOrDigit() }
    .take(12)

private fun String?.defaultExtension(): String = when (this?.lowercase()) {
    "image/jpeg" -> "jpg"
    "image/png" -> "png"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "video/mp4" -> "mp4"
    "audio/mpeg" -> "mp3"
    "audio/mp4" -> "m4a"
    "audio/ogg" -> "ogg"
    "application/pdf" -> "pdf"
    else -> ""
}

private fun String.encodeStoragePath(): String = browserEncodeStoragePath(this)
private fun browserEncodeStoragePath(path: String): String = js("path.split('/').map(encodeURIComponent).join('/')")
private fun browserAttachmentTimestamp(): String = js("String(Date.now())")
private fun browserAttachmentRandomToken(): String = js("Math.random().toString(36).slice(2, 10)")
