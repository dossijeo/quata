package com.quata.feature.neighborhoods.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class NeighborhoodCommunityIdentityTest {
    @Test
    fun aliasesForTheSameWallProduceOneDirectoryRow() {
        val member = NeighborhoodUser("profile-1", "Ada", "", "La Chana")
        val communities = listOf(
            community(name = "La Chana", wallId = "wall-1", users = listOf(member)),
            community(name = "La Chana", wallId = "wall-1", users = emptyList()),
            community(name = "Another", wallId = "wall-2", users = emptyList()),
        )

        val result = communities.distinctByCommunityIdentity()

        assertEquals(listOf("wall-1", "wall-2"), result.map { it.wallId })
        assertEquals(listOf(member), result.first().users)
    }

    private fun community(
        name: String,
        wallId: String?,
        users: List<NeighborhoodUser>,
    ) = NeighborhoodCommunity(
        name = name,
        users = users,
        conversationId = null,
        lastMessagePreview = null,
        lastMessageAtMillis = null,
        messageCount = 0,
        wallId = wallId,
    )
}
