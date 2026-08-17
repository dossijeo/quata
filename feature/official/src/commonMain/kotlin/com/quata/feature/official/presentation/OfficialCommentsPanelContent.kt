package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.quata.core.model.PostComment
import com.quata.core.ui.components.CommunityEmojiLabels
import com.quata.core.ui.components.CommunityEmojiPanelContent
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.QuataCommentInputContent
import com.quata.core.ui.components.QuataCommentInputStrings
import com.quata.core.ui.components.QuataCommentRowContent
import com.quata.core.ui.components.QuataCommentRowStrings
import com.quata.core.ui.components.QuataCommentsPanelHeaderContent
import com.quata.core.ui.components.QuataCommentsPanelLandscapeContent
import com.quata.core.ui.components.QuataCommentsPanelPortraitContent
import com.quata.core.ui.components.QuataReplyTargetBannerContent
import com.quata.core.ui.components.QuataStandardFloatingPanelContent
import com.quata.core.ui.components.communityEmojiSections
import com.quata.core.ui.components.dismissCommunityEmojiPanelOnOutsideTap
import com.quata.core.ui.components.insertAtSelection
import com.quata.core.ui.components.rememberCommunityEmojiPanelDismissState
import com.quata.core.ui.components.trackCommunityEmojiPanelBounds
import com.quata.core.ui.components.trackCommunityEmojiTriggerBounds
import com.quata.designsystem.translation.LocalQuataTranslatableTextRegistry
import com.quata.designsystem.translation.QuataTranslatableTextRegistry
import com.quata.designsystem.translation.QuataTranslatorGateway
import com.quata.designsystem.translation.QuataTranslatorOverlayContent
import com.quata.designsystem.translation.QuataTranslatorStrings
import com.quata.designsystem.translation.quataTranslatableText
import com.quata.feature.official.domain.OfficialPostItem
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Full shared comments experience; platforms only activate their native translator affordance. */
@Composable
@OptIn(ExperimentalTime::class)
fun OfficialCommentsPanelContent(
    post: OfficialPostItem,
    canParticipate: Boolean,
    strings: OfficialCommentsStrings,
    onAuthRequired: () -> Unit,
    onAddComment: (PostComment) -> Unit,
    onReportComment: (PostComment) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onDismiss: () -> Unit,
    translatorTrigger: @Composable (String, Modifier, () -> Unit, Boolean) -> Unit,
    translatorGateway: QuataTranslatorGateway?,
    translatorStrings: QuataTranslatorStrings,
) {
    var draft by rememberSaveable(post.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var replyTo by remember(post.id) { mutableStateOf<PostComment?>(null) }
    var isEmojiPickerVisible by rememberSaveable(post.id) { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val emojiDismissState = rememberCommunityEmojiPanelDismissState { isEmojiPickerVisible = false }
    val emojiGridMaxHeight = if (WindowInsets.ime.getBottom(LocalDensity.current) > 0) 168.dp else 220.dp
    var shouldScrollToCommentsEnd by remember(post.id) { mutableStateOf(true) }
    val commentsListState = rememberLazyListState()
    val inheritedTranslatorRegistry = LocalQuataTranslatableTextRegistry.current
    val translatorRegistry = inheritedTranslatorRegistry ?: remember(post.id) { QuataTranslatableTextRegistry() }
    var translatorActive by rememberSaveable(post.id) { mutableStateOf(false) }
    val translatorEnabled = translatorGateway != null && translatorRegistry.visibleBoxes.isNotEmpty()

    fun setEmojiPickerVisible(visible: Boolean) {
        isEmojiPickerVisible = visible
        if (visible) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }
    fun openTranslator() {
        if (translatorEnabled) translatorActive = true
    }

    LaunchedEffect(post.id, post.comments.size, shouldScrollToCommentsEnd) {
        if (shouldScrollToCommentsEnd) {
            delay(260)
            commentsListState.animateScrollToItem(post.comments.size)
            shouldScrollToCommentsEnd = false
        }
    }

    @Composable
    fun commentRow(comment: PostComment) {
        val timestamp = formatOfficialCommentTimestamp(comment.timestamp)
        val replyLabel = comment.replyToAuthorName?.let(strings.replyTo)
        val displayText = buildString {
            append(comment.authorName)
            if (timestamp.isNotBlank()) append(" - ").append(timestamp)
            replyLabel?.let { append('\n').append(it) }
            comment.replyToMessage?.takeIf(String::isNotBlank)?.let { append('\n').append(it) }
            comment.message.takeIf(String::isNotBlank)?.let { append('\n').append(it) }
        }
        QuataCommentRowContent(
            comment = comment,
            timestamp = timestamp,
            strings = QuataCommentRowStrings(strings.replyTo, strings.report, strings.reply),
            modifier = Modifier.quataTranslatableText(
                id = "official-comment:${comment.id}",
                text = comment.message,
                displayText = displayText,
            ),
            authorProfileTestTagPrefix = "official.comments.author.",
            onOpenAuthorProfile = onOpenUserProfile,
            onReply = { replyTo = comment },
            onReport = { if (canParticipate) onReportComment(comment) else onAuthRequired() },
        )
    }

    @Composable
    fun commentInput(modifier: Modifier) {
        QuataCommentInputContent(
            postId = post.id,
            draft = draft,
            replyTarget = replyTo,
            canParticipate = canParticipate,
            currentUserLabel = strings.commentsYou,
            strings = QuataCommentInputStrings(strings.placeholder, strings.send),
            timestamp = { nowOfficialCommentTimestamp() },
            leadingAction = {
                CompactIconButton(
                    onClick = { setEmojiPickerVisible(!isEmojiPickerVisible) },
                    modifier = Modifier.trackCommunityEmojiTriggerBounds(emojiDismissState),
                    testTag = "official.comments.emoji",
                    contentDescription = strings.showEmojis,
                ) {
                    CompactIcon(Icons.Filled.InsertEmoticon, strings.showEmojis, tint = Color(0xFFFFC55C))
                }
            },
            onDraftChange = { draft = it },
            onAuthRequired = onAuthRequired,
            onAddComment = onAddComment,
            onCommentAdded = {
                draft = TextFieldValue()
                replyTo = null
                isEmojiPickerVisible = false
                shouldScrollToCommentsEnd = true
            },
            onFocused = { if (isEmojiPickerVisible) setEmojiPickerVisible(false) },
            modifier = modifier,
            inputTestTag = "official.comments.input",
            sendTestTag = "official.comments.send",
        )
    }

    CompositionLocalProvider(LocalQuataTranslatableTextRegistry provides translatorRegistry) {
    QuataStandardFloatingPanelContent(onDismiss = onDismiss) { panelModifier, landscape ->
        if (!landscape) {
            QuataCommentsPanelPortraitContent(
                header = {
                    QuataCommentsPanelHeaderContent(
                        strings.title,
                        post.comments.size,
                        { modifier -> translatorTrigger(strings.translatorContentDescription, modifier, ::openTranslator, translatorEnabled) },
                    )
                },
                comments = { modifier ->
                    LazyColumn(
                        modifier = modifier.heightIn(min = 180.dp),
                        state = commentsListState,
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        items(post.comments, key = PostComment::id) { comment -> commentRow(comment) }
                        item(key = "comments-end") { Spacer(Modifier.height(24.dp)) }
                    }
                },
                replyTarget = replyTo?.let { target ->
                    { QuataReplyTargetBannerContent(target, strings.replyingTo(target.authorName), strings.cancelReply) { replyTo = null } }
                },
                emojiPanel = if (isEmojiPickerVisible) {
                    {
                        CommunityEmojiPanelContent(
                            communityEmojiSections(strings.emojiLabels),
                            { draft = draft.insertAtSelection(it) },
                            Modifier.trackCommunityEmojiPanelBounds(emojiDismissState),
                            gridMaxHeight = emojiGridMaxHeight,
                        )
                    }
                } else null,
                input = { modifier -> commentInput(modifier.fillMaxWidth()) },
                modifier = panelModifier.dismissCommunityEmojiPanelOnOutsideTap(isEmojiPickerVisible, emojiDismissState),
            )
        } else {
            QuataCommentsPanelLandscapeContent(
                header = { modifier ->
                    QuataCommentsPanelHeaderContent(
                        strings.title,
                        post.comments.size,
                        { actionModifier -> translatorTrigger(strings.translatorContentDescription, actionModifier, ::openTranslator, translatorEnabled) },
                        modifier,
                    )
                },
                closeAction = {
                    CompactIconButton(onClick = onDismiss) { CompactIcon(Icons.Filled.Close, strings.close) }
                },
                comments = { modifier ->
                    LazyColumn(
                        modifier = modifier,
                        state = commentsListState,
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(post.comments, key = PostComment::id) { comment -> commentRow(comment) }
                        item(key = "comments-end") { Spacer(Modifier.height(12.dp)) }
                    }
                },
                replyTarget = replyTo?.let { target ->
                    { QuataReplyTargetBannerContent(target, strings.replyingTo(target.authorName), strings.cancelReply) { replyTo = null } }
                },
                input = { modifier -> commentInput(modifier) },
                emojiPanel = if (isEmojiPickerVisible) {
                    {
                        CommunityEmojiPanelContent(
                            communityEmojiSections(strings.emojiLabels),
                            { draft = draft.insertAtSelection(it) },
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 12.dp, bottom = 84.dp, start = 24.dp)
                                .fillMaxWidth(0.62f)
                                .trackCommunityEmojiPanelBounds(emojiDismissState),
                            gridMaxHeight = emojiGridMaxHeight,
                        )
                    }
                } else null,
                modifier = panelModifier.dismissCommunityEmojiPanelOnOutsideTap(isEmojiPickerVisible, emojiDismissState),
            )
        }
    }
    translatorGateway?.let { gateway ->
        if (translatorActive) {
            QuataTranslatorOverlayContent(
                registry = translatorRegistry,
                gateway = gateway,
                strings = translatorStrings,
                onDismiss = { translatorActive = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    }
}

data class OfficialCommentsStrings(
    val title: String,
    val close: String,
    val placeholder: String,
    val send: String,
    val report: String,
    val reply: String,
    val replyingTo: (String) -> String,
    val cancelReply: String,
    val commentsYou: String,
    val replyTo: (String) -> String,
    val showEmojis: String,
    val translatorContentDescription: String,
    val emojiLabels: CommunityEmojiLabels,
)

@OptIn(ExperimentalTime::class)
private fun nowOfficialCommentTimestamp(): String = Clock.System.now().toString()

@OptIn(ExperimentalTime::class)
private fun formatOfficialCommentTimestamp(value: String): String {
    val normalized = value.trim()
    if (normalized.isBlank()) return ""
    val parsed = parseOfficialCommentTimestamp(normalized) ?: return normalized
    return "${parsed.day.toString().padStart(2, '0')}/${(parsed.month.ordinal + 1).toString().padStart(2, '0')}/${parsed.year.toString().padStart(4, '0')} ${parsed.hour.toString().padStart(2, '0')}:${parsed.minute.toString().padStart(2, '0')}"
}

@OptIn(ExperimentalTime::class)
private fun parseOfficialCommentTimestamp(value: String): LocalDateTime? {
    runCatching { Instant.parse(value).toLocalDateTime(TimeZone.currentSystemDefault()) }.getOrNull()?.let { return it }
    val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})[ T](\\d{2}):(\\d{2}):(\\d{2})(?:\\.\\d{3})?$").matchEntire(value)
        ?: Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})[ ,]+(\\d{1,2}):(\\d{2}):(\\d{2})$").matchEntire(value)
        ?: return null
    return runCatching {
        val spanish = value.contains('/')
        LocalDateTime(
            year = match.groupValues[if (spanish) 3 else 1].toInt(),
            monthNumber = match.groupValues[2].toInt(),
            dayOfMonth = match.groupValues[if (spanish) 1 else 3].toInt(),
            hour = match.groupValues[4].toInt(),
            minute = match.groupValues[5].toInt(),
            second = match.groupValues[6].toInt(),
        )
    }.getOrNull()
}
