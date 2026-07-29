package com.quata.documentreader

import android.net.Uri
import com.quata.core.platform.AndroidDocumentOpenRequest
import com.quata.core.platform.PlatformResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuataDocumentReaderOpenBridgeTest {
    private val officeRequest = AndroidDocumentOpenRequest(
        uri = Uri.parse("content://com.quata.fileprovider/cache/letter.docx"),
        displayName = "letter.docx",
        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )

    @Test fun successPreservesContentUriAndOfficeMime() {
        var received: AndroidDocumentOpenRequest? = null
        val result = QuataDocumentReaderOpenBridge { request -> received = request; true }.open(officeRequest)

        assertEquals(PlatformResult.Success(Unit), result)
        assertEquals("content", received?.uri?.scheme)
        assertEquals(officeRequest.mimeType, received?.mimeType)
    }

    @Test fun rendererRejectionMapsToUnsupported() {
        assertEquals(PlatformResult.Unsupported, QuataDocumentReaderOpenBridge { false }.open(officeRequest))
    }

    @Test fun rendererFailureMapsToStableFailure() {
        val result = QuataDocumentReaderOpenBridge { error("legacy renderer failed") }.open(officeRequest)
        assertTrue(result is PlatformResult.Failure)
        assertEquals("legacy renderer failed", (result as PlatformResult.Failure).reason)
    }
}
