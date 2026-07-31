package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.QuataOrange

@Composable
fun QuataFeedActionRail(
    likes: Int, isLiked: Boolean, comments: Int, postRank: Int, isLandscape: Boolean,
    likeLabel: String, commentsLabel: String, shareLabel: String, rankLabel: String, liveLabel: String, publishLabel: String,
    isReported: Boolean = false, reportLabel: String? = null, deleteLabel: String? = null,
    showReport: Boolean = false, showDelete: Boolean = false, showPublish: Boolean = true,
    onLike: () -> Unit, onOpenComments: () -> Unit, onShare: () -> Unit, onOpenLive: () -> Unit,
    onReport: () -> Unit = {}, onDelete: () -> Unit = {}, onPublish: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 14.dp),
    ) {
        if (!isLandscape) {
            FeedTextAction(QuataFeedEmoji.Rank, rankLabel, postRank.toString(), onClick = onOpenLive)
            FeedTextAction(liveLabel, liveLabel, onClick = onOpenLive)
        }
        FeedIconAction(if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, likeLabel, likes.toString(), if (isLiked) Color(0xFFFF7EA8) else Color.White, onClick = onLike)
        FeedIconAction(Icons.Filled.ChatBubble, commentsLabel, comments.toString(), onClick = onOpenComments)
        FeedIconAction(Icons.Filled.Share, shareLabel, onClick = onShare)
        if (!isLandscape && showReport && reportLabel != null) FeedIconAction(Icons.Filled.Flag, reportLabel, tint = if (isReported) QuataOrange else Color.White, onClick = onReport)
        if (showDelete && deleteLabel != null) FeedIconAction(Icons.Filled.Delete, deleteLabel, onClick = onDelete)
        if (showPublish) FeedIconAction(Icons.Filled.Add, publishLabel, tint = Color.White, background = QuataOrange, onClick = onPublish)
    }
}

@Composable
fun QuataFeedOverflowActionButton(
    postRank: Int,
    rankLabel: String,
    liveLabel: String,
    reportLabel: String?,
    showReport: Boolean,
    onOpenLive: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    QuataFeedOverflowActionButton(
        postRank = postRank,
        rankLabel = rankLabel,
        liveLabel = liveLabel,
        reportLabel = reportLabel,
        showReport = showReport,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        onOpenLive = {
            expanded = false
            onOpenLive()
        },
        onReport = {
            expanded = false
            onReport()
        },
        modifier = modifier,
    )
}

@Composable
fun QuataFeedOverflowActionButton(
    postRank: Int,
    rankLabel: String,
    liveLabel: String,
    reportLabel: String?,
    showReport: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenLive: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QuataFeedOverflowActionAnchor(
        postRank = postRank,
        rankLabel = rankLabel,
        liveLabel = liveLabel,
        reportLabel = reportLabel,
        showReport = showReport,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        onOpenLive = onOpenLive,
        onReport = onReport,
        modifier = modifier,
    )
}

@Composable
internal fun FeedIconAction(icon: ImageVector, description: String, count: String? = null, tint: Color = Color.White, background: Color = Color.Black.copy(alpha = .42f), onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(background).semantics { contentDescription = description }.clickable(onClick = onClick), Alignment.Center) {
            if (count == null) Icon(icon, null, modifier = Modifier.size(24.dp), tint = tint) else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, modifier = Modifier.size(21.dp), tint = tint)
                Text(count, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FeedTextAction(text: String, description: String, count: String? = null, tint: Color = Color.White, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = .42f)).semantics { contentDescription = description }.clickable(onClick = onClick), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FeedEmojiText(text, color = tint, fontSize = if (text.length <= 2) 19.sp else 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = if (text.length <= 2) 20.sp else 12.sp, modifier = Modifier.padding(horizontal = 5.dp))
                count?.let { Text(it, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 10.sp) }
            }
        }
    }
}

@Composable
private fun FeedEmojiText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified, fontWeight: FontWeight? = null, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip, textAlign: TextAlign? = null, lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified) {
    if (text in QuataFeedEmoji.glyphs) {
        QuataFeedEmojiIcon(text, modifier = modifier, size = if (text.length <= 2) 19.dp else 14.dp)
        return
    }
    val inlineText = rememberQuataFeedEmojiInlineText(text)
    Text(inlineText.text, modifier = modifier, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = maxLines, overflow = overflow, textAlign = textAlign, lineHeight = lineHeight, inlineContent = inlineText.inlineContent)
}
