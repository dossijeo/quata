package com.quata.feature.postcomposer.presentation

import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PostComposerDraftStoreTest {
    @Test
    fun restoresOnlyTheMatchingActorAndClearsThePreviousActorOnChange() = runTest {
        val preferences = MemoryPreferenceStore()
        val store = PostComposerDraftStore(preferences)
        val draft = draft(text = "actor-a")

        store.save("actor-a", draft)
        assertEquals(draft, store.restore("actor-a") { true })
        assertNull(store.restore("actor-b") { true })
        assertNull(store.restore("actor-a") { true })
    }

    @Test
    fun malformedOrUnknownPayloadFailsClosedAndIsRemoved() = runTest {
        val preferences = MemoryPreferenceStore()
        val store = PostComposerDraftStore(preferences)
        store.save("actor-a", draft())
        preferences.values["post-composer.draft.v1.actor-a"] = "QPCD99:broken"

        assertNull(store.restore("actor-a") { true })
        assertNull(preferences.values["post-composer.draft.v1.actor-a"])
    }

    @Test
    fun unavailableMediaIsDroppedWhileSafeFieldsRemain() = runTest {
        val store = PostComposerDraftStore(MemoryPreferenceStore())
        store.save("actor-a", draft(imageUri = "file:///gone.jpg", videoUri = "file:///kept.mp4"))

        val restored = store.restore("actor-a") { it.endsWith("kept.mp4") }

        assertEquals("draft", restored?.text)
        assertNull(restored?.imageUri)
        assertEquals("file:///kept.mp4", restored?.videoUri)
        assertEquals("Madrid", restored?.locationLabel)
    }

    @Test
    fun rawMediaBytesAndInvalidActorsAreNeverPersisted() = runTest {
        val preferences = MemoryPreferenceStore()
        val store = PostComposerDraftStore(preferences)

        store.save("../../other", draft())
        store.save("actor-a", draft(imageUri = "data:image/png;base64,secret"))

        assertEquals(2, preferences.values.size)
        val restored = store.restore("actor-a") { true }
        assertNull(restored?.imageUri)
    }

    @Test
    fun clearRemovesTheDraftAndActiveActorMarker() = runTest {
        val preferences = MemoryPreferenceStore()
        val store = PostComposerDraftStore(preferences)
        store.save("actor-a", draft())

        store.clear("actor-a")

        assertEquals(emptyMap(), preferences.values)
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
