package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.unit.dp
import com.quata.core.platform.ShareService
import com.quata.core.language.FangTranslationService
import com.quata.core.language.IosFastTextLanguageIdentifier
import com.quata.core.language.IosTranslationHttpTransport
import com.quata.core.ui.components.CommunityEmojiLabels
import com.quata.core.ui.components.communityEmojiCatalogState
import com.quata.core.ui.components.communityEmojiSelectorEvidenceCatalogState
import com.quata.core.ui.components.QuataAvatarFrameContent
import com.quata.core.ui.components.QuataAvatarLoadingHaloContent
import com.quata.core.ui.components.QuataLiveRankingItem
import com.quata.core.ui.richtext.QuataRichTextRenderer
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.core.data.toFoundationData
import com.quata.designsystem.translation.FangTextTranslatorGateway
import com.quata.designsystem.translation.quataTranslatorPreferredLanguage
import com.quata.designsystem.translation.quataTranslatorStringsForLanguage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Foundation.NSValue
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIApplication
import platform.UIKit.UIViewContentMode
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.CoreMedia.CMTimeMakeWithSeconds

/** iOS-only native render seams; they do not own any Official state or navigation. */
internal fun iosOfficialPlatformSlots(
    shareService: ShareService,
    viewerFactory: IosOfficialMediaViewerFactory?,
    canCreateOfficialPost: Boolean,
    closeLabel: String,
    openingProfileUserId: String?,
    preferredLanguageTag: String?,
) = OfficialFeedScreenPlatformSlots(
    avatar = { post, modifier ->
        QuataAvatarLoadingHaloContent(
            isLoading = openingProfileUserId == post.author.id,
            modifier = modifier,
        ) {
            IosOfficialAvatar(post.author.displayName, post.author.id, post.author.avatarUrl, Modifier.fillMaxSize())
        }
    },
    media = { post, modifier, open -> IosOfficialMedia(post, open, modifier) },
    article = { post, modifier -> QuataRichTextRenderer(post.contentHtml, modifier, post.contentPlain) },
    mediaViewer = { post, dismiss -> IosOfficialNativeViewer(post, viewerFactory, closeLabel, dismiss) },
    openUrl = ::openIosOfficialUrl,
    share = { payload -> shareService.share(payload) },
    message = {},
    showComposeMessage = true,
    rankingAvatar = { item -> IosOfficialRankingAvatar(item) },
    canCreateOfficialPost = canCreateOfficialPost,
    commentsTranslationGateway = FangTextTranslatorGateway(
        identifier = IosFastTextLanguageIdentifier,
        translator = FangTranslationService(transport = IosTranslationHttpTransport()),
        preferredLanguage = quataTranslatorPreferredLanguage(preferredLanguageTag),
    ),
    commentsTranslatorStrings = quataTranslatorStringsForLanguage(preferredLanguageTag),
    communityEmojiCatalog = { labels, onRetry ->
        iosOfficialCommunityEmojiSelectorEvidenceCatalogState(labels, onRetry)
            ?: communityEmojiCatalogState(labels, onRetry = onRetry)
    },
)

private fun iosOfficialCommunityEmojiSelectorEvidenceCatalogState(
    labels: CommunityEmojiLabels,
    onRetry: (() -> Unit)?,
) = communityEmojiSelectorEvidenceCatalogState(
    labels = labels,
    onRetry = onRetry,
    optIn = NSProcessInfo.processInfo.environment["QUATA_IOS_COMMUNITY_EMOJI_SELECTOR_EVIDENCE_OPT_IN"]?.toString(),
    mode = NSProcessInfo.processInfo.environment["QUATA_IOS_COMMUNITY_EMOJI_SELECTOR_EVIDENCE_MODE"]?.toString(),
    message = NSProcessInfo.processInfo.environment["QUATA_IOS_COMMUNITY_EMOJI_SELECTOR_EVIDENCE_MESSAGE"]?.toString(),
)

@Composable
private fun IosOfficialNativeViewer(post: OfficialPostItem, factory: IosOfficialMediaViewerFactory?, closeLabel: String, dismiss: () -> Unit) {
    val url = post.mediaUrl ?: return
    val surface = remember(url) { factory?.create(url, post.mediaType == OfficialMediaType.Video) }
    androidx.compose.runtime.DisposableEffect(surface) { onDispose { surface?.dispose() } }
    LaunchedEffect(surface) { if (surface == null) dismiss() }
    if (surface != null) {
        Box(Modifier.fillMaxSize()) {
            UIKitView(factory = surface::nativeView, modifier = Modifier.fillMaxSize())
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                IconButton(onClick = dismiss) {
                    Icon(Icons.Filled.Close, contentDescription = closeLabel)
                }
            }
        }
    }
}

private fun openIosOfficialUrl(value: String) {
    NSURL(string = value)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
}

@Composable
private fun IosOfficialAvatar(name: String, id: String, url: String?, modifier: Modifier) {
    val imageUrl = url?.takeIf(::isIosOfficialRemoteUrl)
    var image by remember(imageUrl) { mutableStateOf<UIImage?>(null) }
    LaunchedEffect(imageUrl) { image = imageUrl?.let { loadIosOfficialImageOrNull(it) } }
    QuataAvatarFrameContent(name = name, stableId = id, isOfficial = true, modifier = modifier, avatar = image?.let { decoded ->
        { UIKitView(factory = { UIImageView().apply { contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill; clipsToBounds = true; image = decoded } }, update = { it.image = decoded }, modifier = Modifier.fillMaxSize()) }
    })
}

@Composable
private fun IosOfficialRankingAvatar(item: QuataLiveRankingItem) = IosOfficialAvatar(item.avatarName, item.profileId, item.avatarUrl, Modifier.size(44.dp))

@Composable
private fun IosOfficialMedia(post: OfficialPostItem, onOpenMedia: () -> Unit, modifier: Modifier) {
    val imageUrl = post.mediaUrl?.takeIf(::isIosOfficialRemoteUrl)
    var image by remember(post.id, imageUrl) { mutableStateOf<UIImage?>(null) }
    LaunchedEffect(imageUrl, post.mediaType) {
        image = imageUrl?.let { url ->
            if (post.mediaType == OfficialMediaType.Video) loadIosOfficialVideoThumbnailOrNull(url)
            else loadIosOfficialImageOrNull(url)
        }
    }
    OfficialPostMediaFrameContent(
        onOpenMedia = onOpenMedia,
        showPlayButton = officialInlineMediaContract(post.mediaType).showPlayButton,
        modifier = modifier,
        media = { mediaModifier ->
            // The native view is only a decoder surface.  The Compose frame and its play
            // affordance remain above it, so a failed video decode never turns into a fake label.
            image?.let { decoded ->
                UIKitView(
                    factory = { UIImageView().apply { contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill; clipsToBounds = true } },
                    update = { it.image = decoded },
                    modifier = mediaModifier,
                )
            }
        },
    )
}

/**
 * AVFoundation supplies a still decoder for a remote Official video. The request is tied to the
 * composition coroutine and cancels AVFoundation work when its card leaves composition. Unlike
 * the former image-only branch, video cards always retain the common play/viewer affordance.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun loadIosOfficialVideoThumbnailOrNull(url: String): UIImage? = withContext(Dispatchers.Default) { runCatching {
    val source = NSURL(string = url) ?: return@runCatching null
    val generator = AVAssetImageGenerator(AVURLAsset(uRL = source, options = null)).apply {
        appliesPreferredTrackTransform = true
    }
    generator.copyCGImageAtTime(
        requestedTime = CMTimeMakeWithSeconds(0.0, 600),
        actualTime = null,
        error = null,
    )?.let(UIImage::imageWithCGImage)
}.getOrNull() }

private suspend fun loadIosOfficialImageOrNull(url: String): UIImage? = runCatching {
    UIImage(data = iosOfficialImageData(NSURL(string = url) ?: return@runCatching null))
}.getOrNull()

@OptIn(ExperimentalForeignApi::class)
private suspend fun iosOfficialImageData(url: NSURL): NSData = suspendCancellableCoroutine { continuation ->
    val session = NSURLSession.sessionWithConfiguration(NSURLSessionConfiguration.ephemeralSessionConfiguration(), IosOfficialImageDelegate(continuation), null)
    val task = session.dataTaskWithURL(url)
    continuation.invokeOnCancellation { task.cancel(); session.invalidateAndCancel() }
    task.resume()
}

@OptIn(ExperimentalForeignApi::class)
private class IosOfficialImageDelegate(private val continuation: CancellableContinuation<NSData>) : platform.darwin.NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = mutableListOf<ByteArray>()
    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) { if (continuation.isActive) chunks += didReceiveData.toBytes() }
    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        val status = (task.response as? NSHTTPURLResponse)?.statusCode?.toInt()
        val data = chunks.toFoundationData().takeIf { it.length > 0uL }
        if (didCompleteWithError != null || status !in 200..299 || data == null) continuation.resumeWith(Result.failure(IllegalStateException("ios_official_image_unavailable")))
        else continuation.resumeWith(Result.success(data))
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toBytes(): ByteArray = if (length == 0uL) ByteArray(0) else bytes?.readBytes(length.toInt()) ?: ByteArray(0)

internal fun isIosOfficialRemoteUrl(value: String): Boolean = value.startsWith("https://") || value.startsWith("http://")
