@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.quata.core.model.Post
import com.quata.core.platform.ShareService
import com.quata.core.ui.richtext.QuataRichTextRenderer
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.presentation.OfficialFeedScreenHost
import com.quata.feature.official.presentation.OfficialFeedScreenPlatformSlots
import com.quata.feature.official.presentation.defaultOfficialFeedScreenStrings
import com.quata.feature.official.presentation.OfficialPostMediaFrameContent
import kotlin.js.JsString
import kotlin.js.toJsString

/** Browser adapter: navigation and browser services only; the Official screen itself is common. */
@Composable
fun WebOfficialHost(
    repository: WebOfficialRepository,
    shareService: ShareService,
    officialPostId: String?,
    currentUserId: String?,
    onAuthRequired: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onCreateOfficialPost: () -> Unit,
    modifier: Modifier = Modifier,
) = OfficialFeedScreenHost(
    padding = PaddingValues(),
    repository = repository,
    currentUserId = currentUserId,
    focusedPostId = officialPostId,
    onAuthRequired = onAuthRequired,
    onOpenUserProfile = onOpenUserProfile,
    onCreateOfficialPost = onCreateOfficialPost,
    onFocusedPostHandled = {},
    strings = defaultOfficialFeedScreenStrings(webOfficialLanguageTag()),
    modifier = modifier,
    slots = OfficialFeedScreenPlatformSlots(
        avatar = { post, avatarModifier -> BrowserFeedAuthorAvatar(post.asFeedPost(), onOpenUserProfile, modifier = avatarModifier) },
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
        canCreateOfficialPost = false,
        openUrl = { url -> openBrowserUrl(url) },
        rankingAvatar = { item -> BrowserFeedRankingAvatar(item) },
    ),
)

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
internal fun webOfficialLanguageTag(): String? = js("globalThis.navigator?.language || 'es'")

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
