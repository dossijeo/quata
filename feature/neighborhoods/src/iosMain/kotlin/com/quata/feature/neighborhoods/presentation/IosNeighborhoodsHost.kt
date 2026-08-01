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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import com.quata.core.data.toFoundationData
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.PostComment
import com.quata.core.platform.ShareService
import com.quata.core.platform.SharePayload
import com.quata.core.ui.components.QuataAvatarFrameContent
import com.quata.core.ui.components.QuataLiveRankingItem
import com.quata.core.ui.components.QuataLiveRankingPanelContent
import com.quata.core.ui.components.QuataLiveRankingStrings
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.domain.ProfileAttachment
import com.quata.feature.neighborhoods.presentation.ProfileAttachmentRowContent
import com.quata.feature.neighborhoods.presentation.ProfileAttachmentThumbnailContent
import com.quata.feature.neighborhoods.presentation.ProfileAttachmentAudioLauncherContent
import com.quata.feature.feed.presentation.IosFeedMediaFactory
import com.quata.feature.feed.presentation.IosFeedMediaSlot
import com.quata.feature.chat.data.IosChatAttachmentPreviewService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.VideoThumbnailService
import platform.UIKit.UIViewController
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSFileManager
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
import kotlinx.coroutines.launch

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
    val profileStrings: CommunityProfileStrings,
    val profileCommentsStrings: CommunityProfileCommentsDialogStrings,
    val avatar: @Composable (NeighborhoodUser, Boolean, () -> Unit) -> Unit,
    val onOpenConversation: (String) -> Unit,
    val profileNavigator: IosCommunityProfileNavigator,
    val onAuthRequired: () -> Unit,
    val onPostReportAuthRequired: (String) -> Unit,
    val mediaFactory: IosFeedMediaFactory,
    val shareService: ShareService,
    val attachmentPreviewService: IosChatAttachmentPreviewService,
    val audioPlayer: AudioPlayerService,
    val videoThumbnails: VideoThumbnailService,
    val pendingReportPostId: String?,
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
    onPostReportAuthRequired: (String) -> Unit,
    mediaFactory: IosFeedMediaFactory,
    shareService: ShareService,
    attachmentPreviewService: IosChatAttachmentPreviewService,
    audioPlayer: AudioPlayerService,
    videoThumbnails: VideoThumbnailService,
    pendingReportPostId: String?,
): IosNeighborhoodsHostDependencies = IosNeighborhoodsHostDependencies(
    repository = repository,
    viewModel = NeighborhoodsViewModel(repository, errors = defaultNeighborhoodsErrorStrings(languageTag)),
    currentUserId = currentUserId,
    listStrings = defaultNeighborhoodsScreenStrings(languageTag).list,
    usersStrings = defaultNeighborhoodsScreenStrings(languageTag).members,
    profileStrings = defaultCommunityProfileStrings(languageTag),
    profileCommentsStrings = defaultCommunityProfileCommentsDialogStrings(languageTag),
    avatar = { user, _, onClick -> IosNeighborhoodAvatar(user, onClick) },
    onOpenConversation = onOpenConversation,
    profileNavigator = IosCommunityProfileNavigator(onNavigateToProfile),
    onAuthRequired = onAuthRequired,
    onPostReportAuthRequired = onPostReportAuthRequired,
    mediaFactory = mediaFactory,
    shareService = shareService,
    attachmentPreviewService = attachmentPreviewService,
    audioPlayer = audioPlayer,
    videoThumbnails = videoThumbnails,
    pendingReportPostId = pendingReportPostId,
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
        var reportNotice by rememberSaveable(profileId) { mutableStateOf<String?>(null) }
        var pendingReportHandled by rememberSaveable(profileId, dependencies.pendingReportPostId) { mutableStateOf(false) }
        LaunchedEffect(profile?.user?.id, dependencies.currentUserId, dependencies.pendingReportPostId) {
            val pendingPostId = dependencies.pendingReportPostId
            if (!pendingReportHandled && profile?.user?.id == profileId && !dependencies.currentUserId.isNullOrBlank() && pendingPostId != null) {
                pendingReportHandled = true
                dependencies.viewModel.reportProfilePost(pendingPostId)
            }
        }
        val actionScope = rememberCoroutineScope()
        if (profile == null) {
            Column {
                Text(state.error ?: dependencies.profileStrings.runtime.loadingProfile)
                if (state.error != null) Button(onClick = { dependencies.viewModel.openUserProfile(profileId) }) { Text(dependencies.profileStrings.runtime.retry) }
                TextButton(onClick = onDismiss) { Text(dependencies.profileStrings.back) }
            }
        } else {
            var failedAttachment by remember(profileId) { mutableStateOf<ProfileAttachment?>(null) }
            var attachmentError by rememberSaveable(profileId) { mutableStateOf<String?>(null) }
            val openAttachment: (ProfileAttachment) -> Unit = { attachment ->
                actionScope.launch {
                    attachmentError = null
                    when (val result = dependencies.attachmentPreviewService.openRemoteAttachment(PlatformFile(attachment.uri, attachment.name, attachment.mimeType))) {
                        is PlatformResult.Success -> failedAttachment = null
                        is PlatformResult.Failure -> { failedAttachment = attachment; attachmentError = result.reason ?: dependencies.profileStrings.runtime.attachmentOpenFailed }
                        PlatformResult.Cancelled -> { failedAttachment = attachment; attachmentError = dependencies.profileStrings.runtime.attachmentCancelled }
                        PlatformResult.Unsupported -> { failedAttachment = attachment; attachmentError = dependencies.profileStrings.runtime.attachmentUnsupported }
                    }
                }
            }
            val theme = com.quata.core.designsystem.theme.quataTheme()
            CommunityProfileRoot(
                profile = profile,
                currentUserId = dependencies.currentUserId,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = theme.colors.background,
                contentColor = theme.colors.textPrimary,
                strings = dependencies.profileStrings,
                isOpeningChat = state.openingPrivateChatUserId != null,
                isRefreshing = state.refreshingProfileUserId != null,
                followingUserId = state.followingUserId,
                openingProfileUserId = state.openingProfileUserId,
                roleUpdatingUserId = state.roleUpdatingUserId,
                currentUserIsAdmin = state.currentUserIsAdmin,
                error = state.error,
                notice = reportNotice,
                showModeration = true,
                showAdminControls = true,
                onDismiss = onDismiss,
                onAuthRequired = dependencies.onAuthRequired,
                onFollowUser = dependencies.viewModel::toggleFollowUser,
                onOpenPrivateChat = { dependencies.viewModel.openPrivateChat(it, dependencies.onOpenConversation) },
                onOpenUserProfile = dependencies.profileNavigator::openMemberProfile,
                onReportProfile = { id ->
                    reportNotice = null
                    dependencies.viewModel.reportProfile(id) { success ->
                        if (success) reportNotice = dependencies.profileStrings.runtime.reportSuccess
                    }
                },
                onBlockProfile = { dependencies.viewModel.blockProfile(it, onDismiss) },
                onSetRoles = dependencies.viewModel::setUserRoles,
                onAddPostComment = dependencies.viewModel::addProfilePostComment,
                avatar = { user, _, role, click -> IosNeighborhoodAvatar(user, click, Modifier.size(role.sizeDp.dp)) },
                attachmentItem = { attachment ->
                    Column {
                        ProfileAttachmentRowContent(
                            attachment = attachment,
                            audioPlayer = {
                                ProfileAttachmentAudioPlayerContent(
                                    attachment = attachment,
                                    strings = dependencies.profileStrings.runtime,
                                    audioPlayer = dependencies.audioPlayer,
                                    prepareFile = { dependencies.attachmentPreviewService.downloadRemoteAttachment(PlatformFile(attachment.uri, attachment.name, attachment.mimeType)) },
                                    releaseFile = { dependencies.attachmentPreviewService.releaseDownloadedAttachment(it) },
                                )
                            },
                            thumbnail = { IosProfileAttachmentThumbnail(attachment, dependencies) },
                            onOpen = { openAttachment(attachment) },
                        )
                        if (failedAttachment?.uri == attachment.uri && attachmentError != null) {
                            Text(requireNotNull(attachmentError), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                            Button(onClick = { openAttachment(attachment) }) { Text(dependencies.profileStrings.runtime.retry) }
                        }
                    }
                },
                postPreview = { post, count, isCurrent, openComments ->
                    CommunityProfilePostPreviewContent(post, count, dependencies.currentUserId != null, openComments, dependencies.onAuthRequired, { dependencies.onPostReportAuthRequired(post.id) },
                        { actionScope.launch { dependencies.shareService.share(SharePayload(text = "https://quata.app/post/${post.id}")) } }, { dependencies.viewModel.reportProfilePost(post.id) }, { dependencies.viewModel.toggleProfilePostLike(post.id) }, media = { loaded, load ->
                            if (post.videoUrl != null && !loaded) Button(onClick = load) { Text(dependencies.profileStrings.runtime.loadVideo) }
                            else {
                                var position by remember(post.id) { mutableLongStateOf(0L) }
                                var muted by remember(post.id) { mutableStateOf(false) }
                                Box { IosFeedMediaSlot(post, isCurrent, position, { position = it }, muted, { muted = it }, dependencies.mediaFactory) }
                            }
                        })
                },
                commentsDialog = { post, comments, submit, dismiss ->
                    CommunityProfileCommentsDialogContent(post, comments, dependencies.currentUserId,
                        dependencies.profileCommentsStrings, state.error, dependencies.onAuthRequired,
                        submit, dismiss)
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

@Composable
@OptIn(ExperimentalForeignApi::class)
private fun IosProfileAttachmentThumbnail(
    attachment: ProfileAttachment,
    dependencies: IosNeighborhoodsHostDependencies,
) {
    var localFile by remember(attachment.uri) { mutableStateOf<PlatformFile?>(null) }
    var generatedThumbnail by remember(attachment.uri) { mutableStateOf<PlatformFile?>(null) }
    var image by remember(attachment.uri) { mutableStateOf<UIImage?>(null) }
    LaunchedEffect(attachment.uri) {
        if (attachment.visualKind() !in setOf(ProfileAttachmentVisualKind.Image, ProfileAttachmentVisualKind.Video)) return@LaunchedEffect
        val downloaded = dependencies.attachmentPreviewService.downloadRemoteAttachment(PlatformFile(attachment.uri, attachment.name, attachment.mimeType))
        val source = (downloaded as? PlatformResult.Success)?.value ?: return@LaunchedEffect
        localFile = source
        val visual = if (attachment.visualKind() == ProfileAttachmentVisualKind.Video) {
            (dependencies.videoThumbnails.createThumbnail(source, 320) as? PlatformResult.Success)?.value
        } else source
        if (visual !== source) generatedThumbnail = visual
        val path = visual?.reference?.let { reference -> NSURL(string = reference)?.path ?: reference.removePrefix("file://") }
        image = path?.let { UIImage(contentsOfFile = it) }
    }
    DisposableEffect(attachment.uri) {
        onDispose {
            localFile?.let(dependencies.attachmentPreviewService::releaseDownloadedAttachment)
            generatedThumbnail?.reference?.let { reference ->
                val path = NSURL(string = reference)?.path ?: reference.removePrefix("file://")
                NSFileManager.defaultManager.removeItemAtPath(path, error = null)
            }
        }
    }
    val decoded = image
    if (decoded != null) {
        Box(Modifier.size(58.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))) {
            UIKitView(
                factory = { UIImageView().apply { contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill; clipsToBounds = true; this.image = decoded } },
                update = { it.image = decoded },
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else ProfileAttachmentThumbnailContent(attachment, dependencies.profileStrings.runtime)
}

/** Real remote avatar slot with the common frame retaining its deterministic fallback. */
@Composable
private fun IosNeighborhoodAvatar(user: NeighborhoodUser, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val url = iosNeighborhoodAvatarRequestKey(user.avatarUrl)
    var image by remember(url) { mutableStateOf<UIImage?>(null) }
    LaunchedEffect(url) { image = if (url == null) null else loadIosNeighborhoodAvatarOrNull(url) }
    QuataAvatarFrameContent(
        name = user.displayName,
        stableId = user.id,
        isOfficial = user.isOfficial,
        modifier = modifier.let { base -> if (onClick != null) base.clickable(onClick = onClick) else base },
        avatar = image?.let { decoded -> { UIKitView(factory = { UIImageView().apply { contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill; clipsToBounds = true; image = decoded } }, update = { it.image = decoded }, modifier = Modifier.fillMaxSize()) } },
    )
}

private suspend fun loadIosNeighborhoodAvatarOrNull(url: String): UIImage? =
    iosNeighborhoodAvatarResultOrNull {
        UIImage(data = iosNeighborhoodAvatarData(NSURL(string = url) ?: error("ios_neighborhood_avatar_url_invalid")))
    }

internal suspend fun <T> iosNeighborhoodAvatarResultOrNull(block: suspend () -> T): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Throwable) {
    null
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun iosNeighborhoodAvatarData(url: NSURL): NSData = suspendCancellableCoroutine { continuation ->
    val delegate = IosNeighborhoodAvatarDelegate(continuation)
    val session = NSURLSession.sessionWithConfiguration(NSURLSessionConfiguration.ephemeralSessionConfiguration(), delegate, null)
    val task = session.dataTaskWithURL(url)
    registerIosNeighborhoodAvatarTaskCancellation(continuation) { task.cancel(); session.invalidateAndCancel() }
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

internal fun registerIosNeighborhoodAvatarTaskCancellation(continuation: CancellableContinuation<*>, cancelTask: () -> Unit) {
    continuation.invokeOnCancellation { cancelTask() }
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
