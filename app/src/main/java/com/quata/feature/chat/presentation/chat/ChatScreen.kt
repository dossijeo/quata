package com.quata.feature.chat.presentation.chat

import android.content.Context
import android.content.ClipData
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.quata.core.ui.components.CompactButtonContentPadding
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataThemeTemplate
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.MessageDeliveryState
import com.quata.core.model.User
import com.quata.core.navigation.AppDestinations
import com.quata.core.text.localizedSosMessage
import androidx.core.content.ContextCompat
import com.quata.core.media.normalizeImageOrientationInPlace
import com.quata.core.ui.components.AudioAttachmentPlayer
import com.quata.core.ui.components.AttachmentPreview
import com.quata.core.ui.components.AttachmentThumbnail
import com.quata.core.ui.components.AttachmentViewerDialog
import com.quata.core.ui.components.DocumentAttachmentPreview
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.AvatarLetter
import com.quata.core.ui.components.ClickableProfileAvatar
import com.quata.core.ui.components.CommunityEmojiPanel
import com.quata.core.ui.components.QuataAudioRecorderDialog
import com.quata.core.ui.components.QuataCameraDialog
import com.quata.core.ui.components.QuataCameraMode
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.compactButtonMinSize
import com.quata.core.ui.components.dismissCommunityEmojiPanelOnOutsideTap
import com.quata.core.ui.components.openAttachmentWithDocumentReaderOrChooser
import com.quata.core.ui.components.rememberCommunityEmojiPanelDismissState
import com.quata.core.ui.components.trackCommunityEmojiPanelBounds
import com.quata.core.ui.components.trackCommunityEmojiTriggerBounds
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.core.translation.FangTranslatorIconButton
import com.quata.core.translation.LocalQuataTranslatorModeController
import com.quata.core.translation.QuataTranslatorOverlaySource
import com.quata.core.translation.quataTranslatableText
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.conversations.ConversationCandidatePickerDialog
import com.quata.feature.chat.presentation.conversations.ConversationsUiState
import com.quata.feature.chat.presentation.chatDisplayTitle
import com.quata.feature.chat.presentation.relativeUpdatedAt
import com.quata.feature.chat.presentation.relativeTimeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import com.quata.core.designsystem.theme.QuataResolvedTheme

@Composable
fun ChatScreen(
    padding: PaddingValues,
    conversationId: String,
    repository: ChatRepository,
    onOpenUserProfile: (String) -> Unit = {},
    openingProfileUserId: String? = null,
    onOpenConversation: (String) -> Unit = {},
    focusedMessageId: String? = null,
    onFocusedMessageHandled: () -> Unit = {},
    onOpenMessageConversation: (String, String) -> Unit = { targetConversationId, _ -> onOpenConversation(targetConversationId) },
    onBack: () -> Unit,
    compactHeader: Boolean = false,
    appHeaderActions: (@Composable RowScope.() -> Unit)? = null,
    viewModel: ChatViewModel = viewModel(
        key = "chat_$conversationId",
        factory = ChatViewModel.factory(conversationId, repository, LocalContext.current)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val isAppForeground by repository.isAppForeground.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val screenScope = rememberCoroutineScope()
    val metrics = context.resources.displayMetrics
    val messagesListState = rememberLazyListState()
    val isUserDraggingMessages by messagesListState.interactionSource.collectIsDraggedAsState()
    val selectedMessage = state.messages.firstOrNull { it.id == state.selectedMessageId }
    val isFavoritesConversation = conversationId == AppDestinations.FavoriteMessagesConversationId
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }
    var lastShownError by remember { mutableStateOf<String?>(null) }
    var previousIncomingCount by remember(conversationId) { mutableStateOf(0) }
    var hasInitializedIncomingCount by remember(conversationId) { mutableStateOf(false) }
    var attachmentMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var isEmojiPickerVisible by rememberSaveable(conversationId) { mutableStateOf(false) }
    val emojiDismissState = rememberCommunityEmojiPanelDismissState {
        isEmojiPickerVisible = false
    }
    var messageFieldValue by rememberSaveable(conversationId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(state.messageText))
    }
    var isCameraVisible by remember { mutableStateOf(false) }
    var cameraAudioEnabled by remember { mutableStateOf(false) }
    var isAudioRecorderVisible by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setConversationVisible(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> viewModel.setConversationVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.setConversationVisible(true)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setConversationVisible(false)
        }
    }

    var autoPlayVoiceMessageId by rememberSaveable(conversationId) { mutableStateOf<String?>(null) }
    var activeVoiceMessageId by rememberSaveable(conversationId) { mutableStateOf<String?>(null) }
    var highlightedMessageId by rememberSaveable(conversationId) { mutableStateOf<String?>(null) }
    var highlightVisible by rememberSaveable(conversationId) { mutableStateOf(false) }
    var suppressAutoScrollForFocusedOpen by rememberSaveable(conversationId) {
        mutableStateOf(focusedMessageId != null)
    }
    var userHasDetachedFromBottom by remember(conversationId) { mutableStateOf(false) }
    var previousMessageLayout by remember(conversationId) { mutableStateOf(emptyList<ChatMessageLayoutKey>()) }
    var initialMessagePositionReady by remember(conversationId) { mutableStateOf(false) }
    var selectedAttachment by remember { mutableStateOf<AttachmentPreview?>(null) }
    val isPreparingInitialMessagePosition =
        state.messages.isNotEmpty() && focusedMessageId == null && !initialMessagePositionReady
    val lastMessageNeedsBottomBuffer = remember(context, state.messages.lastOrNull()?.text) {
        state.messages.lastOrNull()?.text?.let { text ->
            context.localizedSosMessage(text) ?: text.parseSosLocationMessage()
        } != null
    }

    LaunchedEffect(messagesListState, state.hasMoreHistory, initialMessagePositionReady, focusedMessageId) {
        if (!initialMessagePositionReady && focusedMessageId == null) return@LaunchedEffect
        snapshotFlow { messagesListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .filter { index -> index <= 2 }
            .collect { viewModel.loadOlderMessages() }
    }
    val activeComposerBannerKey = listOfNotNull(
        state.editingMessage?.id?.let { "edit:$it" }?.takeUnless { isFavoritesConversation },
        state.replyToMessage?.id?.let { "reply:$it" }?.takeUnless { isFavoritesConversation },
        state.attachmentUri?.let { "attachment:$it" }
    ).joinToString("|")
    val usersById = remember(state.participantCandidates, state.currentUser) {
        (state.participantCandidates + listOfNotNull(state.currentUser)).associateBy { it.id }
    }
    val backgroundSeed = conversationId.takeUnless { isFavoritesConversation }

    fun queueNextConsecutiveVoiceNote(finishedMessage: Message) {
        val currentIndex = state.messages.indexOfFirst {
            it.composeKey() == finishedMessage.composeKey()
        }
        val nextIndex = currentIndex + 1
        val nextMessage = state.messages.getOrNull(nextIndex)
        val nextAttachment = nextMessage?.attachmentPreview(context)
        if (
            currentIndex >= 0 &&
            nextMessage != null &&
            !nextMessage.isDeleted &&
            nextMessage.senderId == finishedMessage.senderId &&
            nextAttachment?.isAudio == true
        ) {
            activeVoiceMessageId = nextMessage.composeKey()
            autoPlayVoiceMessageId = nextMessage.composeKey()
            screenScope.launch {
                messagesListState.animateScrollToItem(nextIndex)
            }
        } else {
            if (activeVoiceMessageId == finishedMessage.id) {
                activeVoiceMessageId = null
            }
            if (autoPlayVoiceMessageId == finishedMessage.id) {
                autoPlayVoiceMessageId = null
            }
        }
    }

    DisposableEffect(conversationId) {
        onDispose {
            viewModel.cleanupEmptyConversationIfNeeded()
        }
    }

    LaunchedEffect(state.messageText) {
        if (state.messageText != messageFieldValue.text) {
            messageFieldValue = TextFieldValue(
                text = state.messageText,
                selection = TextRange(state.messageText.length)
            )
        }
    }

    LaunchedEffect(focusedMessageId) {
        if (focusedMessageId != null) {
            suppressAutoScrollForFocusedOpen = true
        }
    }

    LaunchedEffect(messagesListState, isUserDraggingMessages) {
        if (isUserDraggingMessages) {
            snapshotFlow { messagesListState.isAtConversationBottom() }
                .distinctUntilChanged()
                .collect { isAtBottom -> userHasDetachedFromBottom = !isAtBottom }
        } else if (messagesListState.isAtConversationBottom()) {
            userHasDetachedFromBottom = false
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onEvent(ChatUiEvent.AttachmentSelected(it.toString(), context.displayNameForUri(it), context.contentResolver.getType(it))) }
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onEvent(ChatUiEvent.AttachmentSelected(it.toString(), context.displayNameForUri(it), context.contentResolver.getType(it))) }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val hasCamera = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            cameraAudioEnabled = grants[Manifest.permission.RECORD_AUDIO] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            isCameraVisible = true
        } else {
            Toast.makeText(context, context.getString(R.string.conversation_camera_permission), Toast.LENGTH_SHORT).show()
        }
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            isAudioRecorderVisible = true
        } else {
            Toast.makeText(context, context.getString(R.string.conversation_audio_permission), Toast.LENGTH_SHORT).show()
        }
    }
    fun launchCameraAttachment() {
        val requiredPermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            cameraAudioEnabled = true
            isCameraVisible = true
        } else {
            cameraPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
    fun launchAudioRecorder() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            isAudioRecorderVisible = true
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val template = quataTheme()
    val isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(
        state.typingProfileIds,
        state.messages.size,
        state.isLoadingOlderMessages,
        isImeVisible
    ) {
        // The typing bubble is a real final item. Keep it above the composer when the
        // user is already following the conversation, including after the IME resizes it.
        if (state.typingProfileIds.isNotEmpty() && !userHasDetachedFromBottom) {
            delay(80L)
            val typingItemIndex = state.messages.size + if (state.isLoadingOlderMessages) 1 else 0
            messagesListState.animateScrollToItem(typingItemIndex)
        }
    }
    val emojiGridMaxHeight = when {
        isLandscapeLayout -> 128.dp
        isImeVisible -> 168.dp
        else -> 220.dp
    }
    fun setEmojiPickerVisible(visible: Boolean) {
        isEmojiPickerVisible = visible
        if (visible) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }
    val backgroundImage = backgroundSeed?.let { seed ->
        val backgroundTemplateId = "${template.id}-clouds-v3"
        rememberProceduralChatBackground(
            conversationName = seed,
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            template = template,
            templateId = backgroundTemplateId
        )
    }

    if (isCameraVisible) {
        QuataCameraDialog(
            mode = QuataCameraMode.Dual,
            audioEnabled = cameraAudioEnabled,
            onDismiss = {
                isCameraVisible = false
            },
            onPhotoCaptured = { uri, name, mimeType ->
                isCameraVisible = false
                screenScope.launch {
                    val normalizedUri = withContext(Dispatchers.IO) {
                        context.normalizeImageOrientationInPlace(uri).toString()
                    }
                    viewModel.onEvent(
                        ChatUiEvent.AttachmentSelected(
                            uri = normalizedUri,
                            name = name,
                            mimeType = mimeType
                        )
                    )
                }
            },
            onVideoCaptured = { uri, name, mimeType ->
                isCameraVisible = false
                viewModel.onEvent(
                    ChatUiEvent.AttachmentSelected(
                        uri = uri.toString(),
                        name = name,
                        mimeType = mimeType
                    )
                )
            }
        )
    }

    if (isAudioRecorderVisible) {
        QuataAudioRecorderDialog(
            onDismiss = { isAudioRecorderVisible = false },
            onAudioRecorded = { uri, name, mimeType ->
                isAudioRecorderVisible = false
                viewModel.onEvent(
                    ChatUiEvent.AttachmentSelected(
                        uri = uri.toString(),
                        name = name,
                        mimeType = mimeType
                    )
                )
            }
        )
    }

    QuataScreen(padding) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(template.colors.background)
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
                        .background(template.colors.scrim)
                )
            }
            if (attachmentMenuExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { attachmentMenuExpanded = false }
                        )
                )
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .dismissCommunityEmojiPanelOnOutsideTap(
                        isVisible = !isFavoritesConversation && isEmojiPickerVisible,
                        state = emojiDismissState
                    )
                    .imePadding()
            ) {
                if (isFavoritesConversation) {
                    FavoriteMessagesHeader(onBack = onBack)
                } else {
                    ChatHeader(
                            conversation = state.conversation,
                            currentUser = state.currentUser,
                            usersById = usersById,
                            openingProfileUserId = openingProfileUserId,
                            onOpenUserProfile = onOpenUserProfile,
                            selectedMessage = selectedMessage,
                            onClearSelection = { viewModel.onEvent(ChatUiEvent.MessageSelected(null)) },
                            onCopySelected = {
                                selectedMessage?.let { message ->
                                    screenScope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(ClipData.newPlainText("Qüata", message.text))
                                        )
                                    }
                                }
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
                            onReportSelected = { confirmAction = ConfirmAction.ReportMessage },
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
                            },
                            isTranslatorAvailable = state.messages.isNotEmpty(),
                            compact = compactHeader,
                            appHeaderActions = appHeaderActions
                    )
                    if (state.isConversationActionInProgress) {
                        ConversationActionProgressBar()
                    }
                }
                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            state = messagesListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = if (isPreparingInitialMessagePosition) 0f else 1f
                                }
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(
                                top = 14.dp,
                                bottom = if (lastMessageNeedsBottomBuffer) 48.dp else 0.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (state.isLoadingOlderMessages) {
                                item(key = "older_messages_loading") { ConversationActionProgressBar() }
                            }
                            if (state.isLoading && state.messages.isEmpty()) {
                                items(6) { index ->
                                    ChatMessageSkeleton(
                                        isMine = index % 2 == 0,
                                        pulseDelayMillis = index * 90
                                    )
                                }
                            } else {
                                items(
                                    items = state.messages,
                                    key = { message -> message.composeKey() }
                                ) { message ->
                                    MessageBubble(
                                        message = message,
                                        sender = usersById[message.senderId],
                                        showSenderAvatar = state.conversation?.isGroup == true && !message.isMine,
                                        isSelected = message.id == state.selectedMessageId ||
                                            (message.id == highlightedMessageId && highlightVisible),
                                        isSenderProfileLoading = openingProfileUserId == message.senderId,
                                        autoPlayVoiceNote = autoPlayVoiceMessageId == message.composeKey(),
                                        pauseVoiceNote = activeVoiceMessageId != null && activeVoiceMessageId != message.composeKey(),
                                        onOpenSenderProfile = { onOpenUserProfile(message.senderId) },
                                        onOpenAttachment = { attachment ->
                                            if (attachment.isMedia) {
                                                selectedAttachment = attachment
                                            } else {
                                                context.openAttachmentWithDocumentReaderOrChooser(
                                                    attachment = attachment,
                                                    isDarkMode = template.resolvedTheme == QuataResolvedTheme.Dark
                                                )
                                            }
                                        },
                                        onSwipeReply = {
                                            if (!isFavoritesConversation && !message.isLocalEcho) {
                                                viewModel.onEvent(ChatUiEvent.MessageSelected(message.id))
                                                viewModel.onEvent(ChatUiEvent.StartReply)
                                            }
                                        },
                                        onVoiceNoteStarted = {
                                            activeVoiceMessageId = message.composeKey()
                                            if (autoPlayVoiceMessageId != message.composeKey()) {
                                                autoPlayVoiceMessageId = null
                                            }
                                        },
                                        onVoiceNoteEnded = { queueNextConsecutiveVoiceNote(message) },
                                        onClick = {
                                            if (message.isLocalEcho) {
                                                if (message.deliveryState == MessageDeliveryState.Failed) {
                                                    message.clientMessageId?.let(viewModel::retryPendingMessage)
                                                }
                                            } else if (isFavoritesConversation) {
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
                                if (state.typingProfileIds.isNotEmpty()) {
                                    item(key = "typing_indicator") {
                                        TypingIndicatorBubble(
                                            names = state.typingProfileIds.mapNotNull { usersById[it]?.displayName }
                                        )
                                    }
                                }
                            }
                        }
                        if (isPreparingInitialMessagePosition) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentPadding = PaddingValues(top = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                userScrollEnabled = false
                            ) {
                                items(6, key = { index -> "initial_message_skeleton_$index" }) { index ->
                                    ChatMessageSkeleton(
                                        isMine = index % 2 == 0,
                                        pulseDelayMillis = index * 90
                                    )
                                }
                            }
                        }
                        state.pendingAttachmentPreview()?.let { attachment ->
                            PendingChatAttachmentOverlay(
                                attachment = attachment,
                                template = template,
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(16.dp)
                                    .zIndex(1f),
                                onClear = { viewModel.onEvent(ChatUiEvent.ClearAttachment) },
                                onOpen = {
                                    if (attachment.isMedia) {
                                        selectedAttachment = attachment
                                    } else if (!attachment.isAudio) {
                                        context.openAttachmentWithDocumentReaderOrChooser(
                                            attachment = attachment,
                                            isDarkMode = template.resolvedTheme == QuataResolvedTheme.Dark
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
                    if (!isFavoritesConversation && isEmojiPickerVisible && !isLandscapeLayout) {
                        CommunityEmojiPanel(
                            onEmojiClick = { emoji ->
                                val updated = messageFieldValue.insertAtSelection(emoji)
                                messageFieldValue = updated
                                viewModel.onEvent(ChatUiEvent.MessageChanged(updated.text))
                            },
                            gridMaxHeight = emojiGridMaxHeight,
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .trackCommunityEmojiPanelBounds(emojiDismissState)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (!isFavoritesConversation && attachmentMenuExpanded) {
                        ChatAttachmentQuickPanel(
                            onPickFile = {
                                attachmentMenuExpanded = false
                                filePicker.launch("*/*")
                            },
                            onPickGallery = {
                                attachmentMenuExpanded = false
                                mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    if (!isFavoritesConversation) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .requiredHeightIn(min = 78.dp)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = messageFieldValue,
                                onValueChange = {
                                    messageFieldValue = it
                                    viewModel.onEvent(ChatUiEvent.MessageChanged(it.text))
                                },
                                placeholder = { Text(stringResource(R.string.conversation_message)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .requiredHeightIn(min = 62.dp)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            attachmentMenuExpanded = false
                                            if (isEmojiPickerVisible) {
                                                isEmojiPickerVisible = false
                                            }
                                        }
                                    },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences
                                ),
                                leadingIcon = {
                                    CompactIconButton(
                                        onClick = {
                                            attachmentMenuExpanded = false
                                            setEmojiPickerVisible(!isEmojiPickerVisible)
                                        },
                                        modifier = Modifier.trackCommunityEmojiTriggerBounds(emojiDismissState)
                                    ) {
                                        CompactIcon(
                                            imageVector = Icons.Filled.InsertEmoticon,
                                            contentDescription = stringResource(R.string.comments_show_emojis),
                                            tint = Color(0xFFFFC55C)
                                        )
                                    }
                                },
                                trailingIcon = {
                                    CompactIconButton(
                                        onClick = {
                                            isEmojiPickerVisible = false
                                            keyboardController?.hide()
                                            focusManager.clearFocus(force = true)
                                            attachmentMenuExpanded = !attachmentMenuExpanded
                                        }
                                    ) {
                                        CompactIcon(
                                            Icons.Filled.AttachFile,
                                            contentDescription = stringResource(R.string.conversation_attachment)
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            CompactIconButton(
                                onClick = {
                                    isEmojiPickerVisible = false
                                    attachmentMenuExpanded = false
                                    launchCameraAttachment()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                CompactIcon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.conversation_attach_camera))
                            }
                            Spacer(Modifier.width(6.dp))
                            val canSend = messageFieldValue.text.isNotBlank() || state.attachmentUri != null
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(QuataOrange)
                                    .clickable {
                                        if (canSend) {
                                            context.playChatSound(R.raw.sent)
                                            isEmojiPickerVisible = false
                                            attachmentMenuExpanded = false
                                            viewModel.onEvent(ChatUiEvent.Send)
                                        } else {
                                            isEmojiPickerVisible = false
                                            attachmentMenuExpanded = false
                                            launchAudioRecorder()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                CompactIcon(
                                    imageVector = if (canSend) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic,
                                    contentDescription = stringResource(if (canSend) R.string.common_send else R.string.conversation_attach_audio),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            if (!isFavoritesConversation && isEmojiPickerVisible && isLandscapeLayout) {
                CommunityEmojiPanel(
                    onEmojiClick = { emoji ->
                        val updated = messageFieldValue.insertAtSelection(emoji)
                        messageFieldValue = updated
                        viewModel.onEvent(ChatUiEvent.MessageChanged(updated.text))
                    },
                    gridMaxHeight = emojiGridMaxHeight,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 94.dp, start = 24.dp)
                        .fillMaxWidth(0.58f)
                        .trackCommunityEmojiPanelBounds(emojiDismissState)
                )
            }
        }
    }

    val currentMessageLayout = remember(state.messages) { state.messages.map(Message::chatLayoutKey) }
    LaunchedEffect(conversationId, currentMessageLayout, state.isLoading) {
        if (currentMessageLayout.isEmpty()) {
            previousMessageLayout = emptyList()
            return@LaunchedEffect
        }
        val shouldFollowUpdate = shouldFollowChatLayoutUpdate(
            previous = previousMessageLayout,
            current = currentMessageLayout,
            userHasDetachedFromBottom = userHasDetachedFromBottom
        )
        if (
            shouldFollowUpdate &&
            focusedMessageId == null &&
            !suppressAutoScrollForFocusedOpen
        ) {
            userHasDetachedFromBottom = false
            messagesListState.scrollToConversationBottom(
                lastIndex = currentMessageLayout.lastIndex,
                lastItemKey = currentMessageLayout.last().composeKey
            )
        }
        previousMessageLayout = currentMessageLayout
        if (
            !state.isLoading &&
            focusedMessageId == null &&
            !suppressAutoScrollForFocusedOpen
        ) {
            initialMessagePositionReady = true
        }
    }

    LaunchedEffect(focusedMessageId, state.messages) {
        val targetMessageId = focusedMessageId ?: return@LaunchedEffect
        val targetIndex = state.messages.indexOfFirst { it.id == targetMessageId }
        if (targetIndex < 0) return@LaunchedEffect
        suppressAutoScrollForFocusedOpen = true
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

    LaunchedEffect(activeComposerBannerKey, state.messages.size) {
        if (
            activeComposerBannerKey.isBlank() ||
            state.messages.isEmpty() ||
            focusedMessageId != null ||
            suppressAutoScrollForFocusedOpen
        ) return@LaunchedEffect
        delay(80L)
        messagesListState.animateScrollToItem(state.messages.lastIndex)
    }

    LaunchedEffect(state.shouldCloseConversation) {
        if (state.shouldCloseConversation) onBack()
    }

    LaunchedEffect(state.messages) {
        val incomingCount = state.messages.count { !it.isMine }
        if (hasInitializedIncomingCount && isAppForeground && incomingCount > previousIncomingCount) {
            context.playChatSound(R.raw.notification)
        }
        previousIncomingCount = incomingCount
        hasInitializedIncomingCount = true
    }

    LaunchedEffect(state.error) {
        val error = state.error
        if (!error.isNullOrBlank() && error != lastShownError) {
            lastShownError = error
            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.takeIf { it.isNotBlank() }?.let { notice ->
            Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
            viewModel.onEvent(ChatUiEvent.ClearNotice)
        }
    }

    if (state.isAddParticipantsOpen) {
        ConversationCandidatePickerDialog(
            state = ConversationsUiState(
                currentUser = state.currentUser,
                conversationCandidates = state.participantConversationCandidates,
                candidateQuery = state.participantCandidateQuery,
                isCandidateInitialLoading = state.isParticipantCandidateInitialLoading,
                isCandidatePageLoading = state.isParticipantCandidatePageLoading,
                candidateHasMore = state.participantCandidateHasMore,
                candidateNextOffset = state.participantCandidateNextOffset,
                candidateActorNeighborhood = state.participantCandidateActorNeighborhood,
                openingCandidateProfileId = state.addingCandidateProfileId,
                candidateError = state.participantCandidateError
            ),
            onSearchChange = viewModel::onParticipantCandidateQueryChanged,
            onLoadMore = viewModel::loadMoreParticipantCandidates,
            onOpenCandidate = { candidate -> viewModel.addConversationCandidateParticipant(candidate.profileId) },
            onDismiss = { viewModel.onEvent(ChatUiEvent.CloseAddParticipants) },
            title = stringResource(R.string.conversation_add_participants_title),
            actionIcon = Icons.Filled.PersonAdd,
            actionContentDescription = stringResource(R.string.conversation_add_participants),
            excludedProfileIds = state.conversation?.participantIds.orEmpty().toSet()
        )
    }

    if (state.isForwardDialogOpen) {
        val selectedForwardIds = state.selectedForwardProfileIds.toSet()
        val selectedForwardNames = state.forwardConversationCandidates
            .filter { it.profileId in selectedForwardIds }
            .joinToString(", ") { it.displayName }
        ConversationCandidatePickerDialog(
            state = ConversationsUiState(
                currentUser = state.currentUser,
                conversationCandidates = state.forwardConversationCandidates,
                candidateQuery = state.forwardCandidateQuery,
                isCandidateInitialLoading = state.isForwardCandidateInitialLoading,
                isCandidatePageLoading = state.isForwardCandidatePageLoading,
                candidateHasMore = state.forwardCandidateHasMore,
                candidateNextOffset = state.forwardCandidateNextOffset,
                candidateActorNeighborhood = state.forwardCandidateActorNeighborhood,
                candidateError = state.forwardCandidateError
            ),
            onSearchChange = viewModel::onForwardCandidateQueryChanged,
            onLoadMore = viewModel::loadMoreForwardConversationCandidates,
            onOpenCandidate = { candidate -> viewModel.onEvent(ChatUiEvent.ForwardProfileToggled(candidate.profileId)) },
            onDismiss = { viewModel.onEvent(ChatUiEvent.CloseForwardDialog) },
            title = stringResource(R.string.conversation_forward_to),
            selectedCandidateIds = selectedForwardIds,
            onToggleCandidate = { candidate -> viewModel.onEvent(ChatUiEvent.ForwardProfileToggled(candidate.profileId)) },
            onConfirmSelection = {
                if (state.selectedForwardProfileIds.isNotEmpty()) {
                    viewModel.onEvent(ChatUiEvent.SendForward)
                    Toast.makeText(context, context.getString(R.string.conversation_forward_sent), Toast.LENGTH_SHORT).show()
                }
            },
            confirmEnabled = state.selectedForwardProfileIds.isNotEmpty() && !state.isConversationActionInProgress,
            selectionSummary = selectedForwardNames,
            confirmIcon = Icons.AutoMirrored.Filled.Send,
            confirmContentDescription = stringResource(R.string.common_send)
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
                    }
                    ConfirmAction.LeaveConversation -> {
                        viewModel.onEvent(ChatUiEvent.LeaveConversation)
                    }
                    ConfirmAction.DeleteMessage -> viewModel.onEvent(ChatUiEvent.DeleteSelectedMessage)
                    ConfirmAction.ReportMessage -> viewModel.onEvent(ChatUiEvent.ReportSelectedMessage)
                    is ConfirmAction.BlockParticipant -> viewModel.onEvent(ChatUiEvent.BlockParticipant(currentAction.userId))
                    is ConfirmAction.RemoveParticipant -> viewModel.onEvent(ChatUiEvent.RemoveParticipant(currentAction.userId))
                    is ConfirmAction.ToggleModerator -> {
                        viewModel.onEvent(
                            if (currentAction.isModerator) {
                                ChatUiEvent.DemoteModerator(currentAction.userId)
                            } else {
                                ChatUiEvent.PromoteModerator(currentAction.userId)
                            }
                        )
                    }
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
    height: Int,
    template: QuataThemeTemplate,
    templateId: String = template.id
): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    var image by remember(conversationName, width, height, templateId) {
        mutableStateOf(
            ProceduralChatBackground.cachedBitmap(
                context = context,
                conversationName = conversationName,
                templateId = templateId,
                width = width,
                height = height
            )
        )
    }

    LaunchedEffect(conversationName, width, height, templateId) {
        if (image != null) return@LaunchedEffect
        image = withContext(Dispatchers.IO) {
            ProceduralChatBackground.generateIfNeeded(
                context = context,
                conversationName = conversationName,
                templateId = templateId,
                palettes = template.colors.chatBackgroundPalettes,
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
    val template = quataTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 10.dp, end = 12.dp)
            .background(template.colors.surface.copy(alpha = 0.92f), RoundedCornerShape(14.dp))
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
private fun ChatAttachmentQuickPanel(
    onPickFile: () -> Unit,
    onPickGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Surface(
        color = template.colors.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChatAttachmentQuickAction(
                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                label = stringResource(R.string.conversation_attach_file),
                onClick = onPickFile,
                modifier = Modifier.weight(1f)
            )
            ChatAttachmentQuickAction(
                icon = Icons.Filled.PhotoLibrary,
                label = stringResource(R.string.conversation_attach_gallery),
                onClick = onPickGallery,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ChatAttachmentQuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(template.colors.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            CompactIcon(icon, contentDescription = null, tint = template.colors.accent)
        }
        Text(
            text = label,
            color = template.colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PendingChatAttachmentOverlay(
    attachment: AttachmentPreview,
    template: QuataThemeTemplate,
    onClear: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = template.colors.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onOpen)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    attachment.isAudio -> AudioAttachmentPlayer(
                        attachment = attachment,
                        textColor = template.colors.textPrimary
                    )
                    attachment.isMedia -> AttachmentThumbnail(
                        attachment = attachment,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 360.dp)
                    )
                    else -> DocumentAttachmentPreview(
                        attachment = attachment,
                        iconTint = template.colors.accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 360.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    attachment.name,
                    color = template.colors.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            CompactIconButton(
                onClick = onClear,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.12f))
            ) {
                CompactIcon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel))
            }
        }
    }
}

private fun ChatUiState.pendingAttachmentPreview(): AttachmentPreview? {
    val uri = attachmentUri ?: return null
    return AttachmentPreview(
        name = attachmentName?.takeIf { it.isNotBlank() } ?: uri.substringAfterLast('/'),
        uri = uri,
        mimeType = attachmentMimeType
    )
}

@Composable
private fun FavoriteMessagesHeader(onBack: () -> Unit) {
    val template = quataTheme()
    Surface(color = template.colors.surface.copy(alpha = 0.92f), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactIconButton(onClick = onBack) {
                CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(template.colors.accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                CompactIcon(Icons.Filled.Star, contentDescription = null, tint = template.colors.accent)
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.conversation_favorites_title), fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ConversationActionProgressBar() {
    val transition = rememberInfiniteTransition(label = "conversation_action_progress")
    val progress by transition.animateFloat(
        initialValue = -0.45f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 960),
            repeatMode = RepeatMode.Restart
        ),
        label = "conversation_action_progress_offset"
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(QuataOrange.copy(alpha = 0.10f))
    ) {
        val segmentWidth = maxWidth * 0.46f
        Box(
            modifier = Modifier
                .width(segmentWidth)
                .height(3.dp)
                .offset(x = maxWidth * progress)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            QuataOrange.copy(alpha = 0.95f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun ChatHeader(
    conversation: Conversation?,
    currentUser: User?,
    usersById: Map<String, User>,
    openingProfileUserId: String?,
    onOpenUserProfile: (String) -> Unit,
    selectedMessage: Message?,
    onClearSelection: () -> Unit,
    onCopySelected: () -> Unit,
    onReplySelected: () -> Unit,
    onForwardSelected: () -> Unit,
    onEditSelected: () -> Unit,
    onToggleFavoriteSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onReportSelected: () -> Unit,
    onBack: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onToggleMemberInvites: (Boolean) -> Unit,
    onAddParticipants: () -> Unit,
    onToggleModerator: (String, Boolean) -> Unit,
    onBlockParticipant: (String) -> Unit,
    onRemoveParticipant: (String) -> Unit,
    onLeaveConversation: () -> Unit,
    onHideConversation: () -> Unit,
    isTranslatorAvailable: Boolean,
    compact: Boolean = false,
    appHeaderActions: (@Composable RowScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val title = conversation?.chatDisplayTitle().orEmpty().ifBlank { stringResource(R.string.nav_chats) }
    val isGroup = conversation?.isGroup == true
    val members = conversation.membersForDisplay(currentUser, usersById, context)
    val isModerator = currentUser?.id != null && currentUser.id in conversation?.moderatorIds.orEmpty()
    val canInvite = isModerator || conversation?.canMembersInvite == true
    val template = quataTheme()
    val headerVerticalPadding = if (compact) 6.dp else 10.dp
    Surface(color = template.colors.surface.copy(alpha = 0.92f), modifier = Modifier.fillMaxWidth()) {
        Column {
            if (selectedMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = headerVerticalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactIconButton(onClick = onClearSelection) {
                        CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Spacer(Modifier.weight(1f))
                    CompactIconButton(onClick = onCopySelected) {
                        CompactIcon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.conversation_copy_message))
                    }
                    CompactIconButton(onClick = onReplySelected) {
                        CompactIcon(Icons.AutoMirrored.Filled.Reply, contentDescription = stringResource(R.string.conversation_reply_message), tint = template.colors.textPrimary)
                    }
                    CompactIconButton(onClick = onForwardSelected) {
                        CompactIcon(Icons.AutoMirrored.Filled.Forward, contentDescription = stringResource(R.string.conversation_forward_message), tint = template.colors.textPrimary)
                    }
                    if (selectedMessage.isMine && !selectedMessage.isDeleted) {
                        CompactIconButton(onClick = onEditSelected) {
                            CompactIcon(Icons.Filled.Edit, contentDescription = stringResource(R.string.conversation_edit_message))
                        }
                    }
                    if (!selectedMessage.isMine && !selectedMessage.isDeleted) {
                        CompactIconButton(onClick = onReportSelected) {
                            CompactIcon(Icons.Filled.Flag, contentDescription = stringResource(R.string.moderation_report))
                        }
                    }
                    CompactIconButton(onClick = onToggleFavoriteSelected) {
                        CompactIcon(
                            if (selectedMessage.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = stringResource(R.string.conversation_favorite_message)
                        )
                    }
                    if (selectedMessage.isMine && !selectedMessage.isDeleted) {
                        CompactIconButton(onClick = onDeleteSelected) {
                            CompactIcon(Icons.Filled.Delete, contentDescription = stringResource(R.string.conversation_delete_message))
                        }
                    }
                    if (compact) {
                        Spacer(Modifier.width(120.dp))
                    }
                }
            } else {
                val translatorModeController = LocalQuataTranslatorModeController.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isGroup) { expanded = !expanded }
                        .padding(horizontal = 8.dp, vertical = headerVerticalPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactIconButton(onClick = onBack) {
                        CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    ChatAvatar(
                        conversation = conversation,
                        currentUser = currentUser,
                        usersById = usersById,
                        openingProfileUserId = openingProfileUserId,
                        onOpenUserProfile = onOpenUserProfile,
                        compact = compact
                    )
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
                    appHeaderActions?.invoke(this)
                    FangTranslatorIconButton(
                        onClick = { view ->
                            translatorModeController.activate(view, QuataTranslatorOverlaySource.Chat)
                        },
                        enabled = isTranslatorAvailable,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                    Box {
                        CompactIconButton(onClick = { menuExpanded = true }) {
                            CompactIcon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.common_open))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (conversation?.isMuted == true) stringResource(R.string.conversation_reactivate_notifications) else stringResource(R.string.conversation_mute)) },
                                leadingIcon = {
                                    CompactIcon(
                                        if (conversation?.isMuted == true) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
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
                                    menuExpanded = false
                                    onToggleMemberInvites(conversation?.canMembersInvite != true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.conversation_add_participants)) },
                                leadingIcon = { CompactIcon(Icons.Filled.PersonAdd, contentDescription = null) },
                                enabled = canInvite,
                                onClick = {
                                    menuExpanded = false
                                    onAddParticipants()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.conversation_leave)) },
                                leadingIcon = { CompactIcon(Icons.Filled.PersonRemove, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onLeaveConversation()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.conversation_delete)) },
                                leadingIcon = { CompactIcon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onHideConversation()
                                }
                            )
                        }
                    }
                    if (compact) {
                        Spacer(Modifier.width(120.dp))
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
                            if (member.canOpenProfile) {
                                ClickableProfileAvatar(
                                    name = member.name,
                                    avatarUrl = member.avatarUrl,
                                    profileId = member.id,
                                    isLoading = openingProfileUserId == member.id,
                                    onClick = { onOpenUserProfile(member.id) },
                                    modifier = Modifier.size(38.dp)
                                )
                            } else {
                                AvatarImage(
                                    name = member.name,
                                    avatarUrl = member.avatarUrl,
                                    profileId = member.id,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(member.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (member.id in conversation.moderatorIds) {
                                    Text(stringResource(R.string.conversation_moderator), color = QuataOrange, fontSize = 12.sp)
                                }
                            }
                            if (isModerator && member.id != currentUser?.id) {
                                Box {
                                    CompactIconButton(onClick = { memberMenuExpanded = true }) {
                                        CompactIcon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.common_open))
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
                                                    CompactIcon(Icons.Filled.Security, contentDescription = null)
                                                }
                                            },
                                            onClick = {
                                                memberMenuExpanded = false
                                                onToggleModerator(member.id, isMemberModerator)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.conversation_block_user)) },
                                            leadingIcon = { CompactIcon(Icons.Filled.Block, contentDescription = null) },
                                            onClick = {
                                                memberMenuExpanded = false
                                                onBlockParticipant(member.id)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.conversation_remove_participant)) },
                                            leadingIcon = { CompactIcon(Icons.Filled.PersonRemove, contentDescription = null) },
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
private fun ConfirmDialog(
    action: ConfirmAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val title = when (action) {
        ConfirmAction.DeleteConversation -> stringResource(R.string.conversation_delete)
        ConfirmAction.LeaveConversation -> stringResource(R.string.conversation_leave)
        ConfirmAction.DeleteMessage -> stringResource(R.string.conversation_delete_message)
        ConfirmAction.ReportMessage -> stringResource(R.string.moderation_report_message_title)
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
        ConfirmAction.ReportMessage -> stringResource(R.string.moderation_report_message_confirm)
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
            avatarUrl = user?.avatarUrl ?: participantAvatarUrls.getOrNull(index),
            label = if (userId == currentUser?.id) context.getString(R.string.conversation_you_suffix, name) else name,
            canOpenProfile = !userId.startsWith("wp:")
        )
    }.distinctBy { it.id }
}

private fun String.participantLookupKey(): String =
    trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")

private data class ChatMemberDisplay(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val label: String,
    val canOpenProfile: Boolean
)

private sealed class ConfirmAction {
    data object DeleteConversation : ConfirmAction()
    data object LeaveConversation : ConfirmAction()
    data object DeleteMessage : ConfirmAction()
    data object ReportMessage : ConfirmAction()
    data class BlockParticipant(val userId: String) : ConfirmAction()
    data class RemoveParticipant(val userId: String) : ConfirmAction()
    data class ToggleModerator(val userId: String, val isModerator: Boolean) : ConfirmAction()
}

@Composable
private fun ShieldMinusIcon() {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        CompactIcon(Icons.Filled.Security, contentDescription = null)
        CompactIcon(Icons.Filled.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun ChatAvatar(
    conversation: Conversation?,
    currentUser: User?,
    usersById: Map<String, User>,
    openingProfileUserId: String?,
    onOpenUserProfile: (String) -> Unit,
    compact: Boolean = false
) {
    val template = quataTheme()
    val privateUserIndex = conversation?.participantIds
        ?.indexOfFirst { it != currentUser?.id }
        ?: -1
    val privateUserId = conversation?.participantIds?.getOrNull(privateUserIndex)
    val privateUser = privateUserId?.let { usersById[it] }
    val privateUserName = privateUser?.displayName
        ?: conversation?.participantNames?.getOrNull(privateUserIndex)
        ?: conversation?.title.orEmpty().ifBlank { "C" }
    val privateAvatarUrl = privateUser?.avatarUrl
        ?: conversation?.participantAvatarUrls?.getOrNull(privateUserIndex)
        ?: conversation?.avatarUrl
    val resolvedPrivateUser = privateUser ?: usersById.findUserByDisplayIdentity(privateUserName, privateAvatarUrl)
    val resolvedPrivateUserId = resolvedPrivateUser?.id ?: privateUserId?.takeUnless { it.startsWith("wp:") }
    val canOpenPrivateProfile = !resolvedPrivateUserId.isNullOrBlank()
    val containerSize = if (compact) 44.dp else 52.dp
    val avatarSize = if (compact) 38.dp else 46.dp
    Box(modifier = Modifier.size(containerSize), contentAlignment = Alignment.Center) {
        if (conversation?.isGroup == true || conversation?.isEmergency == true) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(if (conversation?.isEmergency == true) template.colors.sosSurface else template.colors.accent.copy(alpha = 0.22f))
                    .border(1.dp, template.colors.accent.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (conversation?.isEmergency == true) {
                    Text(stringResource(R.string.common_sos), color = template.colors.textPrimary, fontWeight = FontWeight.ExtraBold)
                } else {
                    CompactIcon(Icons.Filled.Group, contentDescription = null, tint = template.colors.textPrimary)
                }
            }
        } else if (canOpenPrivateProfile) {
            val profileId = resolvedPrivateUserId.orEmpty()
            ClickableProfileAvatar(
                name = resolvedPrivateUser?.displayName ?: privateUserName,
                avatarUrl = resolvedPrivateUser?.avatarUrl ?: privateAvatarUrl,
                profileId = profileId,
                isLoading = openingProfileUserId == profileId,
                onClick = { onOpenUserProfile(profileId) },
                modifier = Modifier.size(avatarSize)
            )
        } else {
            AvatarImage(
                name = privateUserName,
                avatarUrl = privateAvatarUrl,
                profileId = resolvedPrivateUserId,
                modifier = Modifier.size(avatarSize)
            )
        }
        if (conversation?.isMuted == true) {
            MutedConversationBadge(Modifier.align(Alignment.TopEnd))
        }
    }
}

private fun Map<String, User>.findUserByDisplayIdentity(name: String, avatarUrl: String?): User? {
    avatarUrl?.takeIf { it.isNotBlank() }?.let { expectedAvatar ->
        values.firstOrNull { user -> user.avatarUrl?.equals(expectedAvatar, ignoreCase = true) == true }?.let { return it }
    }
    val expectedName = name.normalizedParticipantName().takeIf { it.isNotBlank() } ?: return null
    values.firstOrNull { it.displayName.normalizedParticipantName() == expectedName }?.let { return it }
    if (expectedName.length < 5) return null
    val fuzzyMatches = values.filter { user ->
        val candidateName = user.displayName.normalizedParticipantName()
        candidateName.length >= 5 &&
            (expectedName.startsWith(candidateName) || candidateName.startsWith(expectedName))
    }
    return fuzzyMatches.singleOrNull()
}

private fun String.normalizedParticipantName(): String =
    trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")

@Composable
private fun MutedConversationBadge(modifier: Modifier = Modifier) {
    val template = quataTheme()
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(template.colors.surfaceRaised)
            .border(1.dp, template.colors.divider, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("\uD83D\uDD15", fontSize = 9.sp)
    }
}

@Composable
private fun ChatMessageSkeleton(
    isMine: Boolean,
    pulseDelayMillis: Int
) {
    val template = quataTheme()
    val transition = rememberInfiniteTransition(label = "chat_message_skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.48f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 860, delayMillis = pulseDelayMillis),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chat_message_skeleton_alpha"
    )
    val bubbleColor = if (isMine) template.colors.accent.copy(alpha = 0.12f + 0.12f * pulse) else template.colors.surface.copy(alpha = 0.28f + 0.20f * pulse)
    val lineColor = template.colors.textPrimary.copy(alpha = 0.07f + 0.13f * pulse)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        if (!isMine) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, end = 8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(template.colors.surface.copy(alpha = 0.28f + 0.20f * pulse))
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(0.76f)
                .clip(RoundedCornerShape(22.dp))
                .background(bubbleColor)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(lineColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(lineColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(lineColor.copy(alpha = lineColor.alpha * 0.82f))
            )
        }
        if (isMine) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp, top = 4.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(template.colors.accent.copy(alpha = 0.08f + 0.10f * pulse))
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    sender: User?,
    showSenderAvatar: Boolean,
    isSelected: Boolean,
    isSenderProfileLoading: Boolean,
    autoPlayVoiceNote: Boolean,
    pauseVoiceNote: Boolean,
    onOpenSenderProfile: () -> Unit,
    onOpenAttachment: (AttachmentPreview) -> Unit,
    onSwipeReply: () -> Unit,
    onVoiceNoteStarted: () -> Unit,
    onVoiceNoteEnded: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val template = quataTheme()
    val composeKey = message.composeKey()
    val mapsUrl = message.text.extractMapsUrl()
    val sosLocationMessage = remember(context, message.text) {
        context.localizedSosMessage(message.text) ?: message.text.parseSosLocationMessage()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (showSenderAvatar) {
            ClickableProfileAvatar(
                name = sender?.displayName ?: message.senderName,
                avatarUrl = sender?.avatarUrl,
                profileId = sender?.id ?: message.senderId,
                isLoading = isSenderProfileLoading,
                onClick = onOpenSenderProfile,
                modifier = Modifier
                    .size(34.dp)
                    .border(1.dp, template.colors.divider, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(if (showSenderAvatar) 0.72f else 0.78f)
                .background(
                    when {
                        isSelected -> template.colors.chatSelected
                        message.isMine -> template.colors.chatMine
                        else -> template.colors.chatOther
                    },
                    RoundedCornerShape(20.dp)
                )
                .border(
                    width = 1.dp,
                    color = when {
                        isSelected -> template.colors.accent.copy(alpha = 0.45f)
                        message.isMine -> Color.Transparent
                        else -> template.colors.divider
                    },
                    shape = RoundedCornerShape(20.dp)
                )
                .pointerInput(composeKey) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragCancel = { totalDrag = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            if (dragAmount > 0f) {
                                totalDrag += dragAmount
                            }
                        },
                        onDragEnd = {
                            if (totalDrag > CHAT_REPLY_SWIPE_THRESHOLD_PX) {
                                onSwipeReply()
                            }
                            totalDrag = 0f
                        }
                    )
                }
                .then(
                    if (!message.isDeleted && message.text.isNotBlank() && sosLocationMessage == null) {
                        Modifier.quataTranslatableText(
                            id = "chat-message:$composeKey",
                            text = message.text,
                            displayText = message.translatorDisplayText(context)
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick)
                .padding(14.dp)
        ) {
            val textColor = if (message.isMine || isSelected) template.colors.accentContent else template.colors.textPrimary
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message.senderName, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(104.dp)
                        .height(16.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            message.chatTimestampLabel(context),
                            color = textColor.copy(alpha = 0.56f),
                            fontSize = 12.sp
                        )
                        if (message.isMine) {
                            Spacer(Modifier.width(4.dp))
                            MessageDeliveryIndicator(
                                state = if (message.isPending) MessageDeliveryState.Pending else message.deliveryState,
                                tint = textColor.copy(alpha = 0.62f),
                                readTint = template.colors.accent
                            )
                        }
                    }
                    if (message.isEdited || message.isFavorite) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(y = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (message.isEdited) {
                                Text(
                                    stringResource(R.string.conversation_edited),
                                    color = textColor.copy(alpha = 0.62f),
                                    fontSize = 11.sp
                                )
                            }
                            if (message.isFavorite) {
                                CompactIcon(
                                    Icons.Filled.StarBorder,
                                    contentDescription = stringResource(R.string.conversation_favorite_marker),
                                    tint = textColor.copy(alpha = 0.62f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
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
                    if (sosLocationMessage != null) {
                        SosLocationMessageContent(
                            message = sosLocationMessage,
                            textColor = textColor,
                            accentColor = if (message.isMine || isSelected) template.colors.accentContent else template.colors.accent,
                            onOpenMaps = { url -> context.openMaps(url) }
                        )
                    } else {
                        LinkifiedMessageText(
                            text = message.text,
                            color = textColor,
                            linkColor = if (message.isMine || isSelected) template.colors.accentContent else template.colors.accent
                        )
                    }
                }
                message.attachmentPreview(context)?.let { attachment ->
                    Spacer(Modifier.padding(4.dp))
                    if (attachment.isAudio) {
                        AudioAttachmentPlayer(
                            attachment = attachment,
                            textColor = textColor,
                            autoPlay = autoPlayVoiceNote,
                            pauseRequested = pauseVoiceNote,
                            onPlaybackStarted = onVoiceNoteStarted,
                            onPlaybackEnded = onVoiceNoteEnded
                        )
                    } else if (attachment.isMedia) {
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
                                CompactIcon(Icons.Filled.AttachFile, contentDescription = null, tint = textColor)
                                Spacer(Modifier.width(8.dp))
                                Text(attachment.name, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (mapsUrl != null && sosLocationMessage == null) {
                Spacer(Modifier.padding(4.dp))
                Text(
                    text = stringResource(R.string.conversation_open_maps),
                    color = if (message.isMine) template.colors.accentContent else template.colors.accent,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clickable { context.openMaps(mapsUrl) }
                )
            }
        }
    }
}

@Composable
private fun TypingIndicatorBubble(names: List<String>) {
    val template = quataTheme()
    val transition = rememberInfiniteTransition(label = "chat_typing_dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "chat_typing_phase"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .background(template.colors.chatOther, RoundedCornerShape(20.dp))
                .border(1.dp, template.colors.divider, RoundedCornerShape(20.dp))
                .padding(horizontal = 17.dp, vertical = 13.dp)
        ) {
            names.firstOrNull()?.takeIf { names.size == 1 }?.let { name ->
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = template.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { index ->
                    val shifted = (phase + index * 0.22f) % 1f
                    val scale = 0.72f + 0.42f * (1f - kotlin.math.abs(shifted * 2f - 1f))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = 0.5f + scale * 0.45f)
                            .background(template.colors.textSecondary, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageDeliveryIndicator(
    state: MessageDeliveryState,
    tint: Color,
    readTint: Color
) {
    when (state) {
        MessageDeliveryState.Pending -> CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            color = tint.copy(alpha = 0.72f),
            strokeWidth = 1.7.dp
        )
        MessageDeliveryState.Failed -> Text(
            text = "!",
            color = Color(0xFFD32F2F),
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold
        )
        MessageDeliveryState.Sent -> Text(
            text = "\u2713",
            color = tint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        MessageDeliveryState.Delivered,
        MessageDeliveryState.Read -> Text(
            text = "\u2713\u2713",
            color = if (state == MessageDeliveryState.Read) readTint else tint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SosLocationMessageContent(
    message: com.quata.core.text.LocalizedSosMessage,
    textColor: Color,
    accentColor: Color,
    onOpenMaps: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = message.title,
            color = textColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp
        )
        message.body?.let { body ->
            Text(text = body, color = textColor, fontSize = 14.sp)
        }
        message.locationLabel?.let { label ->
            Text(
                text = label,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        if (message.mapsUrl != null && message.isUpdate) {
            SosLocationMapPreview(accentColor = accentColor)
        }
        if (message.age != null || message.accuracy != null || message.speed != null) {
            Surface(
                color = Color.White.copy(alpha = 0.30f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.age?.let {
                        Text(
                            text = stringResource(R.string.sos_location_age, it),
                            color = textColor,
                            fontSize = 13.sp
                        )
                    }
                    message.accuracy?.let {
                        Text(
                            text = stringResource(R.string.sos_location_accuracy, it),
                            color = textColor,
                            fontSize = 13.sp
                        )
                    }
                    message.speed?.let {
                        Text(
                            text = stringResource(R.string.sos_location_speed, it),
                            color = textColor,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else if (message.isUnavailable) {
            Surface(
                color = Color.White.copy(alpha = 0.24f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.sos_location_unavailable),
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
        message.mapsUrl?.let { url ->
            Text(
                text = stringResource(R.string.conversation_open_maps),
                color = accentColor,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clickable { onOpenMaps(url) }
            )
        }
    }
}

@Composable
private fun SosLocationMapPreview(accentColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFFE8F0EA),
                        Color(0xFFF6EFE5),
                        Color(0xFFDDECF7)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .offset(y = ((index - 1) * 18).dp)
                    .background(Color.White.copy(alpha = 0.72f))
            )
        }
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(92.dp)
                    .offset(x = ((index - 1) * 42).dp)
                    .background(Color.White.copy(alpha = 0.62f))
            )
        }
        CompactIcon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(42.dp)
        )
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
        var layoutResult by remember(annotatedText) { mutableStateOf<TextLayoutResult?>(null) }
        BasicText(
            text = annotatedText,
            style = TextStyle(color = color, fontSize = 14.sp),
            modifier = Modifier.pointerInput(annotatedText) {
                detectTapGestures { tapOffset ->
                    val textOffset = layoutResult?.getOffsetForPosition(tapOffset) ?: return@detectTapGestures
                    annotatedText
                        .getStringAnnotations(UrlAnnotationTag, textOffset, textOffset)
                        .firstOrNull()
                        ?.item
                        ?.let { context.openBrowserUrl(it) }
                }
            },
            onTextLayout = { layoutResult = it }
        )
    }
}

private fun String.extractMapsUrl(): String? =
    Regex("""https://maps\.google\.com/\?q=[^\s]+""").find(this)?.value

private fun String.parseSosLocationMessage(): com.quata.core.text.LocalizedSosMessage? {
    val normalized = lowercase(java.util.Locale.ROOT)
    val isSos = normalized.contains("sos real") ||
        normalized.contains("real sos") ||
        normalized.contains("sos location update") ||
        normalized.contains("actualizacion de ubicacion sos") ||
        normalized.contains("ubicacion sos actualizada") ||
        normalized.contains("mise a jour de position sos")
    if (!isSos) return null

    val lines = lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
    if (lines.isEmpty()) return null

    val mapsUrl = extractMapsUrl()
    val isUpdate = normalized.contains("location update") ||
        normalized.contains("actualizacion") ||
        normalized.contains("actualizada") ||
        normalized.contains("mise a jour")
    val locationLine = mapsUrl?.let { url -> lines.firstOrNull { it.contains(url) } }
    val body = if (isUpdate) {
        lines.drop(1).firstOrNull { line ->
            line != locationLine &&
                !line.startsWithAnySosLabel("location age", "antiguedad", "age de la position", "speed", "velocidad", "vitesse", "accuracy", "precision")
        }
    } else {
        null
    }

    return com.quata.core.text.LocalizedSosMessage(
        title = lines.first(),
        body = body,
        locationLabel = locationLine,
        mapsUrl = mapsUrl,
        age = lines.extractSosValue("location age", "antiguedad", "age de la position"),
        accuracy = lines.extractSosValue("accuracy", "precision"),
        speed = lines.extractSosValue("speed", "velocidad", "vitesse"),
        isUpdate = isUpdate,
        isUnavailable = mapsUrl == null && normalized.contains("no disponible") || normalized.contains("unavailable") || normalized.contains("indisponible")
    )
}

private fun List<String>.extractSosValue(vararg labels: String): String? =
    firstOrNull { line -> line.startsWithAnySosLabel(*labels) }
        ?.substringAfter(':', missingDelimiterValue = "")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun String.startsWithAnySosLabel(vararg labels: String): Boolean {
    val normalized = lowercase(java.util.Locale.ROOT)
    return labels.any { label -> normalized.startsWith(label) }
}

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

private fun TextFieldValue.insertAtSelection(value: String): TextFieldValue {
    val start = selection.start.coerceIn(0, text.length)
    val end = selection.end.coerceIn(0, text.length)
    val replaceStart = minOf(start, end)
    val replaceEnd = maxOf(start, end)
    val updatedText = text.replaceRange(replaceStart, replaceEnd, value)
    val cursor = replaceStart + value.length
    return TextFieldValue(
        text = updatedText,
        selection = TextRange(cursor)
    )
}

private fun Message.attachmentPreview(context: Context): AttachmentPreview? {
    val uri = attachmentUri?.takeIf { it.isNotBlank() } ?: return null
    return AttachmentPreview(
        name = attachmentName ?: context.getString(R.string.common_file),
        uri = uri,
        mimeType = attachmentMimeType
    )
}

private fun LazyListState.isAtConversationBottom(): Boolean {
    return !canScrollForward
}

private suspend fun LazyListState.scrollToConversationBottom(lastIndex: Int, lastItemKey: String) {
    if (lastIndex < 0) return
    val targetIsAlreadyAtBottom = !canScrollForward &&
        layoutInfo.visibleItemsInfo.any { item ->
            item.index == lastIndex && item.key == lastItemKey
        }
    if (targetIsAlreadyAtBottom) return

    // A large positive offset is clamped to the list's maximum scroll range,
    // placing the final item at the conversation bottom in one operation.
    scrollToItem(lastIndex, scrollOffset = Int.MAX_VALUE)
}

private fun Message.chatTimestampLabel(context: Context): String {
    val millis = sentAtMillis ?: return sentAt
    val now = System.currentTimeMillis()
    val elapsed = (now - millis).coerceAtLeast(0L)
    val day = 24L * 60L * 60L * 1000L
    return when {
        elapsed < day -> java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
        else -> relativeTimeLabel(context, millis, now)
    }
}

private fun Message.translatorDisplayText(context: Context): String = buildString {
    append(if (isMine) "mine" else "other")
    append(" | ")
    append(senderName)
    append(" | ")
    append(chatTimestampLabel(context))
    append('\n')
    append(text)
}

private fun android.content.Context.displayNameForUri(uri: Uri): String {
    val name = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
    return name?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { getString(R.string.common_file) }
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
    val player = runCatching { MediaPlayer.create(this, rawResId) }.getOrNull() ?: return
    player.setOnCompletionListener { completedPlayer ->
        completedPlayer.release()
    }
    player.setOnErrorListener { errorPlayer, _, _ ->
        errorPlayer.release()
        true
    }
    runCatching { player.start() }
        .onFailure { player.release() }
}

private const val CHAT_REPLY_SWIPE_THRESHOLD_PX = 92f
