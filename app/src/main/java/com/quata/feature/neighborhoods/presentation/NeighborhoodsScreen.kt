@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.quata.feature.neighborhoods.presentation

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.quata.core.ui.components.CompactButtonContentPadding
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataResolvedTheme
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.navigation.quataPostUrl
import com.quata.core.text.cleanTextCanvasSeedBody
import com.quata.core.text.withoutPostShortcodes
import com.quata.core.ui.components.AudioAttachmentPlayer
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.AttachmentPreview
import com.quata.core.ui.components.AttachmentThumbnail
import com.quata.core.ui.components.AttachmentViewerDialog
import com.quata.core.ui.components.ClickableProfileAvatar
import com.quata.core.ui.components.ProfileAvatarWithLoadingHalo
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.applyQuataVideoPlaybackTransform
import com.quata.core.ui.components.compactButtonMinSize
import com.quata.core.ui.components.findQuataTextureView
import com.quata.core.ui.components.openAttachmentWithDocumentReaderOrChooser
import com.quata.core.ui.components.readQuataVideoRotation
import com.quata.core.ui.textCanvasBrush
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.domain.ProfileAttachment
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.runtime.produceState
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
fun NeighborhoodsScreen(
    padding: PaddingValues,
    repository: NeighborhoodRepository,
    currentUserId: String? = null,
    openingProfileUserId: String? = null,
    onOpenConversation: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onAuthRequired: () -> Unit = {},
    viewModel: NeighborhoodsAndroidViewModel = viewModel(factory = NeighborhoodsAndroidViewModel.factory(repository))
) {
    val state by viewModel.uiState.collectAsState()
    var selectedCommunity by rememberSaveable { mutableStateOf<String?>(null) }
    var neighborhoodQuery by rememberSaveable { mutableStateOf("") }
    val canParticipate = currentUserId != null
    val communityForDialog = state.communities.firstOrNull { it.name == selectedCommunity }
    DisposableEffect(viewModel) {
        viewModel.startObservingCommunities()
        onDispose { viewModel.stopObservingCommunities() }
    }

    if (communityForDialog != null) {
        NeighborhoodUsersScreen(
            padding = padding,
            community = communityForDialog,
            currentUserId = currentUserId,
            isOpeningChat = state.isOpeningChat,
            openingPrivateChatUserId = state.openingPrivateChatUserId,
            openingProfileUserId = openingProfileUserId,
            followingUserId = state.followingUserId,
            onBack = { selectedCommunity = null },
            onFollowUser = { user ->
                if (canParticipate) viewModel.toggleFollowUser(user.id) else onAuthRequired()
            },
            onOpenProfile = { onOpenUserProfile(it.id) },
            onOpenPrivateChat = { user ->
                if (canParticipate) {
                    viewModel.openPrivateChat(user.id) { conversationId ->
                        selectedCommunity = null
                        onOpenConversation(conversationId)
                    }
                } else {
                    onAuthRequired()
                }
            }
        )
        return
    }

    val context = LocalContext.current
    NeighborhoodListContent(
        padding = padding,
        communities = state.communities,
        query = neighborhoodQuery,
        isLoading = state.isLoading,
        error = state.error,
        currentUserId = currentUserId,
        openingNeighborhood = state.openingChatNeighborhood,
        strings = NeighborhoodListStrings(
            title = stringResource(R.string.neighborhoods_open_community),
            searchPlaceholder = stringResource(R.string.neighborhoods_subtitle),
            loading = stringResource(R.string.neighborhoods_loading),
            oneUser = stringResource(R.string.neighborhoods_one_user),
            users = { count -> context.getString(R.string.neighborhoods_user_count, count) },
            oneMessage = stringResource(R.string.neighborhoods_one_message),
            messages = { count -> context.getString(R.string.neighborhoods_message_count, count) },
            viewUsers = stringResource(R.string.neighborhoods_view_users),
            openChat = stringResource(R.string.neighborhoods_open_chat),
            timeLabel = { communityTimeLabel(context, it) }
        ),
        onQueryChange = { neighborhoodQuery = it },
        onShowUsers = { selectedCommunity = it.name },
        onOpenChat = { community ->
            if (canParticipate) viewModel.openChat(community.name, onOpenConversation) else onAuthRequired()
        }
    )
}

@Composable
private fun NeighborhoodUsersScreen(
    padding: PaddingValues,
    community: NeighborhoodCommunity,
    currentUserId: String?,
    isOpeningChat: Boolean,
    openingPrivateChatUserId: String?,
    openingProfileUserId: String?,
    followingUserId: String?,
    onBack: () -> Unit,
    onFollowUser: (NeighborhoodUser) -> Unit,
    onOpenProfile: (NeighborhoodUser) -> Unit,
    onOpenPrivateChat: (NeighborhoodUser) -> Unit
) {
    val context = LocalContext.current
    NeighborhoodUsersContent(
        padding = padding,
        community = community,
        currentUserId = currentUserId,
        isOpeningChat = isOpeningChat,
        openingPrivateChatUserId = openingPrivateChatUserId,
        openingProfileUserId = openingProfileUserId,
        followingUserId = followingUserId,
        strings = NeighborhoodUsersStrings(
            title = { name -> context.getString(R.string.neighborhoods_users_title, name) },
            subtitle = stringResource(R.string.neighborhoods_users_subtitle),
            backContentDescription = stringResource(R.string.common_back),
            memberCount = { count -> if (count == 1) context.getString(R.string.neighborhoods_one_user) else context.getString(R.string.neighborhoods_user_count, count) },
            row = NeighborhoodUserRowStrings(stringResource(R.string.common_follow), stringResource(R.string.common_following), stringResource(R.string.common_chat))
        ),
        avatar = { user, isLoading, onClick ->
            ClickableProfileAvatar(
                name = user.displayName,
                avatarUrl = user.avatarUrl,
                isOfficial = user.isOfficial,
                profileId = user.id,
                isLoading = isLoading,
                onClick = onClick,
                modifier = Modifier.size(48.dp)
            )
        },
        onBack = onBack,
        onFollowUser = onFollowUser,
        onOpenProfile = onOpenProfile,
        onOpenPrivateChat = onOpenPrivateChat
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityProfileScreen(
    padding: PaddingValues,
    profile: CommunityUserProfile,
    currentUserId: String? = null,
    isOpeningChat: Boolean = false,
    isRefreshingProfile: Boolean = false,
    followingUserId: String? = null,
    roleUpdatingUserId: String? = null,
    currentUserIsAdmin: Boolean = false,
    chatError: String? = null,
    onAuthRequired: () -> Unit = {},
    onReportPost: (String) -> Unit = {},
    onReportProfile: (String) -> Unit = {},
    onBlockProfile: (String) -> Unit = {},
    onBack: () -> Unit,
    onFollow: () -> Unit,
    onFollowUser: (String) -> Unit = { onFollow() },
    onSetUserRoles: (String, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onOpenPrivateChat: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit = {},
    openingProfileUserId: String? = null
) {
    val isOwnProfile = profile.user.id == currentUserId
    val isFollowingLoading = followingUserId == profile.user.id
    var showPosts by rememberSaveable(profile.user.id) { mutableStateOf(false) }
    var userListTitle by rememberSaveable(profile.user.id) { mutableStateOf<String?>(null) }
    var selectedAttachment by remember { mutableStateOf<AttachmentPreview?>(null) }
    var pendingProfileAction by remember { mutableStateOf<ProfileModerationAction?>(null) }
    val context = LocalContext.current
    val template = quataTheme()
    val avatarUrl = profile.user.avatarUrl?.trim()?.takeIf { it.isNotBlank() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(showPosts) {
        if (showPosts) listState.animateScrollToItem(2)
    }
    ProfileModerationConfirmation(
        action = pendingProfileAction,
        strings = ProfileModerationConfirmationStrings(
            reportTitle = stringResource(R.string.moderation_report_title),
            blockTitle = stringResource(R.string.moderation_block_title),
            reportMessage = stringResource(R.string.moderation_report_profile_confirm),
            blockMessage = stringResource(R.string.moderation_block_profile_confirm),
            cancel = stringResource(R.string.common_cancel),
            report = stringResource(R.string.moderation_report),
            block = stringResource(R.string.moderation_block)
        ),
        onDismiss = { pendingProfileAction = null },
        onConfirm = { action ->
            pendingProfileAction = null
            if (action == ProfileModerationAction.Block) onBlockProfile(profile.user.id)
            else onReportProfile(profile.user.id)
        }
    )
    ModalBottomSheet(
        onDismissRequest = onBack,
        sheetState = sheetState,
        containerColor = template.colors.background,
        contentColor = template.colors.textPrimary
    ) {
        if (userListTitle != null) {
            ProfileUsersListContent(
                title = if (userListTitle == "followers") {
                    stringResource(R.string.neighborhoods_followers_of, profile.user.displayName)
                } else {
                    stringResource(R.string.neighborhoods_following_of, profile.user.displayName)
                },
                users = if (userListTitle == "followers") profile.followers else profile.following,
                currentUserId = currentUserId,
                onBack = { userListTitle = null },
                onFollowUser = { user ->
                    if (currentUserId == null) onAuthRequired() else onFollowUser(user.id)
                },
                onOpenProfile = { user -> onOpenUserProfile(user.id) },
                onOpenPrivateChat = { user ->
                    if (currentUserId == null) onAuthRequired() else onOpenPrivateChat(user.id)
                },
                isOpeningChat = isOpeningChat,
                openingProfileUserId = openingProfileUserId,
                followingUserId = followingUserId
            )
        } else {
            CommunityProfileDetailsContent(
                listState = listState,
                modifier = Modifier.heightIn(max = 780.dp),
                header = {
                    CommunityProfileHeaderContent(
                        displayName = profile.user.displayName,
                        neighborhood = profile.user.neighborhood,
                        avatar = {
                        ProfileAvatar(
                            profile.user,
                            Modifier
                                .size(92.dp)
                                .clickable(enabled = avatarUrl != null) {
                                    val imageUri = avatarUrl ?: return@clickable
                                    selectedAttachment = AttachmentPreview(
                                        name = profile.user.displayName,
                                        uri = imageUri,
                                        mimeType = "image/jpeg"
                                    )
                                },
                            isLoading = isRefreshingProfile
                        )
                        },
                        kpis = {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        ProfileKpi(profile.user.postsCount, stringResource(R.string.neighborhoods_posts), Modifier.weight(1f), onClick = { showPosts = true })
                        ProfileKpi(profile.user.followersCount, stringResource(R.string.neighborhoods_followers), Modifier.weight(1f), onClick = {
                            userListTitle = "followers"
                        })
                        ProfileKpi(profile.user.followingCount, stringResource(R.string.neighborhoods_following), Modifier.weight(1f), onClick = {
                            userListTitle = "following"
                        })
                            }
                        },
                        primaryActions = {
                            ProfilePrimaryActions(
                        isOwnProfile = isOwnProfile,
                        isFollowing = profile.user.isFollowing,
                        isFollowingLoading = isFollowingLoading,
                        isOpeningChat = isOpeningChat,
                        strings = ProfileActionStrings(
                            follow = stringResource(R.string.common_follow),
                            following = stringResource(R.string.common_following),
                            chat = stringResource(R.string.common_chat)
                        ),
                        onFollow = { if (currentUserId == null) onAuthRequired() else onFollowUser(profile.user.id) },
                        onChat = { if (currentUserId == null) onAuthRequired() else onOpenPrivateChat(profile.user.id) }
                            )
                        },
                        moderationActions = {
                            ProfileModerationActions(
                        visible = !isOwnProfile,
                        strings = ProfileModerationStrings(
                            report = stringResource(R.string.moderation_report),
                            block = stringResource(R.string.moderation_block)
                        ),
                        onReport = {
                            if (currentUserId == null) onAuthRequired() else pendingProfileAction = ProfileModerationAction.Report
                        },
                        onBlock = {
                            if (currentUserId == null) onAuthRequired() else pendingProfileAction = ProfileModerationAction.Block
                        }
                            )
                        },
                        adminControls = if (currentUserIsAdmin && !isOwnProfile) {
                            {
                                Spacer(Modifier.height(14.dp))
                                ProfileRoleControlsContent(
                            user = profile.user,
                            isUpdating = roleUpdatingUserId == profile.user.id,
                            strings = ProfileRoleStrings(
                                title = stringResource(R.string.profile_admin_controls_title),
                                admin = stringResource(R.string.profile_admin_role),
                                official = stringResource(R.string.profile_official_role)
                            ),
                            onSetRoles = { isAdmin, isOfficial ->
                                onSetUserRoles(profile.user.id, isAdmin, isOfficial)
                            }
                                )
                            }
                        } else null,
                        errorMessage = chatError?.let { error ->
                            {
                                Spacer(Modifier.height(10.dp))
                                Text(error, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                },
                attachments = {
                    ProfileAttachmentsSection(
                        attachments = profile.attachments,
                        onOpenAttachment = { attachment ->
                            val preview = attachment.toAttachmentPreview()
                            if (preview.isMedia) {
                                selectedAttachment = preview
                            } else {
                                context.openAttachmentWithDocumentReaderOrChooser(
                                    attachment = preview,
                                    isDarkMode = template.resolvedTheme == QuataResolvedTheme.Dark
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(18.dp))
                },
                gallery = if (showPosts) {
                    {
                        val pagerState = rememberPagerState(pageCount = { profile.posts.size })
                        ProfileGalleryHeader(
                            title = stringResource(R.string.neighborhoods_photos_videos),
                            currentIndex = (pagerState.currentPage + 1).takeIf { profile.posts.isNotEmpty() },
                            total = profile.posts.size,
                            emptyLabel = stringResource(R.string.neighborhoods_no_visible_posts).takeIf { profile.posts.isEmpty() }
                        )
                        if (profile.posts.isNotEmpty()) {
                            ProfilePostsPager(
                                posts = profile.posts,
                                pagerState = pagerState,
                                canParticipate = currentUserId != null,
                                onAuthRequired = onAuthRequired,
                                onReportPost = onReportPost
                            )
                        }
                    }
                } else null,
            )
        }
    }
    selectedAttachment?.let { attachment ->
        AttachmentViewerDialog(
            attachment = attachment,
            onDismiss = { selectedAttachment = null }
        )
    }
}

@Composable
private fun ProfileAttachmentsSection(
    attachments: List<ProfileAttachment>,
    onOpenAttachment: (ProfileAttachment) -> Unit
) {
    ProfileAttachmentsContent(
        attachments = attachments,
        strings = ProfileAttachmentsStrings(
            title = stringResource(R.string.neighborhoods_attachments),
            empty = stringResource(R.string.neighborhoods_no_attachments)
        ),
        attachmentItem = { attachment -> ProfileAttachmentRow(attachment, onOpen = { onOpenAttachment(attachment) }) }
    )
}

@Composable
private fun ProfileAttachmentRow(attachment: ProfileAttachment, onOpen: () -> Unit) {
    val template = quataTheme()
    val preview = attachment.toAttachmentPreview()
    if (preview.isAudio) {
        AudioAttachmentPlayer(
            attachment = preview,
            textColor = template.colors.textPrimary
        )
        return
    }
    ProfileAttachmentCardContent(
        name = attachment.name,
        senderName = attachment.senderName,
        thumbnail = { AttachmentThumbnail(preview, modifier = Modifier.size(58.dp)) },
        onClick = onOpen
    )
}

private fun ProfileAttachment.toAttachmentPreview(): AttachmentPreview =
    AttachmentPreview(name = name, uri = uri, mimeType = mimeType)


@Composable
private fun NeighborhoodUserRow(
    user: NeighborhoodUser,
    isOwnUser: Boolean,
    isFollowingLoading: Boolean,
    isOpeningChat: Boolean,
    isProfileLoading: Boolean,
    onFollowUser: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenPrivateChat: () -> Unit
) {
    NeighborhoodUserRowContent(
        user = user,
        isOwnUser = isOwnUser,
        isFollowingLoading = isFollowingLoading,
        isOpeningChat = isOpeningChat,
        strings = NeighborhoodUserRowStrings(
            follow = stringResource(R.string.common_follow),
            following = stringResource(R.string.common_following),
            chat = stringResource(R.string.common_chat)
        ),
        avatar = {
            ClickableProfileAvatar(
                name = user.displayName,
                avatarUrl = user.avatarUrl,
                profileId = user.id,
                isOfficial = user.isOfficial,
                isLoading = isProfileLoading,
                onClick = onOpenProfile,
                modifier = Modifier.size(48.dp)
            )
        },
        onFollowUser = onFollowUser,
        onOpenPrivateChat = onOpenPrivateChat
    )
}

@Composable
private fun ProfileUsersListContent(
    title: String,
    users: List<NeighborhoodUser>,
    currentUserId: String?,
    onBack: () -> Unit,
    onFollowUser: (NeighborhoodUser) -> Unit,
    onOpenProfile: (NeighborhoodUser) -> Unit,
    onOpenPrivateChat: (NeighborhoodUser) -> Unit,
    isOpeningChat: Boolean,
    openingProfileUserId: String?,
    followingUserId: String?
) {
    ProfileUsersListCommon(title, users, currentUserId, isOpeningChat, openingProfileUserId, followingUserId, NeighborhoodUserRowStrings(stringResource(R.string.common_follow), stringResource(R.string.common_following), stringResource(R.string.common_chat)), stringResource(R.string.common_back), { user, loading, click -> ClickableProfileAvatar(user.displayName, user.avatarUrl, user.isOfficial, user.id, loading, click, Modifier.size(48.dp)) }, onBack, onFollowUser, onOpenProfile, onOpenPrivateChat)
}

@Composable
private fun ProfileAvatar(user: NeighborhoodUser, modifier: Modifier = Modifier, isLoading: Boolean = false) {
    ProfileAvatarWithLoadingHalo(
        name = user.displayName,
        avatarUrl = user.avatarUrl,
        profileId = user.id,
        isOfficial = user.isOfficial,
        isLoading = isLoading,
        modifier = modifier
    )
}

@Composable
private fun ProfileKpi(value: Int, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) =
    ProfileKpiContent(value, label, modifier, onClick)

@Composable
private fun ProfilePostsPager(
    posts: List<Post>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    canParticipate: Boolean,
    onAuthRequired: () -> Unit,
    onReportPost: (String) -> Unit
) {
    val context = LocalContext.current
    ProfilePostsPagerContent(
        posts = posts,
        pagerState = pagerState,
        postPreview = { post, commentsCount, onOpenComments ->
            ProfilePostPreview(
                post = post,
                commentsCount = commentsCount,
                canParticipate = canParticipate,
                onOpenComments = onOpenComments,
                onAuthRequired = onAuthRequired,
                onShare = { context.shareProfilePost(post) },
                onReport = {
                    if (!post.isReportedByCurrentUser) {
                        if (canParticipate) {
                            onReportPost(post.id)
                            Toast.makeText(context, context.getString(R.string.feed_report_success), Toast.LENGTH_SHORT).show()
                        } else {
                            onAuthRequired()
                        }
                    }
                },
            )
        },
        commentsDialog = { post, localComments, onAddComment, onDismiss ->
            ProfileCommentsDialog(
                post = post,
                localComments = localComments,
                canParticipate = canParticipate,
                onAuthRequired = onAuthRequired,
                onAddComment = onAddComment,
                onDismiss = onDismiss,
            )
        },
    )
}

@Composable
private fun ProfilePostPreview(
    post: Post,
    commentsCount: Int,
    canParticipate: Boolean,
    onOpenComments: () -> Unit,
    onAuthRequired: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit
) {
    var liked by rememberSaveable(post.id, post.isLikedByCurrentUser) { mutableStateOf(post.isLikedByCurrentUser) }
    var isVideoLoaded by rememberSaveable(post.id) { mutableStateOf(false) }
    val likeDelta = when {
        liked && !post.isLikedByCurrentUser -> 1
        !liked && post.isLikedByCurrentUser -> -1
        else -> 0
    }
    val likes = (post.likesCount + likeDelta).coerceAtLeast(0)
    val cleanPostText = remember(post.text) { post.text.withoutPostShortcodes() }
    val seedText = remember(post.text) { post.text.cleanTextCanvasSeedBody() }
    val mediaSeed = post.imageUrl ?: post.videoUrl ?: seedText
    ProfilePostPreviewFrameContent(
        backgroundSeed = mediaSeed,
        media = {
        when {
            post.imageUrl != null -> AsyncImage(
                model = post.imageUrl,
                contentDescription = post.imageTitle(),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(430.dp)
            )
            post.videoUrl != null && isVideoLoaded -> ProfileVideoPlayer(post.videoUrl.orEmpty())
            post.videoUrl != null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp)
                    .clickable { isVideoLoaded = true },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.42f)),
                    contentAlignment = Alignment.Center
                ) {
                    CompactIcon(Icons.Filled.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(50.dp))
                }
            }
            else -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp)
                    .background(textCanvasBrush(seedText))
                    .padding(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(cleanPostText, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            }
        }
        },
        metadata = {
                Text(post.author.displayName, fontWeight = FontWeight.ExtraBold, color = Color.White)
                val subtitle = when {
                    post.imageUrl != null && post.videoUrl == null -> post.placeName.orEmpty()
                    post.videoUrl != null -> cleanPostText
                    else -> ""
                }
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = Color.White.copy(alpha = 0.82f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
        },
        actionRail = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniFeedAction(
                    icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    count = likes.toString(),
                    onClick = {
                        if (canParticipate) {
                            liked = !liked
                        } else {
                            onAuthRequired()
                        }
                    }
                )
                MiniFeedAction(Icons.Filled.ChatBubble, commentsCount.toString(), onClick = onOpenComments)
                MiniFeedAction(Icons.Filled.Share, null, onClick = onShare)
                MiniFeedAction(
                    icon = Icons.Filled.Flag,
                    count = null,
                    tint = if (post.isReportedByCurrentUser) QuataOrange else Color.White,
                    onClick = onReport
                )
            }
        }
    )
}

@Composable
private fun MiniFeedAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: String?,
    tint: Color = Color.White,
    onClick: () -> Unit
) = ProfilePostActionContent(icon, count, tint, onClick)

@Composable
private fun ProfileVideoPlayer(videoUrl: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val playbackRotation by produceState(initialValue = 0, videoUrl) {
        value = readQuataVideoRotation(context, Uri.parse(videoUrl))
    }
    val player = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                player.playWhenReady = false
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(430.dp),
        factory = { viewContext ->
            (LayoutInflater.from(viewContext)
                .inflate(R.layout.quata_attachment_player_texture, null, false) as PlayerView).apply {
                this.player = player
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { playerView ->
            playerView.useController = true
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            if (playerView.player !== player) {
                playerView.player = player
            }
            playerView.findQuataTextureView()?.applyQuataVideoPlaybackTransform(playbackRotation)
        }
    )
}

@Composable
private fun ProfileCommentsDialog(
    post: Post,
    localComments: List<PostComment>,
    canParticipate: Boolean,
    onAuthRequired: () -> Unit,
    onAddComment: (PostComment) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by rememberSaveable(post.id) { mutableStateOf("") }
    val template = quataTheme()
    val comments = post.comments + localComments
    val currentUserName = stringResource(R.string.comments_you)
    val nowLabel = stringResource(R.string.common_now)
    CommunityProfileCommentsPanelContent(
        comments = comments,
        title = stringResource(R.string.feed_comments),
        closeContentDescription = stringResource(R.string.common_close),
        onDismiss = onDismiss,
        commentRow = { comment ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = template.colors.surfaceAlt),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, template.colors.divider, RoundedCornerShape(16.dp))
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(comment.authorName, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(comment.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
        },
        input = {
            CommunityProfileCommentInputContent(
                value = draft,
                placeholder = stringResource(R.string.comments_placeholder),
                sendLabel = stringResource(R.string.common_send),
                onValueChange = { draft = it },
                onSend = {
                            if (canParticipate) {
                                onAddComment(
                                    PostComment(
                                        id = "profile_${post.id}_${System.currentTimeMillis()}",
                                        authorName = currentUserName,
                                        message = draft.trim(),
                                        timestamp = nowLabel
                                    )
                                )
                                draft = ""
                            } else {
                                onAuthRequired()
                            }
                }
            )
        }
    )
}

private fun android.content.Context.shareProfilePost(post: Post) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, quataPostUrl(post.id))
    }
    startActivity(Intent.createChooser(sendIntent, getString(R.string.neighborhoods_share_post)))
}

private fun Post.imageTitle(): String =
    placeName?.takeIf { it.isNotBlank() } ?: rankingLabel.takeIf { it.isNotBlank() } ?: "Qüata"

private fun communityTimeLabel(context: android.content.Context, lastMessageAtMillis: Long?): String {
    if (lastMessageAtMillis == null) return context.getString(R.string.common_new)
    val zone = ZoneId.systemDefault()
    val messageDate = Instant.ofEpochMilli(lastMessageAtMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val days = ChronoUnit.DAYS.between(messageDate, today)
    return when {
        days == 0L -> DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(lastMessageAtMillis).atZone(zone))
        days == 1L -> context.getString(R.string.common_yesterday)
        days < 7L -> context.getString(R.string.neighborhoods_time_days, days.toInt())
        days < 30L -> {
            val weeks = (days / 7).coerceAtLeast(1)
            if (weeks == 1L) context.getString(R.string.neighborhoods_time_one_week) else context.getString(R.string.neighborhoods_time_weeks, weeks.toInt())
        }
        days < 365L -> {
            val months = (days / 30).coerceAtLeast(1)
            if (months == 1L) context.getString(R.string.neighborhoods_time_one_month) else context.getString(R.string.neighborhoods_time_months, months.toInt())
        }
        else -> {
            val years = (days / 365).coerceAtLeast(1)
            if (years == 1L) context.getString(R.string.neighborhoods_time_one_year) else context.getString(R.string.neighborhoods_time_years, years.toInt())
        }
    }
}
