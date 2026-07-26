package com.quata.core.auth

import java.net.URI

internal class TurnstileRequestPolicy private constructor(
    private val applicationOrigin: Origin,
) {
    fun isApplicationOrigin(rawUrl: String): Boolean {
        val uri = rawUrl.toHttpsUri() ?: return false
        return uri.toOrigin() == applicationOrigin
    }

    fun allowsSubresource(rawUrl: String): Boolean {
        val uri = rawUrl.toHttpsUri() ?: return false
        val origin = uri.toOrigin()
        return origin == applicationOrigin || origin == CloudflareChallengeOrigin
    }

    companion object {
        private val CloudflareChallengeOrigin = Origin("challenges.cloudflare.com", 443)

        fun from(rawOrigin: String): TurnstileRequestPolicy? {
            val uri = rawOrigin.toHttpsUri() ?: return null
            if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null
            if (uri.path !in setOf("", "/")) return null
            return TurnstileRequestPolicy(uri.toOrigin())
        }
    }
}

private data class Origin(val host: String, val port: Int)

private fun String.toHttpsUri(): URI? = runCatching { URI(this) }.getOrNull()
    ?.takeIf {
        it.scheme.equals("https", ignoreCase = true) &&
            !it.host.isNullOrBlank() &&
            it.userInfo == null
    }

private fun URI.toOrigin() = Origin(host.lowercase(), if (port == -1) 443 else port)
