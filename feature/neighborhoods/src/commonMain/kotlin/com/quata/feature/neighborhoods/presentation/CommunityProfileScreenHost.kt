package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.domain.ProfileAttachment

const val PublicProfileRootTestTag = "public-profile.root"
const val PublicProfileBackTestTag = "public-profile.back"
const val PublicProfileUserTestTagPrefix = "public-profile.user."
const val PublicProfileHeaderTestTagPrefix = PublicProfileUserTestTagPrefix
const val PublicProfileAvatarTestTagPrefix = "public-profile.avatar."
const val PublicProfileNameTestTagPrefix = "public-profile.name."
const val PublicProfileNeighborhoodTestTagPrefix = "public-profile.neighborhood."
const val PublicProfilePostsKpiTestTagPrefix = "public-profile.kpi.posts."
const val PublicProfileFollowersKpiTestTagPrefix = "public-profile.kpi.followers."
const val PublicProfileFollowingKpiTestTagPrefix = "public-profile.kpi.following."

data class CommunityProfileStrings(
    val posts: String,
    val followers: String,
    val following: String,
    val followersOf: (String) -> String,
    val followingOf: (String) -> String,
    val actions: ProfileActionStrings,
    val userRow: NeighborhoodUserRowStrings,
    val moderation: ProfileModerationStrings,
    val moderationConfirmation: ProfileModerationConfirmationStrings,
    val roles: ProfileRoleStrings,
    val attachments: ProfileAttachmentsStrings,
    val galleryTitle: String,
    val emptyGallery: String,
    val back: String,
    val comments: CommunityProfileCommentsDialogStrings,
)

/** Platform-only rendering boundaries used by the shared public-profile root. */
class CommunityProfilePlatformSlots(
    val avatar: @Composable (
        user: NeighborhoodUser,
        modifier: Modifier,
        isLoading: Boolean,
        onOpenAvatar: (() -> Unit)?,
    ) -> Unit,
    val attachment: @Composable (ProfileAttachment, onOpen: () -> Unit) -> Unit,
    val postMedia: @Composable BoxScope.(Post, isVideoLoaded: Boolean, onLoadVideo: () -> Unit) -> Unit,
    val openAttachment: (ProfileAttachment) -> Unit,
    val sharePost: (Post) -> Unit,
)

/**
 * Complete portable orchestration of Android's global public-profile panel.
 *
 * Navigation, authorization and backend mutations remain explicit callbacks. Platform hosts may
 * adapt image/video/document/share services, but must consume this root rather than reproduce its
 * layout or state machine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityProfileScreenHost(
    profile: CommunityUserProfile,
    currentUserId: String?,
    strings: CommunityProfileStrings,
    slots: CommunityProfilePlatformSlots,
    isOpeningChat: Boolean = false,
    isRefreshingProfile: Boolean = false,
    followingUserId: String? = null,
    roleUpdatingUserId: String? = null,
    commentingPostId: String? = null,
    likingPostId: String? = null,
    profileSafetyUpdatingUserId: String? = null,
    currentUserIsAdmin: Boolean = false,
    openingProfileUserId: String? = null,
    errorMessage: String? = null,
    onAuthRequired: () -> Unit,
    onBack: () -> Unit,
    onFollowUser: (String) -> Unit,
    onOpenPrivateChat: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onSetUserRoles: ((String, Boolean, Boolean) -> Unit)?,
    onReportPost: (String) -> Unit,
    onTogglePostLike: (String) -> Unit,
    onReportProfile: ((String) -> Unit)?,
    onSetProfileBlocked: ((String, Boolean) -> Unit)?,
    onAddComment: (String, PostComment) -> Unit,
    createComment: (Post, String) -> PostComment,
    /** Web has no system back affordance and its Compose sheet cannot rely on swipe dismissal. */
    showDismissButton: Boolean = false,
) {
    val isOwnProfile = profile.user.id == currentUserId
    var showPosts by rememberSaveable(profile.user.id) { mutableStateOf(false) }
    var userList by rememberSaveable(profile.user.id) { mutableStateOf<ProfileUserList?>(null) }
    var pendingModeration by remember { mutableStateOf<ProfileModerationAction?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val template = quataTheme()

    LaunchedEffect(showPosts) {
        if (showPosts) listState.animateScrollToItem(2)
    }
    ProfileModerationConfirmation(
        action = pendingModeration,
        strings = strings.moderationConfirmation,
        onDismiss = { pendingModeration = null },
        onConfirm = { action ->
            pendingModeration = null
            when (action) {
                ProfileModerationAction.Report -> onReportProfile?.invoke(profile.user.id)
                ProfileModerationAction.Block -> onSetProfileBlocked?.invoke(profile.user.id, true)
                ProfileModerationAction.Unblock -> onSetProfileBlocked?.invoke(profile.user.id, false)
            }
        },
    )
    CommunityProfileSheetContent(
        sheetState = sheetState,
        containerColor = template.colors.background,
        contentColor = template.colors.textPrimary,
        modifier = Modifier.semantics { testTag = PublicProfileRootTestTag },
        onDismiss = onBack,
    ) {
        if (showDismissButton) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                CompactIconButton(onClick = onBack, modifier = Modifier.semantics { testTag = PublicProfileBackTestTag }) {
                    CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, strings.back)
                }
            }
        }
        val selectedList = userList
        if (selectedList != null) {
            val users = if (selectedList == ProfileUserList.Followers) profile.followers else profile.following
            ProfileUsersListCommon(
                listKind = selectedList.testTagSuffix,
                title = if (selectedList == ProfileUserList.Followers) strings.followersOf(profile.user.displayName) else strings.followingOf(profile.user.displayName),
                users = users,
                currentUserId = currentUserId,
                isOpeningChat = isOpeningChat,
                openingProfileUserId = openingProfileUserId,
                followingUserId = followingUserId,
                strings = strings.userRow,
                back = strings.back,
                avatar = { user, loading, modifier, click -> slots.avatar(user, Modifier.size(48.dp).then(modifier), loading, click) },
                onBack = { userList = null },
                onFollow = { user -> if (currentUserId == null) onAuthRequired() else onFollowUser(user.id) },
                onProfile = { user -> onOpenUserProfile(user.id) },
                onChat = { user -> if (currentUserId == null) onAuthRequired() else onOpenPrivateChat(user.id) },
            )
        } else {
            CommunityProfileDetailsContent(
                listState = listState,
                modifier = Modifier.heightIn(max = 780.dp),
                header = {
                    CommunityProfileHeaderContent(
                        displayName = profile.user.displayName,
                        neighborhood = profile.user.neighborhood,
                        modifier = Modifier.semantics { testTag = PublicProfileHeaderTestTagPrefix + profile.user.id },
                        displayNameModifier = Modifier.semantics { testTag = PublicProfileNameTestTagPrefix + profile.user.id },
                        neighborhoodModifier = Modifier.semantics { testTag = PublicProfileNeighborhoodTestTagPrefix + profile.user.id },
                        avatar = {
                            slots.avatar(
                                profile.user,
                                Modifier.size(92.dp).semantics { testTag = PublicProfileAvatarTestTagPrefix + profile.user.id },
                                isRefreshingProfile,
                                profile.user.avatarUrl?.takeIf(String::isNotBlank)?.let { { slots.openAttachment(profile.user.toAvatarAttachment()) } },
                            )
                        },
                        kpis = {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                ProfileKpiContent(
                                    profile.user.postsCount,
                                    strings.posts,
                                    Modifier.weight(1f).semantics { testTag = PublicProfilePostsKpiTestTagPrefix + profile.user.id },
                                ) { showPosts = true }
                                ProfileKpiContent(
                                    profile.user.followersCount,
                                    strings.followers,
                                    Modifier.weight(1f).semantics { testTag = PublicProfileFollowersKpiTestTagPrefix + profile.user.id },
                                ) { userList = ProfileUserList.Followers }
                                ProfileKpiContent(
                                    profile.user.followingCount,
                                    strings.following,
                                    Modifier.weight(1f).semantics { testTag = PublicProfileFollowingKpiTestTagPrefix + profile.user.id },
                                ) { userList = ProfileUserList.Following }
                            }
                        },
                        primaryActions = {
                            ProfilePrimaryActions(
                                userId = profile.user.id,
                                isOwnProfile = isOwnProfile,
                                isFollowing = profile.user.isFollowing,
                                isFollowingLoading = followingUserId == profile.user.id,
                                isOpeningChat = isOpeningChat,
                                strings = strings.actions,
                                onFollow = { if (currentUserId == null) onAuthRequired() else onFollowUser(profile.user.id) },
                                onChat = { if (currentUserId == null) onAuthRequired() else onOpenPrivateChat(profile.user.id) },
                            )
                        },
                        moderationActions = {
                            ProfileModerationActions(
                                visible = !isOwnProfile && onReportProfile != null && onSetProfileBlocked != null,
                                isBlocked = profile.isBlockedByCurrentUser,
                                isUpdating = profileSafetyUpdatingUserId == profile.user.id,
                                strings = strings.moderation,
                                onReport = { if (currentUserId == null) onAuthRequired() else pendingModeration = ProfileModerationAction.Report },
                                onBlock = {
                                    if (currentUserId == null) onAuthRequired()
                                    else pendingModeration = if (profile.isBlockedByCurrentUser) ProfileModerationAction.Unblock else ProfileModerationAction.Block
                                },
                            )
                        },
                        adminControls = if (currentUserIsAdmin && !isOwnProfile && onSetUserRoles != null) {
                            {
                                Spacer(Modifier.height(14.dp))
                                ProfileRoleControlsContent(
                                    user = profile.user,
                                    isUpdating = roleUpdatingUserId == profile.user.id,
                                    strings = strings.roles,
                                    onSetRoles = { isAdmin, isOfficial -> onSetUserRoles(profile.user.id, isAdmin, isOfficial) },
                                )
                            }
                        } else null,
                        errorMessage = errorMessage?.let { message ->
                            { Spacer(Modifier.height(10.dp)); Text(message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                        },
                    )
                },
                attachments = {
                    ProfileAttachmentsContent(
                        attachments = profile.attachments,
                        strings = strings.attachments,
                        attachmentItem = { attachment -> slots.attachment(attachment) { slots.openAttachment(attachment) } },
                    )
                    Spacer(Modifier.height(18.dp))
                },
                gallery = if (showPosts) {
                    {
                        val pagerState = rememberPagerState(pageCount = { profile.posts.size })
                        ProfileGalleryHeader(
                            title = strings.galleryTitle,
                            currentIndex = (pagerState.currentPage + 1).takeIf { profile.posts.isNotEmpty() },
                            total = profile.posts.size,
                            emptyLabel = strings.emptyGallery.takeIf { profile.posts.isEmpty() },
                        )
                        if (profile.posts.isNotEmpty()) {
                            ProfilePostsPagerContent(
                                posts = profile.posts,
                                pagerState = pagerState,
                                onAddComment = { post, comment -> onAddComment(post.id, comment) },
                                postPreview = { post, commentsCount, openComments ->
                                    CommunityProfilePostPreviewContent(
                                        post = post,
                                        commentsCount = commentsCount,
                                        canParticipate = currentUserId != null,
                                        isLikeUpdating = likingPostId == post.id,
                                        onToggleLike = { onTogglePostLike(post.id) },
                                        onOpenComments = openComments,
                                        onAuthRequired = onAuthRequired,
                                        onShare = { slots.sharePost(post) },
                                        onReport = {
                                            if (currentUserId == null) onAuthRequired()
                                            else if (!post.isReportedByCurrentUser) onReportPost(post.id)
                                        },
                                        media = { loaded, load -> slots.postMedia(this, post, loaded, load) },
                                    )
                                },
                                commentsDialog = { post, addComment, dismiss ->
                                    CommunityProfileCommentsDialogContent(
                                        post = post,
                                        localComments = emptyList(),
                                        canParticipate = currentUserId != null,
                                        strings = strings.comments,
                                        onAuthRequired = onAuthRequired,
                                        createComment = { draft -> createComment(post, draft) },
                                        onAddComment = addComment,
                                        onDismiss = dismiss,
                                    )
                                },
                            )
                        }
                    }
                } else null,
            )
        }
    }
}

private enum class ProfileUserList(val testTagSuffix: String) { Followers("followers"), Following("following") }

private fun NeighborhoodUser.toAvatarAttachment(): ProfileAttachment = ProfileAttachment(
    id = "avatar-$id",
    name = displayName,
    uri = requireNotNull(avatarUrl),
    mimeType = "image/jpeg",
    sentAtMillis = null,
    senderName = displayName,
)
