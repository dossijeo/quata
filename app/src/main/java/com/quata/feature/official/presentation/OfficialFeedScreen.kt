package com.quata.feature.official.presentation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quata.R
import com.quata.core.model.PostComment
import com.quata.core.platform.ShareService
import com.quata.core.ui.components.AttachmentPreview
import com.quata.core.ui.components.AttachmentViewerDialog
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.QuataCommentsPanel
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
    focusedPostId: String? = null,
    onFocusedPostHandled: () -> Unit = {},
    onAuthRequired: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onCreateOfficialPost: (() -> Unit)? = null,
    onReportComment: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    OfficialFeedScreenHost(
        padding = padding,
        repository = repository,
        currentUserId = currentUserId,
        focusedPostId = focusedPostId,
        onFocusedPostHandled = onFocusedPostHandled,
        onAuthRequired = onAuthRequired,
        onOpenUserProfile = onOpenUserProfile,
        onCreateOfficialPost = { onCreateOfficialPost?.invoke() },
        onReportComment = onReportComment,
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
            readMoreMoreInformation = stringResource(R.string.official_read_more_more_information),
            readMoreContinueReading = stringResource(R.string.official_read_more_continue_reading),
            readMoreDetails = stringResource(R.string.official_read_more_details),
            typeAnnouncement = stringResource(R.string.official_type_announcement),
            typeNews = stringResource(R.string.official_type_news),
            typeEvent = stringResource(R.string.official_type_event),
            typeUrgent = stringResource(R.string.official_type_urgent),
            officialAccountFallback = stringResource(R.string.official_account_fallback),
        ),
        slots = OfficialFeedScreenPlatformSlots(
            avatar = { post, avatarModifier ->
                AvatarImage(post.author.displayName, post.author.avatarUrl, true, post.author.id, avatarModifier)
            },
            media = { post, mediaModifier, open -> OfficialPostMedia(post, open, mediaModifier) },
            article = { post, articleModifier -> QuataRichTextRenderer(post.contentHtml, articleModifier, post.contentPlain) },
            mediaViewer = { post, dismiss -> OfficialMediaViewerDialog(post, dismiss) },
            openUrl = { url -> context.openOfficialPostLink(url) },
            share = { payload -> shareService.share(payload) },
            message = { value -> Toast.makeText(context, value, Toast.LENGTH_SHORT).show() },
            canCreateOfficialPost = onCreateOfficialPost != null,
        ),
    )
}

@Composable
internal fun OfficialPostMedia(post: OfficialPostItem, onOpenMedia: () -> Unit, modifier: Modifier = Modifier) {
    val mediaUrl = post.mediaUrl?.takeIf(String::isNotBlank) ?: return
    OfficialPostMediaFrameContent(onOpenMedia = onOpenMedia, media = { mediaModifier ->
        if (post.mediaType == OfficialMediaType.Image) {
            coil.compose.AsyncImage(model = mediaUrl, contentDescription = post.title, modifier = mediaModifier)
        } else {
            com.quata.core.ui.components.VideoAttachmentThumbnail(uri = mediaUrl, name = post.title, showPlayButton = true, modifier = mediaModifier)
        }
    }, modifier = modifier)
}

@Composable
private fun OfficialMediaViewerDialog(post: OfficialPostItem, onDismiss: () -> Unit) {
    val url = post.mediaUrl?.takeIf(String::isNotBlank) ?: return
    AttachmentViewerDialog(AttachmentPreview(post.title, url, if (post.mediaType == OfficialMediaType.Video) "video/*" else "image/*"), onDismiss)
}

private fun android.content.Context.openOfficialPostLink(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
