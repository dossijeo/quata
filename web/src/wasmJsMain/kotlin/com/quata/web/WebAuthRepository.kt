@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
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
    private var activeSession: WebLocalSession? = null

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
        acceptAuthenticationPayload(payload)
    }

    override suspend fun logout() {
        logoutWithBrowserUnsubscribe { Result.success(Unit) }
    }

    /** Restores a complete, non-expired local session without making a network request. */
    suspend fun restoreLocalSession(): WebLocalSession? {
        val session = storedSessionOrNull() ?: return null
        if (session.expiresAt <= currentEpochSeconds()) {
            WebAuthStorage.clear(preferences)
            activeSession = null
            return null
        }
        activeSession = session
        return session
    }

    /** Returns credentials refreshed through Supabase Auth when they are close to expiry. */
    suspend fun currentWebPushCredentials(): WebPushCredentials? =
        sessionForAuthenticatedRequest()?.let { WebPushCredentials(it.accessToken, it.webSessionToken) }

    /** Shared request entry point for browser transports that also need the stable profile id. */
    suspend fun sessionForAuthenticatedRequest(): WebLocalSession? {
        val stored = storedSessionOrNull() ?: return null
        if (!stored.requiresRefresh()) return stored.also { activeSession = it }
        return refreshMutex.withLock {
            val latest = storedSessionOrNull() ?: return@withLock null
            if (!latest.requiresRefresh()) return@withLock latest.also { activeSession = it }
            runCatching { refreshSession(latest) }.getOrNull()
        }
    }

    /** Non-suspending snapshot for feature session providers after launcher authentication. */
    internal fun activeProfileSessionOrNull(): WebLocalSession? = activeSession

    /** Keeps the server logout, browser unsubscribe and local cleanup in the required order. */
    suspend fun logoutWithBrowserUnsubscribe(browserUnsubscribe: suspend () -> Result<Unit>): Result<Unit> {
        val serverFailure = runCatching { notifyServerLogout() }.exceptionOrNull()
        val browserFailure = browserUnsubscribe().exceptionOrNull()
        WebAuthStorage.clear(preferences)
        activeSession = null
        val failure = serverFailure ?: browserFailure
        return if (failure == null) Result.success(Unit) else Result.failure(failure)
    }

    override suspend fun register(request: RegisterAccountRequest): Result<AuthSession> = runCatching {
        require(configuration.webRegistrationEnabled) { "web_registration_unavailable" }
        val challengeToken = requestTurnstileChallenge(
            configuration.turnstileSiteKey.requireConfigured("turnstile_site_key_missing"),
        )
        val apiKey = configuration.webRegistrationApiKey.requireConfigured("web_registration_api_key_missing")
        val countryCode = request.countryCode.filter(Char::isDigit)
        val phoneLocal = request.phone.filter(Char::isDigit)
        val identity = "$countryCode:$phoneLocal"
        val idempotencyKey = registrationKeyFor(identity)
        val payload = webPostJson(
            endpoint = configuration.webRegistrationEndpoint(),
            apiKey = apiKey,
            body = buildWebRegistrationRequest(
                request = request,
                clientInstanceId = ensureWebClientInstanceId(),
                idempotencyKey = idempotencyKey,
                challengeToken = challengeToken,
            ).toString(),
        )
        require(Json.parseToJsonElement(payload).jsonObject["status"]?.jsonPrimitive?.contentOrNull == "accepted") {
            "web_registration_unavailable"
        }
        login(request.countryCode, request.phone, request.password).getOrThrow().also {
            preferences.remove(PendingRegistrationIdentity)
            preferences.remove(PendingRegistrationKey)
        }
    }

    override suspend fun getPasswordRecoveryQuestion(countryCode: String, phone: String): Result<PasswordRecoveryQuestion?> = runCatching {
        require(countryCode.any(Char::isDigit)) { "web_auth_country_code_required" }
        require(phone.any(Char::isDigit)) { "web_auth_phone_required" }
        val apiKey = configuration.supabasePublishableKey.requireConfigured("supabase_publishable_key_missing")
        val response = webPostJson(
            endpoint = configuration.authBridgeEndpoint(),
            apiKey = apiKey,
            body = buildJsonObject {
                put("action", "recovery_question")
                put("country_code", countryCode.filter(Char::isDigit).toString())
                put("phone_local", phone.filter(Char::isDigit).toString())
            }.toString(),
        )
        Json.parseToJsonElement(response)
            .jsonObject["secret_question"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { PasswordRecoveryQuestion(secretQuestion = it) }
    }

    override suspend fun resetPassword(
        countryCode: String,
        phone: String,
        secretAnswer: String,
        newPassword: String,
    ): Result<Unit> = runCatching {
        require(countryCode.any(Char::isDigit)) { "web_auth_country_code_required" }
        require(phone.any(Char::isDigit)) { "web_auth_phone_required" }
        require(secretAnswer.isNotBlank()) { "web_auth_secret_answer_required" }
        require(newPassword.length >= 6) { "web_auth_new_password_invalid" }

        val apiKey = configuration.supabasePublishableKey.requireConfigured("supabase_publishable_key_missing")
        webPostJson(
            endpoint = configuration.authBridgeEndpoint(),
            apiKey = apiKey,
            body = buildJsonObject {
                put("action", "reset_password")
                put("country_code", countryCode.filter(Char::isDigit).toString())
                put("phone_local", phone.filter(Char::isDigit).toString())
                put("secret_answer", secretAnswer.trim())
                put("new_password", newPassword)
            }.toString(),
        )
        Unit
    }

    /** Authenticated counterpart of Android's updateRecoverySecretWithAuthBridge. */
    suspend fun updateRecoverySecret(secretQuestion: String, secretAnswer: String): Result<Unit> = runCatching {
        require(secretQuestion.isNotBlank()) { "web_auth_secret_question_required" }
        require(secretAnswer.isNotBlank()) { "web_auth_secret_answer_required" }
        val session = sessionForAuthenticatedRequest() ?: error("web_auth_session_required")
        val apiKey = configuration.supabasePublishableKey.requireConfigured("supabase_publishable_key_missing")
        val response = webPostJson(
            endpoint = configuration.authBridgeEndpoint(),
            apiKey = apiKey,
            accessToken = session.accessToken,
            body = webRecoverySecretRequest(secretQuestion, secretAnswer).toString(),
        )
        check(Json.parseToJsonElement(response).jsonObject["ok"]?.jsonPrimitive?.booleanOrNull == true) {
            "web_profile_recovery_secret_update_failed"
        }
    }


    override suspend fun deactivateAccount(password: String): Result<Unit> =
        performAccountLifecycle(action = "deactivate", password = password)

    override suspend fun deleteAccountData(password: String): Result<Unit> =
        performAccountLifecycle(action = "delete", password = password)

    private suspend fun performAccountLifecycle(action: String, password: String): Result<Unit> = runCatching {
        require(password.isNotBlank()) { "web_auth_password_required" }
        val session = sessionForAuthenticatedRequest() ?: error("web_auth_session_required")
        val apiKey = configuration.supabasePublishableKey.requireConfigured("supabase_publishable_key_missing")
        val response = webPostJson(
            endpoint = configuration.accountLifecycleEndpoint(),
            apiKey = apiKey,
            body = buildJsonObject {
                put("action", action)
                put("password", password)
            }.toString(),
            accessToken = session.accessToken,
        )
        check(Json.parseToJsonElement(response).jsonObject["ok"]?.jsonPrimitive?.booleanOrNull == true) {
            "web_auth_lifecycle_failed"
        }
        WebAuthStorage.clear(preferences)
    }

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
        val displayName = preferences.getString(WebAuthStorage.DisplayName)?.trim()?.takeIf(String::isNotBlank)
        val isOfficial = preferences.getString(WebAuthStorage.IsOfficial).toBoolean()
        return if (accessToken != null && refreshToken != null && webSessionToken != null && userId != null && expiresAt != null) {
            WebLocalSession(accessToken, refreshToken, webSessionToken, userId, expiresAt, displayName, isOfficial)
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
        activeSession = refreshed
        return refreshed
    }

    private suspend fun acceptAuthenticationPayload(payload: String): AuthSession {
        val rawSession = payload.toWebAuthSession()
        val session = rawSession.copy(
            isOfficial = rawSession.isOfficial || fetchAuthenticatedProfileIsOfficial(rawSession.bearerToken, rawSession.userId),
        )
        val webSessionToken = payload.webSessionToken()
        val displayName = payload.webProfileDisplayName()
        session.persist(preferences, webSessionToken, displayName)
        activeSession = WebLocalSession(
            accessToken = session.accessToken ?: session.token,
            refreshToken = session.refreshToken.orEmpty(),
            webSessionToken = webSessionToken,
            userId = session.userId,
            expiresAt = session.expiresAt ?: currentEpochSeconds(),
            displayName = displayName,
            isOfficial = session.isOfficial,
        )
        return session
    }

    private suspend fun fetchAuthenticatedProfileIsOfficial(accessToken: String, profileId: String): Boolean {
        val apiKey = configuration.supabasePublishableKey.requireConfigured("supabase_publishable_key_missing")
        val baseUrl = configuration.supabaseUrl.requireConfigured("supabase_url_missing").trimEnd('/')
        val response = webGetJson(
            endpoint = "$baseUrl/rest/v1/community_profiles?select=is_official&id=eq.$profileId&limit=1",
            apiKey = apiKey,
            accessToken = accessToken,
        )
        return Json.parseToJsonElement(response)
            .jsonArray
            .firstOrNull()
            ?.jsonObject
            ?.booleanOrNull("is_official") == true
    }

    private suspend fun registrationKeyFor(identity: String): String {
        val storedIdentity = preferences.getString(PendingRegistrationIdentity)
        val storedKey = preferences.getString(PendingRegistrationKey)
        if (storedIdentity == identity && !storedKey.isNullOrBlank()) {
            return storedKey
        }
        return newWebRegistrationIdempotencyKey().also {
            preferences.putString(PendingRegistrationIdentity, identity)
            preferences.putString(PendingRegistrationKey, it)
        }
    }
}

private const val PendingRegistrationIdentity = "web.auth.registration.identity"
private const val PendingRegistrationKey = "web.auth.registration.idempotency_key"

internal fun webRecoverySecretRequest(secretQuestion: String, secretAnswer: String): JsonObject = buildJsonObject {
    put("version", 1)
    put("action", "update_recovery_secret")
    put("secret_question", secretQuestion.trim())
    put("secret_answer", secretAnswer)
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
    /** Optional so sessions persisted before this field was introduced remain restorable. */
    val displayName: String? = null,
    /** Optional-persisted role flag; old sessions restore as non-official until next login. */
    val isOfficial: Boolean = false,
)

internal object WebAuthStorage {
    const val AccessToken = "quata_web_access_token"
    const val RefreshToken = "quata_web_refresh_token"
    const val WebSessionToken = "quata_web_session_token"
    const val UserId = "quata_web_user_id"
    const val ExpiresAt = "quata_web_expires_at"
    const val DisplayName = "quata_web_display_name"
    const val IsOfficial = "quata_web_is_official"

    suspend fun clear(preferences: PreferenceStore) {
        for (key in listOf(AccessToken, RefreshToken, WebSessionToken, UserId, ExpiresAt, DisplayName, IsOfficial, WebSessionReadyKey)) {
            preferences.remove(key)
        }
    }
}

private suspend fun AuthSession.persist(preferences: PreferenceStore, webSessionToken: String, displayName: String?) {
    preferences.putString(WebAuthStorage.AccessToken, bearerToken)
    preferences.putString(WebAuthStorage.RefreshToken, refreshToken.orEmpty())
    preferences.putString(WebAuthStorage.WebSessionToken, webSessionToken)
    preferences.putString(WebAuthStorage.UserId, userId)
    if (displayName != null) preferences.putString(WebAuthStorage.DisplayName, displayName)
    else preferences.remove(WebAuthStorage.DisplayName)
    preferences.putString(WebAuthStorage.IsOfficial, isOfficial.toString())
    expiresAt?.let { preferences.putString(WebAuthStorage.ExpiresAt, it.toString()) }
}

private suspend fun WebLocalSession.persist(preferences: PreferenceStore) {
    preferences.putString(WebAuthStorage.AccessToken, accessToken)
    preferences.putString(WebAuthStorage.RefreshToken, refreshToken)
    preferences.putString(WebAuthStorage.WebSessionToken, webSessionToken)
    preferences.putString(WebAuthStorage.UserId, userId)
    preferences.putString(WebAuthStorage.ExpiresAt, expiresAt.toString())
    if (displayName != null) preferences.putString(WebAuthStorage.DisplayName, displayName)
    else preferences.remove(WebAuthStorage.DisplayName)
    preferences.putString(WebAuthStorage.IsOfficial, isOfficial.toString())
}

private fun WebLocalSession.requiresRefresh(): Boolean =
    expiresAt <= currentEpochSeconds() + WebSessionRefreshLeewaySeconds

private fun String?.requireConfigured(error: String): String =
    takeIf { !it.isNullOrBlank() } ?: throw IllegalStateException(error)

private fun WebRuntimeConfiguration.authBridgeEndpoint(): String =
    supabaseUrl.requireConfigured("supabase_url_missing").trimEnd('/') + "/functions/v1/quata-auth-bridge"

private fun WebRuntimeConfiguration.webRegistrationEndpoint(): String =
    supabaseUrl.requireConfigured("supabase_url_missing").trimEnd('/') + "/functions/v1/quata-register"

internal fun WebRuntimeConfiguration.webPushEndpoint(): String =
    supabaseUrl.requireConfigured("supabase_url_missing").trimEnd('/') + "/functions/v1/quata-web-push"

private fun WebRuntimeConfiguration.accountLifecycleEndpoint(): String =
    supabaseUrl.requireConfigured("supabase_url_missing").trimEnd('/') + "/functions/v1/quata-account-lifecycle"

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
        isOfficial = profile.booleanOrNull("is_official"),
    )
}

private fun String.webSessionToken(): String = Json.parseToJsonElement(this).jsonObject
    .requiredObject("web_session")
    .requiredString("token")

private fun String.webProfileDisplayName(): String? = Json.parseToJsonElement(this).jsonObject
    .requiredObject("profile")
    .stringOrNull("display_name")
    ?.trim()
    ?.takeIf(String::isNotBlank)

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

private fun JsonObject.booleanOrNull(name: String): Boolean = this[name]?.jsonPrimitive?.booleanOrNull == true

private fun countryCodeDigits(value: String?): String = value.orEmpty().filter(Char::isDigit)

internal fun buildWebRegistrationRequest(
    request: RegisterAccountRequest,
    clientInstanceId: String,
    idempotencyKey: String,
    challengeToken: String,
): JsonObject = buildJsonObject {
    put("version", 1)
    put("channel", "web")
    put("display_name", request.displayName.trim())
    put("neighborhood", request.neighborhood.trim())
    put("country_code", request.countryCode.filter(Char::isDigit))
    put("phone_local", request.phone.filter(Char::isDigit))
    put("password", request.password)
    put("secret_question", request.secretQuestion.trim())
    put("secret_answer", request.secretAnswer.trim())
    put("client_instance_id", clientInstanceId)
    put("idempotency_key", idempotencyKey)
    put("challenge_token", challengeToken)
}

private suspend fun requestTurnstileChallenge(siteKey: String): String = suspendCoroutine { continuation ->
    requestTurnstileWidget(
        siteKey.toJsString(),
        { token -> continuation.resume(token.toString()) },
        { continuation.resumeWith(Result.failure(IllegalStateException("turnstile_challenge_failed"))) },
    )
}

@JsFun("""(siteKey, resolve, reject) => {
  let attempts = 0;
  const run = () => {
    if (!globalThis.turnstile) {
      if (++attempts < 40) { setTimeout(run, 250); return; }
      reject(); return;
    }
    const node = document.createElement('div');
    node.style.position = 'fixed'; node.style.left = '-10000px';
    document.body.appendChild(node);
    const widgetId = globalThis.turnstile.render(node, {
      sitekey: siteKey, size: 'invisible', execution: 'execute', action: 'register_web',
      callback: token => { node.remove(); resolve(token); },
      'error-callback': () => { node.remove(); reject(); },
      'expired-callback': () => { node.remove(); reject(); }
    });
    globalThis.turnstile.execute(widgetId);
  };
  run();
}""")
private external fun requestTurnstileWidget(
    siteKey: JsString,
    resolve: (JsString) -> Unit,
    reject: () -> Unit,
)

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

private suspend fun webGetJson(
    endpoint: String,
    apiKey: String,
    accessToken: String,
): String = suspendCoroutine { continuation ->
    browserGetJson(
        endpoint = endpoint,
        apiKey = apiKey,
        accessToken = accessToken,
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
    (() => {
    const headers = { 'Content-Type': 'application/json', apikey: apiKey };
    if (accessToken != null && accessToken.length > 0) headers.Authorization = `Bearer ${'$'}{accessToken}`;
    if (webSessionToken != null && webSessionToken.length > 0) headers['x-quata-web-session'] = webSessionToken;
    globalThis.fetch(endpoint, { method: 'POST', headers, body })
      .then(async (response) => {
        const text = await response.text();
        if (response.ok) onSuccess(text);
        else {
          let errorCode = null;
          try { errorCode = JSON.parse(text)?.error; } catch (_) {}
          onFailure(errorCode ? `web_auth_${'$'}{errorCode}` : `web_auth_http_${'$'}{response.status}`);
        }
      })
      .catch((error) => onFailure(error?.message || 'web_auth_network_error'));
    })()
    """,
)

private fun browserGetJson(
    endpoint: String,
    apiKey: String,
    accessToken: String,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    (() => {
    const headers = { apikey: apiKey, Authorization: `Bearer ${'$'}{accessToken}` };
    globalThis.fetch(endpoint, { method: 'GET', headers })
      .then(async (response) => {
        const text = await response.text();
        if (response.ok) onSuccess(text);
        else {
          let errorCode = null;
          try { errorCode = JSON.parse(text)?.code || JSON.parse(text)?.message; } catch (_) {}
          onFailure(errorCode ? `web_auth_profile_${'$'}{errorCode}` : `web_auth_profile_http_${'$'}{response.status}`);
        }
      })
      .catch((error) => onFailure(error?.message || 'web_auth_profile_network_error'));
    })()
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

private fun newWebRegistrationIdempotencyKey(): String = js(
    """
    (() => {
      const random = globalThis.crypto?.randomUUID?.();
      if (random) return random.replaceAll('-', '');
      if (!globalThis.crypto?.getRandomValues) throw new Error('web_registration_secure_random_unavailable');
      const bytes = new Uint8Array(24);
      globalThis.crypto.getRandomValues(bytes);
      return Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
    })()
    """,
)
