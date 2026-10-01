package com.quata.feature.chat.presentation.chat

import com.quata.core.platform.PreferenceStore
import com.quata.core.platform.PrefixClearablePreferenceStore
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

/** Durable text-only drafts. Each actor/conversation owns an independent preference record. */
class ChatComposerDraftStore(private val preferences: PreferenceStore) {
    suspend fun open(actorId: String): ChatComposerDraftLease =
        ChatComposerDraftLease(actorId, currentGeneration(actorId))

    suspend fun read(lease: ChatComposerDraftLease, conversationId: String): String? {
        if (currentGeneration(lease.actorId) != lease.generation) return null
        val key = conversationKey(lease.actorId, lease.generation, conversationId)
        val raw = preferences.getString(key) ?: return null
        val record = raw.toDraftRecord() ?: return preferences.remove(key).let { null }
        return if (record.generation == lease.generation) {
            record.text.takeIf(String::isNotEmpty)
        } else {
            preferences.remove(key)
            null
        }
    }

    suspend fun write(lease: ChatComposerDraftLease, conversationId: String, text: String) {
        if (currentGeneration(lease.actorId) != lease.generation) return
        val key = conversationKey(lease.actorId, lease.generation, conversationId)
        if (text.isEmpty()) {
            preferences.remove(key)
            return
        }
        preferences.putString(key, buildJsonObject {
            put("generation", lease.generation)
            put("text", text)
        }.toString())
        if (currentGeneration(lease.actorId) != lease.generation) preferences.remove(key)
    }

    suspend fun clear(lease: ChatComposerDraftLease, conversationId: String) {
        if (currentGeneration(lease.actorId) != lease.generation) return
        val key = conversationKey(lease.actorId, lease.generation, conversationId)
        val record = preferences.getString(key)?.toDraftRecord()
        if (record?.generation == lease.generation) preferences.remove(key)
    }

    suspend fun read(actorId: String, conversationId: String): String? = read(open(actorId), conversationId)
    suspend fun write(actorId: String, conversationId: String, text: String) = write(open(actorId), conversationId, text)
    suspend fun clear(actorId: String, conversationId: String) = clear(open(actorId), conversationId)

    suspend fun clearActor(actorId: String) {
        val retiringGeneration = currentGeneration(actorId)
        preferences.putString(retirementKey(actorId), (retiringGeneration + 1L).toString())
        (preferences as? PrefixClearablePreferenceStore)
            ?.removeByPrefix(generationPrefix(actorId, retiringGeneration))
    }

    private suspend fun currentGeneration(actorId: String): Long =
        preferences.getString(retirementKey(actorId))?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

    private data class DraftRecord(val generation: Long, val text: String)

    private fun String.toDraftRecord(): DraftRecord? = runCatching {
        val value = Json.parseToJsonElement(this).jsonObject
        DraftRecord(
            generation = value["generation"]?.jsonPrimitive?.longOrNull ?: return null,
            text = value["text"]?.jsonPrimitive?.contentOrNull ?: return null,
        )
    }.getOrNull()

    companion object {
        internal fun actorPrefix(actorId: String): String =
            "quata.chat.composer.drafts.v2.${actorId.length}:$actorId."

        internal fun generationPrefix(actorId: String, generation: Long): String =
            "${actorPrefix(actorId)}g$generation."

        internal fun conversationKey(actorId: String, generation: Long, conversationId: String): String =
            "${generationPrefix(actorId, generation)}${conversationId.length}:$conversationId"

        internal fun retirementKey(actorId: String): String =
            "quata.chat.composer.retired.v2.${actorId.length}:$actorId"
    }
}

fun chatComposerDraftGenerationPrefix(actorId: String, generation: Long): String =
    ChatComposerDraftStore.generationPrefix(actorId, generation)
fun chatComposerDraftRetirementKey(actorId: String): String = ChatComposerDraftStore.retirementKey(actorId)
