package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle and action contract consumed by every Communities host.
 *
 * Platform adapters retain platform-only concerns such as resources, avatars and navigation,
 * while the directory state machine remains the same for Android, Wasm and iOS.
 */
interface NeighborhoodsScreenModel {
    val uiState: StateFlow<NeighborhoodsUiState>

    fun startObservingCommunities()
    fun stopObservingCommunities()
    fun openChat(neighborhood: String, onOpened: (String) -> Unit)
    fun toggleFollowUser(userId: String)
    fun openPrivateChat(userId: String, onOpened: (String) -> Unit)
    fun openUserProfile(userId: String)
    fun close()
}

data class NeighborhoodsScreenStrings(
    val list: NeighborhoodListStrings,
    val members: NeighborhoodUsersStrings,
)

/** Browsing the directory is public; mutations and conversations require an identity. */
internal fun canPerformNeighborhoodPrivateAction(currentUserId: String?): Boolean =
    !currentUserId.isNullOrBlank()

internal fun isNeighborhoodPrivateChatOpening(openingPrivateChatUserId: String?): Boolean =
    openingPrivateChatUserId != null

/**
 * Portable directory and members root. Exactly one source of state must be supplied: a platform
 * lifecycle adapter, or a repository for a lightweight host that owns its ViewModel.
 */
@Composable
fun NeighborhoodsScreenHost(
    currentUserId: String?,
    strings: NeighborhoodsScreenStrings,
    avatar: @Composable (NeighborhoodUser, Boolean, () -> Unit) -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onAuthRequired: () -> Unit,
    padding: PaddingValues,
    repository: NeighborhoodRepository? = null,
    model: NeighborhoodsScreenModel? = null,
    closeModelOnDispose: Boolean = false,
    openingProfileUserId: String? = null,
    requestedCommunityMembers: String? = null,
) {
    require((repository == null) != (model == null)) {
        "Provide exactly one Communities state source"
    }
    val ownedModel = if (model == null) {
        remember(repository) { NeighborhoodsViewModel(requireNotNull(repository)) }
    } else {
        null
    }
    val viewModel = model ?: requireNotNull(ownedModel)
    val state by viewModel.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCommunity by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(viewModel, ownedModel) {
        viewModel.startObservingCommunities()
        onDispose {
            viewModel.stopObservingCommunities()
            if (ownedModel != null || closeModelOnDispose) viewModel.close()
        }
    }

    LaunchedEffect(requestedCommunityMembers, state.communities) {
        val requested = requestedCommunityMembers?.trim()?.takeIf(String::isNotEmpty) ?: return@LaunchedEffect
        val selected = state.communities.firstOrNull { it.name.equals(requested, ignoreCase = true) }
            ?: state.communities.firstOrNull { community ->
                neighborhoodRequestKey(community.name) == neighborhoodRequestKey(requested)
            }
        if (selected != null) selectedCommunity = selected.name
    }

    val selected = state.communities.firstOrNull { it.name == selectedCommunity }
    if (selected != null) {
        NeighborhoodUsersContent(
            padding = padding,
            community = selected,
            currentUserId = currentUserId,
            isOpeningChat = isNeighborhoodPrivateChatOpening(state.openingPrivateChatUserId),
            openingPrivateChatUserId = state.openingPrivateChatUserId,
            openingProfileUserId = openingProfileUserId ?: state.openingProfileUserId,
            followingUserId = state.followingUserId,
            strings = strings.members,
            avatar = avatar,
            onBack = { selectedCommunity = null },
            onFollowUser = { user ->
                if (canPerformNeighborhoodPrivateAction(currentUserId)) viewModel.toggleFollowUser(user.id)
                else onAuthRequired()
            },
            onOpenProfile = { user -> onOpenUserProfile(user.id) },
            onOpenPrivateChat = { user ->
                if (canPerformNeighborhoodPrivateAction(currentUserId)) {
                    viewModel.openPrivateChat(user.id) { conversationId ->
                        selectedCommunity = null
                        onOpenConversation(conversationId)
                    }
                } else {
                    onAuthRequired()
                }
            },
        )
    } else {
        NeighborhoodListContent(
            padding = padding,
            communities = state.communities,
            query = query,
            isLoading = state.isLoading,
            error = state.error,
            currentUserId = currentUserId,
            openingNeighborhood = state.openingChatNeighborhood,
            chatErrorNeighborhood = state.chatErrorNeighborhood,
            strings = strings.list,
            onQueryChange = { query = it },
            onShowUsers = { selectedCommunity = it.name },
            onOpenChat = { community ->
                if (canPerformNeighborhoodPrivateAction(currentUserId)) {
                    viewModel.openChat(community.name, onOpenConversation)
                } else {
                    onAuthRequired()
                }
            },
        )
    }
}

private fun neighborhoodRequestKey(value: String): String =
    value.trim().lowercase().replace(Regex("[^a-z0-9]+"), ".").trim('.')
