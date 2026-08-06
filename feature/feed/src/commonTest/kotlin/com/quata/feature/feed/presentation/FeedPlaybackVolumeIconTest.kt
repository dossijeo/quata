package com.quata.feature.feed.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedPlaybackVolumeIconTest {
    @Test
    fun playbackUsesSpeakerIconsInsteadOfWrappingTextLabels() {
        assertEquals(Icons.AutoMirrored.Filled.VolumeUp, feedPlaybackVolumeIcon(isMuted = false))
        assertEquals(Icons.AutoMirrored.Filled.VolumeOff, feedPlaybackVolumeIcon(isMuted = true))
    }

    @Test
    fun playbackUsesPlayPauseIconsAndFeedMuteTogglesBothWays() {
        assertEquals(Icons.Filled.PlayArrow, feedPlaybackPlayPauseIcon(isPlaying = false))
        assertEquals(Icons.Filled.Pause, feedPlaybackPlayPauseIcon(isPlaying = true))
        assertEquals(true, toggledFeedMutedState(isMuted = false))
        assertEquals(false, toggledFeedMutedState(isMuted = true))
    }
}
