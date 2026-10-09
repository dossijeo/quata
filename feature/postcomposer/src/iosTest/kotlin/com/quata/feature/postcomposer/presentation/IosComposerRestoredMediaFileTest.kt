package com.quata.feature.postcomposer.presentation

import com.quata.core.platform.PrefixClearableFileCacheService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosComposerRestoredMediaFileTest {
    @Test
    fun restoredImageKeepsReferenceAndReconstructsMetadata() {
        val file = iosComposerRestoredMediaFile(
            "file:///tmp/quata-draft/photo.png",
            PostComposerDraftMediaKind.Image,
        )

        assertEquals("file:///tmp/quata-draft/photo.png", file.reference)
        assertEquals("photo.png", file.displayName)
        assertEquals("image/png", file.mimeType)
    }

    @Test
    fun restoredVideoKeepsReferenceAndReconstructsQuickTimeMetadata() {
        val file = iosComposerRestoredMediaFile(
            "file:///tmp/quata-draft/clip.mov",
            PostComposerDraftMediaKind.Video,
        )

        assertEquals("clip.mov", file.displayName)
        assertEquals("video/quicktime", file.mimeType)
    }

    @Test
    fun localFileIsCopiedToActorCacheAndResolvedAfterRelaunch() = runTest {
        val files = IosMemoryFileCache()
        val first = IosPostComposerDraftMediaStore("actor-a", files)
        val persistent = requireNotNull(first.persist("file:///tmp/photo.png", PostComposerDraftMediaKind.Image)).reference

        assertTrue(persistent.startsWith("quata-draft-cache:"))
        assertTrue(first.reconcile(imageReference = persistent, videoReference = null))
        val second = IosPostComposerDraftMediaStore("actor-a", files)
        val restored = requireNotNull(second.restore(persistent, PostComposerDraftMediaKind.Image))
        assertTrue(restored.startsWith("file:///private/cache/post-composer-draft.actor-a."))
        assertEquals(persistent, second.persist(restored, PostComposerDraftMediaKind.Image)?.reference)
        assertEquals(1, files.storeCalls)
    }

    @Test
    fun actorAndKindIsolationAndClearAreEnforced() = runTest {
        val files = IosMemoryFileCache()
        val actorA = IosPostComposerDraftMediaStore("actor-a", files)
        val persistent = requireNotNull(actorA.persist("file:///tmp/clip.mov", PostComposerDraftMediaKind.Video)).reference

        assertNull(IosPostComposerDraftMediaStore("actor-b", files).restore(persistent, PostComposerDraftMediaKind.Video))
        assertNull(actorA.restore(persistent, PostComposerDraftMediaKind.Image))
        assertTrue(actorA.clear())
        assertNull(actorA.restore(persistent, PostComposerDraftMediaKind.Video))
    }

    @Test
    fun reconcileRemovesBinaryWhoseMediaKindLeftTheDraft() = runTest {
        val files = IosMemoryFileCache()
        val store = IosPostComposerDraftMediaStore("actor-a", files)
        val persistent = requireNotNull(
            store.persist("file:///tmp/photo.png", PostComposerDraftMediaKind.Image),
        ).reference

        assertTrue(store.reconcile(imageReference = null, videoReference = null))
        assertNull(store.restore(persistent, PostComposerDraftMediaKind.Image))
    }

    @Test
    fun failedReplacementKeepsThePreviouslyCommittedBinaryReadable() = runTest {
        val files = IosMemoryFileCache()
        val store = IosPostComposerDraftMediaStore("actor-a", files)
        val previous = requireNotNull(
            store.persist("file:///tmp/previous.png", PostComposerDraftMediaKind.Image),
        ).reference
        files.failNextStore = true

        assertNull(store.persist("file:///tmp/replacement.png", PostComposerDraftMediaKind.Image))
        assertTrue(store.restore(previous, PostComposerDraftMediaKind.Image)?.contains("post-composer-draft.actor-a.") == true)
    }

    @Test
    fun repeatedTemporaryPathCreatesAnImmutableReplacementAndCompensationKeepsThePreviousEntry() = runTest {
        val files = IosMemoryFileCache()
        val store = IosPostComposerDraftMediaStore("actor-a", files)
        val previous = requireNotNull(store.persist("file:///tmp/selection.png", PostComposerDraftMediaKind.Image))
        val replacement = requireNotNull(store.persist("file:///tmp/selection.png", PostComposerDraftMediaKind.Image))

        assertTrue(previous.reference != replacement.reference)
        store.discard(replacement, PostComposerDraftMediaKind.Image)

        assertTrue(store.restore(previous.reference, PostComposerDraftMediaKind.Image)?.contains("post-composer-draft.actor-a.") == true)
        assertNull(store.restore(replacement.reference, PostComposerDraftMediaKind.Image))
    }
}

private class IosMemoryFileCache : PrefixClearableFileCacheService {
    private val values = mutableMapOf<String, PlatformFile>()
    var storeCalls: Int = 0
    var failNextStore: Boolean = false

    override suspend fun store(cacheKey: String, file: PlatformFile): PlatformResult<PlatformFile> {
        storeCalls++
        if (failNextStore) {
            failNextStore = false
            return PlatformResult.Failure("forced_store_failure")
        }
        val cached = PlatformFile("file:///private/cache/$cacheKey", file.displayName, file.mimeType, file.sizeBytes)
        values[cacheKey] = cached
        return PlatformResult.Success(cached)
    }

    override suspend fun get(cacheKey: String): PlatformResult<PlatformFile> =
        values[cacheKey]?.let { PlatformResult.Success(it) } ?: PlatformResult.Failure("cache_miss")

    override suspend fun remove(cacheKey: String): PlatformResult<Unit> {
        values.remove(cacheKey)
        return PlatformResult.Success(Unit)
    }

    override suspend fun removeByPrefix(prefix: String): PlatformResult<Unit> {
        values.keys.filter { it.startsWith(prefix) }.forEach(values::remove)
        return PlatformResult.Success(Unit)
    }
}
