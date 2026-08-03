package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.PostComment
import com.quata.core.ui.components.QuataAvatarFallback
import com.quata.core.ui.components.QuataLiveRankingItem
import com.quata.core.ui.components.QuataLiveRankingPanelContent
import com.quata.core.ui.components.QuataLiveRankingStrings
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.domain.ProfileAttachment
import platform.UIKit.UIViewController

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
    /** Public Communities may browse anonymously; writes/navigation acquire Auth at the shell. */
    val onAuthRequired: () -> Unit = {},
    val profileNavigator: IosCommunityProfileNavigator,
    val onOpenAttachment: (ProfileAttachment) -> Unit,
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
    languageCode: String,
    onOpenConversation: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onAuthRequired: () -> Unit = {},
): IosNeighborhoodsHostDependencies = IosNeighborhoodsHostDependencies(
    repository = repository,
    viewModel = NeighborhoodsViewModel(repository),
    currentUserId = currentUserId,
    listStrings = neighborhoodsScreenStringsForLanguage(languageCode).list,
    usersStrings = neighborhoodsScreenStringsForLanguage(languageCode).members,
    avatar = { user, isLoading, onClick ->
        // Remote image loading belongs to a separately verified platform media adapter. A
        // deterministic common fallback keeps this host usable without inventing a URL loader.
        Box(contentAlignment = Alignment.Center) {
            QuataAvatarFallback(
                name = user.displayName,
                stableId = user.id,
                modifier = Modifier.size(44.dp).clickable(onClick = onClick),
            )
            if (isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    },
    onOpenConversation = onOpenConversation,
    onAuthRequired = onAuthRequired,
    profileNavigator = IosCommunityProfileNavigator(onNavigateToProfile),
    onOpenAttachment = {},
)

/** Creates an injectable UIKit host for the common Neighborhoods list and member surfaces. */
fun QuataNeighborhoodsViewController(
    dependencies: IosNeighborhoodsHostDependencies,
): UIViewController = ComposeUIViewController {
    QuataTheme {
        NeighborhoodsScreenHost(
            currentUserId = dependencies.currentUserId,
            strings = NeighborhoodsScreenStrings(dependencies.listStrings, dependencies.usersStrings),
            avatar = dependencies.avatar,
            onOpenConversation = dependencies.onOpenConversation,
            onOpenUserProfile = { userId ->
                // Android treats member-profile inspection as public read access. Follow and
                // chat remain separately gated by the shared host.
                dependencies.viewModel.openUserProfile(userId)
                dependencies.profileNavigator.openMemberProfile(userId)
            },
            onAuthRequired = dependencies.onAuthRequired,
            padding = PaddingValues(),
            model = dependencies.viewModel,
            closeModelOnDispose = true,
        )
    }
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
