@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.quata.core.model.User
import com.quata.core.model.Post
import com.quata.core.language.BrowserTranslationHttpTransport
import com.quata.core.language.FangTranslationService
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerSource
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.ShareService
import com.quata.core.ui.components.QuataAvatarLoadingHaloContent
import com.quata.core.ui.richtext.QuataPortableRichTextEditorBox
import com.quata.core.ui.richtext.QuataRichTextRenderer
import com.quata.designsystem.translation.FangTextTranslatorGateway
import com.quata.designsystem.translation.quataTranslatorPreferredLanguage
import com.quata.designsystem.translation.quataTranslatorStringsForLanguage
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialRepository
import com.quata.feature.official.presentation.OfficialFeedScreenHost
import com.quata.feature.official.presentation.OfficialFeedScreenPlatformSlots
import com.quata.feature.official.presentation.OfficialAuthorHeaderContent
import com.quata.feature.official.presentation.OfficialEditorMedia
import com.quata.feature.official.presentation.OfficialEditorMediaPreviewContent
import com.quata.feature.official.presentation.OfficialEditorPostPreviewContent
import com.quata.feature.official.presentation.OfficialPostEditorFangTranslator
import com.quata.feature.official.presentation.OfficialPostEditorPlatformSlots
import com.quata.feature.official.presentation.OfficialPostEditorRoot
import com.quata.feature.official.presentation.OfficialPostEditorPreviewState
import com.quata.feature.official.presentation.defaultOfficialPostEditorStrings
import com.quata.feature.official.presentation.defaultOfficialFeedScreenStrings
import com.quata.feature.official.presentation.detectOfficialPostLanguage
import com.quata.feature.official.presentation.officialPostEditorPreviewItem
import com.quata.feature.official.presentation.OfficialPostMediaFrameContent
import com.quata.feature.official.presentation.officialAuthorAvatarTestTag
import kotlinx.browser.document
import kotlinx.coroutines.launch
import kotlin.js.JsString
import kotlin.js.toJsString

/** Browser adapter: navigation and browser services only; the Official screen itself is common. */
@Composable
fun WebOfficialHost(
    repository: WebOfficialRepository,
    shareService: ShareService,
    officialPostId: String?,
    currentUserId: String?,
    canCreateOfficialPost: Boolean,
    openingProfileUserId: String? = null,
    onAuthRequired: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onCreateOfficialPost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val languageTag = webOfficialLanguageTag()
    val commentsTranslationGateway = remember {
        FangTextTranslatorGateway(
            identifier = BrowserFastTextLanguageIdentifier,
            translator = FangTranslationService(transport = BrowserTranslationHttpTransport()),
            preferredLanguage = quataTranslatorPreferredLanguage(languageTag),
        )
    }
    OfficialFeedScreenHost(
        padding = PaddingValues(),
        repository = repository,
        currentUserId = currentUserId,
        focusedPostId = officialPostId,
        onAuthRequired = onAuthRequired,
        onOpenUserProfile = onOpenUserProfile,
        onCreateOfficialPost = onCreateOfficialPost,
        onFocusedPostHandled = {},
        strings = defaultOfficialFeedScreenStrings(languageTag),
        modifier = modifier,
        slots = OfficialFeedScreenPlatformSlots(
        avatar = { post, avatarModifier ->
            BrowserOfficialAuthorAvatar(post, onOpenUserProfile, openingProfileUserId == post.author.id, avatarModifier)
        },
        media = { post, mediaModifier, open ->
            OfficialPostMediaFrameContent(
                onOpenMedia = open,
                showPlayButton = post.mediaType == com.quata.feature.official.domain.OfficialMediaType.Video,
                modifier = mediaModifier,
                media = { surface -> BrowserOfficialMediaThumbnail(post, surface) },
            )
        },
        article = { post, articleModifier -> QuataRichTextRenderer(post.contentHtml, articleModifier, post.contentPlain) },
        mediaViewer = { post, dismiss -> post.mediaUrl?.let { url -> openBrowserUrl(url) }; dismiss() },
        share = { payload -> shareService.share(payload) },
        message = {},
        showComposeMessage = true,
        canCreateOfficialPost = canCreateOfficialPost,
        openUrl = { url -> openBrowserUrl(url) },
        rankingAvatar = { item -> BrowserFeedRankingAvatar(item) },
        commentsTranslationGateway = commentsTranslationGateway,
        commentsTranslatorStrings = quataTranslatorStringsForLanguage(languageTag),
        ),
    )
}

/** Browser editor adapter: acquisition/rendering are native seams; form and preview are common. */
@Composable
fun WebOfficialEditorHost(
    repository: OfficialRepository,
    platformServices: WebPlatformServices,
    currentUserId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentUser by remember(repository, currentUserId) { mutableStateOf<User?>(null) }
    var isPublishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val translator = remember {
        OfficialPostEditorFangTranslator(FangTranslationService(transport = BrowserTranslationHttpTransport()))
    }
    androidx.compose.runtime.LaunchedEffect(repository, currentUserId) {
        currentUser = repository.refreshCurrentUser().getOrNull()
    }
    OfficialPostEditorRoot(
        padding = PaddingValues(),
        currentUser = currentUser,
        isPublishing = isPublishing,
        error = error,
        strings = defaultOfficialPostEditorStrings(webOfficialLanguageTag()),
        slots = webOfficialEditorPlatformSlots(platformServices),
        language = webOfficialPostLanguage(),
        canPublish = currentUser?.isOfficial == true,
        onSubmit = { drafts: List<OfficialPostDraft> ->
            scope.launch {
                isPublishing = true
                error = null
                repository.createPosts(drafts)
                    .onSuccess { onBack() }
                    .onFailure { failure -> error = failure.message ?: "web_official_publish_failed" }
                isPublishing = false
            }
        },
        detectLanguage = { draft ->
            detectOfficialPostLanguage(
                identifier = BrowserFastTextLanguageIdentifier,
                draft = draft,
                fallback = webOfficialPostLanguage(),
            )
        },
        translator = translator,
        newTranslationGroupId = { webRandomUuid() },
        modifier = modifier,
    )
}

@Composable
private fun webOfficialEditorPlatformSlots(platformServices: WebPlatformServices) = OfficialPostEditorPlatformSlots(
    bodyEditorAction = { html, title, onHtmlChange, buttonModifier ->
        QuataPortableRichTextEditorBox(
            initialHtml = html,
            placeholder = title,
            onHtmlChange = onHtmlChange,
            modifier = buttonModifier,
        )
    },
    imagePicker = { onPicked, buttonModifier ->
        val scope = rememberCoroutineScope()
        OutlinedButton(
            onClick = {
                scope.launch {
                    platformServices.filePicker.pick(
                        FilePickerRequest(listOf("image/*"), source = FilePickerSource.Gallery),
                    ).firstOfficialReferenceOrNull()?.let {
                        onPicked(OfficialEditorMedia(it, OfficialMediaType.Image))
                    }
                }
            },
            modifier = buttonModifier,
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Elegir foto", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    },
    videoPicker = { onPicked, buttonModifier ->
        val scope = rememberCoroutineScope()
        OutlinedButton(
            onClick = {
                scope.launch {
                    platformServices.filePicker.pick(
                        FilePickerRequest(listOf("video/*"), source = FilePickerSource.Gallery),
                    ).firstOfficialReferenceOrNull()?.let {
                        onPicked(OfficialEditorMedia(it, OfficialMediaType.Video))
                    }
                }
            },
            modifier = buttonModifier,
        ) {
            Icon(Icons.Filled.VideoLibrary, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Elegir vídeo", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    },
    mediaPreview = { media, onPicked, onRemove, previewModifier ->
        val scope = rememberCoroutineScope()
        OfficialEditorMediaPreviewContent(
            removeLabel = "Quitar",
            onRemove = onRemove,
            modifier = previewModifier,
            mediaContent = { mediaModifier -> BrowserOfficialEditorMedia(media, mediaModifier) },
            editAction = { editModifier ->
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            platformServices.filePicker.pick(
                                FilePickerRequest(
                                    listOf(if (media.type == OfficialMediaType.Image) "image/*" else "video/*"),
                                    source = FilePickerSource.Gallery,
                                ),
                            ).firstOfficialReferenceOrNull()?.let {
                                onPicked(OfficialEditorMedia(it, media.type))
                            }
                        }
                    },
                    modifier = editModifier,
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Cambiar", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
        )
    },
    preview = { state, previewModifier -> WebOfficialEditorPreview(state, previewModifier) },
)

@Composable
private fun BrowserOfficialEditorMedia(media: OfficialEditorMedia, modifier: Modifier) {
    if (media.type == OfficialMediaType.Video) {
        BrowserOfficialVideoThumbnail(media.url, null, modifier)
    } else {
        BrowserCanvasImage(media.url, "Vista previa", ContentScale.Crop, modifier)
    }
}

@Composable
private fun WebOfficialEditorPreview(state: OfficialPostEditorPreviewState, modifier: Modifier) {
    val strings = defaultOfficialFeedScreenStrings(webOfficialLanguageTag())
    val post = officialPostEditorPreviewItem(
        state = state,
        fallbackAuthorLabel = strings.officialAccountFallback,
        defaultTitle = strings.officialAccountFallback,
        summaryFallback = "Vista previa",
        createdAt = "Ahora",
    )
    OfficialEditorPostPreviewContent(
        post = post,
        typeLabel = state.postType.remoteValue.uppercase(),
        readMoreLabel = strings.webEditorReadMoreLabel(state.readMoreLabel),
        closeLabel = strings.close,
        author = { authorModifier ->
            OfficialAuthorHeaderContent(
                displayName = post.author.displayName,
                neighborhood = post.author.neighborhood,
                fallbackNeighborhood = strings.officialAccountFallback,
                avatar = {
                    BrowserFeedAuthorAvatar(
                        post.asFeedPost(),
                        {},
                        isLoading = false,
                        modifier = Modifier.size(58.dp),
                    )
                },
                modifier = authorModifier,
            )
        },
        media = if (post.mediaUrl.isNullOrBlank()) null else {
            { mediaModifier -> BrowserOfficialEditorMedia(OfficialEditorMedia(post.mediaUrl.orEmpty(), post.mediaType ?: OfficialMediaType.Image), mediaModifier) }
        },
        articleContent = { selectedPost, articleModifier ->
            QuataRichTextRenderer(selectedPost.contentHtml, articleModifier, selectedPost.contentPlain)
        },
        modifier = modifier,
    )
}

@Composable
private fun BrowserOfficialAuthorAvatar(
    post: OfficialPostItem,
    onOpenUserProfile: (String) -> Unit,
    isLoading: Boolean,
    modifier: Modifier,
) {
    QuataAvatarLoadingHaloContent(isLoading = isLoading, modifier = modifier) {
        BrowserRemoteAvatar(
            name = post.author.displayName,
            profileId = post.author.id,
            avatarUrl = post.author.avatarUrl,
            isOfficial = post.author.isOfficial,
            isOnline = null,
            modifier = Modifier.fillMaxSize()
                .testTag(officialAuthorAvatarTestTag(post.author.id))
                .semantics { contentDescription = officialAuthorAvatarTestTag(post.author.id) }
                .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                .clickable(enabled = !isLoading) { onOpenUserProfile(post.author.id) },
        )
    }
}

@Composable
private fun BrowserOfficialMediaThumbnail(post: OfficialPostItem, modifier: Modifier) {
    val url = post.mediaUrl?.takeIf(String::isNotBlank) ?: return
    if (post.mediaType == com.quata.feature.official.domain.OfficialMediaType.Video) {
        BrowserOfficialVideoThumbnail(url, post.title, modifier)
    } else {
        BrowserCanvasImage(url, post.title, ContentScale.Crop, modifier)
    }
}

/**
 * Decodes one still frame into a blob-backed Compose image. It never attaches a player, starts
 * playback or adds controls; the common Official frame remains the sole play affordance.
 */
@Composable
private fun BrowserOfficialVideoThumbnail(videoUrl: String, contentDescription: String?, modifier: Modifier) {
    var thumbnailUrl by remember(videoUrl) { mutableStateOf<String?>(null) }
    DisposableEffect(videoUrl) {
        val cancel = createBrowserOfficialVideoThumbnail(
            videoUrl.toJsString(),
            onSuccess = { thumbnailUrl = it.toString() },
            onFailure = { thumbnailUrl = null },
        )
        onDispose(cancel)
    }
    thumbnailUrl?.let { BrowserCanvasImage(it, contentDescription, ContentScale.Crop, modifier) }
}

private fun OfficialPostItem.asFeedPost() = Post(
    id = id,
    author = author,
    text = contentPlain.ifBlank { summary },
    imageUrl = mediaUrl?.takeIf { mediaType != com.quata.feature.official.domain.OfficialMediaType.Video },
    videoUrl = mediaUrl?.takeIf { mediaType == com.quata.feature.official.domain.OfficialMediaType.Video },
    createdAt = createdAt,
)

private fun openBrowserUrl(url: String): Unit = js("globalThis.open(url, '_blank', 'noopener,noreferrer')")
private fun webOfficialLanguageTag(): String? = js("globalThis.navigator?.language || 'es'")
private fun webRandomUuid(): String = js("globalThis.crypto?.randomUUID?.() || String(Date.now())")

private fun webOfficialPostLanguage(): OfficialPostLanguage = when (webOfficialLanguageTag()?.substringBefore('-')?.lowercase()) {
    "en" -> OfficialPostLanguage.English
    "fr" -> OfficialPostLanguage.French
    else -> OfficialPostLanguage.Spanish
}

private fun com.quata.feature.official.presentation.OfficialFeedScreenStrings.webEditorReadMoreLabel(storedValue: String): String =
    when (storedValue.trim().lowercase()) {
        "more_information" -> readMoreMoreInformation
        "continue_reading" -> readMoreContinueReading
        "details" -> readMoreDetails
        else -> readMore
    }

private fun PlatformResult<List<com.quata.core.platform.PlatformFile>>.firstOfficialReferenceOrNull(): String? = when (this) {
    is PlatformResult.Success -> value.firstOrNull()?.reference
    is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported -> null
}

@JsFun(
    """(url, onSuccess, onFailure) => {
      const video = globalThis.document?.createElement?.('video');
      if (!video || !globalThis.URL?.createObjectURL) { onFailure(); return () => {}; }
      let disposed = false;
      let objectUrl = null;
      let timeout = null;
      const cleanupVideo = () => {
        if (timeout !== null) globalThis.clearTimeout?.(timeout);
        video.onloadeddata = null;
        video.onseeked = null;
        video.onerror = null;
        video.pause?.();
        video.removeAttribute('src');
        video.load?.();
      };
      const fail = () => {
        if (disposed) return;
        cleanupVideo();
        onFailure();
      };
      const render = () => {
        try {
          const width = video.videoWidth;
          const height = video.videoHeight;
          if (!width || !height) { fail(); return; }
          const scale = Math.min(1, 960 / width);
          const canvas = globalThis.document.createElement('canvas');
          canvas.width = Math.max(1, Math.round(width * scale));
          canvas.height = Math.max(1, Math.round(height * scale));
          const context = canvas.getContext('2d');
          if (!context) { fail(); return; }
          context.drawImage(video, 0, 0, canvas.width, canvas.height);
          canvas.toBlob(blob => {
            if (disposed || !blob) { fail(); return; }
            objectUrl = globalThis.URL.createObjectURL(blob);
            cleanupVideo();
            onSuccess(objectUrl);
          }, 'image/jpeg', 0.85);
        } catch (_) { fail(); }
      };
      video.crossOrigin = 'anonymous';
      video.preload = 'metadata';
      video.muted = true;
      video.playsInline = true;
      video.onerror = fail;
      video.onloadeddata = () => {
        const target = Number.isFinite(video.duration) && video.duration > 0.1 ? Math.min(0.1, video.duration / 2) : 0;
        if (target > 0) { video.onseeked = render; video.currentTime = target; } else render();
      };
      timeout = globalThis.setTimeout?.(fail, 15000) ?? null;
      video.src = url;
      video.load();
      return () => {
        disposed = true;
        cleanupVideo();
        if (objectUrl) globalThis.URL.revokeObjectURL(objectUrl);
      };
    }""",
)
private external fun createBrowserOfficialVideoThumbnail(
    url: JsString,
    onSuccess: (JsString) -> Unit,
    onFailure: () -> Unit,
): () -> Unit
