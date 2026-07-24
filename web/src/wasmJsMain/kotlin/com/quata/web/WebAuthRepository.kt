package com.quata.web

import com.quata.core.model.AuthSession
import com.quata.core.model.currentEpochSeconds
import com.quata.core.platform.PreferenceStore
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.domain.PasswordRecoveryQuestion
import com.quata.feature.auth.domain.RegisterAccountRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Browser implementation of the public Web auth bridge contract. It deliberately does not use
 * Android's `action=login` flow or persist any server-side/private credential.
 */
class WebAuthRepository(
    private val configuration: WebRuntimeConfiguration,
    private val preferences: PreferenceStore,
) : AuthRepository {
    private val refreshMutex = Mutex()

    override suspend fun login(countryCode: String, phone: String, password: String): Result<AuthSession> = runCatching {
        val apiKey = configuration.supabasePublishableKey.requireConfigured("supabase_publishable_key_missing")
        val endpoint = configuration.authBridgeEndpoint()
        val request = buildJsonObject {
            put("action", "web_login")
            put("country_code", countryCode)
            put("phone_local", phone)
            put("password", password)
            put("client_instance_id", ensureWebClientInstanceId())
        }
        val payload = webPostJson(endpoint, apiKey, request.toString())
        val session = payload.toWebAuthSession()
        session.persist(preferences, payload.webSessionToken())
        session
    }

    override suspend fun logout() {
        logoutWithBrowserUnsubscribe { Result.success(Unit) }
    }

    /** Restores a complete, non-expired local session without making a network request. */
    suspend fun restoreLocalSession(): WebLocalSession? {
        val session = storedSessionOrNull() ?: return null
        if (session.expiresAt <= currentEpochSeconds()) {
            WebAuthStorage.clear(preferences)
            return null
        }
        return session
    }

    /** Returns credentials refreshed through Supabase Auth when they are close to expiry. */
    suspend fun currentWebPushCredentials(): WebPushCredentials? =
        sessionForAuthenticatedRequest()?.let { WebPushCredentials(it.accessToken, it.webSessionToken) }

    /** Shared request entry point for browser transports that also need the stable profile id. */
    suspend fun sessionForAuthenticatedRequest(): WebLocalSession? {
        val stored = storedSessionOrNull() ?: return null
        if (!stored.requiresRefresh()) return stored
        return refreshMutex.withLock {
            val latest = storedSessionOrNull() ?: return@withLock null
            if (!latest.requiresRefresh()) return@withLock latest
            runCatching { refreshSession(latest) }.getOrNull()
        }
    }

    /** Keeps the server logout, browser unsubscribe and local cleanup in the required order. */
    suspend fun logoutWithBrowserUnsubscribe(browserUnsubscribe: suspend () -> Result<Unit>): Result<Unit> {
        val serverFailure = runCatching { notifyServerLogout() }.exceptionOrNull()
        val browserFailure = browserUnsubscribe().exceptionOrNull()
        WebAuthStorage.clear(preferences)
        val failure = serverFailure ?: browserFailure
        return if (failure == null) Result.success(Unit) else Result.failure(failure)
    }

    override suspend fun register(request: RegisterAccountRequest): Result<AuthSession> =
        Result.failure(UnsupportedOperationException("web_auth_register_not_implemented"))

    override suspend fun getPasswordRecoveryQuestion(countryCode: String, phone: String): Result<PasswordRecoveryQuestion?> =
        Result.failure(UnsupportedOperationException("web_auth_recovery_not_implemented"))

    override suspend fun resetPassword(
        countryCode: String,
        phone: String,
        secretAnswer: String,
        newPassword: String,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("web_auth_recovery_not_implemented"))

    override suspend fun deactivateAccount(password: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("web_auth_lifecycle_not_implemented"))

    override suspend fun deleteAccountData(password: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("web_auth_lifecycle_not_implemented"))

    private suspend fun notifyServerLogout() {
        val credentials = currentWebPushCredentials() ?: return
        val apiKey = configuration.supabasePublishableKey.requireConfigured("supabase_publishable_key_missing")
        webPostJson(
            endpoint = configuration.webPushEndpoint(),
            apiKey = apiKey,
            body = buildJsonObject { put("action", "logout") }.toString(),
            accessToken = credentials.accessToken,
            webSessionToken = credentials.webSessionToken,
        )
    }

    private suspend fun storedSessionOrNull(): WebLocalSession? {
        val accessToken = preferences.getString(WebAuthStorage.AccessToken)?.takeIf(String::isNotBlank)
        val refreshToken = preferences.getString(WebAuthStorage.RefreshToken)?.takeIf(String::isNotBlank)
        val webSessionToken = preferences.getString(WebAuthStorage.WebSessionToken)?.takeIf(String::isNotBlank)
        val userId = preferences.getString(WebAuthStorage.UserId)?.takeIf(String::isNotBlank)
        val expiresAt = preferences.getString(WebAuthStorage.ExpiresAt)?.toLongOrNull()
        return if (accessToken != null && refreshToken != null && webSessionToken != null && userId != null && expiresAt != null) {
            WebLocalSession(accessToken, refreshToken, webSessionToken, userId, expiresAt)
        } else {
            null
        }
    }

    private suspend fun refreshSession(current: WebLocalSession): WebLocalSession {
        val apiKey = configuration.supabasePublishableKey.requireConfigured("supabase_publishable_key_missing")
        val response = webPostJson(
            endpoint = configuration.supabaseRefreshTokenEndpoint(),
            apiKey = apiKey,
            body = buildJsonObject { put("refresh_token", current.refreshToken) }.toString(),
        )
        val refreshed = response.toWebRefreshedSession(current)
        refreshed.persist(preferences)
        return refreshed
    }
}

data class WebPushCredentials(
    val accessToken: String,
    val webSessionToken: String,
)

data class WebLocalSession(
    val accessToken: String,
    val refreshToken: String,
    val webSessionToken: String,
    val userId: String,
    val expiresAt: Long,
)

private object WebAuthStorage {
    const val AccessToken = "quata_web_access_token"
    const val RefreshToken = "quata_web_refresh_token"
    const val WebSessionToken = "quata_web_session_token"
    const val UserId = "quata_web_user_id"
    const val ExpiresAt = "quata_web_expires_at"

    suspend fun clear(preferences: PreferenceStore) {
        for (key in listOf(AccessToken, RefreshToken, WebSessionToken, UserId, ExpiresAt, WebSessionReadyKey)) {
            preferences.remove(key)
        }
    }
}

private suspend fun AuthSession.persist(preferences: PreferenceStore, webSessionToken: String) {
    preferences.putString(WebAuthStorage.AccessToken, bearerToken)
    preferences.putString(WebAuthStorage.RefreshToken, refreshToken.orEmpty())
    preferences.putString(WebAuthStorage.WebSessionToken, webSessionToken)
    preferences.putString(WebAuthStorage.UserId, userId)
    expiresAt?.let { preferences.putString(WebAuthStorage.ExpiresAt, it.toString()) }
}

private suspend fun WebLocalSession.persist(preferences: PreferenceStore) {
    preferences.putString(WebAuthStorage.AccessToken, accessToken)
    preferences.putString(WebAuthStorage.RefreshToken, refreshToken)
    preferences.putString(WebAuthStorage.WebSessionToken, webSessionToken)
    preferences.putString(WebAuthStorage.UserId, userId)
    preferences.putString(WebAuthStorage.ExpiresAt, expiresAt.toString())
}

private fun WebLocalSession.requiresRefresh(): Boolean =
    expiresAt <= currentEpochSeconds() + WebSessionRefreshLeewaySeconds

private fun String?.requireConfigured(error: String): String =
    takeIf { !it.isNullOrBlank() } ?: throw IllegalStateException(error)

private fun WebRuntimeConfiguration.authBridgeEndpoint(): String =
    supabaseUrl.requireConfigured("supabase_url_missing").trimEnd('/') + "/functions/v1/quata-auth-bridge"

internal fun WebRuntimeConfiguration.webPushEndpoint(): String =
    supabaseUrl.requireConfigured("supabase_url_missing").trimEnd('/') + "/functions/v1/quata-web-push"

private fun WebRuntimeConfiguration.supabaseRefreshTokenEndpoint(): String =
    supabaseUrl.requireConfigured("supabase_url_missing").trimEnd('/') + "/auth/v1/token?grant_type=refresh_token"

private fun String.toWebAuthSession(): AuthSession {
    val root = Json.parseToJsonElement(this).jsonObject
    val session = root.requiredObject("session")
    val profile = root.requiredObject("profile")
    val user = root["user"]?.jsonObject
    val accessToken = session.requiredString("access_token")
    val refreshToken = session.requiredString("refresh_token")
    val expiresAt = session["expires_at"]?.jsonPrimitive?.longOrNull
        ?: session["expires_in"]?.jsonPrimitive?.longOrNull?.let { currentEpochSeconds() + it }
    val userId = profile.requiredString("id")
    return AuthSession(
        token = accessToken,
        userId = userId,
        authUserId = profile.stringOrNull("auth_user_id") ?: user?.stringOrNull("id"),
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = expiresAt,
        email = user?.stringOrNull("email") ?: "${countryCodeDigits(profile.stringOrNull("country_code"))}${countryCodeDigits(profile.stringOrNull("phone_local"))}@phone.quata.app",
        displayName = profile.stringOrNull("display_name")
            ?: profile.stringOrNull("phone_local")
            ?: "Usuario",
    )
}

private fun String.webSessionToken(): String = Json.parseToJsonElement(this).jsonObject
    .requiredObject("web_session")
    .requiredString("token")

private fun String.toWebRefreshedSession(current: WebLocalSession): WebLocalSession {
    val root = Json.parseToJsonElement(this).jsonObject
    val accessToken = root.requiredString("access_token")
    val refreshToken = root.requiredString("refresh_token")
    val expiresAt = root["expires_at"]?.jsonPrimitive?.longOrNull
        ?: root["expires_in"]?.jsonPrimitive?.longOrNull?.let { currentEpochSeconds() + it }
        ?: throw IllegalStateException("web_auth_refresh_missing_expiry")
    return current.copy(accessToken = accessToken, refreshToken = refreshToken, expiresAt = expiresAt)
}

private fun JsonObject.requiredObject(name: String): JsonObject =
    this[name]?.jsonObject ?: throw IllegalStateException("web_auth_response_missing_$name")

private fun JsonObject.requiredString(name: String): String =
    stringOrNull(name) ?: throw IllegalStateException("web_auth_response_missing_$name")

private fun JsonObject.stringOrNull(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun countryCodeDigits(value: String?): String = value.orEmpty().filter(Char::isDigit)

private const val WebSessionRefreshLeewaySeconds = 60L

private suspend fun webPostJson(
    endpoint: String,
    apiKey: String,
    body: String,
    accessToken: String? = null,
    webSessionToken: String? = null,
): String = suspendCoroutine { continuation ->
    browserPostJson(
        endpoint = endpoint,
        apiKey = apiKey,
        body = body,
        accessToken = accessToken,
        webSessionToken = webSessionToken,
        onSuccess = { value -> continuation.resume(value) },
        onFailure = { continuation.resumeWith(Result.failure(IllegalStateException(it))) },
    )
}

private fun browserPostJson(
    endpoint: String,
    apiKey: String,
    body: String,
    accessToken: String?,
    webSessionToken: String?,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    const headers = { 'Content-Type': 'application/json', apikey: apiKey };
    if (accessToken != null && accessToken.length > 0) headers.Authorization = `Bearer ${'$'}{accessToken}`;
    if (webSessionToken != null && webSessionToken.length > 0) headers['x-quata-web-session'] = webSessionToken;
    globalThis.fetch(endpoint, { method: 'POST', headers, body })
      .then(async (response) => {
        const text = await response.text();
        if (response.ok) onSuccess(text);
        else onFailure(`web_auth_http_${'$'}{response.status}`);
      })
      .catch((error) => onFailure(error?.message || 'web_auth_network_error'));
    """,
)

/** Stable browser-install identifier required by the Web Push login contract. */
internal fun ensureWebClientInstanceId(): String = js(
    """
    (() => {
      const key = 'quata_web_client_instance_id';
      const existing = globalThis.localStorage?.getItem(key);
      if (existing) return existing;
      const created = globalThis.crypto?.randomUUID?.() ||
        (String(Date.now()) + '-' + Math.random().toString(36).slice(2));
      globalThis.localStorage?.setItem(key, created);
      return created;
    })()
    """,
)
