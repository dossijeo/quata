package com.quata.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TurnstileWebMessageTest {
    private val context = "12345678-1234-1234-1234-123456789abc"

    @Test
    fun acceptsOnlyWellFormedSuccessAndFailureMessagesForTheActiveContext() {
        assertEquals(
            TurnstileWebMessage.Callback.Success("a-valid_turnstile.token-value_123456789"),
            TurnstileWebMessage.parse("success:$context:a-valid_turnstile.token-value_123456789", context),
        )
        assertEquals(
            TurnstileWebMessage.Callback.Failure("interactive_timeout"),
            TurnstileWebMessage.parse("failure:$context:interactive_timeout", context),
        )
        assertEquals(
            TurnstileWebMessage.Callback.Success("opaque:turnstile/token=value"),
            TurnstileWebMessage.parse("success:$context:opaque%3Aturnstile%2Ftoken%3Dvalue", context),
        )
    }

    @Test
    fun rejectsWrongContextMalformedTokensAndUnexpectedMessageKinds() {
        assertNull(TurnstileWebMessage.parse("success:other:valid_turnstile.token-value_123456789", context))
        assertNull(TurnstileWebMessage.parse("success:$context:too-short", context))
        assertNull(TurnstileWebMessage.parse("failure:$context:unsafe%3Acode", context))
        assertNull(TurnstileWebMessage.parse("unexpected:$context:valid_turnstile.token-value_123456789", context))
    }
}
