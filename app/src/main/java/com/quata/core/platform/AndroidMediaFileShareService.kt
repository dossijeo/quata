package com.quata.core.platform

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Looper
import android.os.MessageQueue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

/**
 * File-only share host for the new media export actions. Android's normal ACTION_SEND adapter
 * returns when the chooser opens, which cannot distinguish user cancellation. This host waits for
 * the chooser's explicit component-selection callback and reports a dismissed chooser as
 * [PlatformResult.Cancelled].
 */
@Composable
fun rememberAndroidMediaFileShareService(): ShareService {
    val context = LocalContext.current.applicationContext
    val action = remember(context) { "${context.packageName}.MEDIA_FILE_SHARE_SELECTED.${UUID.randomUUID()}" }
    val coordinator = remember { AndroidMediaShareCoordinator() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        coordinator.scheduleCancellationAfterMainQueueDrains()
    }
    DisposableEffect(context, action, coordinator) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == action) coordinator.complete(PlatformResult.Success(Unit))
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(action),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            context.unregisterReceiver(receiver)
            coordinator.complete(PlatformResult.Cancelled)
        }
    }
    return remember(context, action, coordinator, launcher) {
        AndroidMediaFileShareService(
            context = context,
            callbackAction = action,
            launchChooser = launcher::launch,
            coordinator = coordinator,
        )
    }
}

private class AndroidMediaFileShareService(
    private val context: Context,
    private val callbackAction: String,
    private val launchChooser: (Intent) -> Unit,
    private val coordinator: AndroidMediaShareCoordinator,
) : ShareService {
    override suspend fun share(payload: SharePayload): PlatformResult<Unit> {
        val file = payload.files.singleOrNull() ?: return PlatformResult.Unsupported
        val uri = file.shareableMediaUri(context) ?: return PlatformResult.Unsupported
        return suspendCancellableCoroutine { continuation ->
            if (!coordinator.begin(continuation)) return@suspendCancellableCoroutine
            val send = Intent(Intent.ACTION_SEND).apply {
                type = file.mimeType ?: "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                payload.title?.let { putExtra(Intent.EXTRA_TITLE, it) }
                clipData = ClipData.newRawUri("quata_media", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val callback = Intent(callbackAction).setPackage(context.packageName)
            val selected = PendingIntent.getBroadcast(
                context,
                callbackAction.hashCode(),
                callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            val chooser = Intent.createChooser(send, payload.title, selected.intentSender)
            runCatching { launchChooser(chooser) }
                .onFailure { coordinator.complete(PlatformResult.Failure(it.message)) }
            continuation.invokeOnCancellation { coordinator.abandon(continuation) }
        }
    }
}

private class AndroidMediaShareCoordinator {
    private var pending: CancellableContinuation<PlatformResult<Unit>>? = null
    private var cancellationIdleHandler: MessageQueue.IdleHandler? = null

    @Synchronized
    fun begin(continuation: CancellableContinuation<PlatformResult<Unit>>): Boolean {
        if (pending != null) {
            continuation.resume(PlatformResult.Failure("android_media_share_busy"))
            return false
        }
        pending = continuation
        cancelScheduledCancellation()
        return true
    }

    @Synchronized
    fun complete(result: PlatformResult<Unit>) {
        cancelScheduledCancellation()
        pending?.takeIf { it.isActive }?.resume(result)
        pending = null
    }

    @Synchronized
    fun scheduleCancellationAfterMainQueueDrains() {
        cancelScheduledCancellation()
        val queue = Looper.getMainLooper().queue
        lateinit var handler: MessageQueue.IdleHandler
        handler = MessageQueue.IdleHandler {
            synchronized(this) {
                if (cancellationIdleHandler === handler) {
                    cancellationIdleHandler = null
                    pending?.takeIf { it.isActive }?.resume(PlatformResult.Cancelled)
                    pending = null
                }
            }
            false
        }
        cancellationIdleHandler = handler
        // The documented chooser IntentSender is queued when a target is selected. Waiting for
        // the main queue to become idle lets that broadcast win even when ActivityResult arrives
        // first, without guessing how many milliseconds delivery may take.
        queue.addIdleHandler(handler)
    }

    @Synchronized
    fun abandon(continuation: CancellableContinuation<PlatformResult<Unit>>) {
        if (pending === continuation) {
            pending = null
            cancelScheduledCancellation()
        }
    }

    private fun cancelScheduledCancellation() {
        cancellationIdleHandler?.let(Looper.getMainLooper().queue::removeIdleHandler)
        cancellationIdleHandler = null
    }
}

private fun PlatformFile.shareableMediaUri(context: Context): Uri? {
    val parsed = Uri.parse(reference)
    if (parsed.scheme == "content") return parsed
    if (parsed.scheme != "file") return null
    val file = parsed.path?.let(::File)?.takeIf { it.isFile && it.length() > 0L } ?: return null
    val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
    val root = runCatching { context.cacheDir.canonicalFile }.getOrNull() ?: return null
    if (canonical.path != root.path && !canonical.path.startsWith("${root.path}${File.separator}")) return null
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", canonical)
}
