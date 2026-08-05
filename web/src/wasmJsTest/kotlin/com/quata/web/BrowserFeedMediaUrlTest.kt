package com.quata.web

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.ui.layout.ContentScale

class BrowserFeedMediaUrlTest {
    @Test
    fun acceptsOnlyConfiguredPublicStorageOrLocalBlobMedia() {
        assertTrue(isBrowserFeedMediaUrl("https://yrrlankpwmhluexshxnw.supabase.co/storage/v1/object/public/community-media/opaque-id"))
        assertTrue(isBrowserFeedMediaUrl("blob:http://127.0.0.1:4174/local-media"))
        assertFalse(isBrowserFeedMediaUrl("https://cdn.quata.example/object?token=signed"))
        assertFalse(isBrowserFeedMediaUrl("HTTP://cdn.quata.example/media/opaque-id"))
        assertFalse(isBrowserFeedMediaUrl("https://yrrlankpwmhluexshxnw.supabase.co/storage/v1/object/public/community-media/opaque-id?token=signed"))
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

    @Test
    fun failedCanvasImageLoadsAreNotCachedSoReentryRetries() {
        assertFalse(browserCanvasImageIsCacheable(BrowserCanvasImageState.Loading))
        assertFalse(browserCanvasImageIsCacheable(BrowserCanvasImageState.Error))
    }

    @Test
    fun nativeUnderlayUsesTheSameCropAndFitPolicyAsTheFeed() {
        assertTrue(browserFeedVideoUnderlayObjectFit(isLandscape = false) == "cover")
        assertTrue(browserFeedVideoUnderlayObjectFit(isLandscape = true) == "contain")
    }

    @Test
    fun nativeUnderlayContractKeepsComposeCanvasAboveDecoderAndNoHtmlUi() {
        val contract = browserFeedVideoUnderlayDomContract()

        assertTrue(contract.requiresCanvasInShadowRoot)
        assertTrue(contract.insertsBeforeCanvas)
        assertFalse(contract.exposesHtmlProductControls)
        assertTrue(contract.isolatesCanvasParent)
        assertEquals(1, contract.composeCanvasZIndex)
        assertEquals(0, contract.decoderZIndex)
        assertTrue(contract.composeCanvasZIndex > contract.decoderZIndex)
        assertTrue(contract.restoresHostStylesOnDetach)
    }
}
