package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

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

/**
 * The single cross-platform modal root. It owns modal dismissal and navigation between details,
 * posts and follower/following lists; hosts provide only platform services and visual slots.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityProfileRoot(
    profileId: String,
    sheetState: SheetState,
    containerColor: Color,
    contentColor: Color,
    onDismiss: () -> Unit,
    details: @Composable ColumnScope.(CommunityProfileNavigationState, (CommunityProfileNavigationEvent) -> Unit) -> Unit,
    people: @Composable ColumnScope.(CommunityProfilePeopleList, () -> Unit) -> Unit,
) {
    var navigation by rememberSaveable(profileId) { mutableStateOf(CommunityProfileNavigationState()) }
    val dispatch: (CommunityProfileNavigationEvent) -> Unit = { navigation = navigation.reduce(it) }
    CommunityProfileSheetContent(sheetState, containerColor, contentColor, onDismiss) {
        navigation.peopleList?.let { list ->
            people(list) { dispatch(CommunityProfileNavigationEvent.ShowDetails) }
        } ?: details(navigation, dispatch)
    }
}
