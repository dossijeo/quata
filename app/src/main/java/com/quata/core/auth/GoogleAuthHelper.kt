package com.quata.core.auth

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.quata.core.config.AppConfig
import com.quata.core.model.AuthSession
import com.quata.core.preferences.AndroidKeystorePreferenceValueCipher
import com.quata.feature.auth.domain.acceptFederatedProfile
import com.quata.feature.auth.domain.acceptLinkedGoogleSession
import com.quata.feature.auth.domain.buildGoogleIdentityLinkRequest
import com.quata.feature.auth.domain.buildGoogleOAuthRequest
import com.quata.feature.auth.domain.GoogleOAuthUserCancellation
import com.quata.feature.auth.domain.parseGoogleIdentityAuthorizationUrl
import com.quata.feature.auth.domain.parseGoogleOAuthCallback
import com.quata.feature.auth.domain.parseGoogleOAuthTokenSet
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
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
        check(AppConfig.SUPABASE_ANON_KEY.isNotBlank()) { "google_oauth_publishable_key_missing" }
        val callbackToken = newCallbackToken()
        val request = buildGoogleOAuthRequest(
            supabaseUrl = baseUrl,
            redirectUri = AndroidGoogleOAuthCallbackCoordinator.redirectUri(callbackToken),
            randomBytes = { size -> ByteArray(size).also(secureRandom::nextBytes) },
        )
        awaitAndComplete(
            context = context,
            pending = AndroidGoogleOAuthPending(
                mode = AndroidGoogleOAuthMode.SIGN_IN,
                codeVerifier = request.codeVerifier,
                callbackToken = callbackToken,
            ),
            authorizationUrl = request.authorizationUrl,
        )
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun link(context: Context, current: AuthSession): Result<AuthSession> = runCatching {
        check(current.isSupabaseAuthenticated()) { "google_identity_session_required" }
        val baseUrl = AppConfig.SUPABASE_URL.trim().trimEnd('/').takeIf(String::isNotBlank)
            ?: error("google_oauth_supabase_url_missing")
        val apiKey = AppConfig.SUPABASE_ANON_KEY.trim().takeIf(String::isNotBlank)
            ?: error("google_oauth_publishable_key_missing")
        val callbackToken = newCallbackToken()
        val request = buildGoogleIdentityLinkRequest(
            supabaseUrl = baseUrl,
            redirectUri = AndroidGoogleOAuthCallbackCoordinator.redirectUri(callbackToken),
            randomBytes = { size -> ByteArray(size).also(secureRandom::nextBytes) },
        )
        val providerUrl = parseGoogleIdentityAuthorizationUrl(
            getJson(request.authorizationUrl, apiKey, current.bearerToken),
        )
        awaitAndComplete(
            context = context,
            pending = AndroidGoogleOAuthPending(
                mode = AndroidGoogleOAuthMode.LINK_IDENTITY,
                codeVerifier = request.codeVerifier,
                callbackToken = callbackToken,
                expectedAuthUserId = current.authUserId ?: error("google_identity_auth_user_missing"),
                expectedProfileId = current.userId,
            ),
            authorizationUrl = providerUrl,
        )
    }.onFailure { if (it is CancellationException) throw it }

    internal suspend fun resumePending(
        context: Context,
        callback: Uri,
        current: AuthSession?,
    ): AndroidGoogleOAuthExchange {
        val store = AndroidGoogleOAuthPendingStore(context)
        val pending = store.read() ?: error("google_oauth_pending_request_missing")
        require(callbackMatchesPendingGoogleOAuth(callback, pending)) {
            "google_oauth_callback_attempt_mismatch"
        }
        val completion = runCatching {
            completePending(context, pending, callback.toString(), current)
        }.onFailure { if (it is CancellationException) throw it }
        return AndroidGoogleOAuthExchange(pending, completion)
    }

    private suspend fun awaitAndComplete(
        context: Context,
        pending: AndroidGoogleOAuthPending,
        authorizationUrl: String,
    ): AuthSession {
        val store = AndroidGoogleOAuthPendingStore(context)
        store.prepare(pending)
        val callback = try {
            withTimeout(AndroidGoogleOAuthPendingStore.PendingLifetimeMillis) {
                AndroidGoogleOAuthCallbackCoordinator.awaitCompletion(pending) {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl)).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }
            }
        } catch (failure: Throwable) {
            if (shouldClearPendingGoogleOAuth(failure)) store.consume(pending)
            throw failure
        }
        return callback
    }

    private suspend fun completePending(
        context: Context,
        pending: AndroidGoogleOAuthPending,
        callback: String,
        current: AuthSession?,
    ): AndroidGoogleOAuthCompletion {
        val store = AndroidGoogleOAuthPendingStore(context)
        return try {
            val baseUrl = AppConfig.SUPABASE_URL.trim().trimEnd('/').takeIf(String::isNotBlank)
                ?: error("google_oauth_supabase_url_missing")
            val apiKey = AppConfig.SUPABASE_ANON_KEY.trim().takeIf(String::isNotBlank)
                ?: error("google_oauth_publishable_key_missing")
            val request = com.quata.feature.auth.domain.GoogleOAuthRequest(
                authorizationUrl = "pending-android-oauth",
                redirectUri = AndroidGoogleOAuthCallbackCoordinator.redirectUri(pending.callbackToken),
                codeVerifier = pending.codeVerifier,
            )
            val code = parseGoogleOAuthCallback(callback, request)
            val tokenPayload = postJson(
                endpoint = "$baseUrl/auth/v1/token?grant_type=pkce",
                apiKey = apiKey,
                body = buildJsonObject {
                    put("auth_code", code)
                    put("code_verifier", pending.codeVerifier)
                }.toString(),
            )
            val session = when (pending.mode) {
                AndroidGoogleOAuthMode.SIGN_IN -> {
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
                AndroidGoogleOAuthMode.LINK_IDENTITY -> {
                    val retained = current ?: error("google_identity_session_required")
                    check(retained.userId == pending.expectedProfileId) { "google_identity_session_changed" }
                    check(retained.authUserId == pending.expectedAuthUserId) { "google_identity_user_mismatch" }
                    retained.acceptLinkedGoogleSession(tokenPayload)
                }
            }
            AndroidGoogleOAuthCompletion(
                pending = pending,
                session = session,
                retainedSession = current.takeIf { pending.mode == AndroidGoogleOAuthMode.LINK_IDENTITY },
            )
        } catch (failure: Throwable) {
            store.consume(pending)
            throw failure
        }
    }

    internal fun consumePending(context: Context, completion: AndroidGoogleOAuthCompletion): Boolean =
        AndroidGoogleOAuthPendingStore(context).consume(completion.pending)

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

    private fun newCallbackToken(): String = ByteArray(32)
        .also(secureRandom::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
}

internal fun shouldClearPendingGoogleOAuth(failure: Throwable): Boolean =
    failure is GoogleOAuthUserCancellation ||
        failure is TimeoutCancellationException ||
        failure !is CancellationException

enum class AndroidGoogleOAuthMode { SIGN_IN, LINK_IDENTITY }

internal data class AndroidGoogleOAuthCompletion(
    internal val pending: AndroidGoogleOAuthPending,
    val session: AuthSession,
    internal val retainedSession: AuthSession? = null,
) {
    val mode: AndroidGoogleOAuthMode get() = pending.mode
}

internal data class AndroidGoogleOAuthExchange(
    val pending: AndroidGoogleOAuthPending,
    val outcome: Result<AndroidGoogleOAuthCompletion>,
)

internal data class AndroidGoogleOAuthPending(
    val mode: AndroidGoogleOAuthMode,
    val codeVerifier: String,
    val callbackToken: String = codeVerifier,
    val expectedAuthUserId: String? = null,
    val expectedProfileId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

internal class AndroidGoogleOAuthPendingStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    private val cipher = AndroidKeystorePreferenceValueCipher(KeyAlias)

    @SuppressLint("UseKtx")
    fun prepare(pending: AndroidGoogleOAuthPending) = synchronized(Lock) {
        check(readLocked() == null) { "google_oauth_already_in_progress" }
        val plaintext = listOf(
            RecordVersion,
            pending.mode.name,
            pending.codeVerifier,
            pending.callbackToken,
            pending.expectedAuthUserId.orEmpty(),
            pending.expectedProfileId.orEmpty(),
            pending.createdAtMillis.toString(),
        ).joinToString("\n")
        check(preferences.edit().putString(PendingRecordKey, cipher.encrypt(plaintext)).commit()) {
            "google_oauth_pending_request_not_persisted"
        }
    }

    fun read(): AndroidGoogleOAuthPending? = synchronized(Lock) { readLocked() }

    @SuppressLint("UseKtx")
    fun consume(expected: AndroidGoogleOAuthPending): Boolean = synchronized(Lock) {
        if (readLocked() != expected) return@synchronized false
        check(preferences.edit().remove(PendingRecordKey).commit()) {
            "google_oauth_pending_request_not_cleared"
        }
        true
    }

    @SuppressLint("UseKtx")
    fun clear() = synchronized(Lock) {
        check(preferences.edit().remove(PendingRecordKey).commit()) {
            "google_oauth_pending_request_not_cleared"
        }
    }

    @SuppressLint("UseKtx")
    private fun readLocked(): AndroidGoogleOAuthPending? {
        val encrypted = preferences.getString(PendingRecordKey, null) ?: return null
        val pending = runCatching {
            val parts = cipher.decrypt(encrypted).split('\n')
            require(parts.size == 7 && parts[0] == RecordVersion) { "google_oauth_pending_request_invalid" }
            AndroidGoogleOAuthPending(
                mode = AndroidGoogleOAuthMode.valueOf(parts[1]),
                codeVerifier = parts[2].takeIf(String::isNotBlank)
                    ?: error("google_oauth_pending_verifier_missing"),
                callbackToken = parts[3].takeIf(String::isNotBlank)
                    ?: error("google_oauth_pending_callback_token_missing"),
                expectedAuthUserId = parts[4].takeIf(String::isNotBlank),
                expectedProfileId = parts[5].takeIf(String::isNotBlank),
                createdAtMillis = parts[6].toLong(),
            )
        }.getOrElse {
            preferences.edit().remove(PendingRecordKey).commit()
            return null
        }
        if (System.currentTimeMillis() - pending.createdAtMillis !in 0..PendingLifetimeMillis) {
            preferences.edit().remove(PendingRecordKey).commit()
            return null
        }
        return pending
    }

    companion object {
        const val PendingLifetimeMillis = 180_000L
        private const val PreferencesName = "quata_google_oauth_pending"
        private const val PendingRecordKey = "pending_record"
        private const val RecordVersion = "v2"
        private const val KeyAlias = "quata_google_oauth_pending_aes_gcm_v1"
        private val Lock = Any()
    }
}

object AndroidGoogleOAuthCallbackCoordinator {
    const val RedirectUri = "quata://oauth/callback"
    fun redirectUri(callbackToken: String): String = "$RedirectUri?attempt=$callbackToken"
    private data class Waiter(
        val attempt: AndroidGoogleOAuthPending,
        val continuation: CancellableContinuation<AuthSession>,
    )

    private val pending = AtomicReference<Waiter?>(null)

    internal suspend fun awaitCompletion(
        attempt: AndroidGoogleOAuthPending,
        openBrowser: () -> Unit,
    ): AuthSession = suspendCancellableCoroutine { continuation ->
        val waiter = Waiter(attempt, continuation)
        check(pending.compareAndSet(null, waiter)) { "google_oauth_already_in_progress" }
        continuation.invokeOnCancellation { pending.compareAndSet(waiter, null) }
        try {
            openBrowser()
        } catch (failure: Throwable) {
            if (pending.compareAndSet(waiter, null) && continuation.isActive) {
                continuation.resumeWithException(failure)
            }
        }
    }

    fun isCallback(uri: Uri?): Boolean =
        uri?.scheme == "quata" && uri.host == "oauth" && uri.path == "/callback"

    internal fun complete(attempt: AndroidGoogleOAuthPending, result: Result<AuthSession>): Boolean {
        val waiter = pending.get() ?: return false
        if (waiter.attempt != attempt || !pending.compareAndSet(waiter, null)) return false
        val wasActive = waiter.continuation.isActive
        if (wasActive) waiter.continuation.resumeWith(result)
        return wasActive
    }
}

internal fun callbackMatchesPendingGoogleOAuth(
    callback: Uri,
    pending: AndroidGoogleOAuthPending,
): Boolean = callback.getQueryParameter("attempt") == pending.callbackToken
