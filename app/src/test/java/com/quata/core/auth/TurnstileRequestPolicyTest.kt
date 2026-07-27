package com.quata.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnstileRequestPolicyTest {
    @Test
    fun allowsOnlyConfiguredOriginAndExactCloudflareChallengeOrigin() {
        val policy = requireNotNull(TurnstileRequestPolicy.from("https://register.quata.app"))

        assertTrue(policy.isApplicationOrigin("https://register.quata.app/"))
        assertFalse(policy.isApplicationOrigin("https://challenges.cloudflare.com/"))
        assertTrue(policy.allowsSubresource("https://register.quata.app/theme.css"))
        assertTrue(policy.allowsSubresource("https://challenges.cloudflare.com/turnstile/v0/api.js"))
        assertFalse(policy.allowsSubresource("http://challenges.cloudflare.com/turnstile/v0/api.js"))
        assertFalse(policy.allowsSubresource("https://challenges.cloudflare.com.evil.test/api.js"))
        assertFalse(policy.allowsSubresource("https://evil.test/redirect"))
        assertFalse(policy.allowsSubresource("data:text/html,unexpected"))
    }

    @Test
    fun rejectsConfigurationThatIsNotAnExactHttpsOrigin() {
        assertNull(TurnstileRequestPolicy.from("http://register.quata.app"))
        assertNull(TurnstileRequestPolicy.from("https://register.quata.app/path"))
        assertNull(TurnstileRequestPolicy.from("https://user@register.quata.app"))
        assertNull(TurnstileRequestPolicy.from("https://register.quata.app?redirect=evil"))
    }

    @Test
    fun acceptsOnlyTheExactHttpsOriginForWebMessages() {
        val policy = requireNotNull(TurnstileRequestPolicy.from("https://register.quata.app"))

        assertTrue(policy.isApplicationOrigin("register.quata.app", -1, "https"))
        assertFalse(policy.isApplicationOrigin("register.quata.app", 443, "http"))
        assertFalse(policy.isApplicationOrigin("evil.quata.app", 443, "https"))
        assertFalse(policy.isApplicationOrigin("register.quata.app", 444, "https"))
    }
}
