package com.quata.feature.postcomposer.presentation

import com.quata.core.platform.AtomicPreferenceStore
import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostComposerDraftStoreTest {
    @Test
    fun actorSwitchResetsExistingContentButPreservesTheLoginContinuation() {
        assertTrue(shouldResetDraftForActorTransition(true, "actor-a", "actor-b", false))
        assertFalse(shouldResetDraftForActorTransition(true, null, "actor-a", true))
        assertTrue(shouldResetDraftForActorTransition(true, null, "actor-a", false))
    }

    @Test
    fun restoresOnlyTheMatchingActorAndClearsThePreviousActorOnChange() = runTest {
        val preferences = MemoryPreferenceStore()
        val store = PostComposerDraftStore(preferences)
        val draft = draft(text = "actor-a")

        val lease = requireNotNull(store.activateActor("actor-a"))
        store.save(lease, draft)
        assertEquals(draft, store.restore("actor-a") { true }?.snapshot)
        assertNull(store.restore("actor-b") { true })
        assertNull(store.restore("actor-a") { true })
    }

    @Test
    fun malformedOrUnknownPayloadFailsClosedAndIsRemoved() = runTest {
        val preferences = MemoryPreferenceStore()
        val store = PostComposerDraftStore(preferences)
        val lease = requireNotNull(store.activateActor("actor-a"))
        store.save(lease, draft())
        preferences.values[StateKey] = "QPCS1broken"

        assertNull(store.restore("actor-a") { true })
        assertFalse(preferences.values.getValue(StateKey).contains("broken"))
    }

    @Test
    fun unavailableMediaIsDroppedWhileSafeFieldsRemain() = runTest {
        val store = PostComposerDraftStore(MemoryPreferenceStore())
        val lease = requireNotNull(store.activateActor("actor-a"))
        store.save(lease, draft(imageUri = "file:///gone.jpg", videoUri = "file:///kept.mp4"))

        val restored = store.restore("actor-a") { it.endsWith("kept.mp4") }?.snapshot

        assertEquals("draft", restored?.text)
        assertNull(restored?.imageUri)
        assertEquals("file:///kept.mp4", restored?.videoUri)
        assertEquals("Madrid", restored?.locationLabel)
    }

    @Test
    fun rawMediaBytesAndInvalidActorsAreNeverPersisted() = runTest {
        val preferences = MemoryPreferenceStore()
        val store = PostComposerDraftStore(preferences)

        assertNull(store.activateActor("../../other"))
        val lease = requireNotNull(store.activateActor("actor-a"))
        store.save(lease, draft(imageUri = "data:image/png;base64,secret"))

        assertEquals(1, preferences.values.size)
        val restored = store.restore("actor-a") { true }?.snapshot
        assertNull(restored?.imageUri)
    }

    @Test
    fun clearRemovesTheDraftAndActiveActorMarker() = runTest {
        val preferences = MemoryPreferenceStore()
        val store = PostComposerDraftStore(preferences)
        val lease = requireNotNull(store.activateActor("actor-a"))
        store.save(lease, draft())

        val replacementLease = requireNotNull(store.clear("actor-a"))

        assertFalse(preferences.values.getValue(StateKey).contains("draft"))
        assertFalse(store.save(lease, draft(text = "stale-after-clear")))
        assertTrue(store.save(replacementLease, draft(text = "new-after-clear")))
        assertEquals("new-after-clear", store.restore("actor-a") { true }?.snapshot?.text)
    }

    @Test
    fun actorChangeSerializesWithInFlightSaveAndRejectsStaleWrites() = runTest {
        val preferences = BlockingPreferenceStore()
        val store = PostComposerDraftStore(preferences)
        val actorALease = requireNotNull(store.activateActor("actor-a"))
        preferences.blockDraftWrite = true

        val save = launch { assertTrue(store.save(actorALease, draft(text = "in-flight"))) }
        preferences.draftWriteStarted.await()
        val actorChange = launch { store.activateActor("actor-b") }
        preferences.allowDraftWrite.complete(Unit)
        save.join()
        actorChange.join()

        assertNull(store.restore("actor-a") { true })
        assertFalse(store.save(actorALease, draft(text = "stale")))
        assertNull(store.restore("actor-a") { true })
    }

    @Test
    fun delayedRestoreLeaseIsInvalidAfterActorChange() = runTest {
        val preferences = MemoryPreferenceStore()
        val store = PostComposerDraftStore(preferences)
        val lease = requireNotNull(store.activateActor("actor-a"))
        store.save(lease, draft(imageUri = "file:///slow.jpg"))
        val validatorStarted = CompletableDeferred<Unit>()
        val allowValidator = CompletableDeferred<Unit>()

        val restoration = async {
            store.restore("actor-a") {
                validatorStarted.complete(Unit)
                allowValidator.await()
                true
            }
        }
        validatorStarted.await()
        val actorChange = launch { store.activateActor("actor-b") }
        actorChange.join()
        allowValidator.complete(Unit)
        val restored = restoration.await()

        assertNull(restored)
        assertNull(store.restore("actor-a") { true })
    }

    @Test
    fun delayedMediaRepairCannotOverwriteANewerDraftFromAnotherStore() = runTest {
        val preferences = AtomicMemoryPreferenceStore()
        val restoringTab = PostComposerDraftStore(preferences)
        val writingTab = PostComposerDraftStore(preferences)
        val lease = requireNotNull(restoringTab.activateActor("actor-a"))
        assertTrue(restoringTab.save(lease, draft(text = "old", imageUri = "file:///gone.jpg")))
        val validatorStarted = CompletableDeferred<Unit>()
        val allowValidator = CompletableDeferred<Unit>()

        val restoration = async {
            restoringTab.restore("actor-a") {
                validatorStarted.complete(Unit)
                allowValidator.await()
                false
            }
        }
        validatorStarted.await()
        val writerLease = requireNotNull(writingTab.activateActor("actor-a"))
        assertTrue(writingTab.save(writerLease, draft(text = "new")))
        allowValidator.complete(Unit)

        assertNull(restoration.await())
        assertEquals("new", writingTab.restore("actor-a") { true }?.snapshot?.text)
    }

    @Test
    fun rejectedRestoreDoesNotTriggerAnInitialEmptyAutosaveOverTheNewerDraft() = runTest {
        val preferences = AtomicMemoryPreferenceStore()
        val restoringHost = PostComposerDraftStore(preferences)
        val writingTab = PostComposerDraftStore(preferences)
        val lease = requireNotNull(restoringHost.activateActor("actor-a"))
        assertTrue(restoringHost.save(lease, draft(text = "old", imageUri = "file:///gone.jpg")))
        val validatorStarted = CompletableDeferred<Unit>()
        val allowValidator = CompletableDeferred<Unit>()
        val restoration = async {
            restoringHost.restore("actor-a") {
                validatorStarted.complete(Unit)
                allowValidator.await()
                false
            }
        }

        validatorStarted.await()
        val writerLease = requireNotNull(writingTab.activateActor("actor-a"))
        assertTrue(writingTab.save(writerLease, draft(text = "new")))
        allowValidator.complete(Unit)

        assertNull(restoration.await())
        val emptyHostSnapshot = draft()
        assertFalse(shouldPersistPostComposerDraft(true, emptyHostSnapshot, emptyHostSnapshot))
        assertEquals("new", writingTab.restore("actor-a") { true }?.snapshot?.text)
        assertTrue(shouldPersistPostComposerDraft(true, draft(text = "explicit input"), emptyHostSnapshot))
    }

    @Test
    fun independentStoresShareTheActorFenceAndRejectAStaleTabWrite() = runTest {
        val preferences = AtomicMemoryPreferenceStore()
        val actorATab = PostComposerDraftStore(preferences)
        val actorBTab = PostComposerDraftStore(preferences)
        val actorALease = requireNotNull(actorATab.activateActor("actor-a"))
        assertTrue(actorATab.save(actorALease, draft(text = "actor-a-draft")))

        requireNotNull(actorBTab.activateActor("actor-b"))

        assertFalse(actorATab.save(actorALease, draft(text = "stale-tab-write")))
        assertNull(actorBTab.restore("actor-b") { true })
        assertFalse(preferences.values.getValue(StateKey).contains("actor-a-draft"))
        assertFalse(preferences.values.getValue(StateKey).contains("stale-tab-write"))
    }

    private fun draft(
        text: String = "draft",
        imageUri: String? = null,
        videoUri: String? = null,
    ) = PostComposerDraftSnapshot(
        step = CreatePostStep.Image,
        text = text,
        textPatternId = "midnight-blue",
        imageUri = imageUri,
        videoUri = videoUri,
        locationLabel = "Madrid",
        latitude = 40.4168,
        longitude = -3.7038,
        locationOrigin = CreatePostLocationOrigin.Manual,
        selectedDestinationWallId = "wall-1",
    )
}

private class MemoryPreferenceStore : PreferenceStore {
    val values = mutableMapOf<String, String>()
    override suspend fun getString(key: String): String? = values[key]
    override suspend fun putString(key: String, value: String) { values[key] = value }
    override suspend fun remove(key: String) { values.remove(key) }
}

private class BlockingPreferenceStore : PreferenceStore {
    val values = mutableMapOf<String, String>()
    var blockDraftWrite = false
    val draftWriteStarted = CompletableDeferred<Unit>()
    val allowDraftWrite = CompletableDeferred<Unit>()

    override suspend fun getString(key: String): String? = values[key]

    override suspend fun putString(key: String, value: String) {
        if (blockDraftWrite && key == StateKey) {
            blockDraftWrite = false
            draftWriteStarted.complete(Unit)
            allowDraftWrite.await()
        }
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}

private class AtomicMemoryPreferenceStore : AtomicPreferenceStore {
    val values = mutableMapOf<String, String>()
    private val lock = Mutex()

    override suspend fun getString(key: String): String? = lock.withLock { values[key] }
    override suspend fun putString(key: String, value: String) = lock.withLock { values[key] = value }
    override suspend fun remove(key: String) = lock.withLock { values.remove(key); Unit }

    override suspend fun updateStringAtomically(
        key: String,
        transform: (String?) -> String?,
    ): Boolean = lock.withLock {
        val next = transform(values[key])
        if (next == null) values.remove(key) else values[key] = next
        true
    }
}

private const val StateKey = "post-composer.draft.state.v1"
