package com.quata.web

import com.quata.core.platform.PrefixClearableFileCacheService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.feature.postcomposer.presentation.PostComposerDraftMediaKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebPostComposerDraftMediaStoreTest {
    @Test
    fun blobIsPersistedByActorAndRestoredAsANewRuntimeReference() = runTest {
        val files = MemoryFileCache()
        val first = WebPostComposerDraftMediaStore(files, "actor-a")
        val persistent = first.persist("blob:first-window", PostComposerDraftMediaKind.Image)

        assertTrue(persistent?.startsWith("quata-draft-cache:") == true)
        val second = WebPostComposerDraftMediaStore(files, "actor-a")
        val restored = second.restore(requireNotNull(persistent), PostComposerDraftMediaKind.Image)
        assertTrue(restored?.startsWith("blob:restored-post-composer-draft.actor-a.") == true)
        assertTrue(restored.contains(".image."))
        assertEquals(persistent, second.persist(requireNotNull(restored), PostComposerDraftMediaKind.Image))
    }

    @Test
    fun actorAndMediaKindCannotOpenAnotherCachedDraft() = runTest {
        val files = MemoryFileCache()
        val actorA = WebPostComposerDraftMediaStore(files, "actor-a")
        val persistent = requireNotNull(actorA.persist("blob:image", PostComposerDraftMediaKind.Image))

        assertNull(WebPostComposerDraftMediaStore(files, "actor-b").restore(persistent, PostComposerDraftMediaKind.Image))
        assertNull(actorA.restore(persistent, PostComposerDraftMediaKind.Video))
    }

    @Test
    fun clearRemovesBothKindsAndRejectsLaterRestoration() = runTest {
        val files = MemoryFileCache()
        val store = WebPostComposerDraftMediaStore(files, "actor-a")
        val image = requireNotNull(store.persist("blob:image", PostComposerDraftMediaKind.Image))
        val video = requireNotNull(store.persist("blob:video", PostComposerDraftMediaKind.Video))

        assertTrue(store.clear())
        assertNull(store.restore(image, PostComposerDraftMediaKind.Image))
        assertNull(store.restore(video, PostComposerDraftMediaKind.Video))
        assertTrue(files.values.isEmpty())
    }

    @Test
    fun reconcileRemovesBinaryWhoseMediaKindLeftTheDraft() = runTest {
        val files = MemoryFileCache()
        val store = WebPostComposerDraftMediaStore(files, "actor-a")
        val image = requireNotNull(store.persist("blob:image", PostComposerDraftMediaKind.Image))

        assertTrue(store.reconcile(imageReference = null, videoReference = "https://cdn.example/video.mp4"))
        assertNull(store.restore(image, PostComposerDraftMediaKind.Image))
        assertTrue(files.values.isEmpty())
    }

    @Test
    fun failedReplacementKeepsThePreviouslyCommittedBinaryReadable() = runTest {
        val files = MemoryFileCache()
        val store = WebPostComposerDraftMediaStore(files, "actor-a")
        val previous = requireNotNull(store.persist("blob:previous", PostComposerDraftMediaKind.Image))
        files.failNextStore = true

        assertNull(store.persist("blob:replacement", PostComposerDraftMediaKind.Image))
        assertTrue(store.restore(previous, PostComposerDraftMediaKind.Image)?.startsWith("blob:restored-") == true)
    }
}

private class MemoryFileCache : PrefixClearableFileCacheService {
    val values = mutableMapOf<String, PlatformFile>()
    var failNextStore: Boolean = false

    override suspend fun store(cacheKey: String, file: PlatformFile): PlatformResult<PlatformFile> {
        if (failNextStore) {
            failNextStore = false
            return PlatformResult.Failure("forced_store_failure")
        }
        values[cacheKey] = file
        return PlatformResult.Success(PlatformFile("blob:stored-$cacheKey"))
    }

    override suspend fun get(cacheKey: String): PlatformResult<PlatformFile> =
        if (values.containsKey(cacheKey)) PlatformResult.Success(PlatformFile("blob:restored-$cacheKey"))
        else PlatformResult.Failure("cache_miss")

    override suspend fun remove(cacheKey: String): PlatformResult<Unit> {
        values.remove(cacheKey)
        return PlatformResult.Success(Unit)
    }

    override suspend fun removeByPrefix(prefix: String): PlatformResult<Unit> {
        values.keys.filter { it.startsWith(prefix) }.forEach(values::remove)
        return PlatformResult.Success(Unit)
    }
}
