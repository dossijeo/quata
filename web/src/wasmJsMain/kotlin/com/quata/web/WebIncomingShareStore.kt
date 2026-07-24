package com.quata.web

import com.quata.feature.externalshare.ExternalShareAttachment
import com.quata.feature.externalshare.ExternalSharePayload
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Browser boundary for payloads received by the Web Share Target service worker.
 *
 * Files stay as Blobs in IndexedDB until the user sends or discards the share. Reading a payload
 * only creates window-local Blob URLs, so an app reload cannot silently lose an incoming share.
 */
class WebIncomingShareStore {
    suspend fun readOldest(): Result<ExternalSharePayload?> = suspendCoroutine { continuation ->
        browserReadIncomingShare(
            onSuccess = { json ->
                runCatching { json.toExternalSharePayloadOrNull() }
                    .onSuccess { continuation.resume(Result.success(it)) }
                    .onFailure { continuation.resume(Result.failure(it)) }
            },
            onFailure = { reason -> continuation.resume(Result.failure(IllegalStateException(reason))) },
        )
    }

    suspend fun discard(payload: ExternalSharePayload): Result<Unit> = suspendCoroutine { continuation ->
        browserRemoveIncomingShare(
            payloadId = payload.id,
            referencesJson = Json.encodeToString(
                ListSerializer(String.serializer()),
                payload.attachments.map(ExternalShareAttachment::uri),
            ),
            onSuccess = { continuation.resume(Result.success(Unit)) },
            onFailure = { reason -> continuation.resume(Result.failure(IllegalStateException(reason))) },
        )
    }
}

private fun String.toExternalSharePayloadOrNull(): ExternalSharePayload? {
    if (isBlank()) return null
    val root = Json.parseToJsonElement(this).jsonObject
    val id = root["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return null
    val attachments = root["attachments"]?.jsonArray.orEmpty().mapNotNull { item ->
        val objectValue = item.jsonObject
        val uri = objectValue["uri"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        ExternalShareAttachment(
            uri = uri,
            name = objectValue["name"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "attachment" },
            mimeType = objectValue["mimeType"]?.jsonPrimitive?.contentOrNull,
        )
    }
    val text = root["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return ExternalSharePayload(id = id, text = text, attachments = attachments).takeIf {
        it.text.isNotBlank() || it.attachments.isNotEmpty()
    }
}

private fun browserReadIncomingShare(
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    const request = indexedDB.open('quata-web', 2);
    request.onupgradeneeded = () => {
      const database = request.result;
      if (!database.objectStoreNames.contains('incoming-shares')) {
        database.createObjectStore('incoming-shares', { keyPath: 'id' });
      }
    };
    request.onerror = () => onFailure('web_share_store_open_failed');
    request.onsuccess = () => {
      const database = request.result;
      const transaction = database.transaction('incoming-shares', 'readonly');
      const store = transaction.objectStore('incoming-shares');
      const cursor = store.openCursor();
      cursor.onerror = () => onFailure('web_share_store_read_failed');
      cursor.onsuccess = () => {
        const entry = cursor.result?.value;
        if (!entry) { onSuccess(''); return; }
        try {
          onSuccess(JSON.stringify({
            id: entry.id,
            text: entry.text || '',
            attachments: (entry.attachments || []).map((attachment) => ({
              uri: URL.createObjectURL(attachment.blob),
              name: attachment.name || 'attachment',
              mimeType: attachment.mimeType || attachment.blob?.type || null,
            })),
          }));
        } catch (_) { onFailure('web_share_store_payload_failed'); }
      };
    };
    """,
)

private fun browserRemoveIncomingShare(
    payloadId: String,
    referencesJson: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    try {
      JSON.parse(referencesJson).forEach((reference) => {
        if (typeof reference === 'string' && reference.startsWith('blob:')) URL.revokeObjectURL(reference);
      });
    } catch (_) {}
    const request = indexedDB.open('quata-web', 2);
    request.onupgradeneeded = () => {
      const database = request.result;
      if (!database.objectStoreNames.contains('incoming-shares')) {
        database.createObjectStore('incoming-shares', { keyPath: 'id' });
      }
    };
    request.onerror = () => onFailure('web_share_store_open_failed');
    request.onsuccess = () => {
      const database = request.result;
      const transaction = database.transaction('incoming-shares', 'readwrite');
      transaction.objectStore('incoming-shares').delete(payloadId);
      transaction.oncomplete = () => onSuccess();
      transaction.onerror = () => onFailure('web_share_store_remove_failed');
      transaction.onabort = () => onFailure('web_share_store_remove_failed');
    };
    """,
)
