package com.quata.core.auth

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Parses the origin-bound WebMessageListener payload emitted by our nonce-CSP document. */
internal object TurnstileWebMessage {
    // Turnstile tokens are opaque and may change their URL-safe alphabet; reject only
    // control characters and unreasonable sizes rather than imposing a provider format.
    private val TokenPattern = Regex("[^\\p{Cntrl}]{20,4096}")
    private val ErrorPattern = Regex("[A-Za-z0-9_-]{1,64}")

    fun parse(raw: String?, expectedContext: String): Callback? {
        val parts = raw?.split(':', limit = 3) ?: return null
        if (parts.size != 3 || parts[1] != expectedContext) return null
        val value = runCatching { URLDecoder.decode(parts[2], StandardCharsets.UTF_8.name()) }.getOrNull()
            ?: return null
        return when (parts[0]) {
            "success" -> value.takeIf(TokenPattern::matches)?.let(Callback::Success)
            "failure" -> value.takeIf(ErrorPattern::matches)?.let(Callback::Failure)
            else -> null
        }
    }

    sealed interface Callback {
        data class Success(val token: String) : Callback
        data class Failure(val code: String) : Callback
    }
}
