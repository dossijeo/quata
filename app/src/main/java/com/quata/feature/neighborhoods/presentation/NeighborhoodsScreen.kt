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
import androidx.compose.ui.platform.LocalResources
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
import com.quata.core.ui.components.CommunityEmojiLabels
import com.quata.core.ui.components.ProfileAvatarWithLoadingHalo
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.applyQuataVideoPlaybackTransform
import com.quata.core.ui.components.compactButtonMinSize
import com.quata.core.ui.components.findQuataTextureView
import com.quata.core.ui.components.openAttachmentWithDocumentReaderOrChooser
import com.quata.core.ui.components.readQuataVideoRotation
import com.quata.core.ui.textCanvasBrush
import com.quata.core.translation.FangTranslatorIconButton
import com.quata.core.translation.LocalQuataTranslatorModeController
import com.quata.designsystem.translation.QuataTranslatorOverlaySource
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
    val resources = LocalResources.current
    NeighborhoodsScreenHost(
        currentUserId = currentUserId,
        strings = NeighborhoodsScreenStrings(
            list = NeighborhoodListStrings(
                title = stringResource(R.string.neighborhoods_open_community),
                searchPlaceholder = stringResource(R.string.neighborhoods_subtitle),
                loading = stringResource(R.string.neighborhoods_loading),
                empty = stringResource(R.string.neighborhoods_empty),
                noResults = stringResource(R.string.neighborhoods_no_results),
                oneUser = stringResource(R.string.neighborhoods_one_user),
                users = { count -> resources.getString(R.string.neighborhoods_user_count, count) },
                oneMessage = stringResource(R.string.neighborhoods_one_message),
                messages = { count -> resources.getString(R.string.neighborhoods_message_count, count) },
                viewUsers = stringResource(R.string.neighborhoods_view_users),
                openChat = stringResource(R.string.neighborhoods_open_chat),
                timeLabel = { communityTimeLabel(context, it) },
            ),
            members = NeighborhoodUsersStrings(
                title = { name -> resources.getString(R.string.neighborhoods_users_title, name) },
                subtitle = stringResource(R.string.neighborhoods_users_subtitle),
                backContentDescription = stringResource(R.string.common_back),
                memberCount = { count ->
                    if (count == 1) resources.getString(R.string.neighborhoods_one_user)
                    else resources.getString(R.string.neighborhoods_user_count, count)
                },
                empty = stringResource(R.string.neighborhoods_no_members),
                row = NeighborhoodUserRowStrings(
                    stringResource(R.string.common_follow),
                    stringResource(R.string.common_following),
                    stringResource(R.string.common_chat),
                ),
            ),
        ),
        avatar = { user, isLoading, onClick ->
            ClickableProfileAvatar(
                name = user.displayName,
                avatarUrl = user.avatarUrl,
                isOfficial = user.isOfficial,
                profileId = user.id,
                isLoading = isLoading,
                onClick = onClick,
                modifier = Modifier.size(48.dp),
            )
        },
        onOpenConversation = onOpenConversation,
        onOpenUserProfile = onOpenUserProfile,
        onAuthRequired = onAuthRequired,
        padding = padding,
        model = viewModel,
        openingProfileUserId = openingProfileUserId,
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
    commentingPostId: String? = null,
    likingPostId: String? = null,
    profileSafetyUpdatingUserId: String? = null,
    currentUserIsAdmin: Boolean = false,
    chatError: String? = null,
    onAuthRequired: () -> Unit = {},
    onReportPost: (String) -> Unit = {},
    onTogglePostLike: (String) -> Unit = {},
    onReportProfile: (String) -> Unit = {},
    onSetProfileBlocked: (String, Boolean) -> Unit = { _, _ -> },
    onAddComment: (String, PostComment) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    onFollow: () -> Unit,
    onFollowUser: (String) -> Unit = { onFollow() },
    onSetUserRoles: (String, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onOpenPrivateChat: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit = {},
    openingProfileUserId: String? = null
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val translatorModeController = LocalQuataTranslatorModeController.current
    val template = quataTheme()
    var selectedProfileAttachment by remember { mutableStateOf<AttachmentPreview?>(null) }
    CommunityProfileScreenHost(
        profile = profile,
        currentUserId = currentUserId,
        strings = CommunityProfileStrings(
            posts = stringResource(R.string.neighborhoods_posts),
            followers = stringResource(R.string.neighborhoods_followers),
            following = stringResource(R.string.neighborhoods_following),
            followersOf = { resources.getString(R.string.neighborhoods_followers_of, it) },
            followingOf = { resources.getString(R.string.neighborhoods_following_of, it) },
            actions = ProfileActionStrings(
                follow = stringResource(R.string.common_follow),
                following = stringResource(R.string.common_following),
                chat = stringResource(R.string.common_chat),
            ),
            userRow = NeighborhoodUserRowStrings(
                follow = stringResource(R.string.common_follow),
                following = stringResource(R.string.common_following),
                chat = stringResource(R.string.common_chat),
            ),
            moderation = ProfileModerationStrings(
                report = stringResource(R.string.moderation_report),
                block = stringResource(R.string.moderation_block),
                unblock = stringResource(R.string.moderation_unblock),
            ),
            moderationConfirmation = ProfileModerationConfirmationStrings(
                reportTitle = stringResource(R.string.moderation_report_title),
                blockTitle = stringResource(R.string.moderation_block_title),
                unblockTitle = stringResource(R.string.moderation_unblock_title),
                reportMessage = stringResource(R.string.moderation_report_profile_confirm),
                blockMessage = stringResource(R.string.moderation_block_profile_confirm),
                unblockMessage = stringResource(R.string.moderation_unblock_profile_confirm),
                cancel = stringResource(R.string.common_cancel),
                report = stringResource(R.string.moderation_report),
                block = stringResource(R.string.moderation_block),
                unblock = stringResource(R.string.moderation_unblock),
            ),
            roles = ProfileRoleStrings(
                title = stringResource(R.string.profile_admin_controls_title),
                admin = stringResource(R.string.profile_admin_role),
                official = stringResource(R.string.profile_official_role),
            ),
            attachments = ProfileAttachmentsStrings(
                title = stringResource(R.string.neighborhoods_attachments),
                empty = stringResource(R.string.neighborhoods_no_attachments),
            ),
            galleryTitle = stringResource(R.string.neighborhoods_photos_videos),
            emptyGallery = stringResource(R.string.neighborhoods_no_visible_posts),
            back = stringResource(R.string.common_back),
            comments = CommunityProfileCommentsDialogStrings(
                title = stringResource(R.string.feed_comments),
                closeContentDescription = stringResource(R.string.common_close),
                placeholder = stringResource(R.string.comments_placeholder),
                sendLabel = stringResource(R.string.common_send),
                showEmojis = stringResource(R.string.comments_show_emojis),
                emojiLabels = CommunityEmojiLabels(
                    recent = stringResource(R.string.emoji_recent),
                    frequent = stringResource(R.string.emoji_frequent),
                    gestures = stringResource(R.string.emoji_gestures),
                    people = stringResource(R.string.emoji_people),
                    animalsNature = stringResource(R.string.emoji_animals_nature),
                    foodDrink = stringResource(R.string.emoji_food_drink),
                    objectsSymbols = stringResource(R.string.emoji_objects_symbols),
                    flags = stringResource(R.string.emoji_flags),
                ),
                translatorContentDescription = stringResource(R.string.translator_button_content_description),
            ),
        ),
        slots = CommunityProfilePlatformSlots(
            avatar = { user, modifier, loading, openAvatar ->
                ProfileAvatarWithLoadingHalo(
                    name = user.displayName,
                    avatarUrl = user.avatarUrl,
                    profileId = user.id,
                    isOfficial = user.isOfficial,
                    isLoading = loading,
                    modifier = modifier.then(openAvatar?.let { Modifier.clickable(onClick = it) } ?: Modifier),
                )
            },
            attachment = { attachment, open -> ProfileAttachmentRow(attachment, open) },
            postMedia = { post, loaded, load ->
                when {
                    post.imageUrl != null -> AsyncImage(
                        model = post.imageUrl,
                        contentDescription = post.imageTitle(),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(430.dp),
                    )
                    post.videoUrl != null && loaded -> ProfileVideoPlayer(post.videoUrl.orEmpty())
                }
            },
            openAttachment = { attachment ->
                val preview = attachment.toAttachmentPreview()
                if (preview.isMedia) {
                    selectedProfileAttachment = preview
                } else {
                    context.openAttachmentWithDocumentReaderOrChooser(preview, template.resolvedTheme == QuataResolvedTheme.Dark)
                }
            },
            sharePost = context::shareProfilePost,
            commentsTranslatorTrigger = { _, modifier, _, _ ->
                FangTranslatorIconButton(
                    onClick = { view ->
                        translatorModeController.activate(view, QuataTranslatorOverlaySource.Comments)
                    },
                    modifier = modifier,
                )
            },
        ),
        isOpeningChat = isOpeningChat,
        isRefreshingProfile = isRefreshingProfile,
        followingUserId = followingUserId,
        roleUpdatingUserId = roleUpdatingUserId,
        commentingPostId = commentingPostId,
        likingPostId = likingPostId,
        profileSafetyUpdatingUserId = profileSafetyUpdatingUserId,
        currentUserIsAdmin = currentUserIsAdmin,
        openingProfileUserId = openingProfileUserId,
        errorMessage = chatError,
        onAuthRequired = onAuthRequired,
        onBack = onBack,
        onFollowUser = onFollowUser,
        onOpenPrivateChat = onOpenPrivateChat,
        onOpenUserProfile = onOpenUserProfile,
        onSetUserRoles = onSetUserRoles,
        onReportPost = onReportPost,
        onTogglePostLike = onTogglePostLike,
        onReportProfile = onReportProfile,
        onSetProfileBlocked = onSetProfileBlocked,
        onAddComment = onAddComment,
        createComment = { post, draft ->
            PostComment(
                id = "profile_${post.id}_${System.currentTimeMillis()}",
                authorName = resources.getString(R.string.comments_you),
                message = draft,
                timestamp = resources.getString(R.string.common_now),
            )
        },
    )
    selectedProfileAttachment?.let { attachment ->
        AttachmentViewerDialog(attachment = attachment, onDismiss = { selectedProfileAttachment = null })
    }
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
