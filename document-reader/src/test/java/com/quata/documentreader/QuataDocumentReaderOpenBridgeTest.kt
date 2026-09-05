package com.quata.documentreader

import android.net.Uri
import com.quata.core.platform.AndroidDocumentOpenRequest
import com.quata.core.platform.PlatformResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuataDocumentReaderOpenBridgeTest {
    private val officeRequest = AndroidDocumentOpenRequest(
        uri = Uri.parse("content://com.quata.fileprovider/cache/letter.docx"),
        displayName = "letter.docx",
        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )

    @Test fun successPreservesContentUriAndOfficeMime() {
        var received: AndroidDocumentOpenRequest? = null
        val result = QuataDocumentReaderOpenBridge(
            launchReader = { request -> received = request; true },
            launchChooser = { false },
        ).open(officeRequest)

        assertEquals(PlatformResult.Success(Unit), result)
        assertEquals("content", received?.uri?.scheme)
        assertEquals(officeRequest.mimeType, received?.mimeType)
    }

    @Test fun rendererRejectionFallsBackToChooser() {
        val result = QuataDocumentReaderOpenBridge(
            launchReader = { false },
            launchChooser = { true },
        ).open(officeRequest)

        assertEquals(PlatformResult.Success(Unit), result)
    }

    @Test fun rendererAndChooserRejectionMapsToUnsupported() {
        assertEquals(
            PlatformResult.Unsupported,
            QuataDocumentReaderOpenBridge(
                launchReader = { false },
                launchChooser = { false },
            ).open(officeRequest),
        )
    }

    @Test fun rendererFailureFallsBackToChooser() {
        val result = QuataDocumentReaderOpenBridge(
            launchReader = { error("legacy renderer failed") },
            launchChooser = { true },
        ).open(officeRequest)

        assertEquals(PlatformResult.Success(Unit), result)
    }

    @Test fun chooserFailureMapsToStableFailure() {
        val result = QuataDocumentReaderOpenBridge(
            launchReader = { false },
            launchChooser = { error("chooser failed") },
        ).open(officeRequest)
        assertTrue(result is PlatformResult.Failure)
        assertEquals("chooser failed", (result as PlatformResult.Failure).reason)
    }
}
