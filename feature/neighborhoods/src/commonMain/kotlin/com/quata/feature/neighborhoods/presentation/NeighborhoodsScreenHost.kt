package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser

/** Small common contract so platform lifecycle adapters never recreate the directory state machine. */
interface NeighborhoodsScreenModel {
    val uiState: kotlinx.coroutines.flow.StateFlow<NeighborhoodsUiState>
    fun startObservingCommunities()
    fun stopObservingCommunities()
    fun retryCommunities()
    fun openChat(neighborhood: String, onOpened: (String) -> Unit)
    fun toggleFollowUser(userId: String)
    fun openPrivateChat(userId: String, onOpened: (String) -> Unit)
    fun openUserProfile(userId: String)
    fun close()
}

internal fun canPerformNeighborhoodPrivateAction(currentUserId: String?): Boolean = !currentUserId.isNullOrBlank()

data class NeighborhoodsScreenStrings(
    val list: NeighborhoodListStrings,
    val members: NeighborhoodUsersStrings,
)

/** The one directory/members root shared by Android, Wasm and iOS. */
@Composable
fun NeighborhoodsScreenHost(
    repository: NeighborhoodRepository? = null,
    model: NeighborhoodsScreenModel? = null,
    currentUserId: String?,
    strings: NeighborhoodsScreenStrings,
    errorStrings: NeighborhoodsErrorStrings = defaultNeighborhoodsErrorStrings(null),
    avatar: @Composable (NeighborhoodUser, Boolean, () -> Unit) -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onAuthRequired: () -> Unit,
    padding: PaddingValues,
    closeModelOnDispose: Boolean = false,
    openingProfileUserId: String? = null,
) {
    require((repository == null) != (model == null)) { "Provide exactly one Neighborhoods model source" }
    val ownedModel = if (model == null) androidx.compose.runtime.remember(repository, errorStrings) { NeighborhoodsViewModel(requireNotNull(repository), errors = errorStrings) } else null
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
    val selected = state.communities.firstOrNull { it.name == selectedCommunity }
    if (selected != null) {
        NeighborhoodUsersContent(
            padding = padding, community = selected, currentUserId = currentUserId,
            isOpeningChat = state.isOpeningChat, openingPrivateChatUserId = state.openingPrivateChatUserId,
            openingProfileUserId = openingProfileUserId ?: state.openingProfileUserId, followingUserId = state.followingUserId,
            strings = strings.members, avatar = avatar,
            onBack = { selectedCommunity = null },
            onFollowUser = { user -> if (!canPerformNeighborhoodPrivateAction(currentUserId)) onAuthRequired() else viewModel.toggleFollowUser(user.id) },
            onOpenProfile = { user -> onOpenUserProfile(user.id) },
            onOpenPrivateChat = { user ->
                if (!canPerformNeighborhoodPrivateAction(currentUserId)) onAuthRequired() else viewModel.openPrivateChat(user.id) { conversationId ->
                    selectedCommunity = null
                    onOpenConversation(conversationId)
                }
            },
        )
    } else {
        NeighborhoodListContent(
            padding = padding, communities = state.communities, query = query, isLoading = state.isLoading,
            error = state.error, currentUserId = currentUserId, openingNeighborhood = state.openingChatNeighborhood,
            strings = strings.list, onQueryChange = { query = it }, onShowUsers = { selectedCommunity = it.name },
            onOpenChat = { community -> if (!canPerformNeighborhoodPrivateAction(currentUserId)) onAuthRequired() else viewModel.openChat(community.name, onOpenConversation) },
            onRetry = viewModel::retryCommunities,
        )
    }
}
