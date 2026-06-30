package com.quata.feature.chat.presentation.conversations

import android.graphics.Color as AndroidColor
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.quata.core.ui.components.CompactButtonContentPadding
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataResolvedTheme
import com.quata.core.designsystem.theme.QuataThemeTemplate
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.text.localizedSosPreview
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.AvatarLetter
import com.quata.core.ui.components.ClickableProfileAvatar
import com.quata.core.ui.components.QuataCard
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.compactButtonMinSize
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.chat.domain.ChatConversationCandidate
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
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var timestampNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val visibleConversations = remember(context, state.conversations, state.messagesByConversation, state.usersById, query) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            state.conversations
        } else {
            state.conversations.filter { conversation ->
                val messages = state.messagesByConversation[conversation.id].orEmpty()
                val rawPreview = messages.lastOrNull()?.text ?: conversation.lastMessagePreview
                val preview = context.localizedSosPreview(rawPreview) ?: rawPreview
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

    val isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape
    val contentPadding = if (isLandscapeLayout) {
        PaddingValues(start = 8.dp, top = 18.dp, end = 18.dp, bottom = 18.dp)
    } else {
        PaddingValues(18.dp)
    }

    QuataScreen(padding) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
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
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.72f)
                        .padding(18.dp)
                )
            }
            NewConversationFab(
                onClick = { viewModel.openNewConversationPicker() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp)
            )
        }
    }

    if (state.isNewConversationPickerOpen) {
        NewConversationDialog(
            state = state,
            onSearchChange = viewModel::onCandidateQueryChanged,
            onLoadMore = viewModel::loadMoreConversationCandidates,
            onOpenCandidate = { candidate ->
                viewModel.openCandidateConversation(candidate, onOpenConversation)
            },
            onDismiss = viewModel::closeNewConversationPicker
        )
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
private fun NewConversationFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Surface(
        color = template.colors.accent,
        shape = CircleShape,
        shadowElevation = 8.dp,
        modifier = modifier
            .size(64.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CompactIcon(
                imageVector = Icons.Filled.ChatBubble,
                contentDescription = stringResource(R.string.conversations_new_chat),
                tint = template.colors.accentContent,
                modifier = Modifier.size(34.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(template.colors.surfaceRaised)
                    .border(1.dp, template.colors.accent.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CompactIcon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = template.colors.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewConversationDialog(
    state: ConversationsUiState,
    onSearchChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenCandidate: (ChatConversationCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    val template = quataTheme()
    val listState = rememberLazyListState()
    val isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.PartiallyExpanded }
    )
    val contactsTitle = stringResource(R.string.conversations_new_chat_contacts)
    val followingTitle = stringResource(R.string.conversations_new_chat_following)
    val followersTitle = stringResource(R.string.conversations_new_chat_followers)
    val otherTitle = stringResource(R.string.conversations_new_chat_other_neighborhoods)
    val unknownNeighborhood = stringResource(R.string.conversations_new_chat_unknown_neighborhood)
    val displayItems = remember(
        state.conversationCandidates,
        state.candidateActorNeighborhood,
        contactsTitle,
        followingTitle,
        followersTitle,
        otherTitle,
        unknownNeighborhood
    ) {
        buildCandidateDisplayItems(
            candidates = state.conversationCandidates,
            actorNeighborhood = state.candidateActorNeighborhood,
            contactsTitle = contactsTitle,
            followingTitle = followingTitle,
            followersTitle = followersTitle,
            otherTitle = otherTitle,
            unknownNeighborhood = unknownNeighborhood
        )
    }

    LaunchedEffect(listState, displayItems.size, state.candidateHasMore, state.isCandidatePageLoading) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (displayItems.isNotEmpty() && lastVisible >= displayItems.lastIndex - 5) {
                    onLoadMore()
                }
            }
    }

    LaunchedEffect(Unit) {
        if (!isLandscapeLayout) {
            sheetState.expand()
        }
    }

    if (isLandscapeLayout) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                decorFitsSystemWindows = false
            )
        ) {
            ConfigureNewConversationSheetSystemBars(template, fullscreen = true)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 72.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                val panelInteractionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(onDismiss) {
                            detectTapGestures { onDismiss() }
                        }
                )
                Surface(
                    color = template.colors.surfaceRaised,
                    contentColor = template.colors.textPrimary,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.76f)
                        .fillMaxHeight(0.90f)
                        .border(1.dp, template.colors.divider.copy(alpha = 0.72f), RoundedCornerShape(28.dp))
                        .clickable(
                            interactionSource = panelInteractionSource,
                            indication = null,
                            onClick = {}
                        )
                ) {
                    NewConversationPanelContent(
                        state = state,
                        displayItems = displayItems,
                        listState = listState,
                        onSearchChange = onSearchChange,
                        onOpenCandidate = onOpenCandidate,
                        onDismiss = onDismiss,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 18.dp)
                    )
                }
            }
        }
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = template.colors.surfaceRaised,
        contentColor = template.colors.textPrimary,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        ConfigureNewConversationSheetSystemBars(template)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            Spacer(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                    .background(template.colors.background)
            )
            NewConversationPanelContent(
                state = state,
                displayItems = displayItems,
                listState = listState,
                onSearchChange = onSearchChange,
                onOpenCandidate = onOpenCandidate,
                onDismiss = onDismiss,
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun NewConversationPanelContent(
    state: ConversationsUiState,
    displayItems: List<CandidateDisplayItem>,
    listState: LazyListState,
    onSearchChange: (String) -> Unit,
    onOpenCandidate: (ChatConversationCandidate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.conversations_new_chat_title),
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            CompactIconButton(onClick = onDismiss) {
                CompactIcon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel), tint = template.colors.textPrimary)
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.candidateQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(stringResource(R.string.conversations_new_chat_search_placeholder)) },
            leadingIcon = {
                CompactIcon(Icons.Filled.Search, contentDescription = null, tint = template.colors.textSecondary)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        state.candidateError?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        when {
            state.isCandidateInitialLoading && state.conversationCandidates.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = template.colors.accent)
                }
            }
            displayItems.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.conversations_new_chat_no_results),
                        color = template.colors.textSecondary
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayItems, key = { it.key }) { item ->
                        when (item) {
                            is CandidateDisplayItem.SectionHeader -> CandidateSectionHeader(item.title)
                            is CandidateDisplayItem.NeighborhoodHeader -> CandidateNeighborhoodHeader(item.title)
                            is CandidateDisplayItem.CandidateRow -> CandidateUserCard(
                                candidate = item.candidate,
                                isOpening = state.openingCandidateProfileId == item.candidate.profileId,
                                onOpen = { onOpenCandidate(item.candidate) }
                            )
                        }
                    }
                    if (state.isCandidatePageLoading) {
                        item(key = "loading-more") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = template.colors.accent, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateSectionHeader(title: String) {
    val template = quataTheme()
    Text(
        text = title,
        color = template.colors.textPrimary,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun CandidateNeighborhoodHeader(title: String) {
    val template = quataTheme()
    Text(
        text = title,
        color = template.colors.textSecondary,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp)
    )
}

@Composable
private fun CandidateUserCard(
    candidate: ChatConversationCandidate,
    isOpening: Boolean,
    onOpen: () -> Unit
) {
    val template = quataTheme()
    Surface(
        color = template.colors.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, template.colors.divider, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarImage(
                name = candidate.displayName,
                avatarUrl = candidate.avatarUrl,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(candidate.displayName, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subtitle = candidate.neighborhood
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        color = template.colors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = onOpen,
                enabled = !isOpening,
                colors = ButtonDefaults.buttonColors(containerColor = template.colors.accent, contentColor = template.colors.accentContent),
                shape = CircleShape,
                modifier = Modifier
                    .size(42.dp)
                    .compactButtonMinSize(),
                contentPadding = PaddingValues(0.dp)
            ) {
                if (isOpening) {
                    CircularProgressIndicator(color = template.colors.accentContent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    CompactIcon(Icons.Filled.ChatBubble, contentDescription = stringResource(R.string.common_chat), tint = template.colors.accentContent)
                }
            }
        }
    }
}

private sealed class CandidateDisplayItem(val key: String) {
    data class SectionHeader(val title: String, val sectionKey: String) : CandidateDisplayItem("section:$sectionKey")
    data class NeighborhoodHeader(val title: String) : CandidateDisplayItem("neighborhood:$title")
    data class CandidateRow(val candidate: ChatConversationCandidate) : CandidateDisplayItem("candidate:${candidate.profileId}")
}

private fun buildCandidateDisplayItems(
    candidates: List<ChatConversationCandidate>,
    actorNeighborhood: String,
    contactsTitle: String,
    followingTitle: String,
    followersTitle: String,
    otherTitle: String,
    unknownNeighborhood: String
): List<CandidateDisplayItem> {
    val items = mutableListOf<CandidateDisplayItem>()
    var lastSectionKey: String? = null
    var lastOtherNeighborhood: String? = null
    candidates.forEach { candidate ->
        if (candidate.sectionKey != lastSectionKey) {
            lastSectionKey = candidate.sectionKey
            lastOtherNeighborhood = null
            val title = when (candidate.sectionKey) {
                "contacts" -> contactsTitle
                "following" -> followingTitle
                "followers" -> followersTitle
                "neighborhood" -> actorNeighborhood.ifBlank {
                    candidate.neighborhood.ifBlank { unknownNeighborhood }
                }
                else -> otherTitle
            }
            items += CandidateDisplayItem.SectionHeader(title, candidate.sectionKey)
        }
        if (candidate.sectionKey == "other") {
            val neighborhood = candidate.neighborhoodGroup
                .ifBlank { candidate.neighborhood }
                .ifBlank { unknownNeighborhood }
            if (neighborhood != lastOtherNeighborhood) {
                lastOtherNeighborhood = neighborhood
                items += CandidateDisplayItem.NeighborhoodHeader(neighborhood)
            }
        }
        items += CandidateDisplayItem.CandidateRow(candidate)
    }
    return items
}

@Suppress("DEPRECATION")
@Composable
private fun ConfigureNewConversationSheetSystemBars(
    template: QuataThemeTemplate,
    fullscreen: Boolean = false
) {
    val view = LocalView.current
    DisposableEffect(view, template.id) {
        val window = view.findDialogWindow()
        if (window == null) {
            return@DisposableEffect onDispose {}
        }
        val originalContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced
        } else {
            null
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val originalLightNavigationBars = controller.isAppearanceLightNavigationBars
        val originalAttributes = window.attributes
        val originalGravity = originalAttributes.gravity
        val originalX = originalAttributes.x
        val originalY = originalAttributes.y
        val originalWidth = originalAttributes.width
        val originalHeight = originalAttributes.height
        val originalFlags = originalAttributes.flags
        val originalSystemUiVisibility = window.decorView.systemUiVisibility
        val originalCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            originalAttributes.layoutInDisplayCutoutMode
        } else {
            null
        }
        val fullscreenLayout = {
            val displaySize = Point()
            window.windowManager.defaultDisplay.getRealSize(displaySize)
            val targetWidth = displaySize.x.coerceAtLeast(1)
            val targetHeight = displaySize.y.coerceAtLeast(1)
            val attributes = window.attributes
            attributes.width = targetWidth
            attributes.height = targetHeight
            attributes.gravity = Gravity.TOP or Gravity.START
            attributes.x = 0
            attributes.y = 0
            attributes.flags = attributes.flags or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            window.attributes = attributes
            window.setLayout(targetWidth, targetHeight)
            window.decorView.setPadding(0, 0, 0, 0)
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }

        if (fullscreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        controller.isAppearanceLightNavigationBars = template.resolvedTheme == QuataResolvedTheme.Light
        if (fullscreen) {
            fullscreenLayout()
            window.decorView.post { fullscreenLayout() }
        }

        onDispose {
            if (fullscreen) {
                val attributes = window.attributes
                attributes.gravity = originalGravity
                attributes.x = originalX
                attributes.y = originalY
                attributes.width = originalWidth
                attributes.height = originalHeight
                attributes.flags = originalFlags
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                    attributes.layoutInDisplayCutoutMode = originalCutoutMode
                }
                window.attributes = attributes
                window.decorView.systemUiVisibility = originalSystemUiVisibility
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && originalContrastEnforced != null) {
                window.isNavigationBarContrastEnforced = originalContrastEnforced
            }
            controller.isAppearanceLightNavigationBars = originalLightNavigationBars
        }
    }
}

private tailrec fun View.findDialogWindow(): Window? {
    val parentView = parent
    return when (parentView) {
        is DialogWindowProvider -> parentView.window
        is View -> parentView.findDialogWindow()
        else -> null
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
    val context = LocalContext.current
    val rawPreview = messages.lastOrNull()?.text ?: item.lastMessagePreview
    val preview = context.localizedSosPreview(rawPreview) ?: rawPreview
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
