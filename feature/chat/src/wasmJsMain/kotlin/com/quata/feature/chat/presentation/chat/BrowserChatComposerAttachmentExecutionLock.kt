@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.feature.chat.presentation.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Keeps attachment cleanup outside a send running in any tab for the same actor generation. */
class BrowserChatComposerAttachmentExecutionLock : ChatComposerAttachmentExecutionLock {
    override suspend fun <T> withLock(actorId: String, generation: Long, block: suspend () -> T): T =
        suspendCancellableCoroutine { continuation ->
            var cancelRequest: (() -> Unit)? = null
            browserAcquireComposerAttachmentLock(
                name = "quata-chat-composer-attachment:$actorId:$generation",
                onCancellationReady = { cancel ->
                    cancelRequest = cancel
                    if (!continuation.isActive) cancel()
                },
                onAcquired = { release ->
                    if (!continuation.isActive) {
                        release()
                    } else {
                        CoroutineScope(continuation.context + Dispatchers.Default).launch(
                            start = CoroutineStart.UNDISPATCHED,
                        ) {
                            try {
                                val result = runCatching { block() }
                                if (continuation.isActive) continuation.resumeWith(result)
                            } finally {
                                release()
                            }
                        }
                    }
                },
                onFailure = { reason ->
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(IllegalStateException(reason)))
                    }
                },
            )
            continuation.invokeOnCancellation { cancelRequest?.invoke() }
        }
}

private fun browserAcquireComposerAttachmentLock(
    name: String,
    onCancellationReady: ((() -> Unit) -> Unit),
    onAcquired: (() -> Unit) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    (() => {
      if (!globalThis.navigator?.locks?.request) {
        onFailure('web_chat_composer_attachment_locks_unavailable');
        return;
      }
      const controller = new AbortController();
      onCancellationReady(() => controller.abort());
      globalThis.navigator.locks.request(
        name,
        { signal: controller.signal },
        () => new Promise((resolve) => onAcquired(resolve)),
      )
        .catch((error) => onFailure(error?.message ?? error?.name ?? 'web_chat_composer_attachment_lock_failed'));
    })()
    """,
)
