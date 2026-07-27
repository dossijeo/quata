package com.quata.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnstileWidgetDocumentTest {
    @Test
    fun wiresImmediateSuccessAndAllFailureCallbacksToNamedGlobals() {
        val document = TurnstileWidgetDocument.render(
            siteKey = "0x4AAAAAAATestSiteKey",
            contextNonce = "12345678-1234-1234-1234-123456789abc",
        )

        assertTrue(document.contains("data-callback=\"quataSuccess\""))
        assertTrue(document.contains("data-error-callback=\"quataFailure\""))
        assertTrue(document.contains("data-expired-callback=\"quataExpired\""))
        assertTrue(document.contains("data-timeout-callback=\"quataTimeout\""))
        assertTrue(document.contains("function quataExpired(){ quataFailure('expired'); }"))
        assertTrue(document.contains("function quataTimeout(){ quataFailure('interactive_timeout'); }"))
        assertFalse(document.contains("data-timeout-callback=\"function"))
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
