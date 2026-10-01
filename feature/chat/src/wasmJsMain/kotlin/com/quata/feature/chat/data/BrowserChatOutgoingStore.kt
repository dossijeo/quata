@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.feature.chat.data

import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** IndexedDB records are independently keyed so concurrent tabs cannot replace each other's queue. */
class BrowserChatOutgoingStore(
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ChatOutgoingStore {
    override suspend fun load(actorId: String): List<StoredChatOutgoing> = suspendCoroutine { continuation ->
        browserOutboxLoad(actorId) { state, payload ->
            continuation.resumeWith(runCatching {
                if (state == "success") decode(actorId, payload) else error(payload ?: state)
            })
        }
    }

    override suspend fun insert(message: StoredChatOutgoing): Boolean = suspendCoroutine { continuation ->
        browserOutboxPut(message.actorId, message.clientMessageId, encodeStoredChatOutgoingList(listOf(message))) { state, reason ->
            continuation.resumeWith(runCatching {
                when (state) {
                    "inserted" -> true
                    "exists" -> false
                    else -> error(reason ?: state)
                }
            })
        }
    }

    override suspend fun claim(
        actorId: String,
        clientMessageId: String,
        leaseToken: String,
        nowMillis: Long,
        leaseUntilMillis: Long,
    ): StoredChatOutgoing? = suspendCoroutine { continuation ->
        browserOutboxClaim(actorId, clientMessageId, leaseToken, nowMillis.toDouble(), leaseUntilMillis.toDouble()) { state, payload ->
            continuation.resumeWith(runCatching {
                when (state) {
                    "success" -> decode(actorId, payload).single()
                    "missing", "leased" -> null
                    else -> error(payload ?: state)
                }
            })
        }
    }

    override suspend fun renewClaim(
        actorId: String,
        clientMessageId: String,
        leaseToken: String,
        leaseUntilMillis: Long,
    ): Boolean = mutateClaimed(actorId, clientMessageId, leaseToken, null, leaseUntilMillis, remove = false)

    override suspend fun updateClaimed(message: StoredChatOutgoing, leaseToken: String): Boolean =
        mutateClaimed(
            message.actorId,
            message.clientMessageId,
            leaseToken,
            encodeStoredChatOutgoingList(listOf(message)),
            null,
            remove = false,
        )

    override suspend fun removeClaimed(actorId: String, clientMessageId: String, leaseToken: String): Boolean =
        mutateClaimed(actorId, clientMessageId, leaseToken, null, null, remove = true)

    private suspend fun mutateClaimed(
        actorId: String,
        clientMessageId: String,
        leaseToken: String,
        payload: String?,
        leaseUntilMillis: Long?,
        remove: Boolean,
    ): Boolean = suspendCoroutine { continuation ->
        browserOutboxMutateClaimed(
            actorId,
            clientMessageId,
            leaseToken,
            payload,
            leaseUntilMillis?.toDouble(),
            remove,
        ) { state, reason ->
            continuation.resumeWith(runCatching {
                when (state) {
                    "success" -> true
                    "missing", "lease_lost" -> false
                    else -> error(reason ?: state)
                }
            })
        }
    }

    private fun decode(actorId: String, payload: String?): List<StoredChatOutgoing> =
        decodeStoredChatOutgoingList(json, requireNotNull(payload)).also { records ->
            require(records.all { it.actorId == actorId }) { "chat_outbox_actor_mismatch" }
            require(records.map(StoredChatOutgoing::clientMessageId).distinct().size == records.size) {
                "chat_outbox_duplicate_client_message_id"
            }
        }
}

/** Holds a Web Lock for the complete remote effect sequence, including while a tab is suspended. */
class BrowserChatOutgoingExecutionLock : ChatOutgoingExecutionLock {
    override suspend fun <T> withLock(actorId: String, clientMessageId: String, block: suspend () -> T): T =
        suspendCoroutine { continuation ->
            val name = "quata-chat-outbox:$actorId:$clientMessageId"
            browserAcquireOutboxLock(
                name,
                onAcquired = { release ->
                    CoroutineScope(Dispatchers.Default).launch {
                        val result = runCatching { block() }
                        release()
                        continuation.resumeWith(result)
                    }
                },
                onFailure = { reason ->
                    continuation.resumeWith(Result.failure(IllegalStateException(reason)))
                },
            )
        }
}

private fun browserAcquireOutboxLock(
    name: String,
    onAcquired: (() -> Unit) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    (() => {
      if (!globalThis.navigator?.locks?.request) { onFailure('web_chat_outbox_locks_unavailable'); return; }
      globalThis.navigator.locks.request(name, () => new Promise((resolve) => onAcquired(resolve)))
        .catch((error) => onFailure(error?.message ?? error?.name ?? 'web_chat_outbox_lock_failed'));
    })()
    """,
)

private fun browserOutboxLoad(actorId: String, callback: (String, String?) -> Unit): Unit = js(
    """
    (async () => {
      if (!globalThis.indexedDB) { callback('failure', 'web_chat_outbox_indexeddb_unavailable'); return; }
      const db = await new Promise((resolve, reject) => {
        const request = indexedDB.open('quata-chat-outbox', 1);
        request.onupgradeneeded = () => { if (!request.result.objectStoreNames.contains('messages')) request.result.createObjectStore('messages'); };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error || new Error('web_chat_outbox_database_failed'));
      });
      const records = await new Promise((resolve, reject) => {
        const tx = db.transaction('messages', 'readonly');
        const request = tx.objectStore('messages').getAll();
        request.onsuccess = () => resolve(request.result || []);
        request.onerror = () => reject(request.error || new Error('web_chat_outbox_read_failed'));
      });
      db.close();
      const actorRecords = records.map(JSON.parse).filter((record) => record.actorId === actorId);
      callback('success', JSON.stringify(actorRecords));
    })().catch((error) => callback('failure', error?.message ?? error?.name ?? 'web_chat_outbox_load_failed'))
    """,
)

private fun browserOutboxPut(actorId: String, clientMessageId: String, payload: String, callback: (String, String?) -> Unit): Unit = js(
    """
    (async () => {
      const record = JSON.parse(payload)[0];
      if (!record || record.actorId !== actorId || record.clientMessageId !== clientMessageId) throw new Error('web_chat_outbox_record_mismatch');
      const db = await new Promise((resolve, reject) => {
        const request = indexedDB.open('quata-chat-outbox', 1);
        request.onupgradeneeded = () => { if (!request.result.objectStoreNames.contains('messages')) request.result.createObjectStore('messages'); };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error || new Error('web_chat_outbox_database_failed'));
      });
      let outcome = 'exists';
      await new Promise((resolve, reject) => {
        const tx = db.transaction('messages', 'readwrite');
        const store = tx.objectStore('messages');
        const key = actorId + '\u0000' + clientMessageId;
        const request = store.get(key);
        request.onsuccess = () => {
          if (request.result) return;
          store.put(JSON.stringify(record), key);
          outcome = 'inserted';
        };
        request.onerror = () => reject(request.error || new Error('web_chat_outbox_insert_read_failed'));
        tx.oncomplete = resolve;
        tx.onerror = () => reject(tx.error || new Error('web_chat_outbox_write_failed'));
        tx.onabort = () => reject(tx.error || new Error('web_chat_outbox_write_aborted'));
      });
      db.close(); callback(outcome, null);
    })().catch((error) => callback('failure', error?.message ?? error?.name ?? 'web_chat_outbox_put_failed'))
    """,
)

private fun browserOutboxMutateClaimed(
    actorId: String,
    clientMessageId: String,
    leaseToken: String,
    payload: String?,
    leaseUntilMillis: Double?,
    remove: Boolean,
    callback: (String, String?) -> Unit,
): Unit = js(
    """
    (async () => {
      const db = await new Promise((resolve, reject) => {
        const request = indexedDB.open('quata-chat-outbox', 1);
        request.onupgradeneeded = () => { if (!request.result.objectStoreNames.contains('messages')) request.result.createObjectStore('messages'); };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error || new Error('web_chat_outbox_database_failed'));
      });
      const key = actorId + '\u0000' + clientMessageId;
      let outcome = 'missing';
      await new Promise((resolve, reject) => {
        const tx = db.transaction('messages', 'readwrite');
        const store = tx.objectStore('messages');
        const request = store.get(key);
        request.onsuccess = () => {
          if (!request.result) return;
          const current = JSON.parse(request.result);
          if (current.leaseToken !== leaseToken) { outcome = 'lease_lost'; return; }
          if (remove) store.delete(key);
          else if (payload) {
            const replacement = JSON.parse(payload)[0];
            if (replacement.actorId !== actorId || replacement.clientMessageId !== clientMessageId) {
              tx.abort(); reject(new Error('web_chat_outbox_record_mismatch')); return;
            }
            store.put(JSON.stringify(replacement), key);
          } else {
            current.leaseUntilMillis = leaseUntilMillis;
            store.put(JSON.stringify(current), key);
          }
          outcome = 'success';
        };
        request.onerror = () => reject(request.error || new Error('web_chat_outbox_mutation_read_failed'));
        tx.oncomplete = resolve;
        tx.onerror = () => reject(tx.error || new Error('web_chat_outbox_mutation_failed'));
        tx.onabort = () => reject(tx.error || new Error('web_chat_outbox_mutation_aborted'));
      });
      db.close(); callback(outcome, null);
    })().catch((error) => callback('failure', error?.message ?? error?.name ?? 'web_chat_outbox_mutation_failed'))
    """,
)

private fun browserOutboxClaim(
    actorId: String,
    clientMessageId: String,
    leaseToken: String,
    nowMillis: Double,
    leaseUntilMillis: Double,
    callback: (String, String?) -> Unit,
): Unit = js(
    """
    (async () => {
      const db = await new Promise((resolve, reject) => {
        const request = indexedDB.open('quata-chat-outbox', 1);
        request.onupgradeneeded = () => { if (!request.result.objectStoreNames.contains('messages')) request.result.createObjectStore('messages'); };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error || new Error('web_chat_outbox_database_failed'));
      });
      const key = actorId + '\u0000' + clientMessageId;
      let outcome = 'missing'; let result = null;
      await new Promise((resolve, reject) => {
        const tx = db.transaction('messages', 'readwrite');
        const store = tx.objectStore('messages');
        const request = store.get(key);
        request.onsuccess = () => {
          if (!request.result) return;
          const record = JSON.parse(request.result);
          if (Number(record.leaseUntilMillis || 0) > nowMillis) { outcome = 'leased'; return; }
          record.leaseToken = leaseToken;
          record.leaseUntilMillis = leaseUntilMillis;
          result = record;
          outcome = 'success';
          store.put(JSON.stringify(record), key);
        };
        request.onerror = () => reject(request.error || new Error('web_chat_outbox_claim_read_failed'));
        tx.oncomplete = resolve;
        tx.onerror = () => reject(tx.error || new Error('web_chat_outbox_claim_failed'));
        tx.onabort = () => reject(tx.error || new Error('web_chat_outbox_claim_aborted'));
      });
      db.close(); callback(outcome, result ? JSON.stringify([result]) : null);
    })().catch((error) => callback('failure', error?.message ?? error?.name ?? 'web_chat_outbox_claim_failed'))
    """,
)
