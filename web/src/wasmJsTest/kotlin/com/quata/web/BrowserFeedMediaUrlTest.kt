package com.quata.web

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

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
    fun acceptsHttpsVideoFilesWithoutQueryOrHashForNativePlayback() {
        assertTrue(isBrowserFeedVideoUrl("https://egquata.com/wp-content/uploads/2026/08/sample.mp4"))
        assertTrue(isBrowserFeedVideoUrl("https://yrrlankpwmhluexshxnw.supabase.co/storage/v1/object/public/community-media/opaque-id"))
        assertTrue(isBrowserFeedVideoUrl("blob:http://127.0.0.1:4174/local-video"))
        assertFalse(isBrowserFeedVideoUrl("http://egquata.com/wp-content/uploads/2026/08/sample.mp4"))
        assertFalse(isBrowserFeedVideoUrl("https://egquata.com/wp-content/uploads/2026/08/sample.mp4?token=signed"))
        assertFalse(isBrowserFeedVideoUrl("https://yrrlankpwmhluexshxnw.supabase.co/storage/v1/object/public/community-media/opaque-id?token=signed"))
        assertFalse(isBrowserFeedVideoUrl("https://egquata.com/wp-content/uploads/2026/08/sample.jpg"))
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
    fun portraitVideoControlsStayPinnedToTheSharedBottomChrome() {
        assertEquals(10.dp, browserFeedVideoControlsBottomPadding(isLandscape = false))
        assertEquals(34.dp, browserFeedVideoControlsBottomPadding(isLandscape = true))
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
        assertTrue(contract.decoderBackgroundIsTransparent)
        assertTrue(contract.revealsDecodedFramesOnly)
        assertTrue(contract.decoderRemainsAttachedWhileHidden)
        assertTrue(contract.restoresHostStylesOnDetach)
    }
}
