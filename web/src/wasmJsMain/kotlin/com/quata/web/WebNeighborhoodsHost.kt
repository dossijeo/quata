@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.quata.core.model.PostComment
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.presentation.CommunityProfilePlatformSlots
import com.quata.feature.neighborhoods.presentation.CommunityProfileLoadStateContent
import com.quata.feature.neighborhoods.presentation.CommunityProfileScreenHost
import com.quata.feature.neighborhoods.presentation.CommunityProfileStrings
import com.quata.feature.neighborhoods.presentation.NeighborhoodListStrings
import com.quata.feature.neighborhoods.presentation.NeighborhoodsScreenHost
import com.quata.feature.neighborhoods.presentation.NeighborhoodsScreenStrings
import com.quata.feature.neighborhoods.presentation.NeighborhoodUsersStrings
import com.quata.feature.neighborhoods.presentation.NeighborhoodsViewModel

/** Text and labels are injected by the browser composition root, not hard-coded in shared UI. */
data class WebNeighborhoodsStrings(
    val list: NeighborhoodListStrings,
    val members: NeighborhoodUsersStrings,
    val profile: CommunityProfileStrings,
)

/** Browser-owned visual boundaries: image loading, media rendering and route handling stay outside commonMain. */
class WebNeighborhoodsSlots(
    val avatar: @Composable (NeighborhoodUser, Boolean, () -> Unit) -> Unit,
    val profile: CommunityProfilePlatformSlots,
)

/**
 * Web composition host for the shared Communities UI.
 *
 * The repository and ViewModel are injected; this host does not create browser transport, push,
 * media, URI, audio or image-loader implementations. Navigation and comment persistence cross
 * explicit callbacks so the launcher may wire them later without duplicating shared layouts.
 */
@Composable
fun WebNeighborhoodsHost(
    repository: NeighborhoodRepository,
    currentUserId: String?,
    strings: WebNeighborhoodsStrings,
    slots: WebNeighborhoodsSlots,
    onOpenConversation: (String) -> Unit,
    onAuthRequired: () -> Unit,
    onOpenUserRoute: (String) -> Unit,
    /** Feed author navigation enters the existing shared Community member profile surface. */
    initialMemberProfileId: String? = null,
    requestedCommunityMembers: String? = null,
    onInitialMemberProfileClosed: () -> Unit = {},
    showInitialLoadingSurface: Boolean = true,
    padding: PaddingValues = PaddingValues(),
) {
    val viewModel = remember(repository) { NeighborhoodsViewModel(repository) }
    val state by viewModel.uiState.collectAsState()

    DisposableEffect(viewModel) {
        onDispose { viewModel.close() }
    }
    androidx.compose.runtime.LaunchedEffect(initialMemberProfileId) {
        initialMemberProfileId?.let(viewModel::openUserProfile)
    }

    val selectedProfile = state.selectedProfile
    androidx.compose.runtime.LaunchedEffect(
        selectedProfile?.user?.id,
        selectedProfile?.user?.isFollowing,
        selectedProfile?.user?.followersCount,
        state.followingUserId,
        state.error,
    ) {
        setWebProfileFollowEvidenceState(
            profileId = selectedProfile?.user?.id,
            isFollowing = selectedProfile?.user?.isFollowing,
            followersCount = selectedProfile?.user?.followersCount,
            loading = state.followingUserId == selectedProfile?.user?.id,
            failed = selectedProfile != null && state.error != null,
        )
    }
    DisposableEffect(Unit) {
        onDispose { clearWebProfileFollowEvidenceState() }
    }
    if (selectedProfile == null && initialMemberProfileId != null) {
        if (showInitialLoadingSurface || state.error != null) {
            CommunityProfileLoadStateContent(
                isLoading = state.openingProfileUserId != null || state.error == null,
                errorMessage = state.error,
                backLabel = strings.profile.back,
                onBack = onInitialMemberProfileClosed,
            )
        }
        return
    }
    Box {
        if (initialMemberProfileId == null) {
            NeighborhoodsScreenHost(
                currentUserId = currentUserId,
                strings = NeighborhoodsScreenStrings(strings.list, strings.members),
                avatar = slots.avatar,
                onOpenConversation = onOpenConversation,
                onOpenUserProfile = { userId ->
                    viewModel.openUserProfile(userId)
                    onOpenUserRoute(userId)
                },
                onAuthRequired = onAuthRequired,
                padding = padding,
                model = viewModel,
                requestedCommunityMembers = requestedCommunityMembers,
            )
        }
        if (selectedProfile != null) {
            CommunityProfileScreenHost(
            profile = selectedProfile,
            currentUserId = currentUserId,
            strings = strings.profile,
            slots = slots.profile,
            isOpeningChat = state.openingPrivateChatUserId != null,
            openingPrivateChatUserId = state.openingPrivateChatUserId,
            isRefreshingProfile = state.refreshingProfileUserId == selectedProfile.user.id,
            followingUserId = state.followingUserId,
            roleUpdatingUserId = state.roleUpdatingUserId,
            commentingPostId = state.commentingPostId,
            likingPostId = state.likingPostId,
            profileSafetyUpdatingUserId = state.profileSafetyUpdatingUserId,
            currentUserIsAdmin = state.currentUserIsAdmin,
            openingProfileUserId = state.openingProfileUserId,
            errorMessage = state.error,
            onAuthRequired = onAuthRequired,
            onBack = {
                val closed = viewModel.closeUserProfile()
                if (closed && initialMemberProfileId != null) onInitialMemberProfileClosed()
            },
            onFollowUser = viewModel::toggleFollowUser,
            onOpenPrivateChat = { userId ->
                viewModel.openPrivateChat(userId) { conversationId ->
                    viewModel.clearUserProfile()
                    onOpenConversation(conversationId)
                }
            },
            onOpenUserProfile = { userId ->
                viewModel.openUserProfile(userId)
                onOpenUserRoute(userId)
            },
            onSetUserRoles = viewModel::setUserRoles,
            onReportPost = viewModel::reportProfilePost,
            onTogglePostLike = viewModel::toggleProfilePostLike,
            onReportProfile = viewModel::reportProfile,
            onSetProfileBlocked = viewModel::setProfileBlocked,
            onAddComment = viewModel::addProfileComment,
            createComment = { post, draft ->
                PostComment(
                    id = "profile_${post.id}_${draft.hashCode()}",
                    authorName = "Tú",
                    message = draft,
                    timestamp = "Ahora",
                )
            },
            showDismissButton = true,
            )
        }
    }
}

@JsFun("""(profileId, isFollowing, followersCount, loading, failed) => {
  const root = globalThis.document?.documentElement;
  if (!root) return;
  if (!profileId) {
    for (const name of ['data-quata-profile-follow-id', 'data-quata-profile-following', 'data-quata-profile-followers-count', 'data-quata-profile-follow-loading', 'data-quata-profile-follow-failed']) root.removeAttribute(name);
    return;
  }
  root.setAttribute('data-quata-profile-follow-id', profileId);
  root.setAttribute('data-quata-profile-following', isFollowing === true ? 'true' : 'false');
  root.setAttribute('data-quata-profile-followers-count', String(followersCount ?? 0));
  root.setAttribute('data-quata-profile-follow-loading', loading === true ? 'true' : 'false');
  root.setAttribute('data-quata-profile-follow-failed', failed === true ? 'true' : 'false');
}""")
private external fun setWebProfileFollowEvidenceState(
    profileId: String?,
    isFollowing: Boolean?,
    followersCount: Int?,
    loading: Boolean,
    failed: Boolean,
)

@JsFun("""() => {
  const root = globalThis.document?.documentElement;
  for (const name of ['data-quata-profile-follow-id', 'data-quata-profile-following', 'data-quata-profile-followers-count', 'data-quata-profile-follow-loading', 'data-quata-profile-follow-failed']) root?.removeAttribute(name);
}""")
private external fun clearWebProfileFollowEvidenceState()
