package com.quata.core.auth

import android.annotation.SuppressLint
import android.app.Dialog
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import com.quata.core.config.AppConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException

class MainActivityTurnstileHost(private val activity: ComponentActivity) {
    @Volatile private var activeTeardown: (() -> Unit)? = null

    fun close() {
        activeTeardown?.invoke()
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun request(): RegistrationChallenge = suspendCancellableCoroutine { continuation ->
        val siteKey = AppConfig.TURNSTILE_SITE_KEY.trim()
        val origin = AppConfig.TURNSTILE_ALLOWED_ORIGIN.trim()
        val requestPolicy = TurnstileRequestPolicy.from(origin)
        if (siteKey.isEmpty() || requestPolicy == null) {
            continuation.resumeWithException(IllegalStateException("registration_challenge_not_configured"))
            return@suspendCancellableCoroutine
        }

        activity.runOnUiThread {
            if (!continuation.isActive) return@runOnUiThread
            if (activity.isFinishing || activity.isDestroyed) {
                continuation.resumeWithException(IllegalStateException("registration_challenge_host_unavailable"))
                return@runOnUiThread
            }

            val dialog = Dialog(activity)
            val webView = WebView(activity)
            val handler = Handler(Looper.getMainLooper())
            val completed = AtomicBoolean(false)
            val contextNonce = UUID.randomUUID().toString()
            lateinit var timeout: Runnable
            lateinit var finish: (Result<RegistrationChallenge>) -> Unit
            lateinit var closeRequest: () -> Unit

            fun failedResult(code: String) = Result.failure<RegistrationChallenge>(
                IllegalStateException("registration_challenge_failed:${code.take(64)}")
            )

            finish = { result ->
                if (completed.compareAndSet(false, true)) {
                    handler.removeCallbacks(timeout)
                    dialog.setOnCancelListener(null)
                    dialog.setOnDismissListener(null)
                    if (activeTeardown === closeRequest) activeTeardown = null
                    webView.removeJavascriptInterface(TurnstileJavascriptBridgeName)
                    webView.stopLoading()
                    webView.webViewClient = WebViewClient()
                    if (dialog.isShowing) dialog.dismiss()
                    webView.loadUrl("about:blank")
                    webView.clearHistory()
                    webView.removeAllViews()
                    webView.destroy()
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            }
            timeout = Runnable { finish(failedResult("timeout")) }
            closeRequest = { handler.post { finish(failedResult("host_closed")) } }
            if (activeTeardown != null) {
                finish(failedResult("request_already_active"))
                return@runOnUiThread
            }
            activeTeardown = closeRequest

            val bridge = object {
                @JavascriptInterface
                fun success(callbackContext: String, token: String) {
                    activity.runOnUiThread {
                        if (
                            callbackContext != contextNonce ||
                            token.isBlank() ||
                            !requestPolicy.isApplicationOrigin(webView.url.orEmpty())
                        ) {
                            finish(failedResult("invalid_callback_context"))
                        } else {
                            finish(Result.success(RegistrationChallenge(token.trim())))
                        }
                    }
                }

                @JavascriptInterface
                fun failure(callbackContext: String, code: String) {
                    activity.runOnUiThread {
                        if (
                            callbackContext != contextNonce ||
                            !requestPolicy.isApplicationOrigin(webView.url.orEmpty())
                        ) {
                            finish(failedResult("invalid_callback_context"))
                        } else {
                            finish(failedResult(code.ifBlank { "widget_error" }))
                        }
                    }
                }
            }

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                safeBrowsingEnabled = true
            }
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    if (request.isForMainFrame) {
                        true
                    } else {
                        !requestPolicy.allowsSubresource(request.url.toString())
                    }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? =
                    if (requestPolicy.allowsSubresource(request.url.toString())) {
                        null
                    } else {
                        blockedResponse()
                    }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame || requestPolicy.allowsSubresource(request.url.toString())) {
                        finish(failedResult("network_error"))
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    if (request.isForMainFrame || requestPolicy.allowsSubresource(request.url.toString())) {
                        finish(failedResult("http_${errorResponse.statusCode}"))
                    }
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.cancel()
                    finish(failedResult("ssl_error"))
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    finish(failedResult("renderer_gone"))
                    return true
                }
            }
            webView.addJavascriptInterface(bridge, TurnstileJavascriptBridgeName)
            val html = runCatching { TurnstileWidgetDocument.render(siteKey, contextNonce) }
                .getOrElse {
                    finish(failedResult("configuration_invalid"))
                    return@runOnUiThread
                }

            dialog.setContentView(webView)
            dialog.setOnCancelListener { finish(failedResult("cancelled")) }
            dialog.setOnDismissListener { finish(failedResult("dismissed")) }
            continuation.invokeOnCancellation {
                handler.post { finish(failedResult("cancelled")) }
            }
            try {
                dialog.show()
                handler.postDelayed(timeout, TurnstileChallengeTimeoutMillis)
                webView.loadDataWithBaseURL(origin, html, "text/html", "UTF-8", null)
            } catch (_: RuntimeException) {
                finish(failedResult("host_unavailable"))
            }
        }
    }

    private companion object {
        fun blockedResponse() = WebResourceResponse(
            "text/plain",
            "UTF-8",
            403,
            "Blocked",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )
    }
}
