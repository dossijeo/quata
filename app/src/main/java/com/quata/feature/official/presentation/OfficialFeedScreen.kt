package com.quata.feature.official.presentation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quata.R
import com.quata.core.model.PostComment
import com.quata.core.navigation.AuthenticationContinuationCoordinator
import com.quata.core.navigation.AuthenticationContinuationIntent
import com.quata.core.platform.ShareService
import com.quata.core.platform.AndroidMediaFileExportService
import com.quata.core.platform.rememberAndroidMediaFileShareService
import com.quata.core.platform.MediaFileExportAction
import com.quata.core.platform.MediaFileExportDescriptor
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.PreferenceStore
import com.quata.core.platform.DurableMediaPositionStore
import com.quata.core.ui.components.AttachmentPreview
import com.quata.core.ui.components.AttachmentFullscreenMediaContent
import com.quata.core.ui.components.QuataFullscreenMediaOverlayContent
import com.quata.core.ui.components.QuataMediaExportActionsContent
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.CommunityEmojiLabels
import com.quata.core.ui.components.communityEmojiCatalogState
import com.quata.core.ui.components.communityEmojiSelectorEvidenceCatalogState
import com.quata.core.translation.FangTranslatorIconButton
import com.quata.core.translation.LocalQuataTranslatorModeController
import com.quata.designsystem.translation.QuataTranslatorOverlaySource
import com.quata.core.ui.richtext.QuataRichTextRenderer
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository

/** Android is now only the native adapter around the common Official screen root. */
@Composable
fun OfficialFeedScreen(
    padding: PaddingValues,
    repository: OfficialRepository,
    shareService: ShareService,
    currentUserId: String?,
    preferenceStore: PreferenceStore? = null,
    focusedPostId: String? = null,
    onFocusedPostHandled: () -> Unit = {},
    onFocusedPostChanged: (String) -> Unit = {},
    onBackFromFocusedPost: (() -> Unit)? = null,
    onAuthRequired: () -> Unit,
    onAuthenticationContinuationRequired: (AuthenticationContinuationIntent) -> Unit = { onAuthRequired() },
    authenticationContinuationCoordinator: AuthenticationContinuationCoordinator? = null,
    onOpenUserProfile: (String) -> Unit,
    onCreateOfficialPost: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mediaFileShareService = rememberAndroidMediaFileShareService()
    val mediaFileExportService = remember(context, mediaFileShareService) {
        AndroidMediaFileExportService(context, mediaFileShareService)
    }
    val videoPositionStore = remember(preferenceStore) {
        preferenceStore?.let { DurableMediaPositionStore(it, OfficialVideoPositionStoragePrefix) }
    }
    val translatorModeController = LocalQuataTranslatorModeController.current
    val commentNamePlaceholder = "\u0000"
    BackHandler(enabled = focusedPostId != null && onBackFromFocusedPost != null) {
        onBackFromFocusedPost?.invoke()
    }
    val commentReplyingToTemplate =
        stringResource(R.string.comments_replying_to, commentNamePlaceholder)
    val commentReplyToTemplate =
        stringResource(R.string.comments_reply_to, commentNamePlaceholder)
    OfficialFeedScreenHost(
        padding = padding,
        repository = repository,
        currentUserId = currentUserId,
        videoPositionStore = videoPositionStore,
        focusedPostId = focusedPostId,
        onFocusedPostHandled = onFocusedPostHandled,
        onFocusedPostChanged = onFocusedPostChanged,
        onBackFromFocusedPost = onBackFromFocusedPost,
        onAuthRequired = onAuthRequired,
        onAuthenticationContinuationRequired = onAuthenticationContinuationRequired,
        authenticationContinuationCoordinator = authenticationContinuationCoordinator,
        onOpenUserProfile = onOpenUserProfile,
        onCreateOfficialPost = { onCreateOfficialPost?.invoke() },
        modifier = modifier,
        strings = OfficialFeedScreenStrings(
            empty = stringResource(R.string.official_empty),
            create = stringResource(R.string.official_create),
            retry = "Reintentar",
            loadingError = stringResource(R.string.error_backend_generic),
            like = stringResource(R.string.feed_like),
            comments = stringResource(R.string.feed_comments),
            share = stringResource(R.string.feed_share),
            rank = stringResource(R.string.feed_rank),
            live = stringResource(R.string.common_live),
            delete = stringResource(R.string.feed_delete_post),
            close = stringResource(R.string.common_close),
            profile = stringResource(R.string.common_profile),
            deleted = stringResource(R.string.feed_delete_post_success),
            deleteTitle = stringResource(R.string.official_delete_confirm_title),
            deleteMessage = stringResource(R.string.official_delete_confirm_message),
            confirm = stringResource(R.string.common_confirm),
            cancel = stringResource(R.string.common_cancel),
            refresh = stringResource(R.string.common_refresh),
            readMore = stringResource(R.string.official_read_more),
            reportSent = stringResource(R.string.moderation_report_sent),
            reportFailed = stringResource(R.string.error_backend_generic),
            readMoreMoreInformation = stringResource(R.string.official_read_more_more_information),
            readMoreContinueReading = stringResource(R.string.official_read_more_continue_reading),
            readMoreDetails = stringResource(R.string.official_read_more_details),
            typeAnnouncement = stringResource(R.string.official_type_announcement),
            typeNews = stringResource(R.string.official_type_news),
            typeEvent = stringResource(R.string.official_type_event),
            typeUrgent = stringResource(R.string.official_type_urgent),
            officialAccountFallback = stringResource(R.string.official_account_fallback),
            shareUnavailable = "No se puede compartir este comunicado en este dispositivo.",
            shareFailed = "No se pudo compartir el comunicado.",
            commentPlaceholder = stringResource(R.string.comments_placeholder),
            commentSend = stringResource(R.string.comments_send),
            commentReport = stringResource(R.string.moderation_report),
            commentReply = stringResource(R.string.comments_reply_button),
            commentReplyingTo = { name -> commentReplyingToTemplate.replace(commentNamePlaceholder, name) },
            commentCancelReply = stringResource(R.string.comments_cancel_reply),
            commentsYou = stringResource(R.string.comments_you),
            commentReplyTo = { name -> commentReplyToTemplate.replace(commentNamePlaceholder, name) },
            showEmojis = stringResource(R.string.comments_show_emojis),
            translatorContentDescription = stringResource(R.string.translator_button_content_description),
            emojiLabels = CommunityEmojiLabels(
                recent = stringResource(R.string.emoji_recent),
                frequent = stringResource(R.string.emoji_frequent),
                gestures = stringResource(R.string.emoji_gestures),
                people = stringResource(R.string.emoji_people),
                animalsNature = stringResource(R.string.emoji_animals_nature),
                foodDrink = stringResource(R.string.emoji_food_drink),
                objectsSymbols = stringResource(R.string.emoji_objects_symbols),
                flags = stringResource(R.string.emoji_flags),
                empty = stringResource(R.string.emoji_empty),
            ),
        ),
        slots = OfficialFeedScreenPlatformSlots(
            avatar = { post, avatarModifier ->
                AvatarImage(post.author.displayName, post.author.avatarUrl, true, post.author.id, avatarModifier)
            },
            media = { post, mediaModifier, open -> OfficialPostMedia(post, open, mediaModifier) },
            article = { post, articleModifier -> QuataRichTextRenderer(post.contentHtml, articleModifier, post.contentPlain) },
            mediaViewer = { post, initialPositionMs, onPositionChanged, dismiss ->
                OfficialMediaViewerDialog(
                    post = post,
                    mediaFileExportService = mediaFileExportService::export,
                    downloadLabel = stringResource(R.string.media_download),
                    shareLabel = stringResource(R.string.media_share_file),
                    failureLabel = stringResource(R.string.media_export_failed),
                    retryLabel = "Reintentar",
                    initialPositionMs = initialPositionMs,
                    onPositionChanged = onPositionChanged,
                    onDismiss = dismiss,
                )
            },
            openUrl = { url -> context.openOfficialPostLink(url) },
            share = { payload -> shareService.share(payload) },
            message = { value -> Toast.makeText(context, value, Toast.LENGTH_SHORT).show() },
            showComposeMessage = false,
            canCreateOfficialPost = onCreateOfficialPost != null,
            rankingAvatar = { item ->
                AvatarImage(item.avatarName, item.avatarUrl, true, item.profileId, Modifier.size(44.dp))
            },
            commentsTranslatorTrigger = { _, triggerModifier, _, _ ->
                FangTranslatorIconButton(
                    onClick = { view ->
                        translatorModeController.activate(view, QuataTranslatorOverlaySource.Comments)
                    },
                    modifier = triggerModifier,
                )
            },
            communityEmojiCatalog = { labels, onRetry ->
                androidOfficialCommunityEmojiSelectorEvidenceCatalogState(context, labels, onRetry)
                    ?: communityEmojiCatalogState(labels, onRetry = onRetry)
            },
        ),
    )
}

private fun androidOfficialCommunityEmojiSelectorEvidenceCatalogState(
    context: android.content.Context,
    labels: CommunityEmojiLabels,
    onRetry: (() -> Unit)?,
) = communityEmojiSelectorEvidenceCatalogState(
    labels = labels,
    onRetry = onRetry,
    optIn = context.getSharedPreferences("quata_community_emoji_selector_evidence", android.content.Context.MODE_PRIVATE)
        .getString("optIn", null),
    mode = context.getSharedPreferences("quata_community_emoji_selector_evidence", android.content.Context.MODE_PRIVATE)
        .getString("mode", null),
    message = context.getSharedPreferences("quata_community_emoji_selector_evidence", android.content.Context.MODE_PRIVATE)
        .getString("message", null),
)

@Composable
internal fun OfficialPostMedia(post: OfficialPostItem, onOpenMedia: () -> Unit, modifier: Modifier = Modifier) {
    val mediaUrl = post.mediaUrl?.takeIf(String::isNotBlank) ?: return
    OfficialPostMediaFrameContent(onOpenMedia = onOpenMedia, media = { mediaModifier ->
        if (post.mediaType == OfficialMediaType.Image) {
            coil.compose.AsyncImage(
                model = mediaUrl,
                contentDescription = post.title,
                modifier = mediaModifier,
                contentScale = ContentScale.Crop,
            )
        } else {
            com.quata.core.ui.components.VideoAttachmentThumbnail(uri = mediaUrl, name = post.title, showPlayButton = true, modifier = mediaModifier)
        }
    }, modifier = modifier)
}

@Composable
private fun OfficialMediaViewerDialog(
    post: OfficialPostItem,
    mediaFileExportService: suspend (MediaFileExportDescriptor, MediaFileExportAction) -> PlatformResult<Unit>,
    downloadLabel: String,
    shareLabel: String,
    failureLabel: String,
    retryLabel: String,
    initialPositionMs: Long,
    onPositionChanged: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val url = post.mediaUrl?.takeIf(String::isNotBlank) ?: return
    val descriptor = remember(post.id, url, post.mediaType, post.title) { officialMediaFileExportDescriptor(post) }
    val attachment = remember(post.id, url, descriptor?.mimeType, post.title) {
        AttachmentPreview(post.title, url, descriptor?.mimeType ?: "application/octet-stream")
    }
    var observedPositionMs by remember(post.id, url) { mutableLongStateOf(initialPositionMs) }
    BackHandler(onBack = onDismiss)
    QuataFullscreenMediaOverlayContent(
        title = post.title,
        onDismiss = onDismiss,
        actions = {
            descriptor?.let {
                QuataMediaExportActionsContent(
                    descriptor = it,
                    downloadLabel = downloadLabel,
                    shareLabel = shareLabel,
                    failureLabel = failureLabel,
                    retryLabel = retryLabel,
                    onExport = mediaFileExportService,
                )
            }
        },
    ) { mediaModifier ->
        Box(mediaModifier) {
            AttachmentFullscreenMediaContent(
                attachment = attachment,
                modifier = Modifier,
                initialVideoPositionMs = initialPositionMs,
                onVideoPositionChanged = { positionMs ->
                    observedPositionMs = positionMs
                    onPositionChanged(positionMs)
                },
            )
            if (com.quata.BuildConfig.DEBUG && post.mediaType == OfficialMediaType.Video) {
                Box(
                    Modifier
                        .size(1.dp)
                        .testTag(OfficialVideoPositionTestTag)
                        .semantics { stateDescription = observedPositionMs.toString() },
                )
            }
        }
    }
}

private fun android.content.Context.openOfficialPostLink(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
