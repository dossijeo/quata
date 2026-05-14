package com.quata.feature.neighborhoods.presentation

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import com.quata.core.designsystem.theme.QuataDivider
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataSurface
import com.quata.core.designsystem.theme.QuataSurfaceAlt
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.textCanvasBrush
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun NeighborhoodsScreen(
    padding: PaddingValues,
    repository: NeighborhoodRepository,
    currentUserId: String? = null,
    onOpenConversation: (String) -> Unit,
    viewModel: NeighborhoodsViewModel = viewModel(factory = NeighborhoodsViewModel.factory(repository))
) {
    val state by viewModel.uiState.collectAsState()
    var selectedCommunity by rememberSaveable { mutableStateOf<String?>(null) }
    var neighborhoodQuery by rememberSaveable { mutableStateOf("") }
    val communityForDialog = state.communities.firstOrNull { it.name == selectedCommunity }
    val visibleCommunities = remember(state.communities, neighborhoodQuery) {
        val query = neighborhoodQuery.trim()
        if (query.isBlank()) {
            state.communities
        } else {
            state.communities.filter { community ->
                community.name.contains(query, ignoreCase = true)
            }
        }
    }

    state.selectedProfile?.let { profile ->
        CommunityProfileScreen(
            padding = padding,
            profile = profile,
            currentUserId = currentUserId,
            onReportPost = { postId -> viewModel.reportProfilePost(postId) },
            onBack = { viewModel.closeUserProfile() },
            onFollow = { viewModel.toggleFollowUser(profile.user.id) },
            onOpenPrivateChat = {
                viewModel.openPrivateChat(profile.user.id) { conversationId ->
                    viewModel.closeUserProfile()
                    selectedCommunity = null
                    onOpenConversation(conversationId)
                }
            }
        )
        return
    }

    if (communityForDialog != null) {
        NeighborhoodUsersScreen(
            padding = padding,
            community = communityForDialog,
            currentUserId = currentUserId,
            onBack = { selectedCommunity = null },
            onFollowUser = { viewModel.toggleFollowUser(it.id) },
            onOpenProfile = { viewModel.openUserProfile(it.id) },
            onOpenPrivateChat = { user ->
                viewModel.openPrivateChat(user.id) { conversationId ->
                    selectedCommunity = null
                    onOpenConversation(conversationId)
                }
            }
        )
        return
    }

    QuataScreen(padding) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.neighborhoods_title),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                letterSpacing = 1.6.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.neighborhoods_open_community), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = neighborhoodQuery,
                onValueChange = { neighborhoodQuery = it },
                placeholder = { Text(stringResource(R.string.neighborhoods_subtitle)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))

            if (state.error != null) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                items(visibleCommunities, key = { it.name }) { community ->
                    NeighborhoodCard(
                        community = community,
                        isOpeningChat = state.isOpeningChat,
                        onShowUsers = { selectedCommunity = community.name },
                        onOpenChat = {
                            viewModel.openChat(community.name, onOpenConversation)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NeighborhoodCard(
    community: NeighborhoodCommunity,
    isOpeningChat: Boolean,
    onShowUsers: () -> Unit,
    onOpenChat: () -> Unit
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = QuataSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, QuataDivider, RoundedCornerShape(22.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                NeighborhoodAvatar(community.name, modifier = Modifier.padding(top = 34.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            community.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            communityTimeLabel(context, community.lastMessageAtMillis),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        community.lastMessagePreview ?: stringResource(R.string.neighborhoods_empty_preview),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CountPill(
                            if (community.users.size == 1) {
                                stringResource(R.string.neighborhoods_one_user)
                            } else {
                                stringResource(R.string.neighborhoods_user_count, community.users.size)
                            }
                        )
                        CountPill(
                            if (community.messageCount == 1) {
                                stringResource(R.string.neighborhoods_one_message)
                            } else {
                                stringResource(R.string.neighborhoods_message_count, community.messageCount)
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onShowUsers,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.neighborhoods_view_users))
                }
                Button(
                    onClick = onOpenChat,
                    enabled = !isOpeningChat,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QuataSurfaceAlt,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(stringResource(R.string.neighborhoods_open_chat))
                }
            }
        }
    }
}

@Composable
private fun NeighborhoodAvatar(name: String, modifier: Modifier = Modifier) {
    val letter = name.trim().firstOrNull()?.uppercase() ?: "B"
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(QuataOrange),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 20.sp)
    }
}

@Composable
private fun CountPill(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFF5A372B), CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun NeighborhoodUsersScreen(
    padding: PaddingValues,
    community: NeighborhoodCommunity,
    currentUserId: String?,
    onBack: () -> Unit,
    onFollowUser: (NeighborhoodUser) -> Unit,
    onOpenProfile: (NeighborhoodUser) -> Unit,
    onOpenPrivateChat: (NeighborhoodUser) -> Unit
) {
    QuataScreen(padding) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.neighborhoods_users_title, community.name),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                    Text(
                        stringResource(R.string.neighborhoods_users_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            CountPill(
                if (community.users.size == 1) {
                    stringResource(R.string.neighborhoods_one_user)
                } else {
                    stringResource(R.string.neighborhoods_user_count, community.users.size)
                }
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 22.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(community.users, key = { it.id }) { user ->
                    NeighborhoodUserRow(
                        user = user,
                        isOwnUser = user.id == currentUserId,
                        onFollowUser = { onFollowUser(user) },
                        onOpenProfile = { onOpenProfile(user) },
                        onOpenPrivateChat = { onOpenPrivateChat(user) }
                    )
                }
            }
        }
    }
}

@Composable
fun CommunityProfileScreen(
    padding: PaddingValues,
    profile: CommunityUserProfile,
    currentUserId: String? = null,
    onReportPost: (String) -> Unit = {},
    onBack: () -> Unit,
    onFollow: () -> Unit,
    onOpenPrivateChat: () -> Unit
) {
    val isOwnProfile = profile.user.id == currentUserId
    QuataScreen(padding) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 28.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.user.displayName, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        Text(profile.user.neighborhood, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ProfileAvatar(profile.user, Modifier.size(92.dp))
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ProfileKpi(profile.user.postsCount.toString(), stringResource(R.string.neighborhoods_posts), Modifier.weight(1f))
                    ProfileKpi(profile.user.followersCount.toString(), stringResource(R.string.neighborhoods_followers), Modifier.weight(1f))
                    ProfileKpi(profile.user.followingCount.toString(), stringResource(R.string.neighborhoods_following), Modifier.weight(1f))
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onFollow,
                        enabled = !isOwnProfile,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.Black),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (profile.user.isFollowing) stringResource(R.string.common_following) else stringResource(R.string.common_follow), fontSize = 18.sp)
                    }
                    OutlinedButton(
                        onClick = onOpenPrivateChat,
                        enabled = !isOwnProfile,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = QuataOrange),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.common_privi), fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                val pagerState = rememberPagerState(pageCount = { profile.posts.size })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.neighborhoods_photos_videos), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    if (profile.posts.isNotEmpty()) {
                        Text("${pagerState.currentPage + 1} / ${profile.posts.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (profile.posts.isEmpty()) {
                    Text(
                        stringResource(R.string.neighborhoods_no_visible_posts),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 28.dp)
                    )
                } else {
                    ProfilePostsPager(
                        posts = profile.posts,
                        pagerState = pagerState,
                        onReportPost = onReportPost
                    )
                }
            }
        }
    }
}

@Composable
private fun NeighborhoodUserRow(
    user: NeighborhoodUser,
    isOwnUser: Boolean,
    onFollowUser: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenPrivateChat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, QuataDivider, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarImage(user.displayName, user.avatarUrl, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.displayName, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(user.neighborhood, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onFollowUser,
                enabled = !isOwnUser,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.Black)
            ) {
                Text(
                    if (user.isFollowing) stringResource(R.string.common_following) else stringResource(R.string.common_follow),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
            OutlinedButton(
                onClick = onOpenProfile,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = QuataOrange)
            ) {
                Text(stringResource(R.string.common_profile), fontSize = 14.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = onOpenPrivateChat,
                enabled = !isOwnUser,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = QuataOrange)
            ) {
                Text(stringResource(R.string.common_privi), fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ProfileAvatar(user: NeighborhoodUser, modifier: Modifier = Modifier) {
    AvatarImage(user.displayName, user.avatarUrl, modifier = modifier.clip(CircleShape))
}

@Composable
private fun ProfileKpi(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(1.dp, QuataDivider, RoundedCornerShape(16.dp))
            .background(QuataSurface, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun ProfilePostsPager(
    posts: List<Post>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onReportPost: (String) -> Unit
) {
    val context = LocalContext.current
    var commentsPost by remember { mutableStateOf<Post?>(null) }
    val localComments = remember { mutableStateMapOf<String, List<PostComment>>() }
    Column {
        HorizontalPager(state = pagerState, modifier = Modifier.height(440.dp)) { page ->
            val post = posts[page]
            ProfilePostPreview(
                post = post,
                commentsCount = post.comments.size + localComments[post.id].orEmpty().size,
                onOpenComments = { commentsPost = post },
                onShare = { context.shareProfilePost(post) },
                onReport = {
                    if (!post.isReportedByCurrentUser) {
                        onReportPost(post.id)
                        Toast.makeText(context, context.getString(R.string.feed_report_success), Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    commentsPost?.let { post ->
        ProfileCommentsDialog(
            post = post,
            localComments = localComments[post.id].orEmpty(),
            onAddComment = { comment ->
                localComments[post.id] = localComments[post.id].orEmpty() + comment
            },
            onDismiss = { commentsPost = null }
        )
    }
}

@Composable
private fun ProfilePostPreview(
    post: Post,
    commentsCount: Int,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit
) {
    var liked by rememberSaveable(post.id) { mutableStateOf(false) }
    val likes = post.likesCount + if (liked) 1 else 0
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(430.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black)
    ) {
        when {
            post.imageUrl != null -> AsyncImage(
                model = post.imageUrl,
                contentDescription = post.imageTitle(),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(430.dp)
            )
            post.videoUrl != null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF111827), Color(0xFF334155))))
            ) {
                Text(stringResource(R.string.neighborhoods_video), modifier = Modifier.align(Alignment.Center), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            }
            else -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp)
                    .background(textCanvasBrush(post.text))
                    .padding(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(post.text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                Text(post.author.displayName, fontWeight = FontWeight.ExtraBold, color = Color.White)
                val subtitle = if (post.imageUrl != null && post.videoUrl == null) post.imageTitle() else post.text
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = Color.White.copy(alpha = 0.82f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniFeedAction(
                    icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    count = likes.toString(),
                    onClick = { liked = !liked }
                )
                MiniFeedAction(Icons.Filled.ChatBubble, commentsCount.toString(), onClick = onOpenComments)
                MiniFeedAction(Icons.Filled.Share, null, onClick = onShare)
                MiniFeedAction(
                    icon = Icons.Filled.Flag,
                    count = null,
                    tint = if (post.isReportedByCurrentUser) QuataOrange else Color.White,
                    onClick = onReport
                )
            }
        }
    }
}

@Composable
private fun MiniFeedAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: String?,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        if (count != null) Text(count, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProfileCommentsDialog(
    post: Post,
    localComments: List<PostComment>,
    onAddComment: (PostComment) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by rememberSaveable(post.id) { mutableStateOf("") }
    val comments = post.comments + localComments
    val currentUserName = stringResource(R.string.comments_you)
    val nowLabel = stringResource(R.string.common_now)
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.feed_comments), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
                    }
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(comments, key = { it.id }) { comment ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.055f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(comment.authorName, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(comment.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(stringResource(R.string.comments_placeholder)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 58.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = draft.isNotBlank(),
                        onClick = {
                            onAddComment(
                                PostComment(
                                    id = "profile_${post.id}_${System.currentTimeMillis()}",
                                    authorName = currentUserName,
                                    message = draft.trim(),
                                    timestamp = nowLabel
                                )
                            )
                            draft = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.Black)
                    ) {
                        Text(stringResource(R.string.common_send))
                    }
                }
            }
        }
    }
}

private fun android.content.Context.shareProfilePost(post: Post) {
    val shareText = buildString {
        append(post.author.displayName)
        append(": ")
        append(if (post.imageUrl != null && post.videoUrl == null) post.imageTitle() else post.text)
        post.imageUrl?.let { append("\n").append(it) }
        post.videoUrl?.let { append("\n").append(it) }
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    startActivity(Intent.createChooser(sendIntent, getString(R.string.neighborhoods_share_post)))
}

private fun Post.imageTitle(): String =
    placeName?.takeIf { it.isNotBlank() } ?: rankingLabel.takeIf { it.isNotBlank() } ?: "Qüata"

private fun communityTimeLabel(context: android.content.Context, lastMessageAtMillis: Long?): String {
    if (lastMessageAtMillis == null) return context.getString(R.string.common_new)
    val zone = ZoneId.systemDefault()
    val messageDate = Instant.ofEpochMilli(lastMessageAtMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val days = ChronoUnit.DAYS.between(messageDate, today)
    return when {
        days == 0L -> DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(lastMessageAtMillis).atZone(zone))
        days == 1L -> context.getString(R.string.common_yesterday)
        days < 7L -> context.getString(R.string.neighborhoods_time_days, days.toInt())
        days < 30L -> {
            val weeks = (days / 7).coerceAtLeast(1)
            if (weeks == 1L) context.getString(R.string.neighborhoods_time_one_week) else context.getString(R.string.neighborhoods_time_weeks, weeks.toInt())
        }
        days < 365L -> {
            val months = (days / 30).coerceAtLeast(1)
            if (months == 1L) context.getString(R.string.neighborhoods_time_one_month) else context.getString(R.string.neighborhoods_time_months, months.toInt())
        }
        else -> {
            val years = (days / 365).coerceAtLeast(1)
            if (years == 1L) context.getString(R.string.neighborhoods_time_one_year) else context.getString(R.string.neighborhoods_time_years, years.toInt())
        }
    }
}
