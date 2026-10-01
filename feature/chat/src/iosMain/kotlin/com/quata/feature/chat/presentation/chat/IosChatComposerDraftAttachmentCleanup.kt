package com.quata.feature.chat.presentation.chat

import com.quata.core.platform.IosFileCacheService
import com.quata.core.platform.PlatformResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import platform.Foundation.NSUserDefaults

/** Records cleanup custody before Swift retires the generation and removes its draft metadata. */
fun prepareIosChatComposerDraftAttachmentRetirement(actorId: String, generation: Long) {
    val defaults = NSUserDefaults.standardUserDefaults
    val key = ChatComposerDraftStore.retiredCleanupKey(actorId)
    val pending = runCatching {
        defaults.stringForKey(key)
            ?.let(Json::parseToJsonElement)
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.longOrNull }
            ?.toSet()
            .orEmpty()
    }.getOrDefault(emptySet()) + generation
    defaults.setObject(
        buildJsonArray { pending.sorted().forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }.toString(),
        forKey = key,
    )
}

/** Synchronous bridge used by the Swift logout owner before it tears down the authenticated host. */
fun clearIosChatComposerDraftAttachments(actorId: String, generation: Long) {
    val removed = when (
        IosFileCacheService().removeByPrefixNow(
            chatComposerDraftAttachmentGenerationPrefix(actorId, generation),
        )
    ) {
        is PlatformResult.Success -> true
        is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported -> false
    }
    if (removed) {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = ChatComposerDraftStore.retiredCleanupKey(actorId)
        val remaining = runCatching {
            defaults.stringForKey(key)
                ?.let(Json::parseToJsonElement)
                ?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.longOrNull }
                ?.filterNot { it == generation }
                .orEmpty()
        }.getOrDefault(emptyList())
        if (remaining.isEmpty()) defaults.removeObjectForKey(key)
        else defaults.setObject(
            buildJsonArray { remaining.sorted().forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }.toString(),
            forKey = key,
        )
    }
}
