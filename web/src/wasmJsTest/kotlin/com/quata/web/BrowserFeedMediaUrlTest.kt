package com.quata.web

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.ui.layout.ContentScale

class BrowserFeedMediaUrlTest {
    @Test
    fun acceptsSignedCdnMediaWithoutAnExtension() {
        assertTrue(isBrowserFeedMediaUrl("https://cdn.quata.example/object?token=signed"))
        assertTrue(isBrowserFeedMediaUrl("HTTP://cdn.quata.example/media/opaque-id"))
    }

    @Test
    fun refusesNonHttpMediaReferences() {
        assertFalse(isBrowserFeedMediaUrl("file:///private/video.mp4"))
        assertFalse(isBrowserFeedMediaUrl("javascript:alert(1)"))
    }

    @Test
    fun autoplayPolicyErrorsAreNotReportedAsTransportFailures() {
        assertTrue(isBrowserAutoplayPolicyRejection("NotAllowedError: play() failed"))
        assertTrue(isBrowserAutoplayPolicyRejection("autoplay is not permitted"))
        assertFalse(isBrowserAutoplayPolicyRejection("NetworkError"))
    }

    @Test
    fun canvasImagesKeepTheFeedCropFitContract() {
        assertEquals(ContentScale.Crop, browserFeedImageContentScale(isLandscape = false))
        assertEquals(ContentScale.Fit, browserFeedImageContentScale(isLandscape = true))
    }
}
