package com.quata.feature.neighborhoods.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommunityProfileContractTest {
    @Test fun `opening a people list closes posts and returns to details`() {
        val people = CommunityProfileNavigationState().reduce(
            CommunityProfileNavigationEvent.ShowPeople(CommunityProfilePeopleList.Followers)
        )
        assertEquals(CommunityProfilePeopleList.Followers, people.peopleList)
        assertFalse(people.showingPosts)
        assertEquals(CommunityProfileNavigationState(), people.reduce(CommunityProfileNavigationEvent.ShowDetails))
    }

    @Test fun `posts and mutations are gated consistently`() {
        assertTrue(CommunityProfileNavigationState().reduce(CommunityProfileNavigationEvent.ShowPosts).showingPosts)
        assertFalse(communityProfilePrivateActionAllowed(null))
        assertFalse(communityProfilePrivateActionAllowed("  "))
        assertTrue(communityProfilePrivateActionAllowed("profile"))
    }
}
