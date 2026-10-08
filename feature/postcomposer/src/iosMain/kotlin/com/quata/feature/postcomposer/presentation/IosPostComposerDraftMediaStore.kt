package com.quata.feature.postcomposer.presentation

import com.quata.core.platform.IosFileCacheService
import com.quata.core.platform.PrefixClearableFileCacheService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult

/** Copies composer media into app-private storage before its reference enters the draft envelope. */
internal class IosPostComposerDraftMediaStore(
    actorProfileId: String,
    private val files: PrefixClearableFileCacheService = IosFileCacheService(),
) {
    private val actorPrefix = postComposerDraftMediaActorPrefix(actorProfileId)
    private val runtimeKeys = mutableMapOf<String, String>()

    suspend fun persist(reference: String, kind: PostComposerDraftMediaKind): String? {
        if (reference.isRemotePostComposerMediaReference()) return reference
        val existingKey = reference.postComposerDraftCacheKeyOrNull()?.takeIf { it.startsWith(kindPrefix(kind)) }
            ?: runtimeKeys[reference]?.takeIf { it.startsWith(kindPrefix(kind)) }
        if (existingKey != null) {
            return existingKey.toPostComposerDraftCacheReference()
        }
        val expectedKey = key(kind, reference)
        return when (val stored = files.store(expectedKey, iosComposerRestoredMediaFile(reference, kind))) {
            is PlatformResult.Success -> {
                runtimeKeys[reference] = expectedKey
                runtimeKeys[stored.value.reference] = expectedKey
                expectedKey.toPostComposerDraftCacheReference()
            }
            is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported -> null
        }
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

    private fun key(kind: PostComposerDraftMediaKind, reference: String): String =
        kindPrefix(kind) + reference.hashCode().toUInt().toString(16)
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
