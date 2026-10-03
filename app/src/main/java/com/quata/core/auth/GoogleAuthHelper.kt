package com.quata.core.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.quata.core.config.AppConfig
import com.quata.core.model.AuthSession
import com.quata.feature.auth.domain.acceptFederatedProfile
import com.quata.feature.auth.domain.acceptLinkedGoogleSession
import com.quata.feature.auth.domain.buildGoogleIdentityLinkRequest
import com.quata.feature.auth.domain.buildGoogleOAuthRequest
import com.quata.feature.auth.domain.parseGoogleIdentityAuthorizationUrl
import com.quata.feature.auth.domain.parseGoogleOAuthCallback
import com.quata.feature.auth.domain.parseGoogleOAuthTokenSet
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GoogleAuthHelper(
    private val http: OkHttpClient = OkHttpClient(),
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    suspend fun signIn(context: Context): Result<AuthSession> = runCatching {
        check(!AppConfig.USE_MOCK_BACKEND) { "google_oauth_unavailable_in_mock_backend" }
        val baseUrl = AppConfig.SUPABASE_URL.trim().trimEnd('/').takeIf(String::isNotBlank)
            ?: error("google_oauth_supabase_url_missing")
        val apiKey = AppConfig.SUPABASE_ANON_KEY.trim().takeIf(String::isNotBlank)
            ?: error("google_oauth_publishable_key_missing")
        val request = buildGoogleOAuthRequest(
            supabaseUrl = baseUrl,
            redirectUri = AndroidGoogleOAuthCallbackCoordinator.RedirectUri,
            randomBytes = { size -> ByteArray(size).also(secureRandom::nextBytes) },
        )
        val callback = withTimeout(180_000L) {
            AndroidGoogleOAuthCallbackCoordinator.awaitCallback {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(request.authorizationUrl)).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
        }
        val code = parseGoogleOAuthCallback(callback, request)
        val tokenPayload = postJson(
            endpoint = "$baseUrl/auth/v1/token?grant_type=pkce",
            apiKey = apiKey,
            body = buildJsonObject {
                put("auth_code", code)
                put("code_verifier", request.codeVerifier)
            }.toString(),
        )
        val tokens = parseGoogleOAuthTokenSet(tokenPayload)
        val profilePayload = postJson(
            endpoint = "$baseUrl/functions/v1/quata-auth-bridge",
            apiKey = apiKey,
            accessToken = tokens.accessToken,
            body = buildJsonObject {
                put("action", "federated_profile")
                put("version", 1)
                put("client_type", "android")
            }.toString(),
        )
        tokens.acceptFederatedProfile(profilePayload).session
    }

    suspend fun link(context: Context, current: AuthSession): Result<AuthSession> = runCatching {
        check(current.isSupabaseAuthenticated()) { "google_identity_session_required" }
        val baseUrl = AppConfig.SUPABASE_URL.trim().trimEnd('/').takeIf(String::isNotBlank)
            ?: error("google_oauth_supabase_url_missing")
        val apiKey = AppConfig.SUPABASE_ANON_KEY.trim().takeIf(String::isNotBlank)
            ?: error("google_oauth_publishable_key_missing")
        val request = buildGoogleIdentityLinkRequest(
            supabaseUrl = baseUrl,
            redirectUri = AndroidGoogleOAuthCallbackCoordinator.RedirectUri,
            randomBytes = { size -> ByteArray(size).also(secureRandom::nextBytes) },
        )
        val providerUrl = parseGoogleIdentityAuthorizationUrl(
            getJson(request.authorizationUrl, apiKey, current.bearerToken),
        )
        val callback = withTimeout(180_000L) {
            AndroidGoogleOAuthCallbackCoordinator.awaitCallback {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(providerUrl)).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
        }
        val code = parseGoogleOAuthCallback(callback, request)
        val tokenPayload = postJson(
            endpoint = "$baseUrl/auth/v1/token?grant_type=pkce",
            apiKey = apiKey,
            body = buildJsonObject {
                put("auth_code", code)
                put("code_verifier", request.codeVerifier)
            }.toString(),
        )
        current.acceptLinkedGoogleSession(tokenPayload)
    }

    private suspend fun postJson(
        endpoint: String,
        apiKey: String,
        body: String,
        accessToken: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("apikey", apiKey)
            .apply { accessToken?.let { header("Authorization", "Bearer $it") } }
            .post(body.toRequestBody(JsonMediaType))
            .build()
        http.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            check(response.isSuccessful) { "google_oauth_http_${response.code}" }
            responseBody
        }
    }

    private suspend fun getJson(endpoint: String, apiKey: String, accessToken: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("apikey", apiKey)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                check(response.isSuccessful) { "google_identity_http_${response.code}" }
                responseBody
            }
        }

    private companion object {
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}

object AndroidGoogleOAuthCallbackCoordinator {
    const val RedirectUri = "quata://oauth/callback"
    private val pending = AtomicReference<CancellableContinuation<String>?>(null)

    suspend fun awaitCallback(openBrowser: () -> Unit): String = suspendCancellableCoroutine { continuation ->
        check(pending.compareAndSet(null, continuation)) { "google_oauth_already_in_progress" }
        continuation.invokeOnCancellation { pending.compareAndSet(continuation, null) }
        try {
            openBrowser()
        } catch (failure: Throwable) {
            if (pending.compareAndSet(continuation, null)) continuation.cancel(failure)
        }
    }

    fun handle(uri: Uri?): Boolean {
        if (uri?.scheme != "quata" || uri.host != "oauth" || uri.path != "/callback") return false
        val continuation = pending.getAndSet(null) ?: return true
        if (continuation.isActive) continuation.resume(uri.toString())
        return true
    }
}
