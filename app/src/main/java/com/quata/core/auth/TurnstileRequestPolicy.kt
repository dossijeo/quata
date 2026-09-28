package com.quata.core.auth

import java.net.URI

internal class TurnstileRequestPolicy private constructor(
    private val applicationOrigin: Origin,
) {
    fun applicationDocumentUrl(contextNonce: String): String =
        applicationOrigin.url("/.well-known/quata-turnstile/$contextNonce")

    fun isApplicationOrigin(rawUrl: String): Boolean {
        val uri = rawUrl.toHttpsUri() ?: return false
        return uri.toOrigin() == applicationOrigin
    }

    fun isApplicationOrigin(host: String?, port: Int, scheme: String?): Boolean =
        scheme.equals("https", ignoreCase = true) &&
            host?.lowercase() == applicationOrigin.host &&
            (if (port == -1) 443 else port) == applicationOrigin.port

    fun allowsSubresource(rawUrl: String): Boolean {
        if (rawUrl == AboutBlank || rawUrl == AboutSrcdoc) return true
        val uri = rawUrl.toHttpsUri() ?: return false
        val origin = uri.toOrigin()
        return origin == applicationOrigin || origin == CloudflareChallengeOrigin
    }

    fun isTurnstileBootstrap(rawUrl: String): Boolean {
        val uri = rawUrl.toHttpsUri() ?: return false
        return uri.toOrigin() == CloudflareChallengeOrigin && uri.path == TurnstileBootstrapPath
    }

    companion object {
        private val CloudflareChallengeOrigin = Origin("challenges.cloudflare.com", 443)
        private const val AboutBlank = "about:blank"
        private const val AboutSrcdoc = "about:srcdoc"
        private const val TurnstileBootstrapPath = "/turnstile/v0/api.js"

        fun from(rawOrigin: String): TurnstileRequestPolicy? {
            val uri = rawOrigin.toHttpsUri() ?: return null
            if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null
            if (uri.path !in setOf("", "/")) return null
            return TurnstileRequestPolicy(uri.toOrigin())
        }
    }
}

private data class Origin(val host: String, val port: Int)

private fun Origin.url(path: String): String =
    "https://$host${if (port == 443) "" else ":$port"}$path"

private fun String.toHttpsUri(): URI? = runCatching { URI(this) }.getOrNull()
    ?.takeIf {
        it.scheme.equals("https", ignoreCase = true) &&
            !it.host.isNullOrBlank() &&
            it.userInfo == null
    }

private fun URI.toOrigin() = Origin(host.lowercase(), if (port == -1) 443 else port)
