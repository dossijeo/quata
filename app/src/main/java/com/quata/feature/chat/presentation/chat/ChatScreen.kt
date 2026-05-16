package com.quata.feature.chat.presentation.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
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
import com.quata.core.navigation.AppDestinations
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.quata.core.ui.components.AttachmentPreview
import com.quata.core.ui.components.AttachmentThumbnail
import com.quata.core.ui.components.AttachmentViewerDialog
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.AvatarLetter
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.openAttachmentWithChooser
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.chatDisplayTitle
import com.quata.feature.chat.presentation.relativeUpdatedAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(
    padding: PaddingValues,
    conversationId: String,
    repository: ChatRepository,
    onOpenUserProfile: (String) -> Unit = {},
    onOpenConversation: (String) -> Unit = {},
    focusedMessageId: String? = null,
    onFocusedMessageHandled: () -> Unit = {},
    onOpenMessageConversation: (String, String) -> Unit = { targetConversationId, _ -> onOpenConversation(targetConversationId) },
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(
        key = "chat_$conversationId",
        factory = ChatViewModel.factory(conversationId, repository)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val metrics = context.resources.displayMetrics
    val messagesListState = rememberLazyListState()
    val selectedMessage = state.messages.firstOrNull { it.id == state.selectedMessageId }
    val isFavoritesConversation = conversationId == AppDestinations.FavoriteMessagesConversationId
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }
    var lastShownError by remember { mutableStateOf<String?>(null) }
    var previousIncomingCount by remember(conversationId) { mutableStateOf(0) }
    var attachmentMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraMimeType by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraKind by rememberSaveable { mutableStateOf<String?>(null) }
    var highlightedMessageId by rememberSaveable(conversationId) { mutableStateOf<String?>(null) }
    var highlightVisible by rememberSaveable(conversationId) { mutableStateOf(false) }
    var selectedAttachment by remember { mutableStateOf<AttachmentPreview?>(null) }
    val usersById = remember(state.participantCandidates, state.currentUser) {
        (state.participantCandidates + listOfNotNull(state.currentUser)).associateBy { it.id }
    }
    val backgroundSeed = state.conversation?.chatDisplayTitle()?.ifBlank { conversationId }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onEvent(ChatUiEvent.AttachmentSelected(it.toString(), context.displayNameForUri(it), context.contentResolver.getType(it))) }
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onEvent(ChatUiEvent.AttachmentSelected(it.toString(), context.displayNameForUri(it), context.contentResolver.getType(it))) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) {
            pendingCameraUri?.let { uri ->
                viewModel.onEvent(
                    ChatUiEvent.AttachmentSelected(
                        uri = uri,
                        name = pendingCameraName ?: "photo.jpg",
                        mimeType = pendingCameraMimeType ?: "image/jpeg"
                    )
                )
            }
        }
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { saved ->
        if (saved) {
            pendingCameraUri?.let { uri ->
                viewModel.onEvent(
                    ChatUiEvent.AttachmentSelected(
                        uri = uri,
                        name = pendingCameraName ?: "video.mp4",
                        mimeType = pendingCameraMimeType ?: "video/mp4"
                    )
                )
            }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pendingCameraUri?.let { uri ->
                when (pendingCameraKind) {
                    "video" -> videoLauncher.launch(Uri.parse(uri))
                    else -> cameraLauncher.launch(Uri.parse(uri))
                }
            }
        } else {
            Toast.makeText(context, context.getString(R.string.conversation_camera_permission), Toast.LENGTH_SHORT).show()
        }
    }
    fun launchCameraAttachment(kind: String) {
        val extension = if (kind == "video") "mp4" else "jpg"
        val mimeType = if (kind == "video") "video/mp4" else "image/jpeg"
        val fileName = "quata_${currentAttachmentTimestamp()}.$extension"
        val file = java.io.File(context.cacheDir, fileName)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraUri = uri.toString()
        pendingCameraName = fileName
        pendingCameraMimeType = mimeType
        pendingCameraKind = kind
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (kind == "video") {
                videoLauncher.launch(uri)
            } else {
                cameraLauncher.launch(uri)
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
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
                if (state.isForwardDialogOpen) {
                    ForwardMessageScreen(
                        conversations = state.availableForwardConversations,
                        selectedIds = state.selectedForwardConversationIds,
                        onToggle = { viewModel.onEvent(ChatUiEvent.ForwardConversationToggled(it)) },
                        onBack = { viewModel.onEvent(ChatUiEvent.CloseForwardDialog) },
                        onSend = {
                            if (state.selectedForwardConversationIds.isNotEmpty()) {
                                viewModel.onEvent(ChatUiEvent.SendForward)
                                Toast.makeText(context, context.getString(R.string.conversation_forward_sent), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                } else {
                    if (isFavoritesConversation) {
                        FavoriteMessagesHeader(onBack = onBack)
                    } else {
                    ChatHeader(
                        conversation = state.conversation,
                        currentUser = state.currentUser,
                        usersById = usersById,
                        onOpenUserProfile = onOpenUserProfile,
                        selectedMessage = selectedMessage,
                        onClearSelection = { viewModel.onEvent(ChatUiEvent.MessageSelected(null)) },
                        onCopySelected = {
                            selectedMessage?.let { clipboard.setText(AnnotatedString(it.text)) }
                            Toast.makeText(context, context.getString(R.string.conversation_text_copied), Toast.LENGTH_SHORT).show()
                            viewModel.onEvent(ChatUiEvent.MessageSelected(null))
                        },
                        onReplySelected = { viewModel.onEvent(ChatUiEvent.StartReply) },
                        onForwardSelected = { viewModel.onEvent(ChatUiEvent.OpenForwardDialog) },
                        onEditSelected = { viewModel.onEvent(ChatUiEvent.StartEdit) },
                        onToggleFavoriteSelected = {
                            Toast.makeText(
                                context,
                                context.getString(
                                    if (selectedMessage?.isFavorite == true) {
                                        R.string.conversation_favorite_removed
                                    } else {
                                        R.string.conversation_favorite_added
                                    }
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.onEvent(ChatUiEvent.ToggleFavoriteSelected)
                        },
                        onDeleteSelected = { confirmAction = ConfirmAction.DeleteMessage },
                        onBack = onBack,
                        onToggleMute = { muted -> viewModel.onEvent(ChatUiEvent.ConversationMutedChanged(muted)) },
                        onToggleMemberInvites = { enabled -> viewModel.onEvent(ChatUiEvent.MemberInvitesChanged(enabled)) },
                        onAddParticipants = { viewModel.onEvent(ChatUiEvent.OpenAddParticipants) },
                        onToggleModerator = { userId, isModerator -> confirmAction = ConfirmAction.ToggleModerator(userId, isModerator) },
                        onBlockParticipant = { userId -> confirmAction = ConfirmAction.BlockParticipant(userId) },
                        onRemoveParticipant = { userId -> confirmAction = ConfirmAction.RemoveParticipant(userId) },
                        onLeaveConversation = { confirmAction = ConfirmAction.LeaveConversation },
                        onHideConversation = {
                            confirmAction = ConfirmAction.DeleteConversation
                        }
                    )
                    }
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
                            showSenderAvatar = state.conversation?.isGroup == true && !message.isMine,
                            isSelected = message.id == state.selectedMessageId ||
                                (message.id == highlightedMessageId && highlightVisible),
                            onOpenSenderProfile = { onOpenUserProfile(message.senderId) },
                            onOpenAttachment = { attachment ->
                                if (attachment.isMedia) {
                                    selectedAttachment = attachment
                                } else {
                                    context.openAttachmentWithChooser(attachment)
                                }
                            },
                            onClick = {
                                if (isFavoritesConversation) {
                                    onOpenMessageConversation(message.conversationId, message.id)
                                } else {
                                    viewModel.onEvent(
                                        ChatUiEvent.MessageSelected(
                                            if (message.id == state.selectedMessageId) null else message.id
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
                    if (!isFavoritesConversation) state.editingMessage?.let {
                        ComposerModeBanner(
                            text = stringResource(R.string.conversation_editing_message),
                            onClear = { viewModel.onEvent(ChatUiEvent.CancelEdit) }
                        )
                    }
                    if (!isFavoritesConversation) state.replyToMessage?.let { reply ->
                        ComposerModeBanner(
                            text = stringResource(R.string.conversation_replying_to, reply.senderName),
                            onClear = { viewModel.onEvent(ChatUiEvent.ClearReply) }
                        )
                    }
                    state.attachmentUri?.let {
                        ComposerModeBanner(
                            text = state.attachmentName ?: stringResource(R.string.conversation_attachment),
                            onClear = { viewModel.onEvent(ChatUiEvent.ClearAttachment) }
                        )
                    }
                    if (!isFavoritesConversation) Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(onClick = { attachmentMenuExpanded = true }) {
                            Icon(Icons.Filled.AttachFile, contentDescription = stringResource(R.string.conversation_attachment))
                        }
                        DropdownMenu(expanded = attachmentMenuExpanded, onDismissRequest = { attachmentMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.conversation_attach_file)) },
                                leadingIcon = { Icon(Icons.Filled.InsertDriveFile, contentDescription = null) },
                                onClick = {
                                    attachmentMenuExpanded = false
                                    filePicker.launch("*/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.conversation_attach_gallery)) },
                                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                                onClick = {
                                    attachmentMenuExpanded = false
                                    mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.conversation_attach_take_photo)) },
                                leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                                onClick = {
                                    attachmentMenuExpanded = false
                                    launchCameraAttachment("photo")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.conversation_attach_record_video)) },
                                leadingIcon = { Icon(Icons.Filled.Videocam, contentDescription = null) },
                                onClick = {
                                    attachmentMenuExpanded = false
                                    launchCameraAttachment("video")
                                }
                            )
                        }
                    }
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
                                enabled = state.messageText.isNotBlank() || state.attachmentUri != null,
                                onClick = {
                                    context.playChatSound(R.raw.sent)
                                    viewModel.onEvent(ChatUiEvent.Send)
                                }
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
    }

    LaunchedEffect(conversationId, state.messages.size) {
        if (state.messages.isNotEmpty() && focusedMessageId == null) {
            messagesListState.scrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(focusedMessageId, state.messages) {
        val targetMessageId = focusedMessageId ?: return@LaunchedEffect
        val targetIndex = state.messages.indexOfFirst { it.id == targetMessageId }
        if (targetIndex < 0) return@LaunchedEffect
        messagesListState.scrollToItem(targetIndex)
        highlightedMessageId = targetMessageId
        repeat(4) { index ->
            highlightVisible = index % 2 == 0
            delay(180L)
        }
        highlightVisible = false
        highlightedMessageId = null
        onFocusedMessageHandled()
    }

    LaunchedEffect(state.messages) {
        val incomingCount = state.messages.count { !it.isMine }
        if (incomingCount > previousIncomingCount) {
            context.playChatSound(R.raw.notification)
        }
        previousIncomingCount = incomingCount
    }

    LaunchedEffect(state.error) {
        val error = state.error
        if (!error.isNullOrBlank() && error != lastShownError) {
            lastShownError = error
            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
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

    confirmAction?.let { action ->
        ConfirmDialog(
            action = action,
            onDismiss = { confirmAction = null },
            onConfirm = {
                when (val currentAction = action) {
                    ConfirmAction.DeleteConversation -> {
                        viewModel.onEvent(ChatUiEvent.DeleteConversation)
                        onBack()
                    }
                    ConfirmAction.LeaveConversation -> {
                        viewModel.onEvent(ChatUiEvent.LeaveConversation)
                        if (state.currentUser?.id !in state.conversation?.moderatorIds.orEmpty()) {
                            onBack()
                        }
                    }
                    ConfirmAction.DeleteMessage -> viewModel.onEvent(ChatUiEvent.DeleteSelectedMessage)
                    is ConfirmAction.BlockParticipant -> viewModel.onEvent(ChatUiEvent.BlockParticipant(currentAction.userId))
                    is ConfirmAction.RemoveParticipant -> viewModel.onEvent(ChatUiEvent.RemoveParticipant(currentAction.userId))
                    is ConfirmAction.ToggleModerator -> viewModel.onEvent(ChatUiEvent.PromoteModerator(currentAction.userId))
                }
                confirmAction = null
            }
        )
    }

    selectedAttachment?.let { attachment ->
        AttachmentViewerDialog(
            attachment = attachment,
            onDismiss = { selectedAttachment = null }
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
private fun ComposerModeBanner(
    text: String,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(QuataSurface.copy(alpha = 0.92f), RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onClear) {
            Text("X")
        }
    }
}

@Composable
private fun FavoriteMessagesHeader(onBack: () -> Unit) {
    Surface(color = QuataSurface.copy(alpha = 0.88f), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(QuataOrange.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = QuataOrange)
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.conversation_favorites_title), fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ChatHeader(
    conversation: Conversation?,
    currentUser: User?,
    usersById: Map<String, User>,
    onOpenUserProfile: (String) -> Unit,
    selectedMessage: Message?,
    onClearSelection: () -> Unit,
    onCopySelected: () -> Unit,
    onReplySelected: () -> Unit,
    onForwardSelected: () -> Unit,
    onEditSelected: () -> Unit,
    onToggleFavoriteSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onBack: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onToggleMemberInvites: (Boolean) -> Unit,
    onAddParticipants: () -> Unit,
    onToggleModerator: (String, Boolean) -> Unit,
    onBlockParticipant: (String) -> Unit,
    onRemoveParticipant: (String) -> Unit,
    onLeaveConversation: () -> Unit,
    onHideConversation: () -> Unit
) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val title = conversation?.chatDisplayTitle().orEmpty().ifBlank { stringResource(R.string.nav_chats) }
    val isGroup = conversation?.isGroup == true
    val members = conversation.membersForDisplay(currentUser, usersById, context)
    val isModerator = currentUser?.id != null && currentUser.id in conversation?.moderatorIds.orEmpty()
    val canInvite = isModerator || conversation?.canMembersInvite == true
    Surface(color = QuataSurface.copy(alpha = 0.88f), modifier = Modifier.fillMaxWidth()) {
        Column {
            if (selectedMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onClearSelection) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onCopySelected) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.conversation_copy_message))
                    }
                    IconButton(onClick = onReplySelected) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = stringResource(R.string.conversation_reply_message), tint = Color.White)
                    }
                    IconButton(onClick = onForwardSelected) {
                        Icon(Icons.Filled.Forward, contentDescription = stringResource(R.string.conversation_forward_message), tint = Color.White)
                    }
                    if (selectedMessage.isMine && !selectedMessage.isDeleted) {
                        IconButton(onClick = onEditSelected) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.conversation_edit_message))
                        }
                    }
                    IconButton(onClick = onToggleFavoriteSelected) {
                        Icon(
                            if (selectedMessage.isFavorite) Icons.Filled.StarBorder else Icons.Filled.Star,
                            contentDescription = stringResource(R.string.conversation_favorite_message)
                        )
                    }
                    if (selectedMessage.isMine && !selectedMessage.isDeleted) {
                        IconButton(onClick = onDeleteSelected) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.conversation_delete_message))
                        }
                    }
                }
            } else {
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
                    ChatAvatar(conversation, currentUser, usersById, onOpenUserProfile)
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
                                text = { Text(stringResource(R.string.conversation_enable_member_invites)) },
                                leadingIcon = {
                                    Checkbox(
                                        checked = conversation?.canMembersInvite == true,
                                        onCheckedChange = null
                                    )
                                },
                                enabled = isModerator,
                                onClick = {
                                    onToggleMemberInvites(conversation?.canMembersInvite != true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.conversation_add_participants)) },
                                leadingIcon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                                enabled = canInvite,
                                onClick = {
                                    menuExpanded = false
                                    onAddParticipants()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.conversation_leave)) },
                                leadingIcon = { Icon(Icons.Filled.PersonRemove, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onLeaveConversation()
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
                        var memberMenuExpanded by rememberSaveable(member.id) { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarImage(member.name, member.avatarUrl, modifier = Modifier.size(38.dp).clickable { onOpenUserProfile(member.id) })
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(member.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (member.id in conversation.moderatorIds) {
                                    Text(stringResource(R.string.conversation_moderator), color = QuataOrange, fontSize = 12.sp)
                                }
                            }
                            if (isModerator && member.id != currentUser?.id) {
                                Box {
                                    IconButton(onClick = { memberMenuExpanded = true }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.common_open))
                                    }
                                    DropdownMenu(
                                        expanded = memberMenuExpanded,
                                        onDismissRequest = { memberMenuExpanded = false }
                                    ) {
                                        val isMemberModerator = member.id in conversation.moderatorIds
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (isMemberModerator) {
                                                        stringResource(R.string.conversation_remove_moderator)
                                                    } else {
                                                        stringResource(R.string.conversation_promote_moderator)
                                                    }
                                                )
                                            },
                                            leadingIcon = {
                                                if (isMemberModerator) {
                                                    ShieldMinusIcon()
                                                } else {
                                                    Icon(Icons.Filled.Security, contentDescription = null)
                                                }
                                            },
                                            onClick = {
                                                memberMenuExpanded = false
                                                onToggleModerator(member.id, isMemberModerator)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.conversation_block_user)) },
                                            leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) },
                                            onClick = {
                                                memberMenuExpanded = false
                                                onBlockParticipant(member.id)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.conversation_remove_participant)) },
                                            leadingIcon = { Icon(Icons.Filled.PersonRemove, contentDescription = null) },
                                            onClick = {
                                                memberMenuExpanded = false
                                                onRemoveParticipant(member.id)
                                            }
                                        )
                                    }
                                }
                            }
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
    val blockedIds = conversation?.blockedUserIds.orEmpty().toSet()
    val existingNames = conversation?.participantNames.orEmpty().map { it.lowercase() }.toSet()
    val visibleCandidates = candidates
        .filter { user ->
            user.id != currentUser?.id &&
                user.id !in existingIds &&
                user.id !in blockedIds &&
                user.displayName.lowercase() !in existingNames
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B5563), contentColor = Color.White)
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

@Composable
private fun ForwardMessageScreen(
    conversations: List<Conversation>,
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
    onSend: () -> Unit
) {
    val selectedNames = conversations
        .filter { it.id in selectedIds }
        .joinToString(", ") { it.chatDisplayTitle() }
    Column(Modifier.fillMaxSize()) {
        Surface(color = QuataSurface.copy(alpha = 0.88f), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Text(
                    stringResource(R.string.conversation_forward_to),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(conversations, key = { it.id }) { conversation ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, QuataDivider, RoundedCornerShape(16.dp))
                        .background(QuataSurface.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
                        .clickable { onToggle(conversation.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = conversation.id in selectedIds,
                        onCheckedChange = { onToggle(conversation.id) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(conversation.chatDisplayTitle(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            conversation.lastMessagePreview,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                selectedNames.ifBlank { stringResource(R.string.conversation_forward_none_selected) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = if (selectedNames.isBlank()) 0.52f else 0.92f),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                enabled = selectedIds.isNotEmpty(),
                onClick = onSend,
                modifier = Modifier
                    .size(52.dp)
                    .background(QuataOrange.copy(alpha = if (selectedIds.isNotEmpty()) 1f else 0.35f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.common_send), tint = Color.Black)
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    action: ConfirmAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val title = when (action) {
        ConfirmAction.DeleteConversation -> stringResource(R.string.conversation_delete)
        ConfirmAction.LeaveConversation -> stringResource(R.string.conversation_leave)
        ConfirmAction.DeleteMessage -> stringResource(R.string.conversation_delete_message)
        is ConfirmAction.BlockParticipant -> stringResource(R.string.conversation_block_user)
        is ConfirmAction.RemoveParticipant -> stringResource(R.string.conversation_remove_participant)
        is ConfirmAction.ToggleModerator -> if (action.isModerator) {
            stringResource(R.string.conversation_remove_moderator)
        } else {
            stringResource(R.string.conversation_promote_moderator)
        }
    }
    val body = when (action) {
        ConfirmAction.DeleteConversation -> stringResource(R.string.conversation_delete_confirm)
        ConfirmAction.LeaveConversation -> stringResource(R.string.conversation_leave_confirm)
        ConfirmAction.DeleteMessage -> stringResource(R.string.conversation_delete_message_confirm)
        is ConfirmAction.BlockParticipant -> stringResource(R.string.conversation_block_user_confirm)
        is ConfirmAction.RemoveParticipant -> stringResource(R.string.conversation_remove_participant_confirm)
        is ConfirmAction.ToggleModerator -> if (action.isModerator) {
            stringResource(R.string.conversation_remove_moderator_confirm)
        } else {
            stringResource(R.string.conversation_promote_moderator_confirm)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
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

private sealed class ConfirmAction {
    data object DeleteConversation : ConfirmAction()
    data object LeaveConversation : ConfirmAction()
    data object DeleteMessage : ConfirmAction()
    data class BlockParticipant(val userId: String) : ConfirmAction()
    data class RemoveParticipant(val userId: String) : ConfirmAction()
    data class ToggleModerator(val userId: String, val isModerator: Boolean) : ConfirmAction()
}

@Composable
private fun ShieldMinusIcon() {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Security, contentDescription = null)
        Icon(Icons.Filled.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun ChatAvatar(
    conversation: Conversation?,
    currentUser: User?,
    usersById: Map<String, User>,
    onOpenUserProfile: (String) -> Unit
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
            AvatarImage(privateUser.displayName, privateUser.avatarUrl, modifier = Modifier.size(46.dp).clickable { onOpenUserProfile(privateUser.id) })
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
    showSenderAvatar: Boolean,
    isSelected: Boolean,
    onOpenSenderProfile: () -> Unit,
    onOpenAttachment: (AttachmentPreview) -> Unit,
    onClick: () -> Unit
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
                    .clickable(onClick = onOpenSenderProfile)
                    .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(if (showSenderAvatar) 0.72f else 0.78f)
                .background(
                    when {
                        isSelected -> Color(0xFFFFF3C4)
                        message.isMine -> QuataOrange
                        else -> QuataSurface
                    },
                    RoundedCornerShape(20.dp)
                )
                .clickable(onClick = onClick)
                .padding(14.dp)
        ) {
            val textColor = if (message.isMine || isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message.senderName, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.weight(1f))
                Text(message.chatTimestampLabel(), color = textColor.copy(alpha = 0.56f), fontSize = 12.sp)
            }
            Spacer(Modifier.padding(2.dp))
            if (message.forwardedFromSenderName != null) {
                Text(
                    stringResource(R.string.conversation_forwarded_from),
                    color = textColor.copy(alpha = 0.72f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontSize = 12.sp
                )
                Spacer(Modifier.padding(2.dp))
            }
            if (message.replyToText != null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text(message.replyToSenderName.orEmpty(), fontWeight = FontWeight.Bold, color = textColor, fontSize = 12.sp)
                        Text(message.replyToText, color = textColor.copy(alpha = 0.72f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.padding(4.dp))
            }
            if (message.isDeleted) {
                Text(stringResource(R.string.conversation_deleted_message), color = textColor.copy(alpha = 0.72f))
            } else {
                if (message.text.isNotBlank()) {
                    LinkifiedMessageText(
                        text = message.text,
                        color = textColor,
                        linkColor = if (message.isMine || isSelected) Color.Black else QuataOrange
                    )
                }
                message.attachmentPreview()?.let { attachment ->
                    Spacer(Modifier.padding(4.dp))
                    if (attachment.isMedia) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenAttachment(attachment) }
                        ) {
                            AttachmentThumbnail(
                                attachment = attachment,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                            )
                        }
                    } else {
                        Surface(
                            color = Color.Black.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.clickable { onOpenAttachment(attachment) }
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AttachFile, contentDescription = null, tint = textColor)
                                Spacer(Modifier.width(8.dp))
                                Text(attachment.name, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (message.isEdited || message.isFavorite) {
                Spacer(Modifier.padding(2.dp))
                Text(
                    listOfNotNull(
                        if (message.isEdited) stringResource(R.string.conversation_edited) else null,
                        if (message.isFavorite) stringResource(R.string.conversation_favorite_marker) else null
                    ).joinToString(" · "),
                    color = textColor.copy(alpha = 0.62f),
                    fontSize = 12.sp
                )
            }
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

@Composable
private fun LinkifiedMessageText(
    text: String,
    color: Color,
    linkColor: Color
) {
    val context = LocalContext.current
    val annotatedText = remember(text, color, linkColor) {
        text.toLinkAnnotatedString(
            linkColor = linkColor
        )
    }
    if (annotatedText.getStringAnnotations(UrlAnnotationTag, 0, annotatedText.length).isEmpty()) {
        Text(text, color = color)
    } else {
        ClickableText(
            text = annotatedText,
            style = TextStyle(color = color, fontSize = 14.sp),
            onClick = { offset ->
                annotatedText
                    .getStringAnnotations(UrlAnnotationTag, offset, offset)
                    .firstOrNull()
                    ?.item
                    ?.let { context.openBrowserUrl(it) }
            }
        )
    }
}

private fun String.extractMapsUrl(): String? =
    Regex("""https://maps\.google\.com/\?q=[^\s]+""").find(this)?.value

private const val UrlAnnotationTag = "url"

private val UrlRegex = Regex("""(https?://[^\s]+|www\.[^\s]+)""")

private fun String.toLinkAnnotatedString(linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        var currentIndex = 0
        UrlRegex.findAll(this@toLinkAnnotatedString).forEach { match ->
            if (match.range.first > currentIndex) {
                append(this@toLinkAnnotatedString.substring(currentIndex, match.range.first))
            }
            val rawUrl = match.value.trimEnd('.', ',', ';', ')')
            val normalizedUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "https://$rawUrl"
            pushStringAnnotation(tag = UrlAnnotationTag, annotation = normalizedUrl)
            pushStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Bold))
            append(rawUrl)
            pop()
            pop()
            currentIndex = match.range.first + rawUrl.length
        }
        if (currentIndex < this@toLinkAnnotatedString.length) {
            append(this@toLinkAnnotatedString.substring(currentIndex))
        }
    }

private fun Message.attachmentPreview(): AttachmentPreview? {
    val uri = attachmentUri?.takeIf { it.isNotBlank() } ?: return null
    return AttachmentPreview(
        name = attachmentName ?: "archivo",
        uri = uri,
        mimeType = attachmentMimeType
    )
}

private fun currentAttachmentTimestamp(): String =
    java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
        .format(java.util.Date())

private fun Message.chatTimestampLabel(): String {
    val millis = sentAtMillis ?: return sentAt
    val now = System.currentTimeMillis()
    val elapsed = (now - millis).coerceAtLeast(0L)
    val day = 24L * 60L * 60L * 1000L
    return when {
        elapsed < day -> java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
        elapsed < 7L * day -> "hace ${elapsed / day}d"
        elapsed < 31L * day -> "hace ${elapsed / (7L * day)} sem."
        elapsed < 365L * day -> "hace ${elapsed / (31L * day)} mes"
        else -> "hace un año"
    }
}

private fun android.content.Context.displayNameForUri(uri: Uri): String {
    val name = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
    return name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "archivo" }
}

private fun android.content.Context.openMaps(url: String) {
    val uri = Uri.parse(url)
    val mapsIntent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
    runCatching { startActivity(mapsIntent) }
        .onFailure { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

private fun android.content.Context.openBrowserUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun android.content.Context.playChatSound(rawResId: Int) {
    runCatching {
        MediaPlayer.create(this, rawResId)?.apply {
            setOnCompletionListener { player -> player.release() }
            start()
        }
    }
}
