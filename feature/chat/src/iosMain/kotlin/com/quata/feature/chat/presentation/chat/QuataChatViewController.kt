package com.quata.feature.chat.presentation.chat

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.foundation.layout.fillMaxSize
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.AudioRecorderService
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.ClipboardService
import com.quata.core.platform.IosClipboardService
import com.quata.feature.chat.presentation.conversations.defaultConversationsStrings
import com.quata.feature.chat.domain.ChatRepository
import platform.UIKit.UIViewController
import platform.UIKit.UIImageView
import platform.UIKit.UIImage
import platform.UIKit.UIViewContentMode
import platform.Foundation.NSURL
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.darwin.NSObject
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.quata.core.data.toFoundationData
import com.quata.core.ui.components.QuataAvatarFallback

/**
 * iOS composition input for the shared chat list, bubble stream and composer.
 *
 * Realtime, player/recorder lifecycle, URI resolution and attachment caching stay in the iOS
 * launcher adapters. The common host only receives the contracts it needs to render and dispatch
 * UI events.
 */
class IosChatHostDependencies(
    val repository: ChatRepository,
    val audioPlayer: AudioPlayerService,
    val audioRecorder: AudioRecorderService,
    val filePicker: FilePickerService,
    val conversationId: String? = null,
    /** Optional deep-link target; common UI resolves it only against messages already present. */
    val focusedMessageId: String? = null,
    val navigationMessage: String = "Quata para iOS",
    val languageTag: String = "en",
    val clipboardService: ClipboardService = IosClipboardService(),
    /** AVFoundation records AAC in an MP4 container; Web stays on the common WebM default. */
    val audioRecordingConfiguration: ChatAudioRecordingConfiguration = ChatAudioRecordingConfiguration(
        mimeType = ChatAudioRecordingConfiguration.IOS_MIME_TYPE,
    ),
    val onOpenConversation: (String) -> Unit,
    val onBackToList: () -> Unit,
    /** Host slot for image/document/audio/map URIs selected from a shared bubble. */
    val onOpenAttachment: (PlatformFile) -> Unit,
    /** Host slot reserved for a platform avatar/profile destination. */
    val onOpenAvatar: (String) -> Unit = {},
    /** Host slot for map/location attachment navigation. */
    val onOpenMap: (String) -> Unit = {},
    /** Host slot for translation UI owned by the launcher. */
    val onTranslateMessage: (String) -> Unit = {},
)

/**
 * Stable UIKit/Compose entry point. The injected contracts deliberately prevent this iOS source
 * from creating a fake repository, manual Realtime client, player, cache or URI implementation.
 */
fun QuataChatViewController(dependencies: IosChatHostDependencies): UIViewController =
    ComposeUIViewController {
        QuataTheme {
            ChatBrowserHostContent(
                repository = dependencies.repository,
                audioPlayer = dependencies.audioPlayer,
                audioRecorder = dependencies.audioRecorder,
                filePicker = dependencies.filePicker,
                conversationId = dependencies.conversationId,
                focusedMessageId = dependencies.focusedMessageId,
                navigationMessage = dependencies.navigationMessage,
                conversationsClipboardService = dependencies.clipboardService,
                conversationsStrings = defaultConversationsStrings(dependencies.languageTag),
                onOpenUserProfile = dependencies.onOpenAvatar,
                conversationAvatar = { name, url, id, modifier -> IosConversationAvatar(name, url, id, modifier) },
                onOpenConversation = dependencies.onOpenConversation,
                onBackToList = dependencies.onBackToList,
                onOpenAttachment = dependencies.onOpenAttachment,
                audioRecordingConfiguration = dependencies.audioRecordingConfiguration,
            )
        }
    }

@Composable
private fun IosConversationAvatar(name: String, url: String?, stableId: String, modifier: Modifier) {
    val imageUrl = url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    var image by remember(imageUrl) { mutableStateOf<UIImage?>(null) }
    LaunchedEffect(imageUrl) {
        image = if (imageUrl == null) null else loadIosConversationAvatar(imageUrl)
    }
    val decoded = image
    if (decoded == null) {
        QuataAvatarFallback(name, stableId, modifier)
    } else {
        UIKitView(
            factory = {
                UIImageView().apply {
                    contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
                    clipsToBounds = true
                    this.image = decoded
                }
            },
            update = { it.image = decoded },
            modifier = modifier.fillMaxSize(),
        )
    }
}

private suspend fun loadIosConversationAvatar(url: String): UIImage? = runCatching {
    iosConversationAvatarData(NSURL(string = url) ?: return@runCatching null)
}.getOrNull()?.let { UIImage(data = it) }

@OptIn(ExperimentalForeignApi::class)
private suspend fun iosConversationAvatarData(url: NSURL): NSData =
    suspendCancellableCoroutine { continuation ->
        val delegate = IosConversationAvatarDelegate(continuation)
        val session = NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.ephemeralSessionConfiguration(),
            delegate,
            null,
        )
        val task = session.dataTaskWithURL(url)
        continuation.invokeOnCancellation {
            task.cancel()
            session.invalidateAndCancel()
        }
        task.resume()
    }

@OptIn(ExperimentalForeignApi::class)
private class IosConversationAvatarDelegate(
    private val continuation: CancellableContinuation<NSData>,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = mutableListOf<ByteArray>()

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveData: NSData,
    ) {
        if (continuation.isActive) chunks += didReceiveData.toConversationBytes()
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        if (didCompleteWithError != null) {
            continuation.resumeWithException(IllegalStateException(didCompleteWithError.localizedDescription))
            return
        }
        val status = (task.response as? NSHTTPURLResponse)?.statusCode?.toInt()
        val data = chunks.toFoundationData().takeIf { it.length > 0uL }
        if (status !in 200..299 || data == null) {
            continuation.resumeWithException(IllegalStateException("ios_conversation_avatar_unavailable"))
        } else {
            continuation.resume(data)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toConversationBytes(): ByteArray =
    if (length == 0uL) ByteArray(0) else bytes?.readBytes(length.toInt()) ?: ByteArray(0)
