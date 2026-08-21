package com.quata.feature.postcomposer.videoeditor

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostVideoEditorExportSpecTest {
    @Test
    fun exportSpecPreservesRealSourceDurationWhileLimitingSelectedWindow() {
        val state = PostVideoEditorUiState(
            trimStartFraction = 0.5f,
            trimEndFraction = 1f,
        )

        val spec = postVideoEditorExportSpec(
            state = state,
            videoAspectRatio = 9f / 16f,
            durationMs = 180_000L,
        )

        assertEquals(180_000L, spec.sourceDurationMs)
        assertEquals(90_000L, spec.trimStartMs)
        assertEquals(180_000L, spec.trimEndMs)
        assertEquals(90_000L, spec.trimDurationMs)
    }

    @Test
    fun stateForLongSourceInitializesNinetySecondWindowWithoutClampingSourceDuration() {
        val state = postVideoEditorStateForSourceDuration(PostVideoEditorUiState(), 180_000L)

        assertEquals(0f, state.trimStartFraction)
        assertClose(0.5f, state.trimEndFraction)
        assertClose(0f, state.currentPositionFraction)
    }

    @Test
    fun trimMinimumUsesAbsoluteFiveHundredMilliseconds() {
        val tenSecondMinimum = postVideoEditorMinimumTrimFraction(10_000L)
        val threeSecondMinimum = postVideoEditorMinimumTrimFraction(3_000L)

        assertClose(0.05f, tenSecondMinimum)
        assertClose(0.16666667f, threeSecondMinimum, tolerance = 0.0001f)
    }

    @Test
    fun trimEndCannotExceedNinetySecondWindowOnLongSources() {
        val state = PostVideoEditorUiState(trimStartFraction = 0.25f, trimEndFraction = 1f)

        val next = postVideoEditorStateAfterTrimEnd(state, 1f, 180_000L)

        assertClose(0.5f, next.trimStartFraction)
        assertClose(1f, next.trimEndFraction)
    }

    @Test
    fun trimStartCanSelectLaterNinetySecondWindow() {
        val state = PostVideoEditorUiState(trimStartFraction = 0f, trimEndFraction = 1f)

        val next = postVideoEditorStateAfterTrimStart(state, 0.5f, 180_000L)
        val spec = postVideoEditorExportSpec(next, videoAspectRatio = 9f / 16f, durationMs = 180_000L)

        assertEquals(90_000L, spec.trimStartMs)
        assertEquals(180_000L, spec.trimEndMs)
    }

    @Test
    fun shortSourcesRemainFullySelectable() {
        val state = postVideoEditorStateForSourceDuration(PostVideoEditorUiState(), 3_000L)
        val spec = postVideoEditorExportSpec(state, videoAspectRatio = 9f / 16f, durationMs = 3_000L)

        assertEquals(3_000L, spec.sourceDurationMs)
        assertEquals(0L, spec.trimStartMs)
        assertEquals(3_000L, spec.trimEndMs)
    }

    @Test
    fun resetReturnsToBeginningAndMaximumAllowedWindow() {
        val edited = PostVideoEditorUiState(
            trimStartFraction = 0.4f,
            trimEndFraction = 0.9f,
            currentPositionFraction = 0.7f,
            isMuted = true,
            cropEnabled = true,
            captionsEnabled = true,
        )

        val reset = postVideoEditorStateAfterReset(edited, 180_000L)

        assertEquals(0f, reset.trimStartFraction)
        assertClose(0.5f, reset.trimEndFraction)
        assertEquals(0f, reset.currentPositionFraction)
        assertEquals(false, reset.isMuted)
        assertEquals(false, reset.cropEnabled)
        assertEquals(false, reset.captionsEnabled)
    }

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.00001f) {
        assertTrue(abs(expected - actual) <= tolerance, "Expected $actual to be within $tolerance of $expected")
    }
}
