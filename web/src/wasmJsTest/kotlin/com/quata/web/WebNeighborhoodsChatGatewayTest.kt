package com.quata.web

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebNeighborhoodsChatGatewayTest {
    @Test
    fun directoryKeepsActiveWallsWithoutPublicProfiles() {
        val keys = webCommunityDirectoryKeys(
            profileKeys = listOf("50 viviendas", "caidassa"),
            activeWallKeys = listOf("caidassa", "empty-wall"),
        )

        assertEquals(listOf("50 viviendas", "caidassa", "empty-wall"), keys)
    }

    @Test
    fun communityChatReusesCachedConversationWithoutMutation() = runTest {
        var opened = false

        val result = openWebNeighborhoodConversation(
            neighborhood = "  Centro Norte  ",
            communityIdForName = { error("cached conversation must not resolve its wall") },
            cachedConversationId = { name ->
                assertEquals("Centro Norte", name)
                "sb:41"
            },
            openConversation = { _, _ ->
                opened = true
                Result.success("sb:99")
            },
        )

        assertEquals("sb:41", result.getOrThrow())
        assertTrue(!opened)
    }

    @Test
    fun communityChatUsesPortableChatMutationWithCanonicalId() = runTest {
        val result = openWebNeighborhoodConversation(
            neighborhood = "  Centro   Norte  ",
            communityIdForName = { name ->
                assertEquals("Centro   Norte", name)
                "20000000-0000-4000-8000-000000000042"
            },
            cachedConversationId = { null },
            openConversation = { communityId, title ->
                assertEquals("20000000-0000-4000-8000-000000000042", communityId)
                assertEquals("Centro   Norte", title)
                Result.success("sb:42")
            },
        )

        assertEquals("sb:42", result.getOrThrow())
    }

    @Test
    fun privateChatReusesCacheThenDelegatesToPortableMutation() = runTest {
        val cached = openWebPrivateConversation("profile-7", { "sb:7" }) {
            Result.failure(AssertionError("cached conversation must not be reopened"))
        }
        val opened = openWebPrivateConversation("profile-8", { null }) { peerId ->
            assertEquals("profile-8", peerId)
            Result.success("sb:8")
        }

        assertEquals("sb:7", cached.getOrThrow())
        assertEquals("sb:8", opened.getOrThrow())
    }

    @Test
    fun invalidCommunityAndPeerIdentifiersFailBeforeMutation() = runTest {
        var mutationCalls = 0
        val blankCommunity = openWebNeighborhoodConversation("   ", { null }, { null }) { _, _ ->
            mutationCalls += 1
            Result.success("sb:1")
        }
        val missingWall = openWebNeighborhoodConversation("Centro", { null }, { null }) { _, _ ->
            mutationCalls += 1
            Result.success("sb:3")
        }
        val invalidWall = openWebNeighborhoodConversation("Centro", { "centro" }, { null }) { _, _ ->
            mutationCalls += 1
            Result.success("sb:4")
        }
        val invalidPeer = openWebPrivateConversation("peer/id", { null }) {
            mutationCalls += 1
            Result.success("sb:2")
        }

        assertTrue(blankCommunity.isFailure)
        assertTrue(missingWall.isFailure)
        assertTrue(invalidWall.isFailure)
        assertTrue(invalidPeer.isFailure)
        assertEquals(0, mutationCalls)
    }
}
