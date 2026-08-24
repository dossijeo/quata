package com.quata.feature.neighborhoods.presentation

import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NeighborhoodsScreenHostTest {
    @Test
    fun `directory remains public while private community actions need an identity`() {
        assertFalse(canPerformNeighborhoodPrivateAction(null))
        assertFalse(canPerformNeighborhoodPrivateAction("   "))
        assertTrue(canPerformNeighborhoodPrivateAction("profile-42"))
    }

    @Test
    fun `private row progress follows the private conversation request`() {
        assertFalse(isNeighborhoodPrivateChatOpening(null))
        assertTrue(isNeighborhoodPrivateChatOpening("profile-42"))
    }

    @Test
    fun `community chat opens only when cached conversation or wall exists`() {
        assertTrue(canOpenCommunityChat(community("Bata", conversationId = "sb:1", wallId = null)))
        assertTrue(canOpenCommunityChat(community("Bata", conversationId = null, wallId = "wall-1")))
        assertFalse(canOpenCommunityChat(community("Bata", conversationId = null, wallId = null)))
    }

    @Test
    fun `community chat anchors are stable and normalized`() {
        assertEquals("neighborhood.chat.50.viviendas", neighborhoodChatButtonTestTag(" 50 Viviendas "))
        assertEquals("neighborhood.chat.status.50.viviendas", neighborhoodChatStatusTestTag(" 50 Viviendas "))
        assertEquals("neighborhood.chat.unknown", neighborhoodChatButtonTestTag("!!!"))
    }

    private fun community(
        name: String,
        conversationId: String?,
        wallId: String?,
    ): NeighborhoodCommunity = NeighborhoodCommunity(
        name = name,
        users = emptyList(),
        conversationId = conversationId,
        lastMessagePreview = null,
        lastMessageAtMillis = null,
        messageCount = 0,
        wallId = wallId,
    )
}
