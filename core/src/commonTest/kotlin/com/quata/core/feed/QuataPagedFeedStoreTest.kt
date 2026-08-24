package com.quata.core.feed

import kotlin.test.Test
import kotlin.test.assertEquals

class QuataPagedFeedStoreTest {
    @Test
    fun replaceTransformUpdatesRealtimeAndOlderCopies() {
        val store = QuataPagedFeedStore(
            pageSize = 2,
            idOf = FeedItem::id,
            cursorOf = FeedItem::cursor,
        )

        store.setRealtime(listOf(FeedItem("a", "3", "pending"), FeedItem("b", "2", "old")))
        store.appendOlder(listOf(FeedItem("c", "1", "pending")))

        store.replace("a") { it.copy(value = "realtime-rolled-back") }
        val updated = store.replace("c") { it.copy(value = "older-rolled-back") }

        assertEquals(listOf("a:realtime-rolled-back", "b:old", "c:older-rolled-back"), updated.map { "${it.id}:${it.value}" })
        assertEquals(listOf("a:realtime-rolled-back", "b:old", "c:older-rolled-back"), store.items.map { "${it.id}:${it.value}" })
    }

    private data class FeedItem(
        val id: String,
        val cursor: String,
        val value: String,
    )
}
