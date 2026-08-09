package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.language.FangTranslationService
import com.quata.core.language.IosFastTextLanguageIdentifier
import com.quata.core.language.IosTranslationHttpTransport
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.FilePickerSource
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.VideoThumbnailService
import com.quata.feature.official.data.IosOfficialReadRepository
import com.quata.feature.official.data.IosOfficialRuntimeConfiguration
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialRepository
import com.quata.feature.postcomposer.data.IosPostComposerRuntimeConfiguration
import com.quata.feature.postcomposer.data.IosPostComposerTransport
import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.platform.IosShareService
import com.quata.core.platform.ShareService
import com.quata.core.ui.components.IosMemberProfileOpeningState
import com.quata.core.ui.richtext.QuataPortableRichTextEditorBox
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController
import platform.Foundation.NSUUID
import platform.Foundation.NSURL
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

/** Narrow Swift-owned native viewer contract; it never owns pager or Official state. */
interface IosOfficialMediaViewerFactory {
    fun create(url: String, isVideo: Boolean): IosOfficialMediaViewerSurface
}

interface IosOfficialMediaViewerSurface {
    fun nativeView(): platform.UIKit.UIView
    fun dispose()
}

/**
 * iOS composition input for the shared Official list and detail flow.
 *
 * [repository] belongs to the iOS launcher: it supplies authenticated transport and lifecycle.
 * The UIKit launcher supplies only target seams; the product screen remains common.
 */
class IosOfficialHostDependencies(
    val repository: OfficialRepository,
    val officialPostId: String? = null,
    val currentUserId: String? = null,
    val preferredLanguageTag: String? = null,
    val shareService: ShareService = IosShareService(),
    val mediaViewerFactory: IosOfficialMediaViewerFactory? = null,
    val canCreateOfficialPost: Boolean = false,
    val onAuthRequired: () -> Unit = {},
    val onOpenUserProfile: (String) -> Unit = {},
    val onCreateOfficialPost: () -> Unit = {},
    val profileOpeningState: IosMemberProfileOpeningState,
)

/**
 * Swift-facing dependency factory for the shared iOS Official vertical.
 *
 * Keeping the default platform slots in Kotlin avoids making the UIKit launcher construct
 * Compose slot objects or depend on Kotlin default-argument Objective-C export details.
 */
fun createIosOfficialHostDependencies(
    repository: OfficialRepository,
    officialPostId: String?,
    shareService: ShareService = IosShareService(), mediaViewerFactory: IosOfficialMediaViewerFactory? = null,
    currentUserId: String? = null,
    preferredLanguageTag: String? = null,
    onAuthRequired: () -> Unit = {}, onOpenUserProfile: (String) -> Unit = {},
    onCreateOfficialPost: () -> Unit = {},
    canCreateOfficialPost: Boolean = false,
    profileOpeningState: IosMemberProfileOpeningState,
): IosOfficialHostDependencies = IosOfficialHostDependencies(
    repository = repository,
    officialPostId = officialPostId,
    currentUserId = currentUserId,
    preferredLanguageTag = preferredLanguageTag,
    shareService = shareService,
    mediaViewerFactory = mediaViewerFactory,
    onAuthRequired = onAuthRequired, onOpenUserProfile = onOpenUserProfile,
    onCreateOfficialPost = onCreateOfficialPost,
    canCreateOfficialPost = canCreateOfficialPost,
    profileOpeningState = profileOpeningState,
)

/**
 * Creates the public iOS Official browser from client-safe deployment values only.
 *
 * The repository has no interactive-session parameter on this route. Its read transport can
 * therefore neither wait for Keychain restoration nor attach a bearer credential; authenticated
 * interactions, including publishing, fail closed until the authenticated host injects a session.
 */
fun iosPublicPostgrestReadOnlyOfficialHostDependencies(
    configuration: IosOfficialRuntimeConfiguration,
    officialPostId: String? = null,
    shareService: ShareService = IosShareService(),
    mediaViewerFactory: IosOfficialMediaViewerFactory? = null,
    onAuthRequired: () -> Unit = {}, onOpenUserProfile: (String) -> Unit = {},
    profileOpeningState: IosMemberProfileOpeningState,
): IosOfficialHostDependencies = createIosOfficialHostDependencies(
    repository = IosOfficialReadRepository(configuration = configuration),
    officialPostId = officialPostId,
    shareService = shareService,
    mediaViewerFactory = mediaViewerFactory,
    onAuthRequired = onAuthRequired, onOpenUserProfile = onOpenUserProfile,
    profileOpeningState = profileOpeningState,
)

/** Authenticated iOS path reuses the renewable Keychain session for reviewed Official writes. */
fun iosAuthenticatedPostgrestOfficialHostDependencies(
    configuration: IosOfficialRuntimeConfiguration,
    authSession: IosRenewableAuthSession,
    officialPostId: String? = null,
    shareService: ShareService = IosShareService(),
    mediaViewerFactory: IosOfficialMediaViewerFactory? = null,
    currentUserId: String? = authSession.restoredSession()?.userId,
    onAuthRequired: () -> Unit = {},
    onOpenUserProfile: (String) -> Unit = {},
    onCreateOfficialPost: () -> Unit = {},
    canCreateOfficialPost: Boolean = false,
    preferredLanguageTag: String? = null,
    profileOpeningState: IosMemberProfileOpeningState,
): IosOfficialHostDependencies = createIosOfficialHostDependencies(
    repository = IosOfficialReadRepository(configuration = configuration, authSession = authSession, preferredLanguageTag = preferredLanguageTag),
    officialPostId = officialPostId,
    shareService = shareService,
    mediaViewerFactory = mediaViewerFactory,
    currentUserId = currentUserId,
    preferredLanguageTag = preferredLanguageTag,
    onAuthRequired = onAuthRequired,
    onOpenUserProfile = onOpenUserProfile,
    canCreateOfficialPost = canCreateOfficialPost,
    onCreateOfficialPost = onCreateOfficialPost,
    profileOpeningState = profileOpeningState,
)

/**
 * Stable Swift-callable factory for the shared Official list/detail viewport.
 * No repository implementation is created here; the iOS composition root injects the real one.
 */
fun QuataOfficialViewController(dependencies: IosOfficialHostDependencies): UIViewController =
    ComposeUIViewController {
        QuataTheme {
            val strings = defaultOfficialFeedScreenStrings(dependencies.preferredLanguageTag)
            val openingProfileUserId by dependencies.profileOpeningState.profileId.collectAsState()
            OfficialFeedScreenHost(
                padding = PaddingValues(),
                repository = dependencies.repository,
                currentUserId = dependencies.currentUserId,
                strings = strings,
                focusedPostId = dependencies.officialPostId,
                onAuthRequired = dependencies.onAuthRequired,
                onOpenUserProfile = dependencies.onOpenUserProfile,
                onCreateOfficialPost = dependencies.onCreateOfficialPost,
                slots = iosOfficialPlatformSlots(
                    dependencies.shareService,
                    dependencies.mediaViewerFactory,
                    dependencies.canCreateOfficialPost,
                    strings.close,
                    openingProfileUserId,
                    dependencies.preferredLanguageTag,
                ),
                onFocusedPostHandled = {},
                modifier = Modifier,
            )
        }
    }

/**
 * iOS composition input for the shared Official editor shell.
 *
 * The editor fields, media picker and publication actions are intentionally injected. This avoids
 * a pretend iOS backend while allowing the shared editor hierarchy to be hosted now.
 */
class IosOfficialEditorDependencies(
    val repository: OfficialRepository,
    val filePicker: FilePickerService,
    val videoThumbnails: VideoThumbnailService,
    val currentUserId: String? = null,
    val preferredLanguageTag: String? = null,
    val onClose: () -> Unit,
)

fun createIosOfficialEditorDependencies(
    repository: OfficialRepository,
    filePicker: FilePickerService,
    videoThumbnails: VideoThumbnailService,
    currentUserId: String? = null,
    preferredLanguageTag: String? = null,
    onClose: () -> Unit,
): IosOfficialEditorDependencies = IosOfficialEditorDependencies(
    repository = repository,
    filePicker = filePicker,
    videoThumbnails = videoThumbnails,
    currentUserId = currentUserId,
    preferredLanguageTag = preferredLanguageTag,
    onClose = onClose,
)

fun iosAuthenticatedOfficialEditorDependencies(
    configuration: IosOfficialRuntimeConfiguration,
    authSession: IosRenewableAuthSession,
    filePicker: FilePickerService,
    videoThumbnails: VideoThumbnailService,
    currentUserId: String? = authSession.restoredSession()?.userId,
    preferredLanguageTag: String? = null,
    onClose: () -> Unit,
): IosOfficialEditorDependencies = createIosOfficialEditorDependencies(
    repository = IosOfficialReadRepository(
        configuration = configuration,
        authSession = authSession,
        preferredLanguageTag = preferredLanguageTag,
        mediaTransport = IosPostComposerTransport(
            configuration = IosPostComposerRuntimeConfiguration(
                supabaseUrl = configuration.supabaseUrl,
                supabasePublishableKey = configuration.supabasePublishableKey,
            ),
            authSession = authSession,
        ),
    ),
    filePicker = filePicker,
    videoThumbnails = videoThumbnails,
    currentUserId = currentUserId,
    preferredLanguageTag = preferredLanguageTag,
    onClose = onClose,
)

/** Swift-callable UIKit factory for the shared Official editor root. */
fun QuataOfficialEditorViewController(dependencies: IosOfficialEditorDependencies): UIViewController =
    ComposeUIViewController {
        QuataTheme {
            IosOfficialEditorHost(dependencies)
        }
    }

private fun PlatformResult<List<PlatformFile>>.officialSelectedFileOrNull(): PlatformFile? =
    (this as? PlatformResult.Success)?.value?.firstOrNull()

@Composable
private fun IosOfficialEditorHost(dependencies: IosOfficialEditorDependencies) {
    var currentUser by remember(dependencies.repository, dependencies.currentUserId) { mutableStateOf<com.quata.core.model.User?>(null) }
    var isPublishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var imageFile by remember { mutableStateOf<PlatformFile?>(null) }
    var videoThumbnail by remember { mutableStateOf<PlatformFile?>(null) }
    val scope = rememberCoroutineScope()
    val strings = defaultOfficialPostEditorStrings(dependencies.preferredLanguageTag)
    val translator = remember {
        OfficialPostEditorFangTranslator(FangTranslationService(transport = IosTranslationHttpTransport()))
    }

    fun releaseVideoThumbnail() { videoThumbnail = null }
    fun selectMedia(type: OfficialMediaType, onPicked: (OfficialEditorMedia) -> Unit) {
        scope.launch {
            dependencies.filePicker.pick(
                FilePickerRequest(
                    acceptedMimeTypes = listOf(if (type == OfficialMediaType.Image) "image/*" else "video/*"),
                    source = FilePickerSource.Gallery,
                ),
            ).officialSelectedFileOrNull()?.let { file ->
                if (type == OfficialMediaType.Image) {
                    imageFile = file
                    onPicked(OfficialEditorMedia(file.reference, OfficialMediaType.Image))
                } else {
                    releaseVideoThumbnail()
                    videoThumbnail = (dependencies.videoThumbnails.createThumbnail(file) as? PlatformResult.Success)?.value
                    onPicked(OfficialEditorMedia(file.reference, OfficialMediaType.Video))
                }
            }
        }
    }

    LaunchedEffect(dependencies.repository, dependencies.currentUserId) {
        currentUser = dependencies.repository.refreshCurrentUser().getOrNull()
    }
    DisposableEffect(Unit) { onDispose(::releaseVideoThumbnail) }

    OfficialPostEditorRoot(
        padding = PaddingValues(),
        currentUser = currentUser,
        isPublishing = isPublishing,
        error = error,
        strings = strings,
        slots = OfficialPostEditorPlatformSlots(
            bodyEditorAction = { html, title, onHtmlChange, modifier ->
                QuataPortableRichTextEditorBox(
                    initialHtml = html,
                    placeholder = title,
                    onHtmlChange = onHtmlChange,
                    modifier = modifier,
                )
            },
            imagePicker = { onPicked, modifier ->
                OutlinedButton(onClick = { selectMedia(OfficialMediaType.Image, onPicked) }, modifier = modifier) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text("Elegir foto")
                }
            },
            videoPicker = { onPicked, modifier ->
                OutlinedButton(onClick = { selectMedia(OfficialMediaType.Video, onPicked) }, modifier = modifier) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = null)
                    Text("Elegir vídeo")
                }
            },
            mediaPreview = { media, onPicked, onRemove, modifier ->
                OfficialEditorMediaPreviewContent(
                    removeLabel = strings.close,
                    onRemove = {
                        if (media.type == OfficialMediaType.Video) releaseVideoThumbnail()
                        onRemove()
                    },
                    modifier = modifier,
                    mediaContent = { mediaModifier ->
                        when (media.type) {
                            OfficialMediaType.Image -> imageFile?.let { IosOfficialLocalImagePreview(it, mediaModifier) }
                            OfficialMediaType.Video -> videoThumbnail?.let { IosOfficialLocalImagePreview(it, mediaModifier) }
                        }
                    },
                    editAction = { editModifier ->
                        OutlinedButton(onClick = { selectMedia(media.type, onPicked) }, modifier = editModifier) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Text("Cambiar")
                        }
                    },
                )
            },
            preview = { state, modifier ->
                IosOfficialEditorPreview(
                    state = state,
                    strings = strings,
                    languageTag = dependencies.preferredLanguageTag,
                    previewFile = when (state.mediaType) {
                        OfficialMediaType.Image -> imageFile
                        OfficialMediaType.Video -> videoThumbnail
                        null -> null
                    },
                    modifier = modifier,
                )
            },
        ),
        language = iosOfficialPostLanguage(dependencies.preferredLanguageTag),
        canPublish = currentUser?.isOfficial == true,
        onSubmit = { drafts: List<OfficialPostDraft> ->
            scope.launch {
                isPublishing = true
                error = null
                dependencies.repository.createPosts(drafts)
                    .onSuccess { dependencies.onClose() }
                    .onFailure { failure -> error = failure.message ?: "ios_official_publish_failed" }
                isPublishing = false
            }
        },
        detectLanguage = { draft ->
            detectOfficialPostLanguage(
                identifier = IosFastTextLanguageIdentifier,
                draft = draft,
                fallback = iosOfficialPostLanguage(dependencies.preferredLanguageTag),
            )
        },
        translator = translator,
        newTranslationGroupId = { NSUUID.UUID().UUIDString },
    )
}

@Composable
private fun IosOfficialLocalImagePreview(file: PlatformFile, modifier: Modifier) {
    val image = remember(file.reference) { file.toIosOfficialImageOrNull() }
    if (image != null) {
        UIKitView(
            factory = {
                UIImageView().apply {
                    contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
                    clipsToBounds = true
                    this.image = image
                }
            },
            update = { it.image = image },
            modifier = modifier,
        )
    }
}

private fun PlatformFile.toIosOfficialImageOrNull(): UIImage? {
    val path = NSURL(string = reference)?.path ?: reference.takeIf { it.startsWith("/") } ?: return null
    return UIImage(contentsOfFile = path)
}

@Composable
private fun IosOfficialEditorPreview(
    state: OfficialPostEditorPreviewState,
    strings: OfficialPostEditorStrings,
    languageTag: String?,
    previewFile: PlatformFile?,
    modifier: Modifier,
) {
    val feedStrings = defaultOfficialFeedScreenStrings(languageTag)
    val post = officialPostEditorPreviewItem(
        state = state,
        fallbackAuthorLabel = feedStrings.officialAccountFallback,
        defaultTitle = strings.defaultTitle,
        summaryFallback = strings.summaryLabel,
        createdAt = "Ahora",
    )
    OfficialEditorPostPreviewContent(
        post = post,
        typeLabel = feedStrings.typeLabel(state.postType),
        readMoreLabel = feedStrings.readMoreLabel(state.readMoreLabel),
        closeLabel = strings.close,
        author = { authorModifier ->
            OfficialAuthorHeaderContent(
                displayName = post.author.displayName,
                neighborhood = post.author.neighborhood,
                fallbackNeighborhood = feedStrings.officialAccountFallback,
                avatar = {},
                modifier = authorModifier,
            )
        },
        media = if (state.mediaUrl.isBlank() || state.mediaType == null || previewFile == null) null else {
            { mediaModifier -> IosOfficialLocalImagePreview(previewFile, mediaModifier) }
        },
        articleContent = { selectedPost, articleModifier ->
            com.quata.core.ui.richtext.QuataRichTextRenderer(selectedPost.contentHtml, articleModifier, selectedPost.contentPlain)
        },
        modifier = modifier,
    )
}

private fun iosOfficialPostLanguage(languageTag: String?): OfficialPostLanguage = when (languageTag?.substringBefore('-')?.lowercase()) {
    "en" -> OfficialPostLanguage.English
    "fr" -> OfficialPostLanguage.French
    else -> OfficialPostLanguage.Spanish
}
