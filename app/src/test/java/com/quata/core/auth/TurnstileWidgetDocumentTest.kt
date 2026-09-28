package com.quata.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnstileWidgetDocumentTest {
    @Test
    fun rendersExplicitlyAndWiresSuccessAndAllFailureCallbacksToNamedGlobals() {
        val document = TurnstileWidgetDocument.render(
            siteKey = "0x4AAAAAAATestSiteKey",
            contextNonce = "12345678-1234-1234-1234-123456789abc",
        )

        assertTrue(document.contains("api.js?render=explicit"))
        assertTrue(document.contains("window.turnstile.render('#quata-turnstile'"))
        assertTrue(document.contains("callback: quataSuccess"))
        assertTrue(document.contains("'error-callback': quataFailure"))
        assertTrue(document.contains("'expired-callback': quataExpired"))
        assertTrue(document.contains("'timeout-callback': quataTimeout"))
        assertTrue(document.contains("function quataExpired(){ quataFailure('expired'); }"))
        assertTrue(document.contains("function quataTimeout(){ quataFailure('interactive_timeout'); }"))
        assertTrue(document.contains("$TurnstileWebMessageObjectName.postMessage('success:"))
        assertTrue(document.contains("encodeURIComponent(token)"))
        assertFalse(document.contains("addJavascriptInterface"))
        assertFalse(document.contains("async defer"))
        assertTrue(TurnstileChallengeTimeoutMillis > 0)
    }

    @Test
    fun rejectsValuesThatCouldEscapeTheTrustedDocumentContext() {
        assertThrows(IllegalArgumentException::class.java) {
            TurnstileWidgetDocument.render(
                siteKey = "\"><script>alert(1)</script>",
                contextNonce = "12345678-1234-1234-1234-123456789abc",
            )
        }
    }
}
