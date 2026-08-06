package com.quata.feature.chat.presentation.chat

import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IosChatMediaDownloadStateTest {
    @Test
    fun terminalFailureCanRetryAndThenExposeTheSuccessfulLocalFile() {
        val failed = IosChatMediaDownloadState()
            .complete(PlatformResult.Failure("controlled_download_failure"))

        assertFalse(failed.isLoading)
        assertTrue(failed.hasFailed)
        assertNull(failed.file)

        val retrying = failed.retry()
        assertEquals(1, retrying.attempt)
        assertTrue(retrying.isLoading)
        assertFalse(retrying.hasFailed)
        assertNull(retrying.file)

        val local = PlatformFile(
            reference = "file:///tmp/recovered-video.mp4",
            displayName = "recovered-video.mp4",
            mimeType = "video/mp4",
        )
        val recovered = retrying.complete(PlatformResult.Success(local))

        assertEquals(1, recovered.attempt)
        assertFalse(recovered.isLoading)
        assertFalse(recovered.hasFailed)
        assertSame(local, recovered.file)
    }

    @Test
    fun everyNonSuccessOutcomeProducesTheSameRetryableTerminalState() {
        listOf<PlatformResult<PlatformFile>>(
            PlatformResult.Failure("network"),
            PlatformResult.Cancelled,
            PlatformResult.Unsupported,
        ).forEach { outcome ->
            val state = IosChatMediaDownloadState().complete(outcome)
            assertTrue(state.hasFailed)
            assertFalse(state.isLoading)
            assertNull(state.file)
        }
    }
}
