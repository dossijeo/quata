package com.quata.feature.official.presentation

import com.quata.core.platform.DurableMediaPositionStore
import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfficialVideoPositionStoreTest {
    @Test
    fun officialCheckpointsAreActorScopedAndDoNotReuseFeedStorage() = runTest {
        val preferences = MemoryPreferenceStore()
        val official = DurableMediaPositionStore(preferences, OfficialVideoPositionStoragePrefix)
        val feed = DurableMediaPositionStore(preferences, "quata.feed.video_positions.v1.")
        val mediaId = officialVideoPositionMediaId("official-1", " https://cdn/video.mp4 ")

        official.persistPosition("actor-a", mediaId, 12_345L)
        official.persistPosition(null, mediaId, 2_000L)
        feed.persistPosition("actor-a", mediaId, 99_999L)

        assertEquals("official-1\u001fhttps://cdn/video.mp4", mediaId)
        assertEquals(mapOf(mediaId to 12_345L), official.restore("actor-a"))
        assertEquals(mapOf(mediaId to 2_000L), official.restore(null))
        assertTrue(official.restore("actor-b").isEmpty())
        assertEquals(mapOf(mediaId to 99_999L), feed.restore("actor-a"))
    }

    private class MemoryPreferenceStore : PreferenceStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun getString(key: String): String? = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }
}
