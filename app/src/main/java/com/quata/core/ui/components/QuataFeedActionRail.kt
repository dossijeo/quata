package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    likes: Int,
    isLiked: Boolean,
    comments: Int,
    postRank: Int,
    isLandscape: Boolean,
    likeLabel: String,
    commentsLabel: String,
    shareLabel: String,
    rankLabel: String,
    liveLabel: String,
    publishLabel: String,
    isReported: Boolean = false,
    reportLabel: String? = null,
    deleteLabel: String? = null,
    showReport: Boolean = false,
    showDelete: Boolean = false,
    showPublish: Boolean = true,
    onLike: () -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onOpenLive: () -> Unit,
    onReport: () -> Unit = {},
    onDelete: () -> Unit = {},
    onPublish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = if (isLandscape) 8.dp else 14.dp
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing)
    ) {
        if (!isLandscape) {
            QuataFeedTextActionButton(
                text = "\uD83D\uDD25",
                contentDescription = rankLabel,
                count = postRank.toString(),
                onClick = onOpenLive
            )
            QuataFeedTextActionButton(
                text = liveLabel,
                contentDescription = liveLabel,
                onClick = onOpenLive
            )
        }
        QuataFeedIconActionButton(
            icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = likeLabel,
            count = likes.toString(),
            tint = if (isLiked) Color(0xFFFF7EA8) else Color.White,
            onClick = onLike
        )
        QuataFeedIconActionButton(
            icon = Icons.Filled.ChatBubble,
            contentDescription = commentsLabel,
            count = comments.toString(),
            onClick = onOpenComments
        )
        QuataFeedIconActionButton(
            icon = Icons.Filled.Share,
            contentDescription = shareLabel,
            onClick = onShare
        )
        if (!isLandscape && showReport && reportLabel != null) {
            QuataFeedIconActionButton(
                icon = Icons.Filled.Flag,
                contentDescription = reportLabel,
                tint = if (isReported) QuataOrange else Color.White,
                onClick = onReport
            )
        }
        if (showDelete && deleteLabel != null) {
            QuataFeedIconActionButton(
                icon = Icons.Filled.Delete,
                contentDescription = deleteLabel,
                onClick = onDelete
            )
        }
        if (showPublish) {
            QuataFeedIconActionButton(
                icon = Icons.Filled.Add,
                contentDescription = publishLabel,
                tint = Color.White,
                backgroundColor = QuataOrange,
                onClick = onPublish
            )
        }
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
    modifier: Modifier = Modifier
) {
    var isOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        QuataFeedIconActionButton(
            icon = Icons.Filled.MoreVert,
            contentDescription = liveLabel,
            onClick = { isOpen = true }
        )
        DropdownMenu(
            expanded = isOpen,
            onDismissRequest = { isOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text("$rankLabel #$postRank") },
                leadingIcon = { Text("\uD83D\uDD25", fontWeight = FontWeight.Black) },
                onClick = {
                    isOpen = false
                    onOpenLive()
                }
            )
            DropdownMenuItem(
                text = { Text(liveLabel) },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                onClick = {
                    isOpen = false
                    onOpenLive()
                }
            )
            if (showReport && reportLabel != null) {
                DropdownMenuItem(
                    text = { Text(reportLabel) },
                    leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null) },
                    onClick = {
                        isOpen = false
                        onReport()
                    }
                )
            }
        }
    }
}

@Composable
private fun QuataFeedIconActionButton(
    icon: ImageVector,
    contentDescription: String,
    count: String? = null,
    tint: Color = Color.White,
    backgroundColor: Color = Color.Black.copy(alpha = 0.42f),
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .semantics { this.contentDescription = contentDescription }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (count == null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
                    Text(
                        text = count,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun QuataFeedTextActionButton(
    text: String,
    contentDescription: String,
    count: String? = null,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .semantics { this.contentDescription = contentDescription }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = text,
                    color = tint,
                    fontSize = if (text.length <= 2) 19.sp else 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    lineHeight = if (text.length <= 2) 20.sp else 12.sp,
                    modifier = Modifier.padding(horizontal = 5.dp)
                )
                if (count != null) {
                    Text(
                        text = count,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 10.sp
                    )
                }
            }
        }
    }
}
