package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.navigation.AuthenticationContinuationCoordinator
import com.quata.core.navigation.AuthenticationContinuationIntent
import com.quata.core.navigation.AuthenticationContinuationKind
import com.quata.core.navigation.PendingAuthenticationContinuation
import com.quata.core.ui.components.QuataFullscreenMediaOverlayContent
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.designsystem.translation.FangTranslatorTriggerContent
import com.quata.designsystem.translation.QuataTranslatorGateway
import com.quata.designsystem.translation.QuataTranslatorStrings
import com.quata.designsystem.translation.quataTranslatorStringsForLanguage
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.domain.ProfileAttachment

const val PublicProfileRootTestTag = "public-profile.root"
const val PublicProfileBackTestTag = "public-profile.back"
const val PublicProfileFooterBackTestTag = "public-profile.back.footer"
const val PublicProfileUserTestTagPrefix = "public-profile.user."
const val PublicProfileHeaderTestTagPrefix = PublicProfileUserTestTagPrefix
const val PublicProfileAvatarTestTagPrefix = "public-profile.avatar."
const val PublicProfileNameTestTagPrefix = "public-profile.name."
const val PublicProfileNeighborhoodTestTagPrefix = "public-profile.neighborhood."
const val PublicProfilePostsKpiTestTagPrefix = "public-profile.kpi.posts."
const val PublicProfileFollowersKpiTestTagPrefix = "public-profile.kpi.followers."
const val PublicProfileFollowingKpiTestTagPrefix = "public-profile.kpi.following."
const val PublicProfileGalleryTestTagPrefix = "public-profile.gallery."
const val PublicProfileGalleryHeaderTestTagPrefix = "public-profile.gallery.header."
const val PublicProfileModerationRootTestTagPrefix = "public-profile.safety."
const val PublicProfileModerationReportTestTagPrefix = "public-profile.safety.report."
const val PublicProfileModerationBlockTestTagPrefix = "public-profile.safety.block."
const val PublicProfileModerationUnblockTestTagPrefix = "public-profile.safety.unblock."
const val PublicProfileModerationLoadingTestTagPrefix = "public-profile.safety.loading."
const val PublicProfileErrorTestTagPrefix = "public-profile.error."
const val PublicProfileRolesRootTestTagPrefix = "public-profile.roles."
const val PublicProfileRolesAdminTestTagPrefix = "public-profile.roles.admin."
const val PublicProfileRolesOfficialTestTagPrefix = "public-profile.roles.official."
const val PublicProfileRolesLoadingTestTagPrefix = "public-profile.roles.loading."
const val PublicProfileModerationDialogTestTagPrefix = "public-profile.safety.dialog."
const val PublicProfileModerationDialogConfirmTestTagPrefix = "public-profile.safety.dialog.confirm."
const val PublicProfileModerationDialogCancelTestTag = "public-profile.safety.dialog.cancel"
const val PublicProfileCommentsPendingTestTagPrefix = "public-profile.comments.pending."

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
    val retry: String,
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
    val nativeMediaClose: @Composable BoxScope.(onDismiss: () -> Unit) -> Unit = {},
    val nativeMediaCloseReplacesCommon: Boolean = false,
    val openAttachment: (ProfileAttachment) -> Unit,
    val sharePost: (Post) -> Unit,
    val commentsTranslatorTrigger: @Composable (String, Modifier, () -> Unit, Boolean) -> Unit = { contentDescription, modifier, onClick, enabled ->
        FangTranslatorTriggerContent(contentDescription = contentDescription, onClick = onClick, enabled = enabled, modifier = modifier)
    },
    val commentsTranslationGateway: QuataTranslatorGateway? = null,
    val commentsTranslatorStrings: QuataTranslatorStrings = quataTranslatorStringsForLanguage(null),
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
    openingPrivateChatUserId: String? = null,
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
    onAuthenticationContinuationRequired: (AuthenticationContinuationIntent) -> Unit = { onAuthRequired() },
    authenticationContinuationCoordinator: AuthenticationContinuationCoordinator? = null,
    authenticationContinuationOriginRoute: String = "communities",
    onBack: () -> Unit,
    onFollowUser: (String) -> Unit,
    onEnsureFollowUserState: (String, Boolean) -> Unit,
    onOpenPrivateChat: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onSetUserRoles: ((String, Boolean, Boolean) -> Unit)?,
    onReportPost: (String) -> Unit,
    onEnsurePostReported: (String, String) -> Unit,
    onTogglePostLike: (String) -> Unit,
    onEnsurePostLikeState: (String, String, Boolean) -> Unit,
    onReportProfile: ((String) -> Unit)?,
    onSetProfileBlocked: ((String, Boolean) -> Unit)?,
    onAddComment: (String, PostComment) -> Unit,
    createComment: (Post, String) -> PostComment,
    /** Web has no system back affordance and its Compose sheet cannot rely on swipe dismissal. */
    showDismissButton: Boolean = false,
) {
    val isOwnProfile = profile.user.id == currentUserId
    val isAnyPrivateChatOpening = isOpeningChat || openingPrivateChatUserId != null
    var showPosts by rememberSaveable(profile.user.id) { mutableStateOf(false) }
    var userList by rememberSaveable(profile.user.id) { mutableStateOf<ProfileUserList?>(null) }
    var selectedMediaPostId by rememberSaveable(profile.user.id) { mutableStateOf<String?>(null) }
    var pendingModeration by remember { mutableStateOf<ProfileModerationAction?>(null) }
    val pendingAuthenticationContinuation by (
        authenticationContinuationCoordinator?.pending
            ?: remember { kotlinx.coroutines.flow.MutableStateFlow<PendingAuthenticationContinuation?>(null) }
        ).collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val template = quataTheme()

    LaunchedEffect(showPosts) {
        if (showPosts) listState.animateScrollToItem(2)
    }
    LaunchedEffect(
        currentUserId,
        pendingAuthenticationContinuation?.requestId,
        profile,
        followingUserId,
        openingPrivateChatUserId,
        likingPostId,
        commentingPostId,
        profileSafetyUpdatingUserId,
    ) {
        if (currentUserId.isNullOrBlank()) return@LaunchedEffect
        val coordinator = authenticationContinuationCoordinator ?: return@LaunchedEffect
        val pending = pendingAuthenticationContinuation ?: return@LaunchedEffect
        when (val resolution = resolveCommunityProfileAuthenticationContinuation(
            intent = pending.intent,
            originRoute = authenticationContinuationOriginRoute,
            profile = profile,
            actionInProgress = followingUserId != null || openingPrivateChatUserId != null ||
                likingPostId != null || commentingPostId != null || profileSafetyUpdatingUserId != null,
        )) {
            CommunityProfileAuthenticationContinuationResolution.Ignore,
            CommunityProfileAuthenticationContinuationResolution.Wait -> Unit
            CommunityProfileAuthenticationContinuationResolution.Clear -> coordinator.clear(pending.requestId)
            is CommunityProfileAuthenticationContinuationResolution.EnsureFollow -> {
                if (coordinator.claim(pending.requestId) != null) {
                    onEnsureFollowUserState(resolution.userId, resolution.desiredState)
                }
            }
            is CommunityProfileAuthenticationContinuationResolution.OpenPrivateChat -> {
                if (coordinator.claim(pending.requestId) != null) onOpenPrivateChat(resolution.userId)
            }
            is CommunityProfileAuthenticationContinuationResolution.EnsurePostLike -> {
                if (coordinator.claim(pending.requestId) != null) {
                    onEnsurePostLikeState(profile.user.id, resolution.postId, resolution.desiredState)
                }
            }
            is CommunityProfileAuthenticationContinuationResolution.AddComment -> {
                if (coordinator.claim(pending.requestId) != null) {
                    val base = createComment(resolution.post, resolution.text)
                    onAddComment(
                        resolution.post.id,
                        base.copy(
                            replyToAuthorName = resolution.replyTarget?.authorName,
                            replyToMessage = resolution.replyTarget?.message,
                            replyToCommentId = resolution.replyTarget?.id,
                        ),
                    )
                }
            }
            is CommunityProfileAuthenticationContinuationResolution.EnsurePostReported -> {
                if (coordinator.claim(pending.requestId) != null) {
                    onEnsurePostReported(profile.user.id, resolution.postId)
                }
            }
            is CommunityProfileAuthenticationContinuationResolution.ConfirmModeration -> {
                if (coordinator.claim(pending.requestId) != null) pendingModeration = resolution.action
            }
        }
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
        val selectedMediaPost = selectedMediaPostId?.let { postId -> profile.posts.firstOrNull { it.id == postId } }
        if (selectedMediaPost != null) {
            QuataFullscreenMediaOverlayContent(
                title = selectedMediaPost.imageTitle(),
                onDismiss = { selectedMediaPostId = null },
                showCommonMediaClose = !slots.nativeMediaCloseReplacesCommon,
                nativeClose = { dismiss -> slots.nativeMediaClose(this, dismiss) },
            ) { mediaModifier ->
                androidx.compose.foundation.layout.Box(mediaModifier) {
                    slots.postMedia(this, selectedMediaPost, true) {}
                }
            }
        } else {
            if (showDismissButton) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    CompactIconButton(onClick = onBack, modifier = Modifier.semantics {
                        testTag = PublicProfileBackTestTag
                        contentDescription = PublicProfileBackTestTag
                    }) {
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
                    isOpeningChat = isAnyPrivateChatOpening,
                    openingPrivateChatUserId = openingPrivateChatUserId,
                    openingProfileUserId = openingProfileUserId,
                    followingUserId = followingUserId,
                    strings = strings.userRow,
                    back = strings.back,
                    avatar = { user, loading, modifier, click -> slots.avatar(user, Modifier.size(48.dp).then(modifier), loading, click) },
                    onBack = { userList = null },
                    onFollow = { user ->
                        if (currentUserId != null) onFollowUser(user.id)
                        else onAuthenticationContinuationRequired(
                            communityProfileAuthenticationContinuation(
                                AuthenticationContinuationKind.CommunityProfileEnsureFollow,
                                authenticationContinuationOriginRoute,
                                profile.user.id,
                                targetId = user.id,
                                desiredState = !user.isFollowing,
                            ),
                        )
                    },
                    onProfile = { user -> onOpenUserProfile(user.id) },
                    onChat = { user ->
                        if (currentUserId != null) onOpenPrivateChat(user.id)
                        else onAuthenticationContinuationRequired(
                            communityProfileAuthenticationContinuation(
                                AuthenticationContinuationKind.CommunityProfileOpenPrivateChat,
                                authenticationContinuationOriginRoute,
                                profile.user.id,
                                targetId = user.id,
                            ),
                        )
                    },
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
                                    Modifier.weight(1f),
                                    testTag = PublicProfilePostsKpiTestTagPrefix + profile.user.id,
                                ) { showPosts = true }
                                ProfileKpiContent(
                                    profile.user.followersCount,
                                    strings.followers,
                                    Modifier.weight(1f),
                                    testTag = PublicProfileFollowersKpiTestTagPrefix + profile.user.id,
                                ) { userList = ProfileUserList.Followers }
                                ProfileKpiContent(
                                    profile.user.followingCount,
                                    strings.following,
                                    Modifier.weight(1f),
                                    testTag = PublicProfileFollowingKpiTestTagPrefix + profile.user.id,
                                ) { userList = ProfileUserList.Following }
                            }
                        },
                        primaryActions = {
                            ProfilePrimaryActions(
                                userId = profile.user.id,
                                isOwnProfile = isOwnProfile,
                                isFollowing = profile.user.isFollowing,
                                isFollowingLoading = followingUserId == profile.user.id,
                                isFollowEnabled = followingUserId == null,
                                isOpeningChat = openingPrivateChatUserId?.let { it == profile.user.id } ?: isOpeningChat,
                                isChatEnabled = !isAnyPrivateChatOpening,
                                strings = strings.actions,
                                onFollow = {
                                    if (currentUserId != null) onFollowUser(profile.user.id)
                                    else onAuthenticationContinuationRequired(
                                        communityProfileAuthenticationContinuation(
                                            AuthenticationContinuationKind.CommunityProfileEnsureFollow,
                                            authenticationContinuationOriginRoute,
                                            profile.user.id,
                                            targetId = profile.user.id,
                                            desiredState = !profile.user.isFollowing,
                                        ),
                                    )
                                },
                                onChat = {
                                    if (currentUserId != null) onOpenPrivateChat(profile.user.id)
                                    else onAuthenticationContinuationRequired(
                                        communityProfileAuthenticationContinuation(
                                            AuthenticationContinuationKind.CommunityProfileOpenPrivateChat,
                                            authenticationContinuationOriginRoute,
                                            profile.user.id,
                                            targetId = profile.user.id,
                                        ),
                                    )
                                },
                            )
                        },
                        moderationActions = {
                            ProfileModerationActions(
                                userId = profile.user.id,
                                visible = !isOwnProfile && onReportProfile != null && onSetProfileBlocked != null,
                                isBlocked = profile.isBlockedByCurrentUser,
                                isUpdating = profileSafetyUpdatingUserId == profile.user.id,
                                isEnabled = profileSafetyUpdatingUserId == null,
                                strings = strings.moderation,
                                onReport = {
                                    if (currentUserId != null) pendingModeration = ProfileModerationAction.Report
                                    else onAuthenticationContinuationRequired(
                                        communityProfileAuthenticationContinuation(
                                            AuthenticationContinuationKind.CommunityProfileConfirmReport,
                                            authenticationContinuationOriginRoute,
                                            profile.user.id,
                                            targetId = profile.user.id,
                                        ),
                                    )
                                },
                                onBlock = {
                                    val desiredState = !profile.isBlockedByCurrentUser
                                    if (currentUserId != null) {
                                        pendingModeration = if (desiredState) ProfileModerationAction.Block else ProfileModerationAction.Unblock
                                    } else {
                                        onAuthenticationContinuationRequired(
                                            communityProfileAuthenticationContinuation(
                                                AuthenticationContinuationKind.CommunityProfileConfirmBlock,
                                                authenticationContinuationOriginRoute,
                                                profile.user.id,
                                                targetId = profile.user.id,
                                                desiredState = desiredState,
                                            ),
                                        )
                                    }
                                },
                            )
                        },
                        adminControls = if (currentUserIsAdmin && !isOwnProfile && onSetUserRoles != null) {
                            {
                                Spacer(Modifier.height(14.dp))
                                ProfileRoleControlsContent(
                                    user = profile.user,
                                    isUpdating = roleUpdatingUserId == profile.user.id,
                                    isEnabled = roleUpdatingUserId == null,
                                    strings = strings.roles,
                                    onSetRoles = { isAdmin, isOfficial -> onSetUserRoles(profile.user.id, isAdmin, isOfficial) },
                                )
                            }
                        } else null,
                        errorMessage = errorMessage?.let { message ->
                            {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    message,
                                    modifier = Modifier.semantics {
                                        testTag = PublicProfileErrorTestTagPrefix + profile.user.id
                                        contentDescription = PublicProfileErrorTestTagPrefix + profile.user.id
                                    },
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
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
                            modifier = Modifier.semantics {
                                testTag = PublicProfileGalleryHeaderTestTagPrefix + profile.user.id
                            },
                        )
                        if (profile.posts.isNotEmpty()) {
                            ProfilePostsPagerContent(
                                posts = profile.posts,
                                pagerState = pagerState,
                                onAddComment = { post, comment -> onAddComment(post.id, comment) },
                                modifier = Modifier.semantics {
                                    testTag = PublicProfileGalleryTestTagPrefix + profile.user.id
                                },
                                postPreview = { post, commentsCount, openComments ->
                                    CommunityProfilePostPreviewContent(
                                        post = post,
                                        commentsCount = commentsCount,
                                        canParticipate = currentUserId != null,
                                        isLikeUpdating = likingPostId == post.id,
                                        onToggleLike = { onTogglePostLike(post.id) },
                                        onOpenComments = openComments,
                                        onAuthRequired = {
                                            onAuthenticationContinuationRequired(
                                                communityProfileAuthenticationContinuation(
                                                    AuthenticationContinuationKind.CommunityProfileEnsurePostLike,
                                                    authenticationContinuationOriginRoute,
                                                    profile.user.id,
                                                    targetId = post.id,
                                                    desiredState = !post.isLikedByCurrentUser,
                                                ),
                                            )
                                        },
                                        onOpenMedia = { selectedMediaPostId = post.id },
                                        onShare = { slots.sharePost(post) },
                                        onReport = {
                                            if (currentUserId == null) onAuthenticationContinuationRequired(
                                                communityProfileAuthenticationContinuation(
                                                    AuthenticationContinuationKind.CommunityProfileReportPost,
                                                    authenticationContinuationOriginRoute,
                                                    profile.user.id,
                                                    targetId = post.id,
                                                ),
                                            )
                                            else if (!post.isReportedByCurrentUser) onReportPost(post.id)
                                        },
                                        media = { loaded, load -> slots.postMedia(this, post, loaded, load) },
                                    )
                                },
                                commentsDialog = { post, addComment, dismiss ->
                                    Box {
                                        CommunityProfileCommentsDialogContent(
                                            post = post,
                                            localComments = emptyList(),
                                            canParticipate = currentUserId != null,
                                            strings = strings.comments,
                                            onAuthRequired = onAuthRequired,
                                            onAuthenticationRequired = { draft, replyId ->
                                                onAuthenticationContinuationRequired(
                                                    communityProfileAuthenticationContinuation(
                                                        AuthenticationContinuationKind.CommunityProfileAddComment,
                                                        authenticationContinuationOriginRoute,
                                                        profile.user.id,
                                                        targetId = post.id,
                                                        relatedId = replyId,
                                                        text = draft,
                                                    ),
                                                )
                                            },
                                            createComment = { draft -> createComment(post, draft) },
                                            onAddComment = addComment,
                                            onOpenUserProfile = onOpenUserProfile,
                                            onDismiss = dismiss,
                                            translatorTrigger = slots.commentsTranslatorTrigger,
                                            translatorGateway = slots.commentsTranslationGateway,
                                            translatorStrings = slots.commentsTranslatorStrings,
                                        )
                                        if (commentingPostId == post.id) {
                                            Box(
                                                Modifier
                                                    .size(1.dp)
                                                    .semantics { testTag = PublicProfileCommentsPendingTestTagPrefix + post.id },
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                } else null,
                footer = if (showDismissButton) {
                    {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                            CompactIconButton(onClick = onBack, modifier = Modifier.semantics {
                                testTag = PublicProfileFooterBackTestTag
                                contentDescription = PublicProfileFooterBackTestTag
                            }) {
                                CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, strings.back)
                            }
                        }
                    }
                } else null,
                )
            }
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

private fun Post.imageTitle(): String =
    placeName?.takeIf { it.isNotBlank() } ?: rankingLabel.takeIf { it.isNotBlank() } ?: author.displayName

internal fun communityProfileAuthenticationContinuation(
    kind: AuthenticationContinuationKind,
    originRoute: String,
    profileId: String,
    targetId: String? = null,
    relatedId: String? = null,
    text: String? = null,
    desiredState: Boolean? = null,
): AuthenticationContinuationIntent = AuthenticationContinuationIntent(
    kind = kind,
    originRoute = originRoute,
    targetId = targetId,
    relatedId = relatedId,
    contextId = profileId,
    text = text,
    desiredState = desiredState,
)

internal sealed interface CommunityProfileAuthenticationContinuationResolution {
    data object Ignore : CommunityProfileAuthenticationContinuationResolution
    data object Wait : CommunityProfileAuthenticationContinuationResolution
    data object Clear : CommunityProfileAuthenticationContinuationResolution
    data class EnsureFollow(val userId: String, val desiredState: Boolean) : CommunityProfileAuthenticationContinuationResolution
    data class OpenPrivateChat(val userId: String) : CommunityProfileAuthenticationContinuationResolution
    data class EnsurePostLike(val postId: String, val desiredState: Boolean) : CommunityProfileAuthenticationContinuationResolution
    data class AddComment(
        val post: Post,
        val text: String,
        val replyTarget: PostComment?,
    ) : CommunityProfileAuthenticationContinuationResolution
    data class EnsurePostReported(val postId: String) : CommunityProfileAuthenticationContinuationResolution
    data class ConfirmModeration(val action: ProfileModerationAction) : CommunityProfileAuthenticationContinuationResolution
}

internal fun resolveCommunityProfileAuthenticationContinuation(
    intent: AuthenticationContinuationIntent,
    originRoute: String,
    profile: CommunityUserProfile,
    actionInProgress: Boolean,
): CommunityProfileAuthenticationContinuationResolution {
    if (intent.originRoute != originRoute) return CommunityProfileAuthenticationContinuationResolution.Ignore
    val profileKinds = setOf(
        AuthenticationContinuationKind.CommunityProfileEnsureFollow,
        AuthenticationContinuationKind.CommunityProfileOpenPrivateChat,
        AuthenticationContinuationKind.CommunityProfileEnsurePostLike,
        AuthenticationContinuationKind.CommunityProfileAddComment,
        AuthenticationContinuationKind.CommunityProfileReportPost,
        AuthenticationContinuationKind.CommunityProfileConfirmReport,
        AuthenticationContinuationKind.CommunityProfileConfirmBlock,
    )
    if (intent.kind !in profileKinds) return CommunityProfileAuthenticationContinuationResolution.Ignore
    if (intent.contextId != profile.user.id) return CommunityProfileAuthenticationContinuationResolution.Clear
    if (actionInProgress) return CommunityProfileAuthenticationContinuationResolution.Wait
    return when (intent.kind) {
        AuthenticationContinuationKind.CommunityProfileEnsureFollow -> {
            val userId = intent.targetId ?: return CommunityProfileAuthenticationContinuationResolution.Clear
            val desiredState = intent.desiredState ?: return CommunityProfileAuthenticationContinuationResolution.Clear
            val exists = sequenceOf(profile.user)
                .plus(profile.followers.asSequence())
                .plus(profile.following.asSequence())
                .any { it.id == userId }
            if (exists) CommunityProfileAuthenticationContinuationResolution.EnsureFollow(userId, desiredState)
            else CommunityProfileAuthenticationContinuationResolution.Clear
        }
        AuthenticationContinuationKind.CommunityProfileOpenPrivateChat -> {
            val userId = intent.targetId ?: return CommunityProfileAuthenticationContinuationResolution.Clear
            val exists = sequenceOf(profile.user)
                .plus(profile.followers.asSequence())
                .plus(profile.following.asSequence())
                .any { it.id == userId }
            if (exists) CommunityProfileAuthenticationContinuationResolution.OpenPrivateChat(userId)
            else CommunityProfileAuthenticationContinuationResolution.Clear
        }
        AuthenticationContinuationKind.CommunityProfileEnsurePostLike -> {
            val postId = intent.targetId ?: return CommunityProfileAuthenticationContinuationResolution.Clear
            val desiredState = intent.desiredState ?: return CommunityProfileAuthenticationContinuationResolution.Clear
            if (profile.posts.any { it.id == postId }) {
                CommunityProfileAuthenticationContinuationResolution.EnsurePostLike(postId, desiredState)
            } else CommunityProfileAuthenticationContinuationResolution.Clear
        }
        AuthenticationContinuationKind.CommunityProfileAddComment -> {
            val postId = intent.targetId ?: return CommunityProfileAuthenticationContinuationResolution.Clear
            val text = intent.text ?: return CommunityProfileAuthenticationContinuationResolution.Clear
            val post = profile.posts.firstOrNull { it.id == postId }
                ?: return CommunityProfileAuthenticationContinuationResolution.Clear
            val replyTarget = intent.relatedId?.let { replyId -> post.comments.firstOrNull { it.id == replyId } }
            if (intent.relatedId != null && replyTarget == null) {
                CommunityProfileAuthenticationContinuationResolution.Clear
            } else {
                CommunityProfileAuthenticationContinuationResolution.AddComment(post, text, replyTarget)
            }
        }
        AuthenticationContinuationKind.CommunityProfileReportPost -> intent.targetId
            ?.takeIf { postId -> profile.posts.any { it.id == postId } }
            ?.let(CommunityProfileAuthenticationContinuationResolution::EnsurePostReported)
            ?: CommunityProfileAuthenticationContinuationResolution.Clear
        AuthenticationContinuationKind.CommunityProfileConfirmReport -> {
            if (intent.targetId == profile.user.id) {
                CommunityProfileAuthenticationContinuationResolution.ConfirmModeration(ProfileModerationAction.Report)
            } else CommunityProfileAuthenticationContinuationResolution.Clear
        }
        AuthenticationContinuationKind.CommunityProfileConfirmBlock -> {
            if (intent.targetId != profile.user.id) return CommunityProfileAuthenticationContinuationResolution.Clear
            when (intent.desiredState) {
                true -> CommunityProfileAuthenticationContinuationResolution.ConfirmModeration(ProfileModerationAction.Block)
                false -> CommunityProfileAuthenticationContinuationResolution.ConfirmModeration(ProfileModerationAction.Unblock)
                null -> CommunityProfileAuthenticationContinuationResolution.Clear
            }
        }
        else -> CommunityProfileAuthenticationContinuationResolution.Ignore
    }
}
