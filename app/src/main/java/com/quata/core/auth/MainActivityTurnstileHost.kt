package com.quata.core.auth

import android.annotation.SuppressLint
import android.app.Dialog
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import com.quata.core.config.AppConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivityTurnstileHost(private val activity: ComponentActivity) {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun request(): RegistrationChallenge = suspendCancellableCoroutine { continuation ->
        val siteKey = AppConfig.TURNSTILE_SITE_KEY.trim()
        val origin = AppConfig.TURNSTILE_ALLOWED_ORIGIN.trim()
        if (siteKey.isEmpty() || !origin.startsWith("https://")) {
            continuation.resumeWithException(IllegalStateException("registration_challenge_not_configured"))
            return@suspendCancellableCoroutine
        }
        activity.runOnUiThread {
            val dialog = Dialog(activity)
            val webView = WebView(activity)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.webViewClient = WebViewClient()
            val bridge = object {
                @JavascriptInterface
                fun success(token: String) {
                    if (token.isBlank() || !continuation.isActive) return
                    activity.runOnUiThread {
                        dialog.dismiss()
                        continuation.resume(RegistrationChallenge(token.trim()))
                    }
                }

                @JavascriptInterface
                fun failure(code: String) {
                    if (!continuation.isActive) return
                    activity.runOnUiThread {
                        dialog.dismiss()
                        continuation.resumeWithException(
                            IllegalStateException("registration_challenge_failed:${code.take(64)}")
                        )
                    }
                }
            }
            webView.addJavascriptInterface(bridge, "QuataTurnstile")
            val html = """
                <!doctype html><html><head>
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
                <script>
                  function onQuataSuccess(t){ QuataTurnstile.success(t); }
                  function onQuataError(){ QuataTurnstile.failure('widget_error'); }
                </script>
                </head><body>
                <div class="cf-turnstile"
                  data-sitekey=${JSONObject.quote(siteKey)}
                  data-action="$TurnstileRegisterAction"
                  data-callback="onQuataSuccess"
                  data-error-callback="onQuataError"></div>
                </body></html>
            """.trimIndent()
            dialog.setContentView(webView)
            dialog.setOnCancelListener {
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("registration_challenge_cancelled"))
                }
            }
            continuation.invokeOnCancellation { activity.runOnUiThread(dialog::dismiss) }
            dialog.show()
            webView.loadDataWithBaseURL(origin, html, "text/html", "UTF-8", null)
        }
    }
}
