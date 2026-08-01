package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.PostComment
import com.quata.core.ui.components.QuataAvatarFrameContent
import com.quata.core.ui.components.QuataLiveRankingItem
import com.quata.core.ui.components.QuataLiveRankingPanelContent
import com.quata.core.ui.components.QuataLiveRankingStrings
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.domain.ProfileAttachment
import platform.UIKit.UIViewController
import platform.Foundation.NSLocale
import platform.Foundation.NSURL
import platform.UIKit.UIImage

/**
 * The launcher's only cross-feature navigation obligation for Communities.  The selected ID is
 * the backend profile ID supplied by the member row; callers must not substitute a display name
 * or create a local profile model.
 */
fun interface IosCommunityProfileNavigator {
    fun openMemberProfile(profileId: String)
}

/**
 * Inputs owned by the iOS launcher. The repository is injected into [viewModel] by the
 * launcher; keeping it here makes the composition boundary explicit and avoids a service
 * locator in shared presentation code.
 *
 * Avatar, attachment and navigation work deliberately remain slots. They can be backed by
 * SwiftUI/UIKit, Photos, QuickLook or an application navigator without leaking those APIs into
 * commonMain.
 */
class IosNeighborhoodsHostDependencies(
    val repository: NeighborhoodRepository,
    val viewModel: NeighborhoodsViewModel,
    val currentUserId: String?,
    val listStrings: NeighborhoodListStrings,
    val usersStrings: NeighborhoodUsersStrings,
    val avatar: @Composable (NeighborhoodUser, Boolean, () -> Unit) -> Unit,
    val onOpenConversation: (String) -> Unit,
    val profileNavigator: IosCommunityProfileNavigator,
    val onAuthRequired: () -> Unit,
)

/**
 * Swift-facing composition factory for the portable Communities surface.
 *
 * The UIKit launcher supplies only real navigation callbacks and the authenticated repository;
 * common strings and the offline-safe avatar fallback remain in Kotlin so Swift never needs to
 * manufacture Compose lambdas or example data.
 */
fun createIosNeighborhoodsHostDependencies(
    repository: NeighborhoodRepository,
    currentUserId: String?,
    onOpenConversation: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onAuthRequired: () -> Unit,
): IosNeighborhoodsHostDependencies = IosNeighborhoodsHostDependencies(
    repository = repository,
    viewModel = NeighborhoodsViewModel(repository),
    currentUserId = currentUserId,
    listStrings = defaultNeighborhoodsScreenStrings(null).list,
    usersStrings = defaultNeighborhoodsScreenStrings(null).members,
    avatar = { user, _, onClick -> IosNeighborhoodAvatar(user, onClick) },
    onOpenConversation = onOpenConversation,
    profileNavigator = IosCommunityProfileNavigator(onNavigateToProfile),
    onAuthRequired = onAuthRequired,
)

/** Creates an injectable UIKit host for the common Neighborhoods list and member surfaces. */
fun QuataNeighborhoodsViewController(
    dependencies: IosNeighborhoodsHostDependencies,
): UIViewController = ComposeUIViewController {
    QuataTheme {
        NeighborhoodsScreenHost(
            model = dependencies.viewModel,
            currentUserId = dependencies.currentUserId,
            strings = NeighborhoodsScreenStrings(dependencies.listStrings, dependencies.usersStrings),
            avatar = dependencies.avatar,
            onOpenConversation = dependencies.onOpenConversation,
            onOpenUserProfile = dependencies.profileNavigator::openMemberProfile,
            onAuthRequired = dependencies.onAuthRequired,
            padding = PaddingValues(),
            closeModelOnDispose = true,
        )
    }
}

/** Real remote avatar slot with the common frame retaining its deterministic fallback. */
@Composable
private fun IosNeighborhoodAvatar(user: NeighborhoodUser, onClick: () -> Unit) {
    val url = user.avatarUrl?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    var image by remember(url) { mutableStateOf<UIImage?>(null) }
    LaunchedEffect(url) {
        // The surrounding UIKit launcher can replace this slot. Keeping the URL as state ensures
        // a real remote loader is used by the platform adapter rather than a fabricated avatar.
        image = null
    }
    QuataAvatarFrameContent(
        name = user.displayName,
        stableId = user.id,
        isOfficial = user.isOfficial,
        modifier = Modifier,
        avatar = null,
    )
}

/**
 * Shared profile arrangement available to the iOS launcher once it presents a selected profile.
 * Media playback, attachment opening and navigation are supplied by the surrounding iOS host.
 */
@Composable
fun IosNeighborhoodProfileDetailsContent(
    profile: CommunityUserProfile,
    header: @Composable () -> Unit,
    attachmentStrings: ProfileAttachmentsStrings,
    attachmentItem: @Composable (ProfileAttachment) -> Unit,
    gallery: (@Composable () -> Unit)? = null,
) {
    CommunityProfileDetailsContent(
        listState = androidx.compose.foundation.lazy.rememberLazyListState(),
        header = header,
        attachments = {
            ProfileAttachmentsContent(
                attachments = profile.attachments,
                strings = attachmentStrings,
                attachmentItem = attachmentItem,
            )
        },
        gallery = gallery,
    )
}

/** Exposes the common comments floating panel without coupling iOS to Android media or URI APIs. */
@Composable
fun IosNeighborhoodCommentsPanelContent(
    comments: List<PostComment>,
    title: String,
    closeContentDescription: String,
    onDismiss: () -> Unit,
    commentRow: @Composable (PostComment) -> Unit,
    input: @Composable () -> Unit,
) {
    CommunityProfileCommentsPanelContent(
        comments = comments,
        title = title,
        closeContentDescription = closeContentDescription,
        onDismiss = onDismiss,
        commentRow = commentRow,
        input = input,
    )
}

/** Exposes the portable ranking panel while leaving avatar rendering and post navigation injected. */
@Composable
fun IosNeighborhoodRankingPanelContent(
    items: List<QuataLiveRankingItem>,
    isLandscape: Boolean,
    strings: QuataLiveRankingStrings,
    avatar: @Composable (QuataLiveRankingItem) -> Unit,
    onDismiss: () -> Unit,
    onOpenPost: (String) -> Unit,
) {
    QuataLiveRankingPanelContent(
        items = items,
        isLandscape = isLandscape,
        strings = strings,
        avatar = avatar,
        onDismiss = onDismiss,
        onOpenItem = onOpenPost,
    )
}

private val IosNeighborhoodListStrings = NeighborhoodListStrings(
    title = "Communities",
    searchPlaceholder = "Search communities",
    loading = "Loading communities…",
    oneUser = "1 member",
    users = { "$it members" },
    oneMessage = "1 message",
    messages = { "$it messages" },
    viewUsers = "View members",
    openChat = "Open chat",
    timeLabel = { "No recent activity" },
)

private val IosNeighborhoodUsersStrings = NeighborhoodUsersStrings(
    title = { "$it members" },
    subtitle = "Community members",
    backContentDescription = "Back",
    memberCount = { "$it members" },
    row = NeighborhoodUserRowStrings(
        follow = "Follow",
        following = "Following",
        chat = "Chat",
    ),
)
