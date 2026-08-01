package com.quata.feature.postcomposer.presentation

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class ComposerVideoPipelineContractTest {
    @Test
    fun malformedVideoTimestampsBecomeMonotonicAtFallbackCadence() {
        val track = ComposerVideoRemuxTrack(0, isVideo = true, fallbackSampleDurationUs = 33_333L, forceSyntheticVideoTimestamps = true)
        assertEquals(listOf(0L, 33_333L, 66_666L), listOf(track.presentationTimeUs(900_000L), track.presentationTimeUs(10L), track.presentationTimeUs(10L)))
        assertTrue(composerVideoTimestampsNeedRepair(frameRate = 30, durationUs = 2_000_000L))
        assertTrue(composerVideoTimestampsNeedRepair(frameRate = null, durationUs = null))
    }

    @Test
    fun validTrackUsesSourceDeltasAndRepairsBackwardJump() {
        val track = ComposerVideoRemuxTrack(1, isVideo = false, fallbackSampleDurationUs = 23_000L, forceSyntheticVideoTimestamps = false)
        assertEquals(0L, track.presentationTimeUs(5_000L))
        assertEquals(40_000L, track.presentationTimeUs(45_000L))
        assertEquals(63_000L, track.presentationTimeUs(2_000L))
    }

    @Test
    fun directSourceValidationRotationFallbackAndPreviewLayoutStayExplicit() {
        assertTrue(isValidComposerVideoMetadata(1080, 1920, 5_000L))
        assertFalse(isValidComposerVideoMetadata(0, 1920, 5_000L))
        assertEquals(90, composerVideoRotationHint(null, 90))
        assertEquals(180, composerVideoRotationHint(180, 90))
        assertNull(composerVideoRotationHint(null, null))
        assertEquals(ComposerVideoPreviewLayout.Contain, composerVideoPreviewLayout(true))
        assertEquals(ComposerVideoPreviewLayout.Crop, composerVideoPreviewLayout(false))
    }
}
