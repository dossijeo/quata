package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.domain.ProfileAttachment

data class CommunityProfileStrings(
    val posts: String, val followers: String, val following: String,
    val followersOf: (String) -> String, val followingOf: (String) -> String,
    val gallery: String, val noPosts: String,
    val attachments: ProfileAttachmentsStrings, val actions: ProfileActionStrings,
    val moderation: ProfileModerationStrings,
    val moderationConfirmation: ProfileModerationConfirmationStrings,
    val roles: ProfileRoleStrings, val userActions: NeighborhoodUserRowStrings, val back: String,
    val runtime: CommunityProfileRuntimeStrings,
)

data class CommunityProfileRuntimeStrings(
    val loadingProfile: String, val retry: String, val genericFile: String, val loadVideo: String,
    val attachmentOpenFailed: String, val attachmentCancelled: String, val attachmentUnsupported: String,
    val image: String, val video: String, val audio: String, val document: String,
    val playAudio: String, val pauseAudio: String,
)

enum class CommunityProfileAvatarRole(val sizeDp: Int) { Header(92), Row(48) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityProfileRoot(
    profile: CommunityUserProfile, currentUserId: String?, sheetState: SheetState,
    containerColor: Color, contentColor: Color, strings: CommunityProfileStrings,
    isOpeningChat: Boolean, isRefreshing: Boolean, followingUserId: String?, openingProfileUserId: String?,
    roleUpdatingUserId: String?, currentUserIsAdmin: Boolean, error: String?,
    showModeration: Boolean = true, showAdminControls: Boolean = true,
    onDismiss: () -> Unit, onAuthRequired: () -> Unit, onFollowUser: (String) -> Unit,
    onOpenPrivateChat: (String) -> Unit, onOpenUserProfile: (String) -> Unit,
    onReportProfile: (String) -> Unit, onBlockProfile: (String) -> Unit,
    onSetRoles: (String, Boolean, Boolean) -> Unit, onAddPostComment: (String, String) -> Unit,
    avatar: @Composable (NeighborhoodUser, Boolean, CommunityProfileAvatarRole, (() -> Unit)?) -> Unit,
    attachmentItem: @Composable (ProfileAttachment) -> Unit,
    postPreview: @Composable (Post, Int, Boolean, () -> Unit) -> Unit,
    commentsDialog: @Composable (Post, List<PostComment>, (String) -> Unit, () -> Unit) -> Unit,
) {
    var navigation by rememberSaveable(profile.user.id) { mutableStateOf(CommunityProfileNavigationState()) }
    var moderation by remember { mutableStateOf<ProfileModerationAction?>(null) }
    val dispatch: (CommunityProfileNavigationEvent) -> Unit = { navigation = navigation.reduce(it) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(navigation.showingPosts) { if (navigation.showingPosts) listState.animateScrollToItem(2) }
    ProfileModerationConfirmation(moderation, strings.moderationConfirmation, { moderation = null }) { action ->
        moderation = null
        if (action == ProfileModerationAction.Block) onBlockProfile(profile.user.id) else onReportProfile(profile.user.id)
    }
    CommunityProfileSheetContent(sheetState, containerColor, contentColor, onDismiss) {
        val list = navigation.peopleList
        if (list != null) {
            val users = if (list == CommunityProfilePeopleList.Followers) profile.followers else profile.following
            ProfileUsersListCommon(
                title = if (list == CommunityProfilePeopleList.Followers) strings.followersOf(profile.user.displayName) else strings.followingOf(profile.user.displayName),
                users = users, currentUserId = currentUserId, isOpeningChat = isOpeningChat,
                openingProfileUserId = openingProfileUserId, followingUserId = followingUserId, strings = strings.userActions, back = strings.back,
                avatar = { user, loading, click -> avatar(user, loading, CommunityProfileAvatarRole.Row, click) },
                onBack = { dispatch(CommunityProfileNavigationEvent.ShowDetails) },
                onFollow = { user -> if (communityProfilePrivateActionAllowed(currentUserId)) onFollowUser(user.id) else onAuthRequired() },
                onProfile = { onOpenUserProfile(it.id) },
                onChat = { user -> if (communityProfilePrivateActionAllowed(currentUserId)) onOpenPrivateChat(user.id) else onAuthRequired() },
            )
        } else {
            CommunityProfileDetailsContent(
                listState = listState, modifier = Modifier.heightIn(max = 780.dp),
                header = {
                    CommunityProfileHeaderContent(
                        displayName = profile.user.displayName, neighborhood = profile.user.neighborhood,
                        avatar = { avatar(profile.user, isRefreshing, CommunityProfileAvatarRole.Header, null) },
                        kpis = {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ProfileKpiContent(profile.user.postsCount, strings.posts, Modifier.weight(1f)) { dispatch(CommunityProfileNavigationEvent.ShowPosts) }
                                ProfileKpiContent(profile.user.followersCount, strings.followers, Modifier.weight(1f)) { dispatch(CommunityProfileNavigationEvent.ShowPeople(CommunityProfilePeopleList.Followers)) }
                                ProfileKpiContent(profile.user.followingCount, strings.following, Modifier.weight(1f)) { dispatch(CommunityProfileNavigationEvent.ShowPeople(CommunityProfilePeopleList.Following)) }
                            }
                        },
                        primaryActions = {
                            ProfilePrimaryActions(profile.user.id == currentUserId, profile.user.isFollowing, followingUserId == profile.user.id, isOpeningChat, strings.actions,
                                { if (communityProfilePrivateActionAllowed(currentUserId)) onFollowUser(profile.user.id) else onAuthRequired() },
                                { if (communityProfilePrivateActionAllowed(currentUserId)) onOpenPrivateChat(profile.user.id) else onAuthRequired() })
                        },
                        moderationActions = {
                            ProfileModerationActions(showModeration && profile.user.id != currentUserId, strings.moderation,
                                { if (communityProfilePrivateActionAllowed(currentUserId)) moderation = ProfileModerationAction.Report else onAuthRequired() },
                                { if (communityProfilePrivateActionAllowed(currentUserId)) moderation = ProfileModerationAction.Block else onAuthRequired() })
                        },
                        adminControls = if (showAdminControls && currentUserIsAdmin && profile.user.id != currentUserId) {
                            { Spacer(Modifier.height(14.dp)); ProfileRoleControlsContent(profile.user, roleUpdatingUserId == profile.user.id, strings.roles) { admin, official -> onSetRoles(profile.user.id, admin, official) } }
                        } else null,
                        errorMessage = error?.let { message -> { Text(message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } },
                    )
                },
                attachments = { ProfileAttachmentsContent(profile.attachments, strings.attachments, attachmentItem); Spacer(Modifier.height(18.dp)) },
                gallery = if (navigation.showingPosts) {
                    {
                        val pager = rememberPagerState(pageCount = { profile.posts.size })
                        ProfileGalleryHeader(strings.gallery, (pager.currentPage + 1).takeIf { profile.posts.isNotEmpty() }, profile.posts.size, strings.noPosts.takeIf { profile.posts.isEmpty() })
                        if (profile.posts.isNotEmpty()) ProfilePostsPagerContent(profile.posts, pager, postPreview, commentsDialog, onAddPostComment)
                    }
                } else null,
            )
        }
    }
}
