package com.quata.feature.chat.data

import com.quata.core.platform.PreferenceStore
import com.quata.core.platform.FileCacheService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class StoredChatOutgoing(
    val actorId: String,
    val conversationId: String,
    val text: String,
    val attachmentCacheKey: String? = null,
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null,
    val replyToMessageId: Long? = null,
    val clientMessageId: String,
    val registeredAttachmentIds: List<Long> = emptyList(),
    val createdAtMillis: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val leaseToken: String? = null,
    val leaseUntilMillis: Long? = null,
    val deliveredAwaitingCleanup: Boolean = false,
    val orphanCleanupBlocked: Boolean = false,
    val orphanedStoragePath: String? = null,
)

interface ChatOutgoingStore {
    suspend fun load(actorId: String): List<StoredChatOutgoing>
    suspend fun insert(message: StoredChatOutgoing): Boolean
    suspend fun claim(
        actorId: String,
        clientMessageId: String,
        leaseToken: String,
        nowMillis: Long,
        leaseUntilMillis: Long,
    ): StoredChatOutgoing?
    suspend fun renewClaim(actorId: String, clientMessageId: String, leaseToken: String, leaseUntilMillis: Long): Boolean
    suspend fun updateClaimed(message: StoredChatOutgoing, leaseToken: String): Boolean
    suspend fun removeClaimed(actorId: String, clientMessageId: String, leaseToken: String): Boolean
}

interface ChatOutgoingExecutionLock {
    suspend fun <T> withLock(actorId: String, clientMessageId: String, block: suspend () -> T): T
}

/** Shares message locks across repository instances in one process. */
internal object LocalChatOutgoingExecutionLock : ChatOutgoingExecutionLock {
    private val registryMutex = Mutex()
    private val locks = mutableMapOf<String, LockEntry>()

    override suspend fun <T> withLock(actorId: String, clientMessageId: String, block: suspend () -> T): T {
        val key = "$actorId\u0000$clientMessageId"
        val entry = registryMutex.withLock {
            locks.getOrPut(key, ::LockEntry).also { it.users += 1 }
        }
        return try {
            entry.mutex.withLock { block() }
        } finally {
            registryMutex.withLock {
                entry.users -= 1
                if (entry.users == 0 && locks[key] === entry) locks.remove(key)
            }
        }
    }

    private class LockEntry(
        val mutex: Mutex = Mutex(),
        var users: Int = 0,
    )
}

/** Process-local fallback used only by hosts/tests that have not injected durable preferences. */
internal class MemoryChatOutgoingStore : ChatOutgoingStore {
    private val mutex = Mutex()
    private val byActor = mutableMapOf<String, List<StoredChatOutgoing>>()

    override suspend fun load(actorId: String): List<StoredChatOutgoing> = mutex.withLock {
        byActor[actorId].orEmpty()
    }

    override suspend fun insert(message: StoredChatOutgoing): Boolean = mutex.withLock {
        val current = byActor[message.actorId].orEmpty().associateByTo(mutableMapOf(), StoredChatOutgoing::clientMessageId)
        if (message.clientMessageId in current) return@withLock false
        current[message.clientMessageId] = message
        byActor[message.actorId] = current.values.sortedBy(StoredChatOutgoing::createdAtMillis)
        true
    }

    override suspend fun claim(
        actorId: String,
        clientMessageId: String,
        leaseToken: String,
        nowMillis: Long,
        leaseUntilMillis: Long,
    ): StoredChatOutgoing? = mutex.withLock {
        val current = byActor[actorId].orEmpty().firstOrNull { it.clientMessageId == clientMessageId }
            ?: return@withLock null
        if ((current.leaseUntilMillis ?: Long.MIN_VALUE) > nowMillis) return@withLock null
        current.copy(leaseToken = leaseToken, leaseUntilMillis = leaseUntilMillis).also { claimed ->
            val messages = byActor[actorId].orEmpty().associateByTo(mutableMapOf(), StoredChatOutgoing::clientMessageId)
            messages[clientMessageId] = claimed
            byActor[actorId] = messages.values.sortedBy(StoredChatOutgoing::createdAtMillis)
        }
    }

    override suspend fun renewClaim(actorId: String, clientMessageId: String, leaseToken: String, leaseUntilMillis: Long): Boolean =
        mutex.withLock {
            val current = byActor[actorId].orEmpty().firstOrNull { it.clientMessageId == clientMessageId }
                ?: return@withLock false
            if (current.leaseToken != leaseToken) return@withLock false
            replaceLocked(current.copy(leaseUntilMillis = leaseUntilMillis))
            true
        }

    override suspend fun updateClaimed(message: StoredChatOutgoing, leaseToken: String): Boolean = mutex.withLock {
        val current = byActor[message.actorId].orEmpty().firstOrNull { it.clientMessageId == message.clientMessageId }
            ?: return@withLock false
        if (current.leaseToken != leaseToken) return@withLock false
        replaceLocked(message)
        true
    }

    override suspend fun removeClaimed(actorId: String, clientMessageId: String, leaseToken: String): Boolean = mutex.withLock {
        val current = byActor[actorId].orEmpty().firstOrNull { it.clientMessageId == clientMessageId }
            ?: return@withLock true
        if (current.leaseToken != leaseToken) return@withLock false
        val remaining = byActor[actorId].orEmpty().filterNot { it.clientMessageId == clientMessageId }
        if (remaining.isEmpty()) byActor.remove(actorId) else byActor[actorId] = remaining
        true
    }

    private fun replaceLocked(message: StoredChatOutgoing) {
        val messages = byActor[message.actorId].orEmpty().associateByTo(mutableMapOf(), StoredChatOutgoing::clientMessageId)
        messages[message.clientMessageId] = message
        byActor[message.actorId] = messages.values.sortedBy(StoredChatOutgoing::createdAtMillis)
    }
}

/** Process-local file cache preserving existing test/preview behavior when no durable host exists. */
internal class MemoryChatFileCacheService : FileCacheService {
    private val mutex = Mutex()
    private val files = mutableMapOf<String, PlatformFile>()

    override suspend fun store(cacheKey: String, file: PlatformFile): PlatformResult<PlatformFile> = mutex.withLock {
        files[cacheKey] = file
        PlatformResult.Success(file)
    }

    override suspend fun get(cacheKey: String): PlatformResult<PlatformFile> = mutex.withLock {
        files[cacheKey]?.let { PlatformResult.Success(it) } ?: PlatformResult.Failure("file_cache_miss")
    }

    override suspend fun remove(cacheKey: String): PlatformResult<Unit> = mutex.withLock {
        files.remove(cacheKey)
        PlatformResult.Success(Unit)
    }
}

/**
 * Actor-scoped durable outbox metadata. Corrupt payloads fail closed and remain untouched so a
 * later repair cannot silently discard a message the user already entrusted to the application.
 */
class PreferenceChatOutgoingStore(
    private val preferences: PreferenceStore,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true },
) : ChatOutgoingStore {
    override suspend fun load(actorId: String): List<StoredChatOutgoing> = preferenceOutgoingMutex.withLock {
        loadLocked(actorId)
    }

    override suspend fun insert(message: StoredChatOutgoing): Boolean = preferenceOutgoingMutex.withLock {
        val messages = loadLocked(message.actorId).associateByTo(mutableMapOf(), StoredChatOutgoing::clientMessageId)
        if (message.clientMessageId in messages) return@withLock false
        messages[message.clientMessageId] = message
        writeLocked(message.actorId, messages.values.toList())
        true
    }

    override suspend fun claim(
        actorId: String,
        clientMessageId: String,
        leaseToken: String,
        nowMillis: Long,
        leaseUntilMillis: Long,
    ): StoredChatOutgoing? = preferenceOutgoingMutex.withLock {
        val messages = loadLocked(actorId).associateByTo(mutableMapOf(), StoredChatOutgoing::clientMessageId)
        val current = messages[clientMessageId] ?: return@withLock null
        if ((current.leaseUntilMillis ?: Long.MIN_VALUE) > nowMillis) return@withLock null
        current.copy(leaseToken = leaseToken, leaseUntilMillis = leaseUntilMillis).also { claimed ->
            messages[clientMessageId] = claimed
            writeLocked(actorId, messages.values.toList())
        }
    }

    override suspend fun renewClaim(actorId: String, clientMessageId: String, leaseToken: String, leaseUntilMillis: Long): Boolean =
        preferenceOutgoingMutex.withLock {
            mutateClaimedLocked(actorId, clientMessageId, leaseToken) { it.copy(leaseUntilMillis = leaseUntilMillis) }
        }

    override suspend fun updateClaimed(message: StoredChatOutgoing, leaseToken: String): Boolean =
        preferenceOutgoingMutex.withLock {
            mutateClaimedLocked(message.actorId, message.clientMessageId, leaseToken) { message }
        }

    override suspend fun removeClaimed(actorId: String, clientMessageId: String, leaseToken: String): Boolean =
        preferenceOutgoingMutex.withLock {
            val messages = loadLocked(actorId).associateByTo(mutableMapOf(), StoredChatOutgoing::clientMessageId)
            val current = messages[clientMessageId] ?: return@withLock true
            if (current.leaseToken != leaseToken) return@withLock false
            messages.remove(clientMessageId)
            writeLocked(actorId, messages.values.toList())
            true
        }

    private fun key(actorId: String): String {
        require(actorId.isNotBlank() && actorId.length <= 160 && actorId.all(::isSafeActorKeyCharacter)) {
            "chat_outbox_actor_invalid"
        }
        return "quata.chat.outbox.v1.$actorId"
    }

    private suspend fun loadLocked(actorId: String): List<StoredChatOutgoing> {
        val raw = preferences.getString(key(actorId)) ?: return emptyList()
        return decodeStoredChatOutgoingList(json, raw).also { messages ->
            require(messages.all { it.actorId == actorId }) { "chat_outbox_actor_mismatch" }
            require(messages.map(StoredChatOutgoing::clientMessageId).distinct().size == messages.size) {
                "chat_outbox_duplicate_client_message_id"
            }
        }
    }

    private suspend fun writeLocked(actorId: String, messages: List<StoredChatOutgoing>) {
        require(messages.all { it.actorId == actorId }) { "chat_outbox_actor_mismatch" }
        if (messages.isEmpty()) preferences.remove(key(actorId))
        else preferences.putString(key(actorId), encodeStoredChatOutgoingList(messages))
    }

    private suspend fun mutateClaimedLocked(
        actorId: String,
        clientMessageId: String,
        leaseToken: String,
        transform: (StoredChatOutgoing) -> StoredChatOutgoing,
    ): Boolean {
        val messages = loadLocked(actorId).associateByTo(mutableMapOf(), StoredChatOutgoing::clientMessageId)
        val current = messages[clientMessageId] ?: return false
        if (current.leaseToken != leaseToken) return false
        messages[clientMessageId] = transform(current)
        writeLocked(actorId, messages.values.toList())
        return true
    }
}

fun encodeStoredChatOutgoingList(messages: List<StoredChatOutgoing>): String = buildJsonArray {
        messages.forEach { message ->
            add(buildJsonObject {
                put("actorId", message.actorId)
                put("conversationId", message.conversationId)
                put("text", message.text)
                message.attachmentCacheKey?.let { put("attachmentCacheKey", it) }
                message.attachmentName?.let { put("attachmentName", it) }
                message.attachmentMimeType?.let { put("attachmentMimeType", it) }
                message.replyToMessageId?.let { put("replyToMessageId", it) }
                put("clientMessageId", message.clientMessageId)
                put("registeredAttachmentIds", buildJsonArray {
                    message.registeredAttachmentIds.forEach { add(JsonPrimitive(it)) }
                })
                put("createdAtMillis", message.createdAtMillis)
                put("attempts", message.attempts)
                message.lastError?.let { put("lastError", it) }
                message.leaseToken?.let { put("leaseToken", it) }
                message.leaseUntilMillis?.let { put("leaseUntilMillis", it) }
                put("deliveredAwaitingCleanup", message.deliveredAwaitingCleanup)
                put("orphanCleanupBlocked", message.orphanCleanupBlocked)
                message.orphanedStoragePath?.let { put("orphanedStoragePath", it) }
            })
        }
    }.toString()

fun decodeStoredChatOutgoingList(json: Json, raw: String): List<StoredChatOutgoing> =
    json.parseToJsonElement(raw).jsonArray.map { element ->
            val value = element.jsonObject
            StoredChatOutgoing(
                actorId = value.requiredString("actorId"),
                conversationId = value.requiredString("conversationId"),
                text = value.requiredString("text"),
                attachmentCacheKey = value.optionalString("attachmentCacheKey"),
                attachmentName = value.optionalString("attachmentName"),
                attachmentMimeType = value.optionalString("attachmentMimeType"),
                replyToMessageId = value["replyToMessageId"]?.jsonPrimitive?.longOrNull,
                clientMessageId = value.requiredString("clientMessageId"),
                registeredAttachmentIds = value["registeredAttachmentIds"]?.jsonArray
                    ?.map { it.jsonPrimitive.longOrNull ?: error("chat_outbox_invalid_attachment_id") }
                    .orEmpty(),
                createdAtMillis = value["createdAtMillis"]?.jsonPrimitive?.longOrNull
                    ?: error("chat_outbox_missing_created_at"),
                attempts = value["attempts"]?.jsonPrimitive?.intOrNull ?: 0,
                lastError = value.optionalString("lastError"),
                leaseToken = value.optionalString("leaseToken"),
                leaseUntilMillis = value["leaseUntilMillis"]?.jsonPrimitive?.longOrNull,
                deliveredAwaitingCleanup = value["deliveredAwaitingCleanup"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                orphanCleanupBlocked = value["orphanCleanupBlocked"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                orphanedStoragePath = value.optionalString("orphanedStoragePath"),
            )
        }

private fun Map<String, kotlinx.serialization.json.JsonElement>.requiredString(name: String): String =
    optionalString(name) ?: error("chat_outbox_missing_$name")

private fun Map<String, kotlinx.serialization.json.JsonElement>.optionalString(name: String): String? =
    get(name)?.jsonPrimitive?.contentOrNull

private fun isSafeActorKeyCharacter(value: Char): Boolean =
    value.isLetterOrDigit() || value == '-' || value == '_' || value == '.' || value == ':'

private val preferenceOutgoingMutex = Mutex()
