package com.quata.core.ui.components

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.R
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.PostComment
import com.quata.core.translation.FangTranslatorIconButton
import com.quata.core.translation.LocalQuataTranslatorModeController
import com.quata.designsystem.translation.QuataTranslatorOverlaySource
import com.quata.designsystem.translation.quataTranslatableText
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuataCommentsPanel(
    postId: String,
    comments: List<PostComment>,
    canParticipate: Boolean,
    onAuthRequired: () -> Unit,
    onAddComment: (PostComment) -> Unit,
    onReportComment: (PostComment) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var draft by rememberSaveable(postId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var replyTarget by remember { mutableStateOf<PostComment?>(null) }
    var isEmojiPickerVisible by rememberSaveable(postId) { mutableStateOf(false) }
    val emojiDismissState = rememberCommunityEmojiPanelDismissState {
        isEmojiPickerVisible = false
    }
    var shouldScrollToCommentsEnd by remember { mutableStateOf(true) }
    val commentsListState = rememberLazyListState()
    val template = quataTheme()
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val emojiGridMaxHeight = if (isImeVisible) 168.dp else 220.dp
    val translatorModeController = LocalQuataTranslatorModeController.current

    fun setEmojiPickerVisible(visible: Boolean) {
        isEmojiPickerVisible = visible
        if (visible) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    LaunchedEffect(postId) {
        shouldScrollToCommentsEnd = true
    }

    LaunchedEffect(comments.size, shouldScrollToCommentsEnd) {
        if (shouldScrollToCommentsEnd && comments.isNotEmpty()) {
            delay(260)
            commentsListState.animateScrollToItem(comments.size)
            shouldScrollToCommentsEnd = false
        }
    }

    QuataStandardFloatingPanel(
        onDismiss = onDismiss,
        template = template
    ) { panelModifier, isLandscape ->
        if (isLandscape) {
            LandscapeQuataCommentsPanel(
                postId = postId,
                comments = comments,
                commentsListState = commentsListState,
                draft = draft,
                onDraftChange = { draft = it },
                replyTarget = replyTarget,
                onReplyTargetChange = { replyTarget = it },
                isEmojiPickerVisible = isEmojiPickerVisible,
                onEmojiPickerVisibleChange = ::setEmojiPickerVisible,
                emojiDismissState = emojiDismissState,
                emojiGridMaxHeight = emojiGridMaxHeight,
                canParticipate = canParticipate,
                onAuthRequired = onAuthRequired,
                onAddComment = onAddComment,
                onReportComment = onReportComment,
                onCommentAdded = { shouldScrollToCommentsEnd = true },
                onTranslatorClick = { view ->
                    translatorModeController.activate(view, QuataTranslatorOverlaySource.Comments)
                },
                onDismiss = onDismiss,
                modifier = panelModifier
            )
        } else {
            Column(
                modifier = panelModifier
                    .dismissCommunityEmojiPanelOnOutsideTap(
                        isVisible = isEmojiPickerVisible,
                        state = emojiDismissState
                    )
                    .padding(start = 20.dp, end = 20.dp, bottom = 48.dp)
            ) {
                QuataCommentsPanelHeader(
                    commentsCount = comments.size,
                    onTranslatorClick = { view ->
                        translatorModeController.activate(view, QuataTranslatorOverlaySource.Comments)
                    }
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    state = commentsListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 180.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(comments) { comment ->
                        QuataCommentRow(
                            comment = comment,
                            onReply = { replyTarget = comment },
                            onReport = { onReportComment(comment) }
                        )
                    }
                    item(key = "comments-end") {
                        Spacer(Modifier.height(24.dp))
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (isEmojiPickerVisible) {
                    CommunityEmojiPanel(
                        onEmojiClick = { emoji ->
                            draft = draft.insertAtSelection(emoji)
                        },
                        gridMaxHeight = emojiGridMaxHeight,
                        modifier = Modifier.trackCommunityEmojiPanelBounds(emojiDismissState)
                    )
                    Spacer(Modifier.height(18.dp))
                }
                replyTarget?.let { target ->
                    QuataReplyTargetBanner(
                        comment = target,
                        onClear = { replyTarget = null }
                    )
                    Spacer(Modifier.height(14.dp))
                }
                QuataCommentInput(
                    postId = postId,
                    draft = draft,
                    onDraftChange = { draft = it },
                    replyTarget = replyTarget,
                    isEmojiPickerVisible = isEmojiPickerVisible,
                    onEmojiPickerVisibleChange = ::setEmojiPickerVisible,
                    emojiDismissState = emojiDismissState,
                    canParticipate = canParticipate,
                    onAuthRequired = onAuthRequired,
                    onAddComment = onAddComment,
                    onCommentAdded = {
                        shouldScrollToCommentsEnd = true
                        draft = TextFieldValue("")
                        replyTarget = null
                        isEmojiPickerVisible = false
                    },
                    currentUserLabel = context.getString(R.string.comments_you),
                    modifier = Modifier.requiredHeightIn(min = 82.dp)
                )
            }
        }
    }
}

@Composable
private fun LandscapeQuataCommentsPanel(
    postId: String,
    comments: List<PostComment>,
    commentsListState: LazyListState,
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    replyTarget: PostComment?,
    onReplyTargetChange: (PostComment?) -> Unit,
    isEmojiPickerVisible: Boolean,
    onEmojiPickerVisibleChange: (Boolean) -> Unit,
    emojiDismissState: CommunityEmojiPanelDismissState,
    emojiGridMaxHeight: Dp,
    canParticipate: Boolean,
    onAuthRequired: () -> Unit,
    onAddComment: (PostComment) -> Unit,
    onReportComment: (PostComment) -> Unit,
    onCommentAdded: () -> Unit,
    onTranslatorClick: (View) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val template = quataTheme()
    Box(
        modifier = modifier
            .dismissCommunityEmojiPanelOnOutsideTap(
                isVisible = isEmojiPickerVisible,
                state = emojiDismissState
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuataCommentsPanelHeader(
                    commentsCount = comments.size,
                    onTranslatorClick = onTranslatorClick,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                CompactIconButton(onClick = onDismiss) {
                    CompactIcon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = template.colors.textSecondary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                state = commentsListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 140.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(comments) { comment ->
                    QuataCommentRow(
                        comment = comment,
                        onReply = { onReplyTargetChange(comment) },
                        onReport = { onReportComment(comment) }
                    )
                }
                item(key = "comments-end") {
                    Spacer(Modifier.height(12.dp))
                }
            }
            replyTarget?.let { target ->
                Spacer(Modifier.height(10.dp))
                QuataReplyTargetBanner(
                    comment = target,
                    onClear = { onReplyTargetChange(null) }
                )
            }
            Spacer(Modifier.height(12.dp))
            QuataCommentInput(
                postId = postId,
                draft = draft,
                onDraftChange = onDraftChange,
                replyTarget = replyTarget,
                isEmojiPickerVisible = isEmojiPickerVisible,
                onEmojiPickerVisibleChange = onEmojiPickerVisibleChange,
                emojiDismissState = emojiDismissState,
                canParticipate = canParticipate,
                onAuthRequired = onAuthRequired,
                onAddComment = onAddComment,
                onCommentAdded = {
                    onCommentAdded()
                    onDraftChange(TextFieldValue(""))
                    onReplyTargetChange(null)
                    onEmojiPickerVisibleChange(false)
                },
                currentUserLabel = context.getString(R.string.comments_you),
                modifier = Modifier.requiredHeightIn(min = 64.dp)
            )
        }
        if (isEmojiPickerVisible) {
            CommunityEmojiPanel(
                onEmojiClick = { emoji ->
                    onDraftChange(draft.insertAtSelection(emoji))
                },
                gridMaxHeight = emojiGridMaxHeight,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 84.dp, start = 24.dp)
                    .fillMaxWidth(0.62f)
                    .trackCommunityEmojiPanelBounds(emojiDismissState)
            )
        }
    }
}

@Composable
private fun QuataCommentsPanelHeader(
    commentsCount: Int,
    onTranslatorClick: (View) -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.comments_title),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            color = template.colors.textSecondary
        )
        Spacer(Modifier.width(10.dp))
        Text("\uD83D\uDCAC", fontSize = 16.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            text = commentsCount.toString(),
            color = template.colors.textSecondary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        FangTranslatorIconButton(onClick = onTranslatorClick)
    }
}

@Composable
private fun QuataCommentInput(
    postId: String,
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    replyTarget: PostComment?,
    isEmojiPickerVisible: Boolean,
    onEmojiPickerVisibleChange: (Boolean) -> Unit,
    emojiDismissState: CommunityEmojiPanelDismissState,
    canParticipate: Boolean,
    onAuthRequired: () -> Unit,
    onAddComment: (PostComment) -> Unit,
    onCommentAdded: () -> Unit,
    currentUserLabel: String,
    modifier: Modifier = Modifier
) {
    QuataCommentInputContent(
        postId = postId,
        draft = draft,
        replyTarget = replyTarget,
        canParticipate = canParticipate,
        currentUserLabel = currentUserLabel,
        strings = QuataCommentInputStrings(stringResource(R.string.comments_placeholder), stringResource(R.string.comments_send)),
        timestamp = ::nowCommentTimestamp,
        leadingAction = {
            CompactIconButton(onClick = { onEmojiPickerVisibleChange(!isEmojiPickerVisible) }, modifier = Modifier.trackCommunityEmojiTriggerBounds(emojiDismissState)) {
                CompactIcon(Icons.Filled.InsertEmoticon, stringResource(R.string.comments_show_emojis), tint = Color(0xFFFFC55C))
            }
        },
        onDraftChange = onDraftChange,
        onAuthRequired = onAuthRequired,
        onAddComment = onAddComment,
        onCommentAdded = onCommentAdded,
        onFocused = { if (isEmojiPickerVisible) onEmojiPickerVisibleChange(false) },
        modifier = modifier
    )
}

@Composable
private fun QuataReplyTargetBanner(
    comment: PostComment,
    onClear: () -> Unit
) {
    QuataReplyTargetBannerContent(
        comment = comment,
        replyingTo = stringResource(R.string.comments_replying_to, comment.authorName),
        cancelDescription = stringResource(R.string.comments_cancel_reply),
        onClear = onClear
    )
}

@Composable
private fun QuataCommentRow(
    comment: PostComment,
    onReply: () -> Unit,
    onReport: () -> Unit
) {
    val context = LocalContext.current
    val translatorReplyText = comment.replyToAuthorName?.let { author ->
        stringResource(R.string.comments_reply_to, author)
    }
    val translatorDisplayText = remember(comment, translatorReplyText) {
        comment.translatorDisplayText(translatorReplyText)
    }
    QuataCommentRowContent(
        comment = comment,
        timestamp = formatCommentTimestamp(comment.timestamp),
        strings = QuataCommentRowStrings(
            replyTo = { author -> context.getString(R.string.comments_reply_to, author) },
            report = stringResource(R.string.moderation_report),
            reply = stringResource(R.string.comments_reply_button)
        ),
        modifier = Modifier.quataTranslatableText(id = "feed-comment:${comment.id}", text = comment.message, displayText = translatorDisplayText),
        onReply = onReply,
        onReport = onReport
    )
}

private fun PostComment.translatorDisplayText(replyText: String?): String =
    buildString {
        append(authorName)
        val timestampText = formatCommentTimestamp(timestamp)
        if (timestampText.isNotBlank()) {
            append(" - ")
            append(timestampText)
        }
        replyText?.let { reply ->
            append('\n')
            append(reply)
        }
        replyToMessage?.takeIf { it.isNotBlank() }?.let { quoted ->
            append('\n')
            append(quoted)
        }
        if (message.isNotBlank()) {
            append('\n')
            append(message)
        }
    }

private fun nowCommentTimestamp(): String =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("d/M/yyyy, H:mm:ss"))

private fun formatCommentTimestamp(value: String): String {
    val normalized = value.trim()
    if (normalized.isBlank()) return ""
    val parsed = parseAbsoluteDateTime(normalized) ?: return normalized
    return parsed.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
}

private fun parseLocalDateTime(value: String): LocalDateTime? {
    val patterns = listOf(
        "d/M/yyyy, H:mm:ss",
        "d/M/yyyy H:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS"
    )
    patterns.forEach { pattern ->
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern))
        } catch (_: DateTimeParseException) {
            // Try the next supported backend/mock format.
        }
    }
    return null
}

private fun parseAbsoluteDateTime(value: String): LocalDateTime? {
    parseLocalDateTime(value)?.let { return it }
    runCatching {
        OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
    }.getOrNull()?.let { return it }
    return runCatching {
        LocalDateTime.ofInstant(Instant.parse(value), ZoneId.systemDefault())
    }.getOrNull()
}
