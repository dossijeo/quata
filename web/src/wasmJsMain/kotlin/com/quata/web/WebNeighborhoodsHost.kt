package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.quata.core.ui.components.QuataLiveRankingItem
import com.quata.core.ui.components.QuataLiveRankingPanelContent
import com.quata.core.ui.components.QuataLiveRankingStrings
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.presentation.CommunityProfileCommentsPanelContent
import com.quata.feature.neighborhoods.presentation.CommunityProfileCommentInputContent
import com.quata.feature.neighborhoods.presentation.CommunityProfileCommentRowContent
import com.quata.feature.neighborhoods.presentation.CommunityProfileDetailsContent
import com.quata.feature.neighborhoods.presentation.CommunityProfileHeaderContent
import com.quata.feature.neighborhoods.presentation.NeighborhoodListContent
import com.quata.feature.neighborhoods.presentation.NeighborhoodListStrings
import com.quata.feature.neighborhoods.presentation.NeighborhoodUsersContent
import com.quata.feature.neighborhoods.presentation.NeighborhoodUsersStrings
import com.quata.feature.neighborhoods.presentation.NeighborhoodsViewModel
import com.quata.feature.neighborhoods.presentation.ProfileKpiContent

/** Text and labels are injected by the browser composition root, not hard-coded in shared UI. */
data class WebNeighborhoodsStrings(
    val list: NeighborhoodListStrings,
    val members: NeighborhoodUsersStrings,
    val commentsTitle: String,
    val commentsClose: String,
    val commentPlaceholder: String,
    val sendComment: String,
    val profilePosts: String,
    val profileFollowers: String,
    val profileFollowing: String,
    val back: String,
    val ranking: QuataLiveRankingStrings,
)

/** Browser-owned visual boundaries: image loading, media rendering and route handling stay outside commonMain. */
class WebNeighborhoodsSlots(
    val avatar: @Composable (NeighborhoodUser, Boolean, () -> Unit) -> Unit,
    val profileMedia: @Composable (CommunityUserProfile) -> Unit,
    val profileAttachments: @Composable (CommunityUserProfile) -> Unit,
    val rankingAvatar: @Composable (QuataLiveRankingItem) -> Unit,
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
    rankingItems: List<QuataLiveRankingItem>,
    onOpenConversation: (String) -> Unit,
    onOpenUserRoute: (String) -> Unit,
    /** Feed author navigation enters the existing shared Community member profile surface. */
    initialMemberProfileId: String? = null,
    onInitialMemberProfileClosed: () -> Unit = {},
    onOpenRankingItem: (String) -> Unit,
    onSubmitComment: (String) -> Unit,
    commentsEnabled: Boolean = true,
    padding: PaddingValues = PaddingValues(),
) {
    val viewModel = remember(repository) { NeighborhoodsViewModel(repository) }
    val state by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedNeighborhood by remember { mutableStateOf<String?>(null) }
    var commentDraft by remember { mutableStateOf("") }
    var showComments by remember { mutableStateOf(false) }
    var showRanking by remember { mutableStateOf(false) }

    DisposableEffect(viewModel) {
        viewModel.startObservingCommunities()
        onDispose {
            viewModel.stopObservingCommunities()
            viewModel.close()
        }
    }
    androidx.compose.runtime.LaunchedEffect(initialMemberProfileId) {
        initialMemberProfileId?.let(viewModel::openUserProfile)
    }

    if (showRanking) {
        QuataLiveRankingPanelContent(
            items = rankingItems,
            isLandscape = false,
            strings = strings.ranking,
            avatar = slots.rankingAvatar,
            onDismiss = { showRanking = false },
            onOpenItem = onOpenRankingItem,
        )
        return
    }

    val selectedProfile = state.selectedProfile
    if (selectedProfile != null) {
        WebCommunityProfileContent(
            profile = selectedProfile,
            strings = strings,
            slots = slots,
            onBack = {
                viewModel.closeUserProfile()
                if (initialMemberProfileId != null) onInitialMemberProfileClosed()
            },
            onOpenUserRoute = onOpenUserRoute,
            onShowComments = if (commentsEnabled) ({ showComments = true }) else null,
            onShowRanking = { showRanking = true },
        )
        if (showComments) {
            val comments = selectedProfile.posts.flatMap { it.comments }
            CommunityProfileCommentsPanelContent(
                comments = comments,
                title = strings.commentsTitle,
                closeContentDescription = strings.commentsClose,
                onDismiss = { showComments = false },
                commentRow = { CommunityProfileCommentRowContent(it) },
                input = {
                    CommunityProfileCommentInputContent(
                        value = commentDraft,
                        placeholder = strings.commentPlaceholder,
                        sendLabel = strings.sendComment,
                        onValueChange = { commentDraft = it },
                        onSend = {
                            onSubmitComment(commentDraft)
                            commentDraft = ""
                        },
                    )
                },
            )
        }
        return
    }

    val selectedCommunity = state.communities.firstOrNull { it.name == selectedNeighborhood }
    if (selectedCommunity != null) {
        NeighborhoodUsersContent(
            padding = padding,
            community = selectedCommunity,
            currentUserId = currentUserId,
            isOpeningChat = state.isOpeningChat,
            openingPrivateChatUserId = state.openingPrivateChatUserId,
            openingProfileUserId = state.openingProfileUserId,
            followingUserId = state.followingUserId,
            strings = strings.members,
            avatar = slots.avatar,
            onBack = { selectedNeighborhood = null },
            onFollowUser = { viewModel.toggleFollowUser(it.id) },
            onOpenProfile = {
                viewModel.openUserProfile(it.id)
                onOpenUserRoute(it.id)
            },
            onOpenPrivateChat = { user -> viewModel.openPrivateChat(user.id, onOpenConversation) },
        )
        return
    }

    NeighborhoodListContent(
        padding = padding,
        communities = state.communities,
        query = query,
        isLoading = state.isLoading,
        error = state.error,
        currentUserId = currentUserId,
        openingNeighborhood = state.openingChatNeighborhood,
        strings = strings.list,
        onQueryChange = { query = it },
        onShowUsers = { selectedNeighborhood = it.name },
        onOpenChat = { community -> viewModel.openChat(community.name, onOpenConversation) },
    )
}

@Composable
private fun WebCommunityProfileContent(
    profile: CommunityUserProfile,
    strings: WebNeighborhoodsStrings,
    slots: WebNeighborhoodsSlots,
    onBack: () -> Unit,
    onOpenUserRoute: (String) -> Unit,
    onShowComments: (() -> Unit)?,
    onShowRanking: () -> Unit,
) {
    val listState = rememberLazyListState()
    CommunityProfileDetailsContent(
        listState = listState,
        header = {
            CommunityProfileHeaderContent(
                displayName = profile.user.displayName,
                neighborhood = profile.user.neighborhood,
                avatar = { slots.avatar(profile.user, false) { onOpenUserRoute(profile.user.id) } },
                kpis = {
                    ProfileKpiContent(profile.user.postsCount, strings.profilePosts, onClick = onShowComments)
                    ProfileKpiContent(profile.user.followersCount, strings.profileFollowers, onClick = onShowRanking)
                    ProfileKpiContent(profile.user.followingCount, strings.profileFollowing)
                },
                primaryActions = { TextButton(onClick = onBack) { Text(strings.back) } },
                moderationActions = {},
                adminControls = null,
                errorMessage = null,
            )
        },
        attachments = { slots.profileAttachments(profile) },
        gallery = { slots.profileMedia(profile) },
    )
}
