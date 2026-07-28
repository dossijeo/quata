package com.quata.feature.chat.presentation.chat

import com.quata.core.platform.AudioPlayerService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns Chat audio work independently from the composition coroutine scope.
 *
 * Compose can cancel rememberCoroutineScope while effects are being disposed. This owner keeps
 * terminal player cleanup alive long enough to cancel an active load and stop the platform
 * player, without blocking the main thread or using an unowned global coroutine.
 */
internal class ChatAudioPlaybackLifecycleOwner(
    private val audioPlayer: AudioPlayerService,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var activeOperation: Job? = null
    private var disposed = false

    fun launch(block: suspend () -> Unit) {
        if (disposed) return
        activeOperation?.cancel()
        activeOperation = scope.launch { block() }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        val operation = activeOperation
        activeOperation = null
        operation?.cancel()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                operation?.join()
                withContext(NonCancellable) { audioPlayer.stop() }
            } finally {
                scope.cancel()
            }
        }
    }
}
