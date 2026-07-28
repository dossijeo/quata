package com.quata.feature.postcomposer.presentation

import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComposerMediaSelectionTest {
    @Test
    fun pickerCancellationUnsupportedAndFailureDoNotProduceADraftMutation() {
        assertNull((PlatformResult.Cancelled as PlatformResult<List<PlatformFile>>).composerSelectedFileOrNull())
        assertNull((PlatformResult.Unsupported as PlatformResult<List<PlatformFile>>).composerSelectedFileOrNull())
        assertNull(
            (PlatformResult.Failure("picker_failed") as PlatformResult<List<PlatformFile>>)
                .composerSelectedFileOrNull(),
        )
    }

    @Test
    fun acceptsOnlyARealNonBlankReference() {
        val selected = PlatformFile("file:///tmp/photo.jpg", "photo.jpg", "image/jpeg")
        assertEquals(selected, PlatformResult.Success(listOf(selected)).composerSelectedFileOrNull())
        assertNull(PlatformResult.Success(listOf(PlatformFile("   "))).composerSelectedFileOrNull())
    }

    @Test
    fun cameraUsesTheSameNoMutationRuleWithoutJvmErasureClash() {
        val captured = PlatformFile("file:///tmp/camera.jpg", "camera.jpg", "image/jpeg")
        assertEquals(captured, PlatformResult.Success(captured).composerCapturedFileOrNull())
        assertNull(
            (PlatformResult.Cancelled as PlatformResult<PlatformFile>).composerCapturedFileOrNull(),
        )
    }
}
