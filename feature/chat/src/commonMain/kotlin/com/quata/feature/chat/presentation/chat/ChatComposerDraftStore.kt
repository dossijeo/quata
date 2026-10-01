package com.quata.feature.chat.presentation.chat

import com.quata.core.platform.FileCacheService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.PreferenceStore
import com.quata.core.platform.PrefixClearableFileCacheService
import com.quata.core.platform.PrefixClearablePreferenceStore
import com.quata.core.platform.UnsupportedFileCacheService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class ChatComposerDraftLease internal constructor(
    internal val actorId: String,
    internal val generation: Long,
)

enum class ChatComposerDraftMode { Plain, Reply, Edit }

data class ChatComposerDraftAttachment(
    val cacheKey: String,
    val reference: String,
    val name: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
) {
    init {
        require(cacheKey.isNotBlank())
        require(reference.isNotBlank())
    }
}

data class ChatComposerDraftRecord(
    val text: String,
    val mode: ChatComposerDraftMode = ChatComposerDraftMode.Plain,
    val targetMessageId: String? = null,
    val attachment: ChatComposerDraftAttachment? = null,
) {
    init {
        require((mode == ChatComposerDraftMode.Plain) == (targetMessageId == null))
        require(targetMessageId == null || targetMessageId.isNotBlank())
    }

    val isEmpty: Boolean get() = mode == ChatComposerDraftMode.Plain && text.isEmpty() && attachment == null
}

/** Durable actor/conversation composer state. Message targets and cached files are revalidated. */
class ChatComposerDraftStore(
    private val preferences: PreferenceStore,
    private val attachmentFiles: FileCacheService = UnsupportedFileCacheService,
) {
    private val retainedAttachmentKeys = mutableMapOf<String, Int>()

    suspend fun open(actorId: String): ChatComposerDraftLease {
        val generation = currentGeneration(actorId)
        retryRetiredGenerationCleanup(actorId, generation)
        retryDetachedAttachmentCleanup(actorId, generation)
        return ChatComposerDraftLease(actorId, generation)
    }

    fun retainAttachment(cacheKey: String) {
        retainedAttachmentKeys[cacheKey] = retainedAttachmentKeys.getOrElse(cacheKey) { 0 } + 1
    }

    suspend fun releaseAttachment(
        lease: ChatComposerDraftLease,
        conversationId: String,
        cacheKey: String,
    ) {
        val remainingRetainers = retainedAttachmentKeys.getOrElse(cacheKey) { 0 } - 1
        if (remainingRetainers > 0) retainedAttachmentKeys[cacheKey] = remainingRetainers
        else retainedAttachmentKeys.remove(cacheKey)
        reconcileRecordCleanup(lease, conversationId)
    }

    suspend fun readRecord(lease: ChatComposerDraftLease, conversationId: String): ChatComposerDraftRecord? {
        if (currentGeneration(lease.actorId) != lease.generation) return null
        val key = conversationKey(lease.actorId, lease.generation, conversationId)
        val raw = preferences.getString(key) ?: return null
        var stored = raw.toStoredDraftRecord() ?: return preferences.remove(key).let { null }
        if (stored.generation != lease.generation) {
            preferences.remove(key)
            return null
        }
        stored = reconcileRecordCleanup(lease, conversationId, stored) ?: return null
        val attachment = stored.attachment ?: return stored.toDraftRecord(null).takeUnless(ChatComposerDraftRecord::isEmpty)
        return when (val cached = attachmentFiles.get(attachment.cacheKey)) {
            is PlatformResult.Success -> stored.toDraftRecord(
                ChatComposerDraftAttachment(
                    cacheKey = attachment.cacheKey,
                    reference = cached.value.reference,
                    name = attachment.name ?: cached.value.displayName,
                    mimeType = attachment.mimeType ?: cached.value.mimeType,
                    sizeBytes = attachment.sizeBytes ?: cached.value.sizeBytes,
                ),
            )
            is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported -> {
                val recovered = stored.toDraftRecord(null)
                writeRecord(lease, conversationId, recovered)
                recovered.takeUnless(ChatComposerDraftRecord::isEmpty)
            }
        }
    }

    suspend fun read(lease: ChatComposerDraftLease, conversationId: String): String? =
        readRecord(lease, conversationId)?.text?.takeIf(String::isNotEmpty)

    suspend fun stageAttachment(
        lease: ChatComposerDraftLease,
        uniqueId: String,
        file: PlatformFile,
    ): PlatformResult<ChatComposerDraftAttachment> {
        if (currentGeneration(lease.actorId) != lease.generation) {
            return PlatformResult.Failure("composer_draft_generation_retired")
        }
        val suffix = uniqueId.filter { it.isLetterOrDigit() || it in "._:-" }
        if (suffix.isEmpty()) return PlatformResult.Failure("composer_attachment_key_invalid")
        val cacheKey = attachmentGenerationPrefix(lease.actorId, lease.generation) + suffix
        if (cacheKey.length > MaxAttachmentCacheKeyLength) {
            return PlatformResult.Failure("composer_attachment_key_too_long")
        }
        return when (val cached = attachmentFiles.store(cacheKey, file)) {
            is PlatformResult.Success -> {
                if (currentGeneration(lease.actorId) != lease.generation) {
                    queueDetachedAttachmentCleanup(lease, cacheKey)
                    PlatformResult.Failure("composer_draft_generation_retired")
                } else {
                    PlatformResult.Success(
                        ChatComposerDraftAttachment(
                            cacheKey = cacheKey,
                            reference = cached.value.reference,
                            name = file.displayName ?: cached.value.displayName,
                            mimeType = file.mimeType ?: cached.value.mimeType,
                            sizeBytes = file.sizeBytes ?: cached.value.sizeBytes,
                        ),
                    )
                }
            }
            is PlatformResult.Failure -> cached
            PlatformResult.Cancelled -> PlatformResult.Cancelled
            PlatformResult.Unsupported -> PlatformResult.Unsupported
        }
    }

    suspend fun discardStagedAttachment(lease: ChatComposerDraftLease, attachment: ChatComposerDraftAttachment) {
        if (attachment.cacheKey.startsWith(attachmentGenerationPrefix(lease.actorId, lease.generation))) {
            queueDetachedAttachmentCleanup(lease, attachment.cacheKey)
        }
    }

    suspend fun writeRecord(
        lease: ChatComposerDraftLease,
        conversationId: String,
        draft: ChatComposerDraftRecord,
    ) {
        if (currentGeneration(lease.actorId) != lease.generation) return
        val key = conversationKey(lease.actorId, lease.generation, conversationId)
        val oldStored = preferences.getString(key)?.toStoredDraftRecord()
        val oldAttachmentKey = oldStored?.attachment?.cacheKey
        val attachment = draft.attachment
        if (attachment != null && !attachment.cacheKey.startsWith(attachmentGenerationPrefix(lease.actorId, lease.generation))) {
            return
        }
        val pendingCleanupKeys = buildSet {
            addAll(oldStored?.pendingCleanupKeys.orEmpty())
            if (oldAttachmentKey != null && oldAttachmentKey != attachment?.cacheKey) add(oldAttachmentKey)
        }
        val stored = StoredDraftRecord(
            generation = lease.generation,
            text = draft.text,
            mode = draft.mode,
            targetMessageId = draft.targetMessageId,
            attachment = attachment?.let { StoredAttachment(it.cacheKey, it.name, it.mimeType, it.sizeBytes) },
            pendingCleanupKeys = pendingCleanupKeys,
        )
        preferences.putString(key, stored.encode())
        if (currentGeneration(lease.actorId) != lease.generation) {
            preferences.remove(key)
            attachment?.cacheKey?.let { queueDetachedAttachmentCleanup(lease, it) }
            return
        }
        reconcileRecordCleanup(lease, conversationId, stored)
    }

    suspend fun write(lease: ChatComposerDraftLease, conversationId: String, text: String) =
        writeRecord(lease, conversationId, ChatComposerDraftRecord(text))

    suspend fun clear(lease: ChatComposerDraftLease, conversationId: String) {
        if (currentGeneration(lease.actorId) != lease.generation) return
        writeRecord(lease, conversationId, ChatComposerDraftRecord(""))
    }

    suspend fun read(actorId: String, conversationId: String): String? = read(open(actorId), conversationId)
    suspend fun readRecord(actorId: String, conversationId: String): ChatComposerDraftRecord? =
        readRecord(open(actorId), conversationId)
    suspend fun write(actorId: String, conversationId: String, text: String) = write(open(actorId), conversationId, text)
    suspend fun writeRecord(actorId: String, conversationId: String, draft: ChatComposerDraftRecord) =
        writeRecord(open(actorId), conversationId, draft)
    suspend fun clear(actorId: String, conversationId: String) = clear(open(actorId), conversationId)

    suspend fun clearActor(actorId: String) {
        val retiringGeneration = currentGeneration(actorId)
        val pending = retiredGenerations(actorId) + retiringGeneration
        preferences.putString(retiredCleanupKey(actorId), pending.sorted().encodeLongs())
        preferences.putString(retirementKey(actorId), (retiringGeneration + 1L).toString())
        (preferences as? PrefixClearablePreferenceStore)
            ?.removeByPrefix(generationPrefix(actorId, retiringGeneration))
        preferences.remove(detachedCleanupKey(actorId, retiringGeneration))
        retryRetiredGenerationCleanup(actorId, retiringGeneration + 1L)
    }

    private suspend fun reconcileRecordCleanup(
        lease: ChatComposerDraftLease,
        conversationId: String,
        supplied: StoredDraftRecord? = null,
    ): StoredDraftRecord? {
        if (currentGeneration(lease.actorId) != lease.generation) return null
        val key = conversationKey(lease.actorId, lease.generation, conversationId)
        val stored = supplied ?: preferences.getString(key)?.toStoredDraftRecord() ?: return null
        val activeKey = stored.attachment?.cacheKey
        val remaining = stored.pendingCleanupKeys.filterTo(linkedSetOf()) { pendingKey ->
            if (pendingKey == activeKey || pendingKey in retainedAttachmentKeys) return@filterTo true
            attachmentFiles.remove(pendingKey) !is PlatformResult.Success
        }
        val reconciled = stored.copy(pendingCleanupKeys = remaining)
        if (reconciled.toDraftRecord(null).isEmpty && remaining.isEmpty()) {
            preferences.remove(key)
            return null
        }
        if (reconciled != stored) preferences.putString(key, reconciled.encode())
        return reconciled
    }

    private suspend fun queueDetachedAttachmentCleanup(lease: ChatComposerDraftLease, cacheKey: String) {
        val key = detachedCleanupKey(lease.actorId, lease.generation)
        val pending = preferences.getString(key).decodeStrings() + cacheKey
        preferences.putString(key, pending.sorted().encodeStrings())
        retryDetachedAttachmentCleanup(lease.actorId, lease.generation)
    }

    private suspend fun retryDetachedAttachmentCleanup(actorId: String, generation: Long) {
        val key = detachedCleanupKey(actorId, generation)
        val remaining = preferences.getString(key).decodeStrings().filterTo(linkedSetOf()) {
            it in retainedAttachmentKeys || attachmentFiles.remove(it) !is PlatformResult.Success
        }
        if (remaining.isEmpty()) preferences.remove(key)
        else preferences.putString(key, remaining.sorted().encodeStrings())
    }

    private suspend fun retryRetiredGenerationCleanup(actorId: String, currentGeneration: Long) {
        val service = attachmentFiles as? PrefixClearableFileCacheService ?: return
        val remaining = retiredGenerations(actorId).filterTo(linkedSetOf()) { generation ->
            generation >= currentGeneration ||
                service.removeByPrefix(attachmentGenerationPrefix(actorId, generation)) !is PlatformResult.Success
        }
        if (remaining.isEmpty()) preferences.remove(retiredCleanupKey(actorId))
        else preferences.putString(retiredCleanupKey(actorId), remaining.sorted().encodeLongs())
    }

    private suspend fun retiredGenerations(actorId: String): Set<Long> =
        preferences.getString(retiredCleanupKey(actorId)).decodeLongs()

    private suspend fun currentGeneration(actorId: String): Long =
        preferences.getString(retirementKey(actorId))?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

    private data class StoredAttachment(
        val cacheKey: String,
        val name: String?,
        val mimeType: String?,
        val sizeBytes: Long?,
    )

    private data class StoredDraftRecord(
        val generation: Long,
        val text: String,
        val mode: ChatComposerDraftMode,
        val targetMessageId: String?,
        val attachment: StoredAttachment?,
        val pendingCleanupKeys: Set<String> = emptySet(),
    ) {
        fun toDraftRecord(resolvedAttachment: ChatComposerDraftAttachment?): ChatComposerDraftRecord =
            ChatComposerDraftRecord(text, mode, targetMessageId, resolvedAttachment)

        fun encode(): String = buildJsonObject {
            put("generation", generation)
            put("text", text)
            put("mode", mode.name.lowercase())
            targetMessageId?.let { put("targetMessageId", it) }
            attachment?.let {
                put("attachmentCacheKey", it.cacheKey)
                it.name?.let { value -> put("attachmentName", value) }
                it.mimeType?.let { value -> put("attachmentMimeType", value) }
                it.sizeBytes?.let { value -> put("attachmentSizeBytes", value) }
            }
            if (pendingCleanupKeys.isNotEmpty()) {
                put("pendingAttachmentCleanup", buildJsonArray {
                    pendingCleanupKeys.sorted().forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                })
            }
        }.toString()
    }

    private fun String.toStoredDraftRecord(): StoredDraftRecord? = runCatching {
        val value = Json.parseToJsonElement(this).jsonObject
        val mode = when (value["mode"]?.jsonPrimitive?.contentOrNull) {
            null, "plain" -> ChatComposerDraftMode.Plain
            "reply" -> ChatComposerDraftMode.Reply
            "edit" -> ChatComposerDraftMode.Edit
            else -> return null
        }
        val cacheKey = value["attachmentCacheKey"]?.jsonPrimitive?.contentOrNull
        StoredDraftRecord(
            generation = value["generation"]?.jsonPrimitive?.longOrNull ?: return null,
            text = value["text"]?.jsonPrimitive?.contentOrNull ?: return null,
            mode = mode,
            targetMessageId = value["targetMessageId"]?.jsonPrimitive?.contentOrNull,
            attachment = cacheKey?.let {
                StoredAttachment(
                    cacheKey = it,
                    name = value["attachmentName"]?.jsonPrimitive?.contentOrNull,
                    mimeType = value["attachmentMimeType"]?.jsonPrimitive?.contentOrNull,
                    sizeBytes = value["attachmentSizeBytes"]?.jsonPrimitive?.longOrNull,
                )
            },
            pendingCleanupKeys = value["pendingAttachmentCleanup"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?.toSet()
                .orEmpty(),
        ).also { stored -> stored.toDraftRecord(null) }
    }.getOrNull()

    companion object {
        private const val MaxAttachmentCacheKeyLength = 120

        internal fun actorPrefix(actorId: String): String =
            "quata.chat.composer.drafts.v2.${actorId.length}:$actorId."

        internal fun generationPrefix(actorId: String, generation: Long): String =
            "${actorPrefix(actorId)}g$generation."

        internal fun conversationKey(actorId: String, generation: Long, conversationId: String): String =
            "${generationPrefix(actorId, generation)}${conversationId.length}:$conversationId"

        internal fun retirementKey(actorId: String): String =
            "quata.chat.composer.retired.v2.${actorId.length}:$actorId"

        internal fun retiredCleanupKey(actorId: String): String =
            "quata.chat.composer.retired-cleanup.v1.${actorId.length}:$actorId"

        internal fun detachedCleanupKey(actorId: String, generation: Long): String =
            "quata.chat.composer.detached-cleanup.v1.${actorId.length}:$actorId.g$generation"

        internal fun attachmentGenerationPrefix(actorId: String, generation: Long): String =
            "qccd-${actorId.stableCacheHash()}-g$generation-"
    }
}

private fun Iterable<String>.encodeStrings(): String = buildJsonArray {
    forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
}.toString()

private fun String?.decodeStrings(): Set<String> = runCatching {
    this?.let(Json::parseToJsonElement)?.jsonArray
        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
        ?.toSet()
        .orEmpty()
}.getOrDefault(emptySet())

private fun Iterable<Long>.encodeLongs(): String = buildJsonArray {
    forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
}.toString()

private fun String?.decodeLongs(): Set<Long> = runCatching {
    this?.let(Json::parseToJsonElement)?.jsonArray
        ?.mapNotNull { it.jsonPrimitive.longOrNull }
        ?.toSet()
        .orEmpty()
}.getOrDefault(emptySet())

private fun String.stableCacheHash(): String {
    var hash = 14695981039346656037uL
    encodeToByteArray().forEach { byte ->
        hash = (hash xor byte.toUByte().toULong()) * 1099511628211uL
    }
    return hash.toString(16)
}

fun chatComposerDraftGenerationPrefix(actorId: String, generation: Long): String =
    ChatComposerDraftStore.generationPrefix(actorId, generation)
fun chatComposerDraftRetirementKey(actorId: String): String = ChatComposerDraftStore.retirementKey(actorId)
fun chatComposerDraftAttachmentGenerationPrefix(actorId: String, generation: Long): String =
    ChatComposerDraftStore.attachmentGenerationPrefix(actorId, generation)
