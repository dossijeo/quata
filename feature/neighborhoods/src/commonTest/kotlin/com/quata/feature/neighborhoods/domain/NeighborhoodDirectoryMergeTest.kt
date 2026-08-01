package com.quata.feature.neighborhoods.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class NeighborhoodDirectoryMergeTest {
    @Test fun `wall-only communities retain backend identity and stats`() {
        val result = mergeNeighborhoodDirectory(emptyList(), listOf(NeighborhoodWallSnapshot("wall-id", "Centro", "centro", 12, 99L)))
        assertEquals("wall:wall-id", result.single().conversationId)
        assertEquals(12, result.single().messageCount)
        assertEquals(99L, result.single().lastMessageAtMillis)
    }

    @Test fun `profiles merge into normalized wall without losing members`() {
        val user = NeighborhoodUser("u1", "Ana", "", " Centro ")
        val result = mergeNeighborhoodDirectory(listOf(user), listOf(NeighborhoodWallSnapshot("w1", "Centro", "centro", 3, null)))
        assertEquals(listOf(user), result.single().users)
    }
}
