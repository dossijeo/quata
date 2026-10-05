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
import com.quata.core.navigation.AuthenticationContinuationCoordinator
import com.quata.core.navigation.AuthenticationContinuationIntent
import com.quata.core.navigation.AuthenticationContinuationKind
import com.quata.core.navigation.PendingAuthenticationContinuation
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
    fun retryCommunities() {
        stopObservingCommunities()
        startObservingCommunities()
    }
    fun openChat(neighborhood: String, onOpened: (String) -> Unit)
    fun toggleFollowUser(userId: String)
    fun ensureFollowUserState(userId: String, desiredState: Boolean)
    fun retryFollowUser(userId: String)
    fun openPrivateChat(userId: String, onOpened: (String) -> Unit)
    fun cancelPrivateChatOpen()
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
    onAuthenticationContinuationRequired: (AuthenticationContinuationIntent) -> Unit = { onAuthRequired() },
    authenticationContinuationCoordinator: AuthenticationContinuationCoordinator? = null,
    authenticationContinuationOriginRoute: String = "communities",
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
    val pendingAuthenticationContinuation by (
        authenticationContinuationCoordinator?.pending
            ?: remember { kotlinx.coroutines.flow.MutableStateFlow<PendingAuthenticationContinuation?>(null) }
        ).collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCommunity by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(viewModel, ownedModel) {
        viewModel.startObservingCommunities()
        onDispose {
            viewModel.cancelPrivateChatOpen()
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

    LaunchedEffect(
        currentUserId,
        pendingAuthenticationContinuation?.requestId,
        state.communities,
        state.isLoading,
    ) {
        if (currentUserId.isNullOrBlank()) return@LaunchedEffect
        val coordinator = authenticationContinuationCoordinator ?: return@LaunchedEffect
        val pending = pendingAuthenticationContinuation ?: return@LaunchedEffect
        val intent = pending.intent
        when (val resolution = resolveCommunitiesAuthenticationContinuation(
            intent = intent,
            originRoute = authenticationContinuationOriginRoute,
            communities = state.communities,
            isLoading = state.isLoading,
        )) {
            CommunitiesAuthenticationContinuationResolution.Ignore,
            CommunitiesAuthenticationContinuationResolution.Wait -> Unit
            CommunitiesAuthenticationContinuationResolution.Clear -> coordinator.clear(pending.requestId)
            is CommunitiesAuthenticationContinuationResolution.OpenNeighborhoodChat -> {
                if (coordinator.claim(pending.requestId) != null) {
                    viewModel.openChat(resolution.neighborhood, onOpenConversation)
                }
            }
            is CommunitiesAuthenticationContinuationResolution.EnsureFollowState -> {
                if (coordinator.claim(pending.requestId) != null) {
                    viewModel.ensureFollowUserState(resolution.userId, resolution.desiredState)
                }
            }
            is CommunitiesAuthenticationContinuationResolution.OpenPrivateChat -> {
                if (coordinator.claim(pending.requestId) != null) {
                    viewModel.openPrivateChat(resolution.userId, onOpenConversation)
                }
            }
        }
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
            onBack = {
                viewModel.cancelPrivateChatOpen()
                selectedCommunity = null
            },
            onFollowUser = { user ->
                if (canPerformNeighborhoodPrivateAction(currentUserId)) viewModel.toggleFollowUser(user.id)
                else onAuthenticationContinuationRequired(
                    communitiesAuthenticationContinuation(
                        kind = AuthenticationContinuationKind.CommunitiesToggleFollow,
                        originRoute = authenticationContinuationOriginRoute,
                        targetId = user.id,
                        desiredState = !user.isFollowing,
                    ),
                )
            },
            onOpenProfile = { user ->
                viewModel.cancelPrivateChatOpen()
                onOpenUserProfile(user.id)
            },
            onOpenPrivateChat = { user ->
                if (canPerformNeighborhoodPrivateAction(currentUserId)) {
                    viewModel.openPrivateChat(user.id) { conversationId ->
                        selectedCommunity = null
                        onOpenConversation(conversationId)
                    }
                } else {
                    onAuthenticationContinuationRequired(
                        communitiesAuthenticationContinuation(
                            kind = AuthenticationContinuationKind.CommunitiesOpenPrivateChat,
                            originRoute = authenticationContinuationOriginRoute,
                            targetId = user.id,
                        ),
                    )
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
            directoryLoadFailed = state.directoryLoadFailed,
            directoryAccessDenied = state.directoryAccessDenied,
            currentUserId = currentUserId,
            openingNeighborhood = state.openingChatNeighborhood,
            chatErrorNeighborhood = state.chatErrorNeighborhood,
            strings = strings.list,
            onQueryChange = { query = it },
            onRetry = viewModel::retryCommunities,
            onShowUsers = { selectedCommunity = it.name },
            onOpenChat = { community ->
                if (canPerformNeighborhoodPrivateAction(currentUserId)) {
                    viewModel.openChat(community.name, onOpenConversation)
                } else {
                    onAuthenticationContinuationRequired(
                        communitiesAuthenticationContinuation(
                            kind = AuthenticationContinuationKind.CommunitiesOpenNeighborhoodChat,
                            originRoute = authenticationContinuationOriginRoute,
                            targetId = community.name,
                        ),
                    )
                }
            },
        )
    }
}

private fun neighborhoodRequestKey(value: String): String =
    value.trim().lowercase().replace(Regex("[^a-z0-9]+"), ".").trim('.')

internal fun communitiesAuthenticationContinuation(
    kind: AuthenticationContinuationKind,
    originRoute: String,
    targetId: String,
    desiredState: Boolean? = null,
): AuthenticationContinuationIntent = AuthenticationContinuationIntent(
    kind = kind,
    originRoute = originRoute,
    targetId = targetId,
    desiredState = desiredState,
)

internal sealed interface CommunitiesAuthenticationContinuationResolution {
    data object Ignore : CommunitiesAuthenticationContinuationResolution
    data object Wait : CommunitiesAuthenticationContinuationResolution
    data object Clear : CommunitiesAuthenticationContinuationResolution
    data class OpenNeighborhoodChat(val neighborhood: String) : CommunitiesAuthenticationContinuationResolution
    data class EnsureFollowState(
        val userId: String,
        val desiredState: Boolean,
    ) : CommunitiesAuthenticationContinuationResolution
    data class OpenPrivateChat(val userId: String) : CommunitiesAuthenticationContinuationResolution
}

internal fun resolveCommunitiesAuthenticationContinuation(
    intent: AuthenticationContinuationIntent,
    originRoute: String,
    communities: List<com.quata.feature.neighborhoods.domain.NeighborhoodCommunity>,
    isLoading: Boolean,
): CommunitiesAuthenticationContinuationResolution {
    if (intent.originRoute != originRoute) return CommunitiesAuthenticationContinuationResolution.Ignore
    return when (intent.kind) {
        AuthenticationContinuationKind.CommunitiesOpenNeighborhoodChat -> intent.targetId
            ?.let(CommunitiesAuthenticationContinuationResolution::OpenNeighborhoodChat)
            ?: CommunitiesAuthenticationContinuationResolution.Clear
        AuthenticationContinuationKind.CommunitiesToggleFollow -> {
            val userId = intent.targetId ?: return CommunitiesAuthenticationContinuationResolution.Clear
            val desiredState = intent.desiredState ?: return CommunitiesAuthenticationContinuationResolution.Clear
            val user = communities.asSequence().flatMap { it.users.asSequence() }.firstOrNull { it.id == userId }
            when {
                user == null && isLoading -> CommunitiesAuthenticationContinuationResolution.Wait
                user == null -> CommunitiesAuthenticationContinuationResolution.Clear
                else -> CommunitiesAuthenticationContinuationResolution.EnsureFollowState(userId, desiredState)
            }
        }
        AuthenticationContinuationKind.CommunitiesOpenPrivateChat -> intent.targetId
            ?.let(CommunitiesAuthenticationContinuationResolution::OpenPrivateChat)
            ?: CommunitiesAuthenticationContinuationResolution.Clear
        else -> CommunitiesAuthenticationContinuationResolution.Ignore
    }
}
