package com.quata.feature.neighborhoods.presentation

/**
 * Navigation state owned by the portable community-profile root.  Hosts deliberately keep
 * platform services (share sheets, media players and document viewers) outside this contract.
 */
data class CommunityProfileNavigationState(
    val showingPosts: Boolean = false,
    val peopleList: CommunityProfilePeopleList? = null,
)

enum class CommunityProfilePeopleList { Followers, Following }

sealed interface CommunityProfileNavigationEvent {
    data object ShowPosts : CommunityProfileNavigationEvent
    data class ShowPeople(val list: CommunityProfilePeopleList) : CommunityProfileNavigationEvent
    data object ShowDetails : CommunityProfileNavigationEvent
}

fun CommunityProfileNavigationState.reduce(event: CommunityProfileNavigationEvent): CommunityProfileNavigationState =
    when (event) {
        CommunityProfileNavigationEvent.ShowPosts -> copy(showingPosts = true, peopleList = null)
        is CommunityProfileNavigationEvent.ShowPeople -> copy(showingPosts = false, peopleList = event.list)
        CommunityProfileNavigationEvent.ShowDetails -> copy(showingPosts = false, peopleList = null)
    }

/** Private mutations must always pass through the shared authentication gate. */
internal fun communityProfilePrivateActionAllowed(currentUserId: String?): Boolean =
    !currentUserId.isNullOrBlank()
