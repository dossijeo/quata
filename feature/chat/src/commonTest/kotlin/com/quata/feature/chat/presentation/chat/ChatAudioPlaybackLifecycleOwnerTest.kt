package com.quata.feature.chat.presentation.chat

import com.quata.core.platform.AudioPlaybackState
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatAudioPlaybackLifecycleOwnerTest {
    @Test
    fun disposeCancelsActiveWorkAndStopsThePlatformPlayerExactlyOnce() = runTest {
        val player = RecordingAudioPlayer()
        val owner = ChatAudioPlaybackLifecycleOwner(player, StandardTestDispatcher(testScheduler))
        owner.launch { awaitCancellation() }

        owner.dispose()
        owner.dispose()
        advanceUntilIdle()

        assertEquals(1, player.stopCalls)
    }

    private class RecordingAudioPlayer : AudioPlayerService {
        var stopCalls = 0

        override suspend fun load(file: PlatformFile) = PlatformResult.Success(AudioPlaybackState(isLoaded = true))
        override suspend fun play() = PlatformResult.Success(AudioPlaybackState(isLoaded = true, isPlaying = true))
        override suspend fun pause() = PlatformResult.Success(AudioPlaybackState(isLoaded = true))
        override suspend fun seekTo(positionMillis: Long) = PlatformResult.Success(AudioPlaybackState(positionMillis = positionMillis))
        override suspend fun stop(): PlatformResult<Unit> {
            stopCalls += 1
            return PlatformResult.Success(Unit)
        }
        override suspend fun state() = AudioPlaybackState()
    }
}
