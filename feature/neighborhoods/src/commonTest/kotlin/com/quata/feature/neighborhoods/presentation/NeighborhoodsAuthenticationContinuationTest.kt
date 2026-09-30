package com.quata.feature.neighborhoods.presentation

import com.quata.core.navigation.AuthenticationContinuationKind
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import kotlin.test.Test
import kotlin.test.assertEquals

class NeighborhoodsAuthenticationContinuationTest {
    private val communities = listOf(
        NeighborhoodCommunity(
            name = "North",
            users = listOf(
                NeighborhoodUser(
                    id = "peer",
                    displayName = "Peer",
                    email = "peer@example.invalid",
                    neighborhood = "North",
                    isFollowing = false,
                ),
                NeighborhoodUser(
                    id = "followed",
                    displayName = "Followed",
                    email = "followed@example.invalid",
                    neighborhood = "North",
                    isFollowing = true,
                ),
            ),
            conversationId = "sb:north",
            lastMessagePreview = null,
            lastMessageAtMillis = null,
            messageCount = 0,
            wallId = "wall-north",
        ),
    )

    @Test
    fun `community and private chat continuations preserve exact destinations`() {
        val community = communitiesAuthenticationContinuation(
            kind = AuthenticationContinuationKind.CommunitiesOpenNeighborhoodChat,
            originRoute = "communities",
            targetId = "North",
        )
        val private = communitiesAuthenticationContinuation(
            kind = AuthenticationContinuationKind.CommunitiesOpenPrivateChat,
            originRoute = "communities",
            targetId = "peer",
        )

        assertEquals(
            CommunitiesAuthenticationContinuationResolution.OpenNeighborhoodChat("North"),
            resolveCommunitiesAuthenticationContinuation(community, "communities", communities, isLoading = false),
        )
        assertEquals(
            CommunitiesAuthenticationContinuationResolution.OpenPrivateChat("peer"),
            resolveCommunitiesAuthenticationContinuation(private, "communities", communities, isLoading = false),
        )
    }

    @Test
    fun `follow continuation preserves desired state for a fresh relationship lookup`() {
        val follow = communitiesAuthenticationContinuation(
            kind = AuthenticationContinuationKind.CommunitiesToggleFollow,
            originRoute = "communities",
            targetId = "peer",
            desiredState = true,
        )
        assertEquals(
            CommunitiesAuthenticationContinuationResolution.EnsureFollowState("peer", true),
            resolveCommunitiesAuthenticationContinuation(follow, "communities", communities, isLoading = false),
        )
        assertEquals(
            CommunitiesAuthenticationContinuationResolution.EnsureFollowState("followed", true),
            resolveCommunitiesAuthenticationContinuation(follow.copy(targetId = "followed"), "communities", communities, isLoading = false),
        )
    }

    @Test
    fun `continuation waits for directory data and ignores a different origin`() {
        val follow = communitiesAuthenticationContinuation(
            kind = AuthenticationContinuationKind.CommunitiesToggleFollow,
            originRoute = "communities",
            targetId = "missing",
            desiredState = true,
        )

        assertEquals(
            CommunitiesAuthenticationContinuationResolution.Wait,
            resolveCommunitiesAuthenticationContinuation(follow, "communities", emptyList(), isLoading = true),
        )
        assertEquals(
            CommunitiesAuthenticationContinuationResolution.Clear,
            resolveCommunitiesAuthenticationContinuation(follow, "communities", emptyList(), isLoading = false),
        )
        assertEquals(
            CommunitiesAuthenticationContinuationResolution.Ignore,
            resolveCommunitiesAuthenticationContinuation(follow, "feed", communities, isLoading = false),
        )
    }
}
