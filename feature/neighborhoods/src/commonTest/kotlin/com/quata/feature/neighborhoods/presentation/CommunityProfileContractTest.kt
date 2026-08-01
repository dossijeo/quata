package com.quata.feature.neighborhoods.presentation

import com.quata.feature.neighborhoods.domain.ProfileAttachmentAvailability
import com.quata.feature.neighborhoods.domain.profileAttachmentAvailability
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

    @Test fun `attachment availability distinguishes authentication from load failure`() {
        assertEquals(
            ProfileAttachmentAvailability.AuthenticationRequired,
            profileAttachmentAvailability(hasAuthenticatedSession = false, loadSucceeded = false),
        )
        assertEquals(
            ProfileAttachmentAvailability.Available,
            profileAttachmentAvailability(hasAuthenticatedSession = true, loadSucceeded = true),
        )
        assertEquals(
            ProfileAttachmentAvailability.Unavailable,
            profileAttachmentAvailability(hasAuthenticatedSession = true, loadSucceeded = false),
        )
    }
}
