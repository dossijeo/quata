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

        store.persistPosition("actor-a", "post-a\u001fvideo-a", 12_345L)
        store.persistPosition(null, "post-public\u001fvideo-public", 2_000L)

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

        repeat(FeedVideoPositionEntryLimit + 5) { index ->
            store.persistPosition("actor", "media-$index", index.toLong())
        }

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
        store.persistPosition("actor", "media", 1_000L)
    }

    @Test
    fun separateStoreInstancesPreserveEachOthersActorMediaUpdates() = runTest {
        val preferences = MemoryPreferenceStore()
        val first = FeedVideoPositionStore(preferences)
        val second = FeedVideoPositionStore(preferences)

        first.persistPosition("actor", "media-a", 1_000L)
        second.persistPosition("actor", "media-b", 2_000L)
        first.persistPosition("actor", "media-c", 3_000L)

        assertEquals(
            mapOf("media-a" to 1_000L, "media-b" to 2_000L, "media-c" to 3_000L),
            second.restore("actor"),
        )
    }

    private class MemoryPreferenceStore : PreferenceStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun getString(key: String): String? = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }
}
