package com.quata.feature.feed.presentation

import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedVideoPositionStoreTest {
    @Test
    fun roundTripsActorScopedPositionsAndKeepsPublicStateSeparate() = runTest {
        val preferences = MemoryPreferenceStore()
        val store = FeedVideoPositionStore(preferences)

        store.persist("actor-a", linkedMapOf("post-a\u001fvideo-a" to 12_345L))
        store.persist(null, linkedMapOf("post-public\u001fvideo-public" to 2_000L))

        assertEquals(mapOf("post-a\u001fvideo-a" to 12_345L), store.restore("actor-a"))
        assertEquals(mapOf("post-public\u001fvideo-public" to 2_000L), store.restore(null))
        assertTrue(store.restore("actor-b").isEmpty())
    }

    @Test
    fun rejectsMalformedSnapshotsAndBoundsRestoredHistory() = runTest {
        val preferences = MemoryPreferenceStore()
        val store = FeedVideoPositionStore(preferences)
        preferences.putString("${FeedVideoPositionStoragePrefix}broken", "not-json")
        assertTrue(store.restore("broken").isEmpty())

        val positions = linkedMapOf<String, Long>()
        repeat(FeedVideoPositionEntryLimit + 5) { index -> positions["media-$index"] = index.toLong() }
        store.persist("actor", positions)

        val restored = store.restore("actor")
        assertEquals(FeedVideoPositionEntryLimit, restored.size)
        assertTrue("media-0" !in restored)
        assertEquals((FeedVideoPositionEntryLimit + 4).toLong(), restored["media-${FeedVideoPositionEntryLimit + 4}"])
    }

    @Test
    fun mediaIdentityUsesPostAndNormalizedVideoUrl() {
        assertEquals("post-1\u001fhttps://cdn/video.mp4", feedVideoPositionMediaId("post-1", " https://cdn/video.mp4 "))
    }

    @Test
    fun unavailablePlatformStorageDoesNotBreakFeedPlayback() = runTest {
        val store = FeedVideoPositionStore(object : PreferenceStore {
            override suspend fun getString(key: String): String? = error("storage-unavailable")
            override suspend fun putString(key: String, value: String) = error("storage-unavailable")
            override suspend fun remove(key: String) = error("storage-unavailable")
        })

        assertTrue(store.restore("actor").isEmpty())
        store.persist("actor", mapOf("media" to 1_000L))
    }

    private class MemoryPreferenceStore : PreferenceStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun getString(key: String): String? = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }
}
