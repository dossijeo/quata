package com.quata.feature.chat.presentation.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataSurface
import com.quata.core.designsystem.theme.QuataDivider
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.AvatarLetter
import com.quata.core.ui.components.QuataScreen
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.chatDisplayTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(
    padding: PaddingValues,
    conversationId: String,
    repository: ChatRepository,
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(conversationId, repository))
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val messagesListState = rememberLazyListState()
    val usersById = remember(state.participantCandidates, state.currentUser) {
        (state.participantCandidates + listOfNotNull(state.currentUser)).associateBy { it.id }
    }
    val backgroundSeed = state.conversation?.chatDisplayTitle()?.ifBlank { conversationId }
    val backgroundImage = backgroundSeed?.let { seed ->
        rememberProceduralChatBackground(
            conversationName = seed,
            width = metrics.widthPixels,
            height = metrics.heightPixels
        )
    }

    QuataScreen(padding) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF05070C))
        ) {
            backgroundImage?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.63f))
                )
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                ChatHeader(
                    conversation = state.conversation,
                    currentUser = state.currentUser,
                    usersById = usersById,
                    onBack = onBack,
                    onToggleMute = { muted -> viewModel.onEvent(ChatUiEvent.ConversationMutedChanged(muted)) },
                    onAddParticipants = { viewModel.onEvent(ChatUiEvent.OpenAddParticipants) },
                    onHideConversation = {
                        viewModel.onEvent(ChatUiEvent.HideConversation)
                        onBack()
                    }
                )
                LazyColumn(
                    state = messagesListState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.messages) { message ->
                        MessageBubble(
                            message = message,
                            sender = usersById[message.senderId],
                            showSenderAvatar = state.conversation?.isGroup == true && !message.isMine
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.messageText,
                        onValueChange = { viewModel.onEvent(ChatUiEvent.MessageChanged(it)) },
                        label = { Text(stringResource(R.string.conversation_message)) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 58.dp),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                enabled = state.messageText.isNotBlank(),
                                onClick = { viewModel.onEvent(ChatUiEvent.Send) }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.common_send))
                            }
                        },
                        shape = RoundedCornerShape(18.dp)
                    )
                }
            }
        }
    }

    LaunchedEffect(conversationId, state.messages.size) {
        if (state.messages.isNotEmpty()) {
            messagesListState.scrollToItem(state.messages.lastIndex)
        }
    }

    if (state.isAddParticipantsOpen) {
        AddParticipantsDialog(
            conversation = state.conversation,
            currentUser = state.currentUser,
            candidates = state.participantCandidates,
            search = state.participantSearch,
            selectedIds = state.selectedParticipantIds,
            onSearchChange = { viewModel.onEvent(ChatUiEvent.ParticipantSearchChanged(it)) },
            onToggleUser = { viewModel.onEvent(ChatUiEvent.ParticipantSelectionToggled(it)) },
            onDismiss = { viewModel.onEvent(ChatUiEvent.CloseAddParticipants) },
            onAdd = { viewModel.onEvent(ChatUiEvent.AddSelectedParticipants) }
        )
    }
}

@Composable
private fun rememberProceduralChatBackground(
    conversationName: String,
    width: Int,
    height: Int
): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    var image by remember(conversationName, width, height) {
        mutableStateOf(
            ProceduralChatBackground.cachedBitmap(
                context = context,
                conversationName = conversationName,
                width = width,
                height = height
            )
        )
    }

    LaunchedEffect(conversationName, width, height) {
        if (image != null) return@LaunchedEffect
        image = withContext(Dispatchers.IO) {
            ProceduralChatBackground.generateIfNeeded(
                context = context,
                conversationName = conversationName,
                width = width,
                height = height
            )
        }
    }

    return image
}

@Composable
private fun ChatHeader(
    conversation: Conversation?,
    currentUser: User?,
    usersById: Map<String, User>,
    onBack: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onAddParticipants: () -> Unit,
    onHideConversation: () -> Unit
) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val title = conversation?.chatDisplayTitle().orEmpty().ifBlank { stringResource(R.string.nav_chats) }
    val isGroup = conversation?.isGroup == true
    val members = conversation.membersForDisplay(currentUser, usersById, context)
    Surface(color = QuataSurface.copy(alpha = 0.88f), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isGroup) { expanded = !expanded }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                ChatAvatar(conversation, currentUser, usersById)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isGroup) {
                        Text(
                            stringResource(R.string.conversation_member_count, members.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.common_open))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (conversation?.isMuted == true) stringResource(R.string.conversation_reactivate_notifications) else stringResource(R.string.conversation_mute)) },
                            leadingIcon = {
                                Icon(
                                    if (conversation?.isMuted == true) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleMute(conversation?.isMuted != true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.conversation_add_participants)) },
                            leadingIcon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onAddParticipants()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.conversation_delete)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onHideConversation()
                            }
                        )
                    }
                }
            }
            if (expanded && conversation != null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(members, key = { it.id }) { member ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarImage(member.name, member.avatarUrl, modifier = Modifier.size(38.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(member.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddParticipantsDialog(
    conversation: Conversation?,
    currentUser: User?,
    candidates: List<User>,
    search: String,
    selectedIds: List<String>,
    onSearchChange: (String) -> Unit,
    onToggleUser: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: () -> Unit
) {
    val existingIds = conversation?.participantIds.orEmpty().toSet()
    val existingNames = conversation?.participantNames.orEmpty().map { it.lowercase() }.toSet()
    val visibleCandidates = candidates
        .filter { user ->
            user.id != currentUser?.id && user.id !in existingIds && user.displayName.lowercase() !in existingNames
        }
        .filter { user ->
            val query = search.trim()
            query.isBlank() ||
                user.displayName.contains(query, ignoreCase = true) ||
                user.neighborhood.contains(query, ignoreCase = true) ||
                user.email.contains(query, ignoreCase = true)
        }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF111827),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(stringResource(R.string.conversation_add_participants_title), fontWeight = FontWeight.ExtraBold)
                Text(
                    stringResource(R.string.conversation_add_participants_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.padding(8.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    placeholder = { Text(stringResource(R.string.conversation_search_users)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.padding(8.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleCandidates, key = { it.id }) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, QuataDivider, RoundedCornerShape(16.dp))
                                .clickable { onToggleUser(user.id) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarImage(user.displayName, user.avatarUrl, modifier = Modifier.size(42.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user.displayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(user.neighborhood, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Checkbox(
                                checked = user.id in selectedIds,
                                onCheckedChange = { onToggleUser(user.id) }
                            )
                        }
                    }
                }
                Spacer(Modifier.padding(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = QuataSurface)
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Button(
                        onClick = onAdd,
                        enabled = selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.Black)
                    ) {
                        Text(stringResource(R.string.conversation_add_participants_title))
                    }
                }
            }
        }
    }
}

private fun Conversation?.membersForDisplay(
    currentUser: User?,
    usersById: Map<String, User>,
    context: android.content.Context
): List<ChatMemberDisplay> {
    if (this == null) return emptyList()
    return participantIds.mapIndexed { index, userId ->
        val user = usersById[userId]
        val name = user?.displayName ?: participantNames.getOrNull(index) ?: userId
        ChatMemberDisplay(
            id = userId,
            name = name,
            avatarUrl = user?.avatarUrl,
            label = if (userId == currentUser?.id) context.getString(R.string.conversation_you_suffix, name) else name
        )
    }.distinctBy { it.id }
}

private data class ChatMemberDisplay(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val label: String
)

@Composable
private fun ChatAvatar(
    conversation: Conversation?,
    currentUser: User?,
    usersById: Map<String, User>
) {
    val privateUser = conversation?.participantIds
        ?.firstOrNull { it != currentUser?.id }
        ?.let { usersById[it] }
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        if (conversation?.isGroup == true || conversation?.isEmergency == true) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (conversation?.isEmergency == true) Color(0xFF7F1D1D) else QuataOrange.copy(alpha = 0.22f))
                    .border(1.dp, QuataOrange.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (conversation?.isEmergency == true) {
                    Text(stringResource(R.string.common_sos), color = Color.White, fontWeight = FontWeight.ExtraBold)
                } else {
                    Icon(Icons.Filled.Group, contentDescription = null, tint = Color.White)
                }
            }
        } else if (privateUser != null) {
            AvatarImage(privateUser.displayName, privateUser.avatarUrl, modifier = Modifier.size(46.dp))
        } else {
            AvatarLetter(conversation?.title.orEmpty().ifBlank { "C" }, modifier = Modifier.size(46.dp))
        }
        if (conversation?.isMuted == true) {
            MutedConversationBadge(Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun MutedConversationBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color(0xFF111827))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("\uD83D\uDD15", fontSize = 13.sp)
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    sender: User?,
    showSenderAvatar: Boolean
) {
    val context = LocalContext.current
    val mapsUrl = message.text.extractMapsUrl()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (showSenderAvatar) {
            AvatarImage(
                name = sender?.displayName ?: message.senderName,
                avatarUrl = sender?.avatarUrl,
                modifier = Modifier
                    .size(34.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(if (showSenderAvatar) 0.72f else 0.78f)
                .background(if (message.isMine) QuataOrange else QuataSurface, RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            val textColor = if (message.isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Text(message.senderName, fontWeight = FontWeight.Bold, color = textColor)
            Spacer(Modifier.padding(2.dp))
            Text(message.text, color = textColor)
            if (mapsUrl != null) {
                Spacer(Modifier.padding(4.dp))
                Text(
                    text = stringResource(R.string.conversation_open_maps),
                    color = if (message.isMine) Color.Black else QuataOrange,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clickable { context.openMaps(mapsUrl) }
                )
            }
        }
    }
}

private fun String.extractMapsUrl(): String? =
    Regex("""https://maps\.google\.com/\?q=[^\s]+""").find(this)?.value

private fun android.content.Context.openMaps(url: String) {
    val uri = Uri.parse(url)
    val mapsIntent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
    runCatching { startActivity(mapsIntent) }
        .onFailure { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}
