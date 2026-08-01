package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import com.quata.core.data.toFoundationData
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
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.UIKit.UIImage
import platform.UIKit.UIApplication
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
    languageTag: String?,
    onOpenConversation: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onAuthRequired: () -> Unit,
): IosNeighborhoodsHostDependencies = IosNeighborhoodsHostDependencies(
    repository = repository,
    viewModel = NeighborhoodsViewModel(repository),
    currentUserId = currentUserId,
    listStrings = defaultNeighborhoodsScreenStrings(languageTag).list,
    usersStrings = defaultNeighborhoodsScreenStrings(languageTag).members,
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

/** UIKit route for a public member profile. It mounts the same common modal root as Android/Web. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun QuataCommunityProfileViewController(
    dependencies: IosNeighborhoodsHostDependencies,
    profileId: String,
    onDismiss: () -> Unit,
): UIViewController = ComposeUIViewController {
    QuataTheme {
        val state by dependencies.viewModel.uiState.collectAsState()
        DisposableEffect(profileId) {
            dependencies.viewModel.openUserProfile(profileId)
            onDispose { dependencies.viewModel.closeUserProfile() }
        }
        val profile = state.selectedProfile
        if (profile == null) {
            Column {
                Text(state.error ?: "Loading profile…")
                if (state.error != null) Button(onClick = { dependencies.viewModel.openUserProfile(profileId) }) { Text("Retry") }
            }
        } else {
            val theme = com.quata.core.designsystem.theme.quataTheme()
            CommunityProfileRoot(
                profile = profile,
                currentUserId = dependencies.currentUserId,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = theme.colors.background,
                contentColor = theme.colors.textPrimary,
                strings = iosCommunityProfileStrings(),
                isOpeningChat = state.openingPrivateChatUserId != null,
                isRefreshing = state.refreshingProfileUserId != null,
                followingUserId = state.followingUserId,
                roleUpdatingUserId = state.roleUpdatingUserId,
                currentUserIsAdmin = state.currentUserIsAdmin,
                error = state.error,
                showModeration = true,
                showAdminControls = true,
                onDismiss = onDismiss,
                onAuthRequired = dependencies.onAuthRequired,
                onFollowUser = dependencies.viewModel::toggleFollowUser,
                onOpenPrivateChat = { dependencies.viewModel.openPrivateChat(it, dependencies.onOpenConversation) },
                onOpenUserProfile = dependencies.profileNavigator::openMemberProfile,
                onReportProfile = dependencies.viewModel::reportProfile,
                onBlockProfile = dependencies.viewModel::blockProfile,
                onSetRoles = dependencies.viewModel::setUserRoles,
                avatar = { user, _, click -> IosNeighborhoodAvatar(user) { click?.invoke() } },
                attachmentItem = { attachment -> TextButton(onClick = { iosOpenProfileResource(attachment.uri) }) { Text(attachment.name) } },
                postPreview = { post, count, openComments ->
                    CommunityProfilePostPreviewContent(post, count, dependencies.currentUserId != null, openComments, dependencies.onAuthRequired,
                        { iosOpenProfileResource("https://quata.app/post/${post.id}") }, { dependencies.viewModel.reportProfilePost(post.id) }, media = { _, _ -> })
                },
                commentsDialog = { post, local, add, dismiss ->
                    CommunityProfileCommentsDialogContent(post, local, dependencies.currentUserId != null,
                        CommunityProfileCommentsDialogStrings("Comments", "Close", "Write a comment", "Send"), dependencies.onAuthRequired,
                        { draft -> PostComment("ios:${post.id}:${local.size}", "You", draft, "Now") }, add, dismiss)
                },
            )
            /*
                Column {
                    Text(profile.user.displayName)
                    Text("${profile.user.postsCount} posts · ${profile.user.followersCount} followers")
                    if (navigation.peopleList != null) {
                        val people = if (navigation.peopleList == CommunityProfilePeopleList.Followers) profile.followers else profile.following
                        people.forEach { Text(it.displayName) }
                    } else {
                        profile.posts.forEach { Text(it.text) }
                    }
                }
            }*/
        }
    }
}

private fun iosCommunityProfileStrings() = CommunityProfileStrings(
    "Posts", "Followers", "Following", { "Followers of $it" }, { "Following of $it" },
    "Photos and videos", "No visible posts", ProfileAttachmentsStrings("Attachments", "No attachments"),
    ProfileActionStrings("Follow", "Following", "Chat"), ProfileModerationStrings("Report", "Block"),
    ProfileModerationConfirmationStrings("Report profile", "Block profile", "Report this profile?", "Block this profile?", "Cancel", "Report", "Block"),
    ProfileRoleStrings("Permissions", "Admin", "Official"), NeighborhoodUserRowStrings("Follow", "Following", "Chat"), "Back",
)

private fun iosOpenProfileResource(value: String) {
    NSURL(string = value)?.let { UIApplication.sharedApplication.openURL(it) }
}

/** Real remote avatar slot with the common frame retaining its deterministic fallback. */
@Composable
private fun IosNeighborhoodAvatar(user: NeighborhoodUser, onClick: () -> Unit) {
    val url = iosNeighborhoodAvatarRequestKey(user.avatarUrl)
    var image by remember(url) { mutableStateOf<UIImage?>(null) }
    LaunchedEffect(url) { image = if (url == null) null else loadIosNeighborhoodAvatarOrNull(url) }
    QuataAvatarFrameContent(
        name = user.displayName,
        stableId = user.id,
        isOfficial = user.isOfficial,
        modifier = Modifier.clickable(onClick = onClick),
        avatar = image?.let { decoded -> { UIKitView(factory = { UIImageView().apply { contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill; clipsToBounds = true; image = decoded } }, update = { it.image = decoded }, modifier = Modifier.fillMaxSize()) } },
    )
}

private suspend fun loadIosNeighborhoodAvatarOrNull(url: String): UIImage? = runCatching {
    iosNeighborhoodAvatarData(NSURL(string = url) ?: return@runCatching null)
}.getOrNull()?.let { UIImage(data = it) }

@OptIn(ExperimentalForeignApi::class)
private suspend fun iosNeighborhoodAvatarData(url: NSURL): NSData = suspendCancellableCoroutine { continuation ->
    val delegate = IosNeighborhoodAvatarDelegate(continuation)
    val session = NSURLSession.sessionWithConfiguration(NSURLSessionConfiguration.ephemeralSessionConfiguration(), delegate, null)
    val task = session.dataTaskWithURL(url)
    continuation.invokeOnCancellation { task.cancel(); session.invalidateAndCancel() }
    task.resume()
}

@OptIn(ExperimentalForeignApi::class)
private class IosNeighborhoodAvatarDelegate(private val continuation: CancellableContinuation<NSData>) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = mutableListOf<ByteArray>()
    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) { if (continuation.isActive) chunks += didReceiveData.toNeighborhoodAvatarBytes() }
    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        val data = chunks.toFoundationData().takeIf { it.length > 0uL }
        val status = (task.response as? NSHTTPURLResponse)?.statusCode?.toInt()
        if (didCompleteWithError != null || status !in 200..299 || data == null) continuation.resumeWithException(IllegalStateException("ios_neighborhood_avatar_unavailable")) else continuation.resume(data)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toNeighborhoodAvatarBytes(): ByteArray = if (length == 0uL) ByteArray(0) else bytes?.readBytes(length.toInt()) ?: ByteArray(0)

internal fun isIosNeighborhoodAvatarUrl(value: String): Boolean = value.startsWith("https://") || value.startsWith("http://")

internal fun iosNeighborhoodAvatarRequestKey(value: String?): String? = value?.trim()?.takeIf(::isIosNeighborhoodAvatarUrl)

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
