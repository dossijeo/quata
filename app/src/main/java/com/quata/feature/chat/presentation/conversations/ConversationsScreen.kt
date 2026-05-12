package com.quata.feature.chat.presentation.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.ui.components.AvatarLetter
import com.quata.core.ui.components.QuataCard
import com.quata.core.ui.components.QuataScreen
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.chatDisplayTitle
import com.quata.feature.chat.presentation.relativeUpdatedAt

@Composable
fun ConversationsScreen(
    padding: PaddingValues,
    repository: ChatRepository,
    onOpenConversation: (String) -> Unit,
    viewModel: ConversationsViewModel = viewModel(factory = ConversationsViewModel.factory(repository))
) {
    val state by viewModel.uiState.collectAsState()

    QuataScreen(padding) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.conversations_title), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.conversations_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.padding(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.conversations) { item ->
                    ConversationCard(
                        item = item,
                        messages = state.messagesByConversation[item.id].orEmpty(),
                        onOpenConversation = onOpenConversation
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationCard(
    item: Conversation,
    messages: List<Message>,
    onOpenConversation: (String) -> Unit
) {
    val context = LocalContext.current
    val preview = messages.lastOrNull()?.text ?: item.lastMessagePreview
    QuataCard(modifier = Modifier.clickable { onOpenConversation(item.id) }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConversationAvatar(item)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.chatDisplayTitle(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(preview, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(item.relativeUpdatedAt(context), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                if (item.unreadCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(item.unreadCount.toString()) }
                }
            }
        }
    }
}

@Composable
private fun ConversationAvatar(item: Conversation) {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        if (item.isGroup || item.isEmergency) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (item.isEmergency) Color(0xFF7F1D1D) else QuataOrange.copy(alpha = 0.22f))
                    .border(1.dp, QuataOrange.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (item.isEmergency) {
                    Text(stringResource(R.string.common_sos), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                } else {
                    Icon(Icons.Filled.Group, contentDescription = null, tint = Color.White)
                }
            }
        } else {
            AvatarLetter(item.chatDisplayTitle(), modifier = Modifier.size(46.dp))
        }
        if (item.isMuted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF111827))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("\uD83D\uDEAB", fontSize = 13.sp)
            }
        }
    }
}
