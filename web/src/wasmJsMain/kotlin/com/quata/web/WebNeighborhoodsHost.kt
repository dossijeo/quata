package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.DocumentPreviewKind
import com.quata.core.platform.DocumentSupport
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import com.quata.core.designsystem.theme.quataTheme
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.presentation.NeighborhoodsScreenHost
import com.quata.feature.neighborhoods.presentation.NeighborhoodsViewModel
import com.quata.feature.neighborhoods.presentation.CommunityProfileRoot
import com.quata.feature.neighborhoods.presentation.CommunityProfileStrings
import com.quata.feature.neighborhoods.presentation.ProfileAttachmentsStrings
import com.quata.feature.neighborhoods.presentation.ProfileActionStrings
import com.quata.feature.neighborhoods.presentation.ProfileModerationStrings
import com.quata.feature.neighborhoods.presentation.ProfileModerationConfirmationStrings
import com.quata.feature.neighborhoods.presentation.ProfileRoleStrings
import com.quata.feature.neighborhoods.presentation.NeighborhoodUserRowStrings
import com.quata.feature.neighborhoods.presentation.CommunityProfilePostPreviewContent
import com.quata.feature.neighborhoods.presentation.CommunityProfileCommentsDialogContent
import com.quata.feature.neighborhoods.presentation.CommunityProfileCommentsDialogStrings
import com.quata.core.model.PostComment
import com.quata.feature.neighborhoods.presentation.NeighborhoodsScreenStrings
import com.quata.feature.neighborhoods.presentation.defaultNeighborhoodsScreenStrings
import com.quata.feature.neighborhoods.presentation.defaultCommunityProfileStrings
import com.quata.feature.neighborhoods.presentation.defaultCommunityProfileCommentsDialogStrings
import com.quata.feature.neighborhoods.presentation.ProfileAttachmentRowContent
import com.quata.feature.neighborhoods.presentation.ProfileAttachmentThumbnailContent
import com.quata.feature.neighborhoods.presentation.ProfileAttachmentAudioLauncherContent

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private val browserNeighborhoodsLanguage: String = js("globalThis.navigator?.language || 'en'")

fun browserNeighborhoodsStrings(): WebNeighborhoodsStrings =
    WebNeighborhoodsStrings(defaultNeighborhoodsScreenStrings(browserNeighborhoodsLanguage))

data class WebNeighborhoodsStrings(val screen: NeighborhoodsScreenStrings)

class WebNeighborhoodsSlots(
    val avatar: @Composable (NeighborhoodUser, Boolean, Int, (() -> Unit)?) -> Unit,
)

/** Thin browser wrapper: state, gates and directory/member navigation are common. */
@Composable
fun WebNeighborhoodsHost(
    repository: NeighborhoodRepository,
    currentUserId: String?,
    strings: WebNeighborhoodsStrings,
    slots: WebNeighborhoodsSlots,
    onOpenConversation: (String) -> Unit,
    onAuthRequired: () -> Unit,
    onPostReportAuthRequired: (String) -> Unit = { onAuthRequired() },
    pendingReportPostId: String? = null,
    onPendingReportHandled: () -> Unit = {},
    onOpenUserProfile: (String) -> Unit,
    onDismissProfile: () -> Unit = {},
    profileId: String? = null,
    documentOpener: DocumentOpenService? = null,
    padding: PaddingValues = PaddingValues(),
) {
    if (profileId != null) {
        WebCommunityProfileRoute(repository, currentUserId, profileId, slots, onDismissProfile, onOpenConversation, onAuthRequired, onPostReportAuthRequired, pendingReportPostId, onPendingReportHandled, documentOpener)
    } else NeighborhoodsScreenHost(
    repository = repository,
    currentUserId = currentUserId,
    strings = strings.screen,
        avatar = { user, loading, click -> slots.avatar(user, loading, 48, click) },
    onOpenConversation = onOpenConversation,
    onOpenUserProfile = onOpenUserProfile,
    onAuthRequired = onAuthRequired,
    padding = padding,
)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WebCommunityProfileRoute(
    repository: NeighborhoodRepository,
    currentUserId: String?,
    profileId: String,
    slots: WebNeighborhoodsSlots,
    onDismiss: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onAuthRequired: () -> Unit,
    onPostReportAuthRequired: (String) -> Unit,
    pendingReportPostId: String?,
    onPendingReportHandled: () -> Unit,
    documentOpener: DocumentOpenService?,
) {
    val model = remember(repository) { NeighborhoodsViewModel(repository) }
    val state by model.uiState.collectAsState()
    val profileStrings = defaultCommunityProfileStrings(browserNeighborhoodsLanguage)
    val actionScope = rememberCoroutineScope()
    DisposableEffect(model) { onDispose(model::close) }
    LaunchedEffect(profileId) { model.openUserProfile(profileId) }
    val profile = state.selectedProfile
    var pendingReportInFlight by remember(profileId, pendingReportPostId) { mutableStateOf(false) }
    LaunchedEffect(profile?.user?.id, currentUserId, pendingReportPostId) {
        if (!pendingReportInFlight && profile?.user?.id == profileId && !currentUserId.isNullOrBlank() && pendingReportPostId != null) {
            pendingReportInFlight = true
            model.reportProfilePost(pendingReportPostId) { success ->
                pendingReportInFlight = false
                if (success) onPendingReportHandled()
            }
        }
    }
    if (profile == null) {
        Column {
            Text(state.error ?: profileStrings.runtime.loadingProfile)
            if (state.error != null) Button(onClick = { model.openUserProfile(profileId) }) { Text(profileStrings.runtime.retry) }
        }
        return
    }
    val theme = quataTheme()
    CommunityProfileRoot(
        profile = profile,
        currentUserId = currentUserId,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = theme.colors.background,
        contentColor = theme.colors.textPrimary,
        strings = profileStrings,
        isOpeningChat = state.openingPrivateChatUserId != null,
        isRefreshing = state.refreshingProfileUserId != null,
        followingUserId = state.followingUserId,
        openingProfileUserId = state.openingProfileUserId,
        roleUpdatingUserId = state.roleUpdatingUserId,
        currentUserIsAdmin = state.currentUserIsAdmin,
        error = state.error,
        showModeration = true,
        showAdminControls = true,
        onDismiss = { model.closeUserProfile(); onDismiss() },
        onAuthRequired = onAuthRequired,
        onFollowUser = model::toggleFollowUser,
        onOpenPrivateChat = { model.openPrivateChat(it, onOpenConversation) },
        onOpenUserProfile = model::openUserProfile,
        onReportProfile = model::reportProfile, onBlockProfile = { model.blockProfile(it, onDismiss) }, onSetRoles = model::setUserRoles,
        onAddPostComment = model::addProfilePostComment,
        avatar = { user, loading, role, click -> slots.avatar(user, loading, role.sizeDp, click) },
        attachmentItem = { attachment ->
            val open: () -> Unit = {
                val file = PlatformFile(attachment.uri, attachment.name, attachment.mimeType)
                when (DocumentSupport.describe(file.reference, file.displayName, file.mimeType).kind) {
                    DocumentPreviewKind.Pdf, DocumentPreviewKind.RichText, DocumentPreviewKind.Office ->
                        if (documentOpener != null) actionScope.launch { documentOpener.open(file) } else webOpenProfileResource(attachment.uri)
                    else -> webOpenProfileResource(attachment.uri)
                }
                Unit
            }
            ProfileAttachmentRowContent(
                attachment = attachment,
                audioPlayer = { ProfileAttachmentAudioLauncherContent(attachment, profileStrings.runtime, open) },
                thumbnail = { ProfileAttachmentThumbnailContent(attachment, profileStrings.runtime) },
                onOpen = open,
            )
        },
        postPreview = { post, count, isCurrent, openComments ->
            CommunityProfilePostPreviewContent(post, count, currentUserId != null, openComments, onAuthRequired, { onPostReportAuthRequired(post.id) },
                { webShareProfilePost(post.id) }, { model.reportProfilePost(post.id) }, { model.toggleProfilePostLike(post.id) }, media = { loaded, load ->
                    if (post.videoUrl != null && !loaded) Button(onClick = load) { Text(profileStrings.runtime.loadVideo) }
                    else {
                        var position by remember(post.id) { mutableLongStateOf(0L) }
                        var muted by remember(post.id) { mutableStateOf(false) }
                        BrowserFeedMediaContent(post, isCurrent, muted, position, { position = it }, { muted = it })
                    }
                })
        },
        commentsDialog = { post, comments, submit, dismiss ->
            CommunityProfileCommentsDialogContent(post, comments, currentUserId,
                defaultCommunityProfileCommentsDialogStrings(browserNeighborhoodsLanguage), state.error, onAuthRequired,
                submit, dismiss)
        },
    )
    /*
        Column {
            Text(profile.user.displayName)
            Text("${profile.user.postsCount} posts · ${profile.user.followersCount} followers")
            val peopleList = navigation.peopleList
            if (peopleList != null) {
                val people = if (peopleList.name == "Followers") profile.followers else profile.following
                people.forEach { Text(it.displayName) }
                Text("Back", modifier = androidx.compose.ui.Modifier) // root state is preserved for host actions
            } else {
                Text("Posts", modifier = androidx.compose.ui.Modifier)
                profile.posts.forEach { Text(it.text) }
            }
        }
    }*/
}

private fun webOpenProfileResource(url: String) { js("globalThis.open(url, '_blank', 'noopener,noreferrer')") }
private fun webShareProfilePost(postId: String) {
    js(
        """
        (() => {
          const url = globalThis.location.origin + '/#post/' + postId;
          const copy = () => globalThis.navigator?.clipboard?.writeText(url)
            ?.catch(() => globalThis.prompt('Copy this link', url))
            ?? globalThis.prompt('Copy this link', url);
          if (typeof globalThis.navigator?.share === 'function') {
            globalThis.navigator.share({url}).catch(copy);
          } else copy();
        })()
        """
    )
}
