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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
            FeedTextAction("🔥", rankLabel, postRank.toString(), onClick = onOpenLive)
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
fun QuataFeedOverflowActionButton(postRank: Int, rankLabel: String, liveLabel: String, reportLabel: String?, showReport: Boolean, onOpenLive: () -> Unit, onReport: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FeedIconAction(Icons.Filled.MoreVert, liveLabel, onClick = { open = true })
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text("$rankLabel #$postRank") }, leadingIcon = { Text("🔥", fontWeight = FontWeight.Black) }, onClick = { open = false; onOpenLive() })
            DropdownMenuItem({ Text(liveLabel) }, leadingIcon = { Icon(Icons.Filled.PlayArrow, null) }, onClick = { open = false; onOpenLive() })
            if (showReport && reportLabel != null) DropdownMenuItem({ Text(reportLabel) }, leadingIcon = { Icon(Icons.Filled.Flag, null) }, onClick = { open = false; onReport() })
        }
    }
}

@Composable
private fun FeedIconAction(icon: ImageVector, description: String, count: String? = null, tint: Color = Color.White, background: Color = Color.Black.copy(alpha = .42f), onClick: () -> Unit) {
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
                Text(text, color = tint, fontSize = if (text.length <= 2) 19.sp else 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = if (text.length <= 2) 20.sp else 12.sp, modifier = Modifier.padding(horizontal = 5.dp))
                count?.let { Text(it, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 10.sp) }
            }
        }
    }
}
