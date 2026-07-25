package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * IndexedDB-backed binary cache for browser Blob/HTTP references.
 *
 * URLs returned by [store] and [get] are owned by this instance and can be released with
 * [release] once a host no longer renders or uploads them.
 */
class BrowserFileCacheService : FileCacheService {
    private val issuedReferences = mutableSetOf<String>()

    override suspend fun store(cacheKey: String, file: PlatformFile): PlatformResult<PlatformFile> {
        if (!cacheKey.isSafeFileCacheKey()) return PlatformResult.Failure("invalid_cache_key")
        return browserCacheStore(cacheKey, file).also(::trackIssuedReference)
    }

    override suspend fun get(cacheKey: String): PlatformResult<PlatformFile> {
        if (!cacheKey.isSafeFileCacheKey()) return PlatformResult.Failure("invalid_cache_key")
        return browserCacheGet(cacheKey).also(::trackIssuedReference)
    }

    override suspend fun remove(cacheKey: String): PlatformResult<Unit> {
        if (!cacheKey.isSafeFileCacheKey()) return PlatformResult.Failure("invalid_cache_key")
        return suspendCoroutine<PlatformResult<Unit>> { continuation ->
            browserCacheRemove(cacheKey) { state, reason ->
                continuation.resume(
                    when (state) {
                        "success" -> PlatformResult.Success(Unit)
                        "unsupported" -> PlatformResult.Unsupported
                        else -> PlatformResult.Failure(reason)
                    },
                )
            }
        }
    }

    /** Revokes only a Blob URL created by this cache instance; arbitrary caller URLs are ignored. */
    fun release(file: PlatformFile) {
        val reference = file.reference
        if (reference.startsWith("blob:", ignoreCase = true) && issuedReferences.remove(reference)) {
            browserRevokeObjectUrl(reference)
        }
    }

    private fun trackIssuedReference(result: PlatformResult<PlatformFile>) {
        (result as? PlatformResult.Success<PlatformFile>)?.value?.reference
            ?.takeIf { it.startsWith("blob:", ignoreCase = true) }
            ?.let(issuedReferences::add)
    }
}

private suspend fun browserCacheStore(cacheKey: String, file: PlatformFile): PlatformResult<PlatformFile> =
    suspendCoroutine { continuation ->
        browserStoreFile(cacheKey, file.reference, file.displayName, file.mimeType) { state, payload ->
            continuation.resume(state.toCachedFileResult(payload))
        }
    }

private suspend fun browserCacheGet(cacheKey: String): PlatformResult<PlatformFile> = suspendCoroutine { continuation ->
    browserGetFile(cacheKey) { state, payload ->
        continuation.resume(state.toCachedFileResult(payload))
    }
}

private fun String.toCachedFileResult(payload: String?): PlatformResult<PlatformFile> = when (this) {
    "success" -> payload.toCachedPlatformFile()?.let { PlatformResult.Success(it) }
        ?: PlatformResult.Failure("cache_payload_invalid")
    "unsupported" -> PlatformResult.Unsupported
    else -> PlatformResult.Failure(payload)
}

private fun String?.toCachedPlatformFile(): PlatformFile? = runCatching {
    val value = Json.parseToJsonElement(orEmpty()).jsonObject
    val reference = requireNotNull(value["reference"]?.jsonPrimitive?.contentOrNull)
    PlatformFile(
        reference = reference,
        displayName = value["displayName"]?.jsonPrimitive?.contentOrNull,
        mimeType = value["mimeType"]?.jsonPrimitive?.contentOrNull,
        sizeBytes = value["sizeBytes"]?.jsonPrimitive?.longOrNull,
    )
}.getOrNull()

private fun String.isSafeFileCacheKey(): Boolean = matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))

private fun browserRevokeObjectUrl(reference: String): Unit = js(
    """
    (() => { if (reference.startsWith('blob:')) globalThis.URL?.revokeObjectURL?.(reference); })()
    """,
)

private fun browserStoreFile(
    cacheKey: String,
    reference: String,
    displayName: String?,
    mimeType: String?,
    onResult: (String, String?) -> Unit,
): Unit = js(
    """
    (async () => {
      if (!globalThis.indexedDB || typeof globalThis.fetch !== 'function' || !globalThis.URL?.createObjectURL) {
        onResult('unsupported', null); return;
      }
      const openDatabase = () => new Promise((resolve, reject) => {
        const request = indexedDB.open('quata-file-cache', 1);
        request.onupgradeneeded = () => {
          const db = request.result;
          if (!db.objectStoreNames.contains('files')) db.createObjectStore('files');
        };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error || new Error('web_file_cache_database_failed'));
      });
      const response = await globalThis.fetch(reference);
      if (!response.ok) throw new Error(`web_file_cache_source_${'$'}{response.status}`);
      const blob = await response.blob();
      const db = await openDatabase();
      await new Promise((resolve, reject) => {
        const transaction = db.transaction('files', 'readwrite');
        transaction.objectStore('files').put({ cacheKey, blob, displayName, mimeType: mimeType || blob.type || null, sizeBytes: blob.size }, cacheKey);
        transaction.oncomplete = resolve;
        transaction.onerror = () => reject(transaction.error || new Error('web_file_cache_write_failed'));
        transaction.onabort = () => reject(transaction.error || new Error('web_file_cache_write_aborted'));
      });
      db.close();
      onResult('success', JSON.stringify({ reference: globalThis.URL.createObjectURL(blob), displayName: displayName || null, mimeType: mimeType || blob.type || null, sizeBytes: blob.size }));
    })().catch((error) => onResult('failure', error?.message ?? error?.name ?? 'web_file_cache_store_failed'));
    """,
)

private fun browserGetFile(cacheKey: String, onResult: (String, String?) -> Unit): Unit = js(
    """
    (async () => {
      if (!globalThis.indexedDB || !globalThis.URL?.createObjectURL) { onResult('unsupported', null); return; }
      const openDatabase = () => new Promise((resolve, reject) => {
        const request = indexedDB.open('quata-file-cache', 1);
        request.onupgradeneeded = () => {
          const db = request.result;
          if (!db.objectStoreNames.contains('files')) db.createObjectStore('files');
        };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error || new Error('web_file_cache_database_failed'));
      });
      const db = await openDatabase();
      const record = await new Promise((resolve, reject) => {
        const transaction = db.transaction('files', 'readonly');
        const request = transaction.objectStore('files').get(cacheKey);
        request.onsuccess = () => resolve(request.result || null);
        request.onerror = () => reject(request.error || new Error('web_file_cache_read_failed'));
      });
      db.close();
      if (!record?.blob) { onResult('miss', 'cache_miss'); return; }
      onResult('success', JSON.stringify({ reference: globalThis.URL.createObjectURL(record.blob), displayName: record.displayName || null, mimeType: record.mimeType || record.blob.type || null, sizeBytes: record.sizeBytes ?? record.blob.size ?? null }));
    })().catch((error) => onResult('failure', error?.message ?? error?.name ?? 'web_file_cache_get_failed'));
    """,
)

private fun browserCacheRemove(cacheKey: String, onResult: (String, String?) -> Unit): Unit = js(
    """
    (async () => {
      if (!globalThis.indexedDB) { onResult('unsupported', null); return; }
      const openDatabase = () => new Promise((resolve, reject) => {
        const request = indexedDB.open('quata-file-cache', 1);
        request.onupgradeneeded = () => {
          const db = request.result;
          if (!db.objectStoreNames.contains('files')) db.createObjectStore('files');
        };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error || new Error('web_file_cache_database_failed'));
      });
      const db = await openDatabase();
      await new Promise((resolve, reject) => {
        const transaction = db.transaction('files', 'readwrite');
        transaction.objectStore('files').delete(cacheKey);
        transaction.oncomplete = resolve;
        transaction.onerror = () => reject(transaction.error || new Error('web_file_cache_delete_failed'));
        transaction.onabort = () => reject(transaction.error || new Error('web_file_cache_delete_aborted'));
      });
      db.close();
      onResult('success', null);
    })().catch((error) => onResult('failure', error?.message ?? error?.name ?? 'web_file_cache_remove_failed'));
    """,
)
