package com.quata.feature.chat.presentation.conversations

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.quata.core.ui.components.CompactButtonContentPadding
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.AvatarLetter
import com.quata.core.ui.components.ClickableProfileAvatar
import com.quata.core.ui.components.QuataCard
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.compactButtonMinSize
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.chatDisplayTitle
import com.quata.feature.chat.presentation.relativeUpdatedAt
import kotlinx.coroutines.delay

@Composable
fun ConversationsScreen(
    padding: PaddingValues,
    repository: ChatRepository,
    onOpenConversation: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit = {},
    openingProfileUserId: String? = null,
    onOpenFavorites: () -> Unit = {},
    viewModel: ConversationsViewModel = viewModel(factory = ConversationsViewModel.factory(repository))
) {
    val state by viewModel.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var timestampNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val visibleConversations = remember(state.conversations, state.messagesByConversation, state.usersById, query) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            state.conversations
        } else {
            state.conversations.filter { conversation ->
                val messages = state.messagesByConversation[conversation.id].orEmpty()
                val preview = messages.lastOrNull()?.text ?: conversation.lastMessagePreview
                val participantNames = conversation.participantIds
                    .mapNotNull { state.usersById[it]?.displayName }
                    .joinToString(" ")
                listOf(
                    conversation.chatDisplayTitle(),
                    conversation.title,
                    conversation.participantNames.joinToString(" "),
                    participantNames,
                    preview
                ).any { value -> value.contains(cleanQuery, ignoreCase = true) }
            }
        }
    }

    QuataScreen(padding) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.conversations_title), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    CompactIconButton(onClick = onOpenFavorites) {
                        CompactIcon(Icons.Filled.Star, contentDescription = stringResource(R.string.conversation_favorites_title), tint = QuataOrange)
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.conversations_search_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.padding(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.isLoading && state.conversations.isEmpty()) {
                        items(6) { index ->
                            ConversationCardSkeleton(pulseDelayMillis = index * 85)
                        }
                    } else {
                        items(visibleConversations) { item ->
                            ConversationCard(
                                item = item,
                                messages = state.messagesByConversation[item.id].orEmpty(),
                                currentUser = state.currentUser,
                                usersById = state.usersById,
                                openingProfileUserId = openingProfileUserId,
                                timestampNowMillis = timestampNowMillis,
                                onOpenUserProfile = onOpenUserProfile,
                                onOpenConversation = onOpenConversation
                            )
                        }
                    }
                }
            }
            state.pendingDeletedConversation?.let { conversation ->
                UndoDeleteButton(
                    title = conversation.chatDisplayTitle(),
                    onUndo = { viewModel.onEvent(ConversationsUiEvent.RestoreDeletedConversation) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(18.dp)
                )
            }
        }
    }

    LaunchedEffect(state.pendingDeletedConversation?.id) {
        if (state.pendingDeletedConversation == null) return@LaunchedEffect
        delay(4_000L)
        viewModel.onEvent(ConversationsUiEvent.FinalizeDeletedConversation)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            timestampNowMillis = System.currentTimeMillis()
        }
    }
}

@Composable
private fun ConversationCardSkeleton(pulseDelayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "conversation_skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 880, delayMillis = pulseDelayMillis),
            repeatMode = RepeatMode.Reverse
        ),
        label = "conversation_skeleton_alpha"
    )
    val surface = Color.White.copy(alpha = 0.055f + 0.055f * pulse)
    val line = Color.White.copy(alpha = 0.08f + 0.12f * pulse)
    QuataCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(QuataOrange.copy(alpha = 0.12f + 0.10f * pulse))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.52f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(line)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(surface)
                )
            }
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(line.copy(alpha = line.alpha * 0.75f))
            )
        }
    }
}

@Composable
private fun UndoDeleteButton(
    title: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Surface(
        color = template.colors.surfaceRaised,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = onUndo,
                colors = ButtonDefaults.buttonColors(containerColor = template.colors.accent, contentColor = template.colors.accentContent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(32.dp)
                    .compactButtonMinSize(),
                contentPadding = CompactButtonContentPadding
            ) {
                Text(stringResource(R.string.conversation_undo_delete), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ConversationCard(
    item: Conversation,
    messages: List<Message>,
    currentUser: User?,
    usersById: Map<String, User>,
    openingProfileUserId: String?,
    timestampNowMillis: Long,
    onOpenUserProfile: (String) -> Unit,
    onOpenConversation: (String) -> Unit
) {
    val preview = messages.lastOrNull()?.text ?: item.lastMessagePreview
    QuataCard(modifier = Modifier.clickable { onOpenConversation(item.id) }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConversationAvatar(item, currentUser, usersById, openingProfileUserId, onOpenUserProfile)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.chatDisplayTitle(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(preview, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(item.relativeUpdatedAt(timestampNowMillis), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                if (!item.isMuted && item.unreadCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    ) {
                        Text(item.unreadCount.toString(), fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationAvatar(
    item: Conversation,
    currentUser: User?,
    usersById: Map<String, User>,
    openingProfileUserId: String?,
    onOpenUserProfile: (String) -> Unit
) {
    val template = quataTheme()
    val privateUser = item.participantIds
        .firstOrNull { it != currentUser?.id }
        ?.let { usersById[it] }
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        if (item.isGroup || item.isEmergency) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (item.isEmergency) template.colors.sosSurface else template.colors.accent.copy(alpha = 0.22f))
                    .border(1.dp, template.colors.accent.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (item.isEmergency) {
                    Text(stringResource(R.string.common_sos), color = template.colors.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = template.textSizes.caption)
                } else {
                    CompactIcon(Icons.Filled.Group, contentDescription = null, tint = template.colors.textPrimary)
                }
            }
        } else {
            if (privateUser != null) {
                ClickableProfileAvatar(
                    name = privateUser.displayName,
                    avatarUrl = privateUser.avatarUrl,
                    isLoading = openingProfileUserId == privateUser.id,
                    onClick = { onOpenUserProfile(privateUser.id) },
                    modifier = Modifier.size(46.dp)
                )
            } else {
                AvatarLetter(item.chatDisplayTitle(), modifier = Modifier.size(46.dp))
            }
        }
        if (item.isMuted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(template.colors.surfaceRaised)
                    .border(1.dp, template.colors.divider, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("\uD83D\uDD15", fontSize = 13.sp)
            }
        }
    }
}
