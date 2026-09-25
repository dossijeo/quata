package com.quata.feature.auth.data

import com.quata.core.platform.IosViewControllerProvider
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIViewController
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TurnstileMessageHandler = "QuataTurnstile"
private const val TurnstileTimeoutMillis = 90_000L
private val TurnstileSiteKeyPattern = Regex("^[A-Za-z0-9_-]{8,200}$")
private val TurnstileOriginPattern = Regex(
    "^https://[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?(?::[1-9][0-9]{0,4})?$",
)

fun isValidIosTurnstileSiteKey(raw: String?): Boolean =
    TurnstileSiteKeyPattern.matches(raw?.trim().orEmpty())

fun isValidIosTurnstileOrigin(raw: String?): Boolean {
    val origin = raw?.trim().orEmpty()
    if (!TurnstileOriginPattern.matches(origin)) return false
    val url = NSURL.URLWithString(origin) ?: return false
    if (url.scheme?.lowercase() != "https" || url.host.isNullOrBlank()) return false
    val port = origin.substringAfterLast(':', missingDelimiterValue = "")
        .takeIf { origin.substringAfter("https://").contains(':') }
        ?.toIntOrNull()
    return port == null || port in 1..65535
}

/**
 * Acquires one Turnstile token inside a launcher-owned UIKit presentation.
 *
 * The document is generated in memory, runs at one configured HTTPS origin, accepts navigation
 * only to that origin or Cloudflare's challenge host, and is destroyed as soon as the one-time
 * token is delivered. Requests are serialized so two registration taps cannot share a widget.
 */
@OptIn(ExperimentalForeignApi::class)
class IosTurnstileChallengeProvider(
    private val siteKey: String,
    private val allowedOrigin: String,
    private val presenterProvider: IosViewControllerProvider,
) : IosRegistrationChallengeProvider {
    private val requests = Mutex()

    override suspend fun acquire(): String = requests.withLock {
        val normalizedSiteKey = siteKey.trim()
        val normalizedOrigin = allowedOrigin.trim()
        check(isValidIosTurnstileSiteKey(normalizedSiteKey)) {
            "ios_registration_challenge_not_configured"
        }
        check(isValidIosTurnstileOrigin(normalizedOrigin)) {
            "ios_registration_challenge_not_configured"
        }
        withContext(Dispatchers.Main) {
            withTimeout(TurnstileTimeoutMillis) {
                presentChallenge(normalizedSiteKey, normalizedOrigin)
            }
        }
    }

    private suspend fun presentChallenge(siteKey: String, origin: String): String =
        suspendCancellableCoroutine { continuation ->
            val presenter = presenterProvider.activeViewController()
            if (presenter == null) {
                continuation.resumeWithException(
                    IllegalStateException("ios_registration_challenge_host_unavailable"),
                )
                return@suspendCancellableCoroutine
            }

            val nonce = NSUUID.UUID().UUIDString
            val contentController = WKUserContentController()
            val configuration = WKWebViewConfiguration().apply {
                userContentController = contentController
            }
            val webView = WKWebView(frame = CGRectZero.readValue(), configuration = configuration)
            val controller = UIViewController().apply {
                view = webView
                modalPresentationStyle = UIModalPresentationFullScreen
            }
            lateinit var delegate: IosTurnstileWebDelegate
            var cleaned = false

            fun cleanup() {
                if (cleaned) return
                cleaned = true
                contentController.removeScriptMessageHandlerForName(TurnstileMessageHandler)
                webView.stopLoading()
                webView.navigationDelegate = null
                controller.dismissViewControllerAnimated(flag = true, completion = null)
            }

            fun finish(result: Result<String>) {
                cleanup()
                if (continuation.isActive) {
                    result.fold(continuation::resume, continuation::resumeWithException)
                }
            }

            delegate = IosTurnstileWebDelegate(origin, nonce, ::finish)
            contentController.addScriptMessageHandler(delegate, TurnstileMessageHandler)
            webView.navigationDelegate = delegate
            continuation.invokeOnCancellation {
                dispatch_async(dispatch_get_main_queue()) { cleanup() }
            }
            presenter.presentViewController(controller, animated = true) {
                webView.loadHTMLString(
                    string = turnstileDocument(siteKey, nonce),
                    baseURL = NSURL.URLWithString(origin),
                )
            }
        }
}

@OptIn(ExperimentalForeignApi::class)
private class IosTurnstileWebDelegate(
    private val allowedOrigin: String,
    private val nonce: String,
    private val finish: (Result<String>) -> Unit,
) : NSObject(), WKScriptMessageHandlerProtocol, WKNavigationDelegateProtocol {
    private var completed = false

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        val source = didReceiveScriptMessage.frameInfo.securityOrigin
        val expected = NSURL.URLWithString(allowedOrigin)
        if (!didReceiveScriptMessage.frameInfo.mainFrame ||
            source.protocol.lowercase() != "https" ||
            source.host.lowercase() != expected?.host?.lowercase() ||
            source.port.toInt().let { if (it == 0) 443 else it } !=
                (expected?.port?.intValue ?: 443)
        ) {
            complete(Result.failure(IllegalStateException("ios_registration_challenge_invalid_context")))
            return
        }
        val message = didReceiveScriptMessage.body?.toString().orEmpty()
        val successPrefix = "success:$nonce:"
        val failurePrefix = "failure:$nonce:"
        when {
            message.startsWith(successPrefix) -> {
                val token = message.removePrefix(successPrefix)
                if (token.length in 20..4096 && token.none { it.isWhitespace() }) {
                    complete(Result.success(token))
                } else {
                    complete(Result.failure(IllegalStateException("ios_registration_challenge_invalid_token")))
                }
            }
            message.startsWith(failurePrefix) -> complete(
                Result.failure(IllegalStateException("ios_registration_challenge_failed")),
            )
            else -> complete(Result.failure(IllegalStateException("ios_registration_challenge_invalid_context")))
        }
    }

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (platform.WebKit.WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL
        val destinationIsMainFrame = decidePolicyForNavigationAction.targetFrame?.mainFrame != false
        val configuredOrigin = NSURL.URLWithString(allowedOrigin)
        val originHost = configuredOrigin?.host?.lowercase()
        val originPort = configuredOrigin?.port?.intValue ?: 443
        val destinationPort = url?.port?.intValue ?: 443
        val allowed = url == null || url.absoluteString == "about:blank" ||
            (url.scheme?.lowercase() == "https" && when {
                destinationIsMainFrame -> url.host?.lowercase() == originHost && destinationPort == originPort
                else -> (url.host?.lowercase() == originHost && destinationPort == originPort) ||
                    (url.host?.lowercase() == "challenges.cloudflare.com" && destinationPort == 443)
            })
        decisionHandler(
            if (allowed) {
                WKNavigationActionPolicy.WKNavigationActionPolicyAllow
            } else {
                WKNavigationActionPolicy.WKNavigationActionPolicyCancel
            },
        )
    }

    private fun complete(result: Result<String>) {
        if (completed) return
        completed = true
        finish(result)
    }
}

private fun turnstileDocument(siteKey: String, nonce: String): String = """
    <!doctype html>
    <html><head>
      <meta name="viewport" content="width=device-width,initial-scale=1">
      <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-$nonce' https://challenges.cloudflare.com; frame-src https://challenges.cloudflare.com; connect-src https://challenges.cloudflare.com; img-src https://challenges.cloudflare.com data:; style-src 'unsafe-inline'">
      <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
    </head><body>
      <div class="cf-turnstile" data-sitekey="$siteKey" data-action="register_ios"
        data-callback="quataSuccess" data-error-callback="quataFailure"
        data-expired-callback="quataExpired"></div>
      <script nonce="$nonce">
        function quataSuccess(token) {
          window.webkit.messageHandlers.$TurnstileMessageHandler.postMessage('success:$nonce:' + token);
        }
        function quataFailure() {
          window.webkit.messageHandlers.$TurnstileMessageHandler.postMessage('failure:$nonce:error');
        }
        function quataExpired() {
          window.webkit.messageHandlers.$TurnstileMessageHandler.postMessage('failure:$nonce:expired');
        }
      </script>
    </body></html>
""".trimIndent()

class IosRegistrationIdentityStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) {
    fun clientInstanceId(): String = defaults.stringForKey(ClientInstanceIdKey)
        ?.takeIf(String::isNotBlank)
        ?: newIdentifier().also { defaults.setObject(it, forKey = ClientInstanceIdKey) }

    fun idempotencyKey(identity: String, payloadFingerprint: String): String {
        require(payloadFingerprint.matches(Regex("^[0-9a-f]{64}$"))) {
            "ios_registration_payload_fingerprint_invalid"
        }
        val prefix = "$PendingPrefix${identity.filter(Char::isDigit)}"
        val recordKey = "$prefix.record"
        val existing = defaults.stringForKey(recordKey)?.let(::parsePendingRecord)
        if (existing?.fingerprint == payloadFingerprint) return existing.idempotencyKey
        return newIdentifier().also { replacement ->
            defaults.setObject(
                "$PendingRecordVersion|$payloadFingerprint|$replacement",
                forKey = recordKey,
            )
            removeLegacyPendingKeys(prefix)
        }
    }

    fun complete(identity: String) {
        val prefix = "$PendingPrefix${identity.filter(Char::isDigit)}"
        defaults.removeObjectForKey("$prefix.record")
        removeLegacyPendingKeys(prefix)
    }

    private fun parsePendingRecord(value: String): PendingRecord? {
        val fields = value.split('|')
        if (fields.size != 3 || fields[0] != PendingRecordVersion) return null
        val fingerprint = fields[1].takeIf { it.matches(Regex("^[0-9a-f]{64}$")) } ?: return null
        val idempotencyKey = fields[2].takeIf { it.matches(Regex("^[0-9A-Fa-f]{32}$")) } ?: return null
        return PendingRecord(fingerprint, idempotencyKey)
    }

    private fun removeLegacyPendingKeys(prefix: String) {
        defaults.removeObjectForKey("$prefix.fingerprint")
        defaults.removeObjectForKey("$prefix.key")
    }

    private fun newIdentifier(): String = NSUUID.UUID().UUIDString.replace("-", "")

    private data class PendingRecord(
        val fingerprint: String,
        val idempotencyKey: String,
    )

    private companion object {
        const val ClientInstanceIdKey = "quata.registration.client-instance-id"
        const val PendingPrefix = "quata.registration.pending."
        const val PendingRecordVersion = "v1"
    }
}

fun iosRegistrationPayloadFingerprint(request: com.quata.feature.auth.domain.RegisterAccountRequest): String {
    val canonical = com.quata.feature.auth.domain.buildRegistrationEdgeRequest(
        request = request,
        channel = "ios",
        clientInstanceId = "client-instance-placeholder",
        idempotencyKey = "idempotency-placeholder",
        challengeToken = "challenge-placeholder",
    ).toString().encodeToByteArray()
    return iosRegistrationSha256Hex(canonical)
}

fun iosRegistrationSha256Hex(value: String): String = iosRegistrationSha256Hex(value.encodeToByteArray())

private fun iosRegistrationSha256Hex(input: ByteArray): String {
    val bitLength = input.size.toLong() * 8L
    val paddedSize = ((input.size + 9 + 63) / 64) * 64
    val padded = ByteArray(paddedSize)
    input.copyInto(padded)
    padded[input.size] = 0x80.toByte()
    repeat(8) { index ->
        padded[padded.lastIndex - index] = (bitLength ushr (index * 8)).toByte()
    }

    val hash = intArrayOf(
        0x6a09e667, 0xbb67ae85L.toInt(), 0x3c6ef372, 0xa54ff53aL.toInt(),
        0x510e527f, 0x9b05688cL.toInt(), 0x1f83d9ab, 0x5be0cd19,
    )
    val schedule = IntArray(64)
    for (offset in padded.indices step 64) {
        repeat(16) { index ->
            val start = offset + index * 4
            schedule[index] =
                ((padded[start].toInt() and 0xff) shl 24) or
                ((padded[start + 1].toInt() and 0xff) shl 16) or
                ((padded[start + 2].toInt() and 0xff) shl 8) or
                (padded[start + 3].toInt() and 0xff)
        }
        for (index in 16 until 64) {
            val s0 = rotateRight(schedule[index - 15], 7) xor
                rotateRight(schedule[index - 15], 18) xor
                (schedule[index - 15] ushr 3)
            val s1 = rotateRight(schedule[index - 2], 17) xor
                rotateRight(schedule[index - 2], 19) xor
                (schedule[index - 2] ushr 10)
            schedule[index] = schedule[index - 16] + s0 + schedule[index - 7] + s1
        }

        var a = hash[0]
        var b = hash[1]
        var c = hash[2]
        var d = hash[3]
        var e = hash[4]
        var f = hash[5]
        var g = hash[6]
        var h = hash[7]
        repeat(64) { index ->
            val sum1 = rotateRight(e, 6) xor rotateRight(e, 11) xor rotateRight(e, 25)
            val choice = (e and f) xor (e.inv() and g)
            val temporary1 = h + sum1 + choice + IosRegistrationSha256Constants[index] + schedule[index]
            val sum0 = rotateRight(a, 2) xor rotateRight(a, 13) xor rotateRight(a, 22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temporary2 = sum0 + majority
            h = g
            g = f
            f = e
            e = d + temporary1
            d = c
            c = b
            b = a
            a = temporary1 + temporary2
        }
        hash[0] += a
        hash[1] += b
        hash[2] += c
        hash[3] += d
        hash[4] += e
        hash[5] += f
        hash[6] += g
        hash[7] += h
    }
    return hash.joinToString(separator = "") { word ->
        word.toUInt().toString(16).padStart(8, '0')
    }
}

private fun rotateRight(value: Int, bits: Int): Int =
    (value ushr bits) or (value shl (32 - bits))

private val IosRegistrationSha256Constants = intArrayOf(
    0x428a2f98, 0x71374491, 0xb5c0fbcfL.toInt(), 0xe9b5dba5L.toInt(),
    0x3956c25b, 0x59f111f1, 0x923f82a4L.toInt(), 0xab1c5ed5L.toInt(),
    0xd807aa98L.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1feL.toInt(), 0x9bdc06a7L.toInt(), 0xc19bf174L.toInt(),
    0xe49b69c1L.toInt(), 0xefbe4786L.toInt(), 0x0fc19dc6, 0x240ca1cc,
    0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152L.toInt(), 0xa831c66dL.toInt(), 0xb00327c8L.toInt(), 0xbf597fc7L.toInt(),
    0xc6e00bf3L.toInt(), 0xd5a79147L.toInt(), 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92eL.toInt(), 0x92722c85L.toInt(),
    0xa2bfe8a1L.toInt(), 0xa81a664bL.toInt(), 0xc24b8b70L.toInt(), 0xc76c51a3L.toInt(),
    0xd192e819L.toInt(), 0xd6990624L.toInt(), 0xf40e3585L.toInt(), 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
    0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814L.toInt(), 0x8cc70208L.toInt(),
    0x90befffaL.toInt(), 0xa4506cebL.toInt(), 0xbef9a3f7L.toInt(), 0xc67178f2L.toInt(),
)
