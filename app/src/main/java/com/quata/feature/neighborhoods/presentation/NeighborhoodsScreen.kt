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
import androidx.compose.material3.CircularProgressIndicator
import com.quata.core.ui.components.CompactButtonContentPadding
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
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
    val context = LocalContext.current
    NeighborhoodsScreenHost(
        model = viewModel,
        currentUserId = currentUserId,
        padding = padding,
        onOpenConversation = onOpenConversation,
        onOpenUserProfile = onOpenUserProfile,
        onAuthRequired = onAuthRequired,
        openingProfileUserId = openingProfileUserId,
        avatar = { user, isLoading, onClick ->
            ClickableProfileAvatar(user.displayName, user.avatarUrl, user.isOfficial, user.id, isLoading, onClick, Modifier.size(48.dp))
        },
        strings = NeighborhoodsScreenStrings(
            list = NeighborhoodListStrings(
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
            members = NeighborhoodUsersStrings(
                title = { name -> context.getString(R.string.neighborhoods_users_title, name) },
                subtitle = stringResource(R.string.neighborhoods_users_subtitle),
                backContentDescription = stringResource(R.string.common_back),
                memberCount = { count -> if (count == 1) context.getString(R.string.neighborhoods_one_user) else context.getString(R.string.neighborhoods_user_count, count) },
                row = NeighborhoodUserRowStrings(stringResource(R.string.common_follow), stringResource(R.string.common_following), stringResource(R.string.common_chat)),
            ),
        ),
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
    val usersTitleFormat = stringResource(R.string.neighborhoods_users_title)
    val oneUser = stringResource(R.string.neighborhoods_one_user)
    val userCountFormat = stringResource(R.string.neighborhoods_user_count)
    NeighborhoodUsersContent(
        padding = padding,
        community = community,
        currentUserId = currentUserId,
        isOpeningChat = isOpeningChat,
        openingPrivateChatUserId = openingPrivateChatUserId,
        openingProfileUserId = openingProfileUserId,
        followingUserId = followingUserId,
        strings = NeighborhoodUsersStrings(
            title = { name -> usersTitleFormat.format(name) },
            subtitle = stringResource(R.string.neighborhoods_users_subtitle),
            backContentDescription = stringResource(R.string.common_back),
            memberCount = { count -> if (count == 1) oneUser else userCountFormat.format(count) },
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
    onTogglePostLike: (String) -> Unit = {},
    onAddPostComment: (String, String) -> Unit = { _, _ -> },
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
    var selectedAttachment by remember { mutableStateOf<AttachmentPreview?>(null) }
    val context = LocalContext.current
    val template = quataTheme()
    val followersOfFormat = stringResource(R.string.neighborhoods_followers_of)
    val followingOfFormat = stringResource(R.string.neighborhoods_following_of)
    CommunityProfileRoot(
        profile = profile,
        currentUserId = currentUserId,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = template.colors.background,
        contentColor = template.colors.textPrimary,
        strings = CommunityProfileStrings(
            posts = stringResource(R.string.neighborhoods_posts),
            followers = stringResource(R.string.neighborhoods_followers),
            following = stringResource(R.string.neighborhoods_following),
            followersOf = { followersOfFormat.format(it) },
            followingOf = { followingOfFormat.format(it) },
            gallery = stringResource(R.string.neighborhoods_photos_videos),
            noPosts = stringResource(R.string.neighborhoods_no_visible_posts),
            attachments = ProfileAttachmentsStrings(stringResource(R.string.neighborhoods_attachments), stringResource(R.string.neighborhoods_no_attachments)),
            actions = ProfileActionStrings(stringResource(R.string.common_follow), stringResource(R.string.common_following), stringResource(R.string.common_chat)),
            moderation = ProfileModerationStrings(stringResource(R.string.moderation_report), stringResource(R.string.moderation_block)),
            moderationConfirmation = ProfileModerationConfirmationStrings(
                stringResource(R.string.moderation_report_title), stringResource(R.string.moderation_block_title),
                stringResource(R.string.moderation_report_profile_confirm), stringResource(R.string.moderation_block_profile_confirm),
                stringResource(R.string.common_cancel), stringResource(R.string.moderation_report), stringResource(R.string.moderation_block)
            ),
            roles = ProfileRoleStrings(stringResource(R.string.profile_admin_controls_title), stringResource(R.string.profile_admin_role), stringResource(R.string.profile_official_role)),
            userActions = NeighborhoodUserRowStrings(stringResource(R.string.common_follow), stringResource(R.string.common_following), stringResource(R.string.common_chat)),
            back = stringResource(R.string.common_back),
            runtime = defaultCommunityProfileStrings(java.util.Locale.getDefault().language).runtime,
        ),
        isOpeningChat = isOpeningChat,
        isRefreshing = isRefreshingProfile,
        followingUserId = followingUserId,
        openingProfileUserId = openingProfileUserId,
        roleUpdatingUserId = roleUpdatingUserId,
        currentUserIsAdmin = currentUserIsAdmin,
        error = chatError,
        onDismiss = onBack,
        onAuthRequired = onAuthRequired,
        onFollowUser = onFollowUser,
        onOpenPrivateChat = onOpenPrivateChat,
        onOpenUserProfile = onOpenUserProfile,
        onReportProfile = onReportProfile,
        onBlockProfile = onBlockProfile,
        onSetRoles = onSetUserRoles,
        onAddPostComment = onAddPostComment,
        onProfileAvatarClick = { user ->
            user.avatarUrl?.takeIf(String::isNotBlank)?.let { url ->
                selectedAttachment = AttachmentPreview(user.displayName, url, "image/jpeg")
            }
        },
        avatar = { user, loading, role, click ->
            ProfileAvatar(
                user,
                Modifier.size(role.sizeDp.dp).let { modifier ->
                    if (click == null) modifier else modifier.clickable(onClick = click)
                },
                loading
            )
        },
        attachmentItem = { attachment ->
            ProfileAttachmentRow(attachment) {
                val preview = attachment.toAttachmentPreview()
                if (preview.isMedia) selectedAttachment = preview
                else context.openAttachmentWithDocumentReaderOrChooser(preview, template.resolvedTheme == QuataResolvedTheme.Dark)
            }
        },
        postPreview = { post, commentsCount, _, openComments ->
            ProfilePostPreview(
                post, commentsCount, currentUserId != null, openComments, onAuthRequired,
                { context.shareProfilePost(post) },
                { if (currentUserId == null) onAuthRequired() else onReportPost(post.id) },
                { if (currentUserId == null) onAuthRequired() else onTogglePostLike(post.id) },
            )
        },
        commentsDialog = { post, comments, add, dismiss ->
            ProfileCommentsDialog(post, comments, currentUserId, chatError, onAuthRequired, add, dismiss)
        },
    )
    selectedAttachment?.let { attachment -> AttachmentViewerDialog(attachment) { selectedAttachment = null } }
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
    ProfileAttachmentRowContent(
        attachment = attachment,
        audioPlayer = {
            AudioAttachmentPlayer(
                attachment = preview,
                textColor = template.colors.textPrimary
            )
        },
        thumbnail = { AttachmentThumbnail(preview, modifier = Modifier.size(58.dp)) },
        onOpen = onOpen,
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
    currentUserId: String?,
    errorMessage: String?,
    onAuthRequired: () -> Unit,
    onReportPost: (String) -> Unit,
    onTogglePostLike: (String) -> Unit,
    onAddPostComment: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val reportSuccess = stringResource(R.string.feed_report_success)
    ProfilePostsPagerContent(
        posts = posts,
        pagerState = pagerState,
        postPreview = { post, commentsCount, _, onOpenComments ->
            ProfilePostPreview(
                post = post,
                commentsCount = commentsCount,
                canParticipate = currentUserId != null,
                onOpenComments = onOpenComments,
                onAuthRequired = onAuthRequired,
                onShare = { context.shareProfilePost(post) },
                onReport = {
                    if (!post.isReportedByCurrentUser) {
                        if (currentUserId != null) {
                            onReportPost(post.id)
                            Toast.makeText(context, reportSuccess, Toast.LENGTH_SHORT).show()
                        } else {
                            onAuthRequired()
                        }
                    }
                },
                onToggleLike = { if (currentUserId != null) onTogglePostLike(post.id) else onAuthRequired() },
            )
        },
        commentsDialog = { post, localComments, onAddComment, onDismiss ->
            ProfileCommentsDialog(
                post = post,
                comments = localComments,
                currentUserId = currentUserId,
                errorMessage = errorMessage,
                onAuthRequired = onAuthRequired,
                onAddComment = onAddComment,
                onDismiss = onDismiss,
            )
        },
        onAddComment = onAddPostComment,
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
    onReport: () -> Unit,
    onToggleLike: () -> Unit,
) {
    CommunityProfilePostPreviewContent(
        post = post,
        commentsCount = commentsCount,
        canParticipate = canParticipate,
        onOpenComments = onOpenComments,
        onAuthRequired = onAuthRequired,
        onReportAuthRequired = onAuthRequired,
        onShare = onShare,
        onReport = onReport,
        onToggleLike = onToggleLike,
        media = { isVideoLoaded, onLoadVideo ->
            when {
            post.imageUrl != null -> AsyncImage(
                model = post.imageUrl,
                contentDescription = post.imageTitle(),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(430.dp),
            )
            post.videoUrl != null && isVideoLoaded -> ProfileVideoPlayer(post.videoUrl.orEmpty())
            post.videoUrl != null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp)
                    .clickable(onClick = onLoadVideo),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.42f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CompactIcon(Icons.Filled.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(50.dp))
                }
            }
            else -> Unit
        }
        },
    )
}

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
    comments: List<PostComment>,
    currentUserId: String?,
    errorMessage: String?,
    onAuthRequired: () -> Unit,
    onAddComment: (String) -> Unit,
    onDismiss: () -> Unit
) {
    CommunityProfileCommentsDialogContent(
        post = post,
        comments = comments,
        currentUserId = currentUserId,
        strings = CommunityProfileCommentsDialogStrings(
            title = stringResource(R.string.feed_comments),
            closeContentDescription = stringResource(R.string.common_close),
            placeholder = stringResource(R.string.comments_placeholder),
            sendLabel = stringResource(R.string.common_send),
        ),
        errorMessage = errorMessage,
        onAuthRequired = onAuthRequired,
        onSubmitComment = onAddComment,
        onDismiss = onDismiss,
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
