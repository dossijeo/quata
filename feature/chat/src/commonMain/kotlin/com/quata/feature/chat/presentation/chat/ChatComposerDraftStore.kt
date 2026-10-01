package com.quata.feature.chat.presentation.chat

import com.quata.core.platform.FileCacheService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.PreferenceStore
import com.quata.core.platform.PrefixClearableFileCacheService
import com.quata.core.platform.PrefixClearablePreferenceStore
import com.quata.core.platform.UnsupportedFileCacheService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
    suspend fun open(actorId: String): ChatComposerDraftLease =
        ChatComposerDraftLease(actorId, currentGeneration(actorId))

    suspend fun readRecord(lease: ChatComposerDraftLease, conversationId: String): ChatComposerDraftRecord? {
        if (currentGeneration(lease.actorId) != lease.generation) return null
        val key = conversationKey(lease.actorId, lease.generation, conversationId)
        val raw = preferences.getString(key) ?: return null
        val stored = raw.toStoredDraftRecord() ?: return preferences.remove(key).let { null }
        if (stored.generation != lease.generation) {
            preferences.remove(key)
            stored.attachment?.cacheKey?.let { attachmentFiles.remove(it) }
            return null
        }
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
                    attachmentFiles.remove(cacheKey)
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
            attachmentFiles.remove(attachment.cacheKey)
        }
    }

    suspend fun writeRecord(
        lease: ChatComposerDraftLease,
        conversationId: String,
        draft: ChatComposerDraftRecord,
    ) {
        if (currentGeneration(lease.actorId) != lease.generation) return
        val key = conversationKey(lease.actorId, lease.generation, conversationId)
        val oldAttachmentKey = preferences.getString(key)
            ?.toStoredDraftRecord()
            ?.attachment
            ?.cacheKey
        val attachment = draft.attachment
        if (attachment != null && !attachment.cacheKey.startsWith(attachmentGenerationPrefix(lease.actorId, lease.generation))) {
            return
        }
        if (draft.isEmpty) {
            preferences.remove(key)
        } else {
            preferences.putString(key, buildJsonObject {
                put("generation", lease.generation)
                put("text", draft.text)
                put("mode", draft.mode.name.lowercase())
                draft.targetMessageId?.let { put("targetMessageId", it) }
                attachment?.let {
                    put("attachmentCacheKey", it.cacheKey)
                    it.name?.let { value -> put("attachmentName", value) }
                    it.mimeType?.let { value -> put("attachmentMimeType", value) }
                    it.sizeBytes?.let { value -> put("attachmentSizeBytes", value) }
                }
            }.toString())
        }
        if (currentGeneration(lease.actorId) != lease.generation) {
            preferences.remove(key)
            attachment?.cacheKey?.let { attachmentFiles.remove(it) }
        }
        if (oldAttachmentKey != null && oldAttachmentKey != attachment?.cacheKey) {
            attachmentFiles.remove(oldAttachmentKey)
        }
    }

    suspend fun write(lease: ChatComposerDraftLease, conversationId: String, text: String) =
        writeRecord(lease, conversationId, ChatComposerDraftRecord(text))

    suspend fun clear(lease: ChatComposerDraftLease, conversationId: String) {
        if (currentGeneration(lease.actorId) != lease.generation) return
        val key = conversationKey(lease.actorId, lease.generation, conversationId)
        val attachmentKey = preferences.getString(key)
            ?.toStoredDraftRecord()
            ?.takeIf { it.generation == lease.generation }
            ?.attachment
            ?.cacheKey
        preferences.remove(key)
        attachmentKey?.let { attachmentFiles.remove(it) }
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
        preferences.putString(retirementKey(actorId), (retiringGeneration + 1L).toString())
        (preferences as? PrefixClearablePreferenceStore)
            ?.removeByPrefix(generationPrefix(actorId, retiringGeneration))
        (attachmentFiles as? PrefixClearableFileCacheService)
            ?.removeByPrefix(attachmentGenerationPrefix(actorId, retiringGeneration))
    }

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
    ) {
        fun toDraftRecord(resolvedAttachment: ChatComposerDraftAttachment?): ChatComposerDraftRecord =
            ChatComposerDraftRecord(text, mode, targetMessageId, resolvedAttachment)
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

        internal fun attachmentGenerationPrefix(actorId: String, generation: Long): String =
            "qccd-${actorId.stableCacheHash()}-g$generation-"
    }
}

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
