package com.quata.web

import com.quata.core.platform.PrefixClearableFileCacheService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.feature.postcomposer.presentation.PostComposerDraftMediaKind
import com.quata.feature.postcomposer.presentation.PostComposerDraftMediaPersistence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Keeps browser-owned composer media in IndexedDB while the durable draft stores only a short,
 * actor-bound cache reference. Blob URLs are recreated after a document reload and never treated
 * as durable identifiers.
 */
class WebPostComposerDraftMediaStore(
    private val files: PrefixClearableFileCacheService,
    actorProfileId: String,
    private val releaseCachedReference: (PlatformFile) -> Unit = {},
) {
    private val actorPrefix = buildString {
        append("post-composer-draft.")
        append(actorProfileId.take(72))
        append('.')
        append(actorProfileId.hashCode().toUInt().toString(16))
        append('.')
    }
    private val runtimeKeys = mutableMapOf<String, String>()

    suspend fun persist(reference: String, kind: PostComposerDraftMediaKind): PostComposerDraftMediaPersistence? {
        if (reference.isRemoteMediaReference()) return PostComposerDraftMediaPersistence(reference, created = false)
        // runtimeKeys contains only URLs produced by restore from an already committed envelope.
        // A raw local URL always gets a fresh key, so provisional media cannot be adopted by a
        // concurrent save before the envelope that owns it is confirmed.
        val existingKey = reference.cacheKeyOrNull()?.takeIf { it.startsWith(kindPrefix(kind)) }
            ?: runtimeKeys[reference]?.takeIf { it.startsWith(kindPrefix(kind)) }
        if (existingKey != null) {
            return PostComposerDraftMediaPersistence(existingKey.toDraftCacheReference(), created = false)
        }
        val expectedKey = key(kind, reference)
        val stored = try {
            files.store(expectedKey, PlatformFile(reference))
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { removeBestEffort(expectedKey) }
            throw cancelled
        } catch (_: Throwable) {
            removeBestEffort(expectedKey)
            return null
        }
        return when (stored) {
            is PlatformResult.Success -> {
                releaseCachedReference(stored.value)
                PostComposerDraftMediaPersistence(expectedKey.toDraftCacheReference(), created = true)
            }
            is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported -> {
                files.remove(expectedKey)
                null
            }
        }
    }

    suspend fun discard(persistence: PostComposerDraftMediaPersistence, kind: PostComposerDraftMediaKind) {
        if (!persistence.created) return
        val key = persistence.reference.cacheKeyOrNull()?.takeIf { it.startsWith(kindPrefix(kind)) } ?: return
        runtimeKeys.entries.removeAll { (runtime, cachedKey) ->
            (cachedKey == key).also { remove -> if (remove) releaseCachedReference(PlatformFile(runtime)) }
        }
        files.remove(key)
    }

    suspend fun restore(reference: String, kind: PostComposerDraftMediaKind): String? {
        if (reference.isRemoteMediaReference()) return reference
        val expectedKey = reference.cacheKeyOrNull()?.takeIf { it.startsWith(kindPrefix(kind)) } ?: return null
        return when (val restored = files.get(expectedKey)) {
            is PlatformResult.Success -> restored.value.reference.also { runtimeKeys[it] = expectedKey }
            is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported -> null
        }
    }

    suspend fun clear(): Boolean {
        runtimeKeys.keys.forEach { releaseCachedReference(PlatformFile(it)) }
        runtimeKeys.clear()
        return files.removeByPrefix(actorPrefix) is PlatformResult.Success
    }

    suspend fun reconcile(imageReference: String?, videoReference: String?): Boolean = listOf(
        PostComposerDraftMediaKind.Image to imageReference,
        PostComposerDraftMediaKind.Video to videoReference,
    ).map { (kind, reference) -> reconcile(kind, reference) }.all { it }

    private suspend fun reconcile(kind: PostComposerDraftMediaKind, reference: String?): Boolean {
        val retainedKey = reference?.let { candidate ->
            candidate.cacheKeyOrNull()?.takeIf { it.startsWith(kindPrefix(kind)) }
                ?: runtimeKeys[candidate]?.takeIf { it.startsWith(kindPrefix(kind)) }
        }
        if (retainedKey == null) {
            runtimeKeys.entries.removeAll { (runtime, key) ->
                (key.startsWith(kindPrefix(kind))).also { remove ->
                    if (remove) releaseCachedReference(PlatformFile(runtime))
                }
            }
            return files.removeByPrefix(kindPrefix(kind)) is PlatformResult.Success
        }
        val staleKeys = runtimeKeys.values.filter { it.startsWith(kindPrefix(kind)) && it != retainedKey }.toSet()
        runtimeKeys.entries.removeAll { (runtime, key) ->
            (key in staleKeys).also { remove -> if (remove) releaseCachedReference(PlatformFile(runtime)) }
        }
        return staleKeys.map { files.remove(it) is PlatformResult.Success }.all { it }
    }

    private fun kindPrefix(kind: PostComposerDraftMediaKind): String = actorPrefix + kind.name.lowercase() + '.'

    private suspend fun removeBestEffort(key: String) {
        try {
            files.remove(key)
        } catch (_: Throwable) {
            // Actor retirement also clears the complete prefix.
        }
    }

    private fun key(kind: PostComposerDraftMediaKind, reference: String): String =
        kindPrefix(kind) + reference.hashCode().toUInt().toString(16) + '.' + Random.nextLong().toULong().toString(16)
}

private const val DraftCacheReferencePrefix = "quata-draft-cache:"

private fun String.toDraftCacheReference(): String = DraftCacheReferencePrefix + this

private fun String.cacheKeyOrNull(): String? = takeIf { startsWith(DraftCacheReferencePrefix) }
    ?.removePrefix(DraftCacheReferencePrefix)

private fun String.isRemoteMediaReference(): Boolean =
    startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)
