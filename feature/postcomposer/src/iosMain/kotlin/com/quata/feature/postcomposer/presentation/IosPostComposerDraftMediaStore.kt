package com.quata.feature.postcomposer.presentation

import com.quata.core.platform.IosFileCacheService
import com.quata.core.platform.PrefixClearableFileCacheService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Copies composer media into app-private storage before its reference enters the draft envelope. */
internal class IosPostComposerDraftMediaStore(
    actorProfileId: String,
    private val files: PrefixClearableFileCacheService = IosFileCacheService(),
) {
    private val actorPrefix = postComposerDraftMediaActorPrefix(actorProfileId)
    private val runtimeKeys = mutableMapOf<String, String>()

    suspend fun persist(reference: String, kind: PostComposerDraftMediaKind): PostComposerDraftMediaPersistence? {
        if (reference.isRemotePostComposerMediaReference()) return PostComposerDraftMediaPersistence(reference, created = false)
        // runtimeKeys contains only file URLs produced by restore from an already committed
        // envelope. A picker URL always gets a fresh key and remains private to this save until
        // the opaque cache reference is committed.
        val existingKey = reference.postComposerDraftCacheKeyOrNull()?.takeIf { it.startsWith(kindPrefix(kind)) }
            ?: runtimeKeys[reference]?.takeIf { it.startsWith(kindPrefix(kind)) }
        if (existingKey != null) {
            return PostComposerDraftMediaPersistence(existingKey.toPostComposerDraftCacheReference(), created = false)
        }
        val expectedKey = key(kind, reference)
        val stored = try {
            files.store(expectedKey, iosComposerRestoredMediaFile(reference, kind))
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { removeBestEffort(expectedKey) }
            throw cancelled
        } catch (_: Throwable) {
            removeBestEffort(expectedKey)
            return null
        }
        return when (stored) {
            is PlatformResult.Success -> {
                PostComposerDraftMediaPersistence(expectedKey.toPostComposerDraftCacheReference(), created = true)
            }
            is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported -> {
                files.remove(expectedKey)
                null
            }
        }
    }

    suspend fun discard(persistence: PostComposerDraftMediaPersistence, kind: PostComposerDraftMediaKind) {
        if (!persistence.created) return
        val key = persistence.reference.postComposerDraftCacheKeyOrNull()
            ?.takeIf { it.startsWith(kindPrefix(kind)) }
            ?: return
        runtimeKeys.entries.removeAll { (_, cachedKey) -> cachedKey == key }
        files.remove(key)
    }

    suspend fun restore(reference: String, kind: PostComposerDraftMediaKind): String? {
        if (reference.isRemotePostComposerMediaReference()) return reference
        val expectedKey = reference.postComposerDraftCacheKeyOrNull()
            ?.takeIf { it.startsWith(kindPrefix(kind)) }
            ?: return null
        return when (val restored = files.get(expectedKey)) {
            is PlatformResult.Success -> restored.value.reference.also { runtimeKeys[it] = expectedKey }
            is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported -> null
        }
    }

    suspend fun clear(): Boolean {
        runtimeKeys.clear()
        return files.removeByPrefix(actorPrefix) is PlatformResult.Success
    }

    suspend fun reconcile(imageReference: String?, videoReference: String?): Boolean = listOf(
        PostComposerDraftMediaKind.Image to imageReference,
        PostComposerDraftMediaKind.Video to videoReference,
    ).map { (kind, reference) -> reconcile(kind, reference) }.all { it }

    private suspend fun reconcile(kind: PostComposerDraftMediaKind, reference: String?): Boolean {
        val retainedKey = reference?.let { candidate ->
            candidate.postComposerDraftCacheKeyOrNull()?.takeIf { it.startsWith(kindPrefix(kind)) }
                ?: runtimeKeys[candidate]?.takeIf { it.startsWith(kindPrefix(kind)) }
        }
        if (retainedKey == null) {
            runtimeKeys.entries.removeAll { (_, key) -> key.startsWith(kindPrefix(kind)) }
            return files.removeByPrefix(kindPrefix(kind)) is PlatformResult.Success
        }
        val staleKeys = runtimeKeys.values.filter { it.startsWith(kindPrefix(kind)) && it != retainedKey }.toSet()
        runtimeKeys.entries.removeAll { (_, key) -> key in staleKeys }
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

internal fun retireAllIosPostComposerDraftMedia(): Boolean =
    IosFileCacheService().removeByPrefixNow(PostComposerDraftMediaCacheKeyPrefix) is PlatformResult.Success

private const val PostComposerDraftMediaCacheKeyPrefix = "post-composer-draft."
private const val PostComposerDraftCacheReferencePrefix = "quata-draft-cache:"

private fun postComposerDraftMediaActorPrefix(actorProfileId: String): String = buildString {
    append(PostComposerDraftMediaCacheKeyPrefix)
    append(actorProfileId.take(72))
    append('.')
    append(actorProfileId.hashCode().toUInt().toString(16))
    append('.')
}

private fun String.toPostComposerDraftCacheReference(): String = PostComposerDraftCacheReferencePrefix + this

private fun String.postComposerDraftCacheKeyOrNull(): String? =
    takeIf { startsWith(PostComposerDraftCacheReferencePrefix) }
        ?.removePrefix(PostComposerDraftCacheReferencePrefix)

private fun String.isRemotePostComposerMediaReference(): Boolean =
    startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)
