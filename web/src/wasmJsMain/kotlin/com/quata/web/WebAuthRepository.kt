@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.core.model.AuthSession
import com.quata.core.model.currentEpochSeconds
import com.quata.core.platform.PreferenceStore
import com.quata.core.platform.BrowserFileCacheService
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.domain.GoogleAuthProvider
import com.quata.feature.auth.domain.GoogleIdentityLinker
import com.quata.feature.auth.domain.acceptFederatedProfile
import com.quata.feature.auth.domain.buildGoogleIdentityLinkRequest
import com.quata.feature.auth.domain.buildGoogleOAuthRequest
import com.quata.feature.auth.domain.parseGoogleAuthUserId
import com.quata.feature.auth.domain.parseGoogleIdentityAuthorizationUrl
import com.quata.feature.auth.domain.parseGoogleOAuthCallback
import com.quata.feature.auth.domain.parseGoogleOAuthTokenSet
import com.quata.feature.auth.domain.PasswordRecoveryQuestion
import com.quata.feature.auth.domain.RegisterAccountRequest
import com.quata.feature.chat.presentation.chat.ChatComposerDraftStore
import com.quata.feature.chat.presentation.chat.BrowserChatComposerAttachmentExecutionLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.suspendCoroutine

/**
 * Browser implementation of the public Web auth bridge contract. It deliberately does not use
 * Android's `action=login` flow or persist any server-side/private credential.
 */
class WebAuthRepository(
    private val configuration: WebRuntimeConfiguration,
    private val preferences: PreferenceStore,
) : AuthRepository, GoogleAuthProvider, GoogleIdentityLinker {
    private val refreshMutex = Mutex()
    private val sessionMutationMutex = Mutex()
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

    override fun beginSignIn(): suspend () -> Result<AuthSession> {
        val popupToken = reserveWebGoogleOAuthPopup()
            ?: return suspend { Result.failure(IllegalStateException("google_oauth_popup_blocked")) }
        return suspend {
            try {
                signInWithGoogle(popupToken)
            } finally {
                closeWebGoogleOAuthPopup(popupToken)
            }
        }
    }

    private suspend fun signInWithGoogle(popupToken: String): Result<AuthSession> = runCatching {
        val retainedSession = sessionMutationMutex.withLock { storedSessionOrNull() }
        val apiKey = configuration.supabasePublishableKey.requireConfigured("supabase_publishable_key_missing")
        val baseUrl = configuration.supabaseUrl.requireConfigured("supabase_url_missing").trimEnd('/')
        val request = buildGoogleOAuthRequest(
            supabaseUrl = baseUrl,
            redirectUri = webGoogleOAuthRedirectUri(),
            randomBytes = ::webSecureRandomBytes,
        )
        val callback = awaitWebGoogleOAuthCallback(popupToken, request.authorizationUrl, request.redirectUri)
        val code = parseGoogleOAuthCallback(callback, request)
        val tokenPayload = webPostJson(
            endpoint = "$baseUrl/auth/v1/token?grant_type=pkce",
            apiKey = apiKey,
            body = buildJsonObject {
                put("auth_code", code)
                put("code_verifier", request.codeVerifier)
            }.toString(),
        )
        val tokens = parseGoogleOAuthTokenSet(tokenPayload)
        val profilePayload = webPostJson(
            endpoint = configuration.authBridgeEndpoint(),
            apiKey = apiKey,
            accessToken = tokens.accessToken,
            body = buildJsonObject {
                put("action", "federated_profile")
                put("version", 1)
                put("client_type", "web")
                put("client_instance_id", ensureWebClientInstanceId())
            }.toString(),
        )
        val federated = tokens.acceptFederatedProfile(profilePayload)
        val webSessionToken = federated.webSessionToken ?: error("google_oauth_web_session_missing")
        val acceptedSession = federated.session.copy(
            isOfficial = federated.session.isOfficial || fetchAuthenticatedProfileIsOfficial(
                federated.session.bearerToken,
                federated.session.userId,
            ),
        )
        val accepted = WebLocalSession(
            accessToken = acceptedSession.bearerToken,
            refreshToken = acceptedSession.refreshToken.orEmpty(),
            webSessionToken = webSessionToken,
            userId = acceptedSession.userId,
            expiresAt = acceptedSession.expiresAt ?: error("google_oauth_expiry_missing"),
            displayName = acceptedSession.displayName,
            isOfficial = acceptedSession.isOfficial,
        )
        sessionMutationMutex.withLock {
            check(webGoogleOAuthSessionStillCurrent(retainedSession, storedSessionOrNull())) {
                "google_oauth_session_superseded"
            }
            acceptedSession.persist(preferences, webSessionToken, acceptedSession.displayName)
            activeSession = accepted
        }
        acceptedSession
    }.onFailure { if (it is CancellationException) throw it }

    override fun beginIdentityLink(): suspend () -> Result<AuthSession> {
        val popupToken = reserveWebGoogleOAuthPopup()
            ?: return suspend { Result.failure(IllegalStateException("google_oauth_popup_blocked")) }
        return suspend {
            try {
                linkGoogleIdentity(popupToken)
            } finally {
                closeWebGoogleOAuthPopup(popupToken)
            }
        }
    }

    private suspend fun linkGoogleIdentity(popupToken: String): Result<AuthSession> = runCatching {
        val current = sessionForAuthenticatedRequest() ?: error("web_auth_session_required")
        val apiKey = configuration.supabasePublishableKey.requireConfigured("supabase_publishable_key_missing")
        val baseUrl = configuration.supabaseUrl.requireConfigured("supabase_url_missing").trimEnd('/')
        val currentUserPayload = webGetJson("$baseUrl/auth/v1/user", apiKey, current.accessToken)
        val expectedAuthUserId = parseGoogleAuthUserId(currentUserPayload)
        val request = buildGoogleIdentityLinkRequest(
            supabaseUrl = baseUrl,
            redirectUri = webGoogleOAuthRedirectUri(),
            randomBytes = ::webSecureRandomBytes,
        )
        val authorizationPayload = webGetJson(request.authorizationUrl, apiKey, current.accessToken)
        val providerUrl = parseGoogleIdentityAuthorizationUrl(authorizationPayload)
        val callback = awaitWebGoogleOAuthCallback(popupToken, providerUrl, request.redirectUri)
        val code = parseGoogleOAuthCallback(callback, request)
        val tokenPayload = webPostJson(
            endpoint = "$baseUrl/auth/v1/token?grant_type=pkce",
            apiKey = apiKey,
            body = buildJsonObject {
                put("auth_code", code)
                put("code_verifier", request.codeVerifier)
            }.toString(),
        )
        check(parseGoogleAuthUserId(tokenPayload) == expectedAuthUserId) { "google_identity_user_mismatch" }
        val tokens = parseGoogleOAuthTokenSet(tokenPayload)
        val updated = current.copy(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresAt = tokens.expiresAt,
        )
        val published = sessionMutationMutex.withLock {
            val latest = storedSessionOrNull()
                ?.takeIf { it.userId == current.userId }
                ?: error("google_identity_session_changed")
            if (!latest.sameCredentialsAs(current)) {
                activeSession = latest
                return@withLock latest
            }
            updated.persist(preferences)
            activeSession = updated
            updated
        }
        AuthSession(
            token = published.accessToken,
            userId = published.userId,
            authUserId = expectedAuthUserId,
            accessToken = published.accessToken,
            refreshToken = published.refreshToken,
            expiresAt = published.expiresAt,
            email = "federated-${published.userId}@profile.quata.app",
            displayName = published.displayName ?: "Usuario",
            isOfficial = published.isOfficial,
        )
    }.onFailure { if (it is CancellationException) throw it }

    override suspend fun logout() {
        logoutWithBrowserUnsubscribe(global = false) { Result.success(Unit) }
    }

    override suspend fun logoutEverywhere() {
        logoutWithBrowserUnsubscribe(global = true) { Result.success(Unit) }
    }

    /** Restores a complete, non-expired local session without making a network request. */
    suspend fun restoreLocalSession(): WebLocalSession? {
        return sessionMutationMutex.withLock {
            val session = storedSessionOrNull() ?: return@withLock null
            if (session.expiresAt <= currentEpochSeconds()) {
                WebAuthStorage.clear(preferences)
                activeSession = null
                return@withLock null
            }
            activeSession = session
            session
        }
    }

    /** Returns the persisted profile id without refreshing, expiring or clearing local credentials. */
    internal suspend fun storedProfileIdOrNull(): String? =
        storedSessionOrNull()?.userId?.trim()?.takeIf(String::isNotBlank)

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
            try {
                refreshSession(latest)
            } catch (_: WebRefreshSessionRejected) {
                retireSessionIfCurrent(latest)
                null
            } catch (_: Exception) {
                // Network, throttling and unknown responses do not prove revocation.
                null
            }
        }
    }

    /**
     * Renews the exact credentials rejected by an authenticated request.
     *
     * A login may finish while the rejected request is in flight. In that case the newer session
     * is returned without refreshing or clearing it; the caller must still verify that the actor
     * has not changed before replaying an actor-bound request.
     */
    internal suspend fun sessionAfterUnauthorized(rejectedAccessToken: String): WebLocalSession? =
        refreshMutex.withLock {
            val latest = storedSessionOrNull() ?: return@withLock null
            if (latest.accessToken != rejectedAccessToken) {
                return@withLock latest.also { activeSession = it }
            }
            try {
                refreshSession(latest)
            } catch (_: WebRefreshSessionRejected) {
                retireSessionIfCurrent(latest)
                null
            } catch (_: Exception) {
                // A transport failure does not prove that the refresh token is invalid.
                null
            }
        }

    /** Non-suspending snapshot for feature session providers after launcher authentication. */
    internal fun activeProfileSessionOrNull(): WebLocalSession? = activeSession

    /** Realtime must never join or publish with a token already inside the refresh window. */
    internal fun activeRealtimeSessionOrNull(nowEpochSeconds: Long = currentEpochSeconds()): WebLocalSession? =
        activeSession?.takeUnless { it.requiresRefresh(nowEpochSeconds) }

    internal fun activeRealtimeRefreshDelayMillis(nowEpochSeconds: Long = currentEpochSeconds()): Long? =
        activeSession?.let { session ->
            val delaySeconds = (session.expiresAt - nowEpochSeconds - WebSessionRefreshLeewaySeconds)
                .coerceAtLeast(0L)
            if (delaySeconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else delaySeconds * 1_000L
        }

    /** Keeps the server logout, browser unsubscribe and local cleanup in the required order. */
    suspend fun logoutWithBrowserUnsubscribe(
        global: Boolean = false,
        browserUnsubscribe: suspend () -> Result<Unit>,
    ): Result<Unit> {
        val retiringProfileId = storedProfileIdOrNull()
        val webSessionFailure = if (global) null else runCatching { notifyServerLogout() }.exceptionOrNull()
        val authFailure = runCatching { notifySupabaseLogout(global) }.exceptionOrNull()
        if (global && authFailure != null) return Result.failure(authFailure)
        val browserResult = withTimeoutOrNull(WebBrowserUnsubscribeTimeoutMillis) {
            runCatching { browserUnsubscribe().getOrThrow() }
        } ?: Result.failure(IllegalStateException("web_push_unsubscribe_timeout"))
        val browserFailure = browserResult.exceptionOrNull()
        sessionMutationMutex.withLock {
            WebAuthStorage.clear(preferences)
            activeSession = null
        }
        retiringProfileId?.let {
            ChatComposerDraftStore(
                preferences,
                BrowserFileCacheService(),
                BrowserChatComposerAttachmentExecutionLock(),
            ).clearActor(it)
        }
        // A successful global endpoint already retired every Auth session and device endpoint.
        // Browser push cleanup is still attempted, but it cannot turn that irreversible success
        // into a retryable failure that leaves the shell on a private route.
        val failure = webSessionFailure ?: authFailure ?: browserFailure.takeUnless { global }
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
        sessionMutationMutex.withLock {
            WebAuthStorage.clear(preferences)
            activeSession = null
        }
        ChatComposerDraftStore(
            preferences,
            BrowserFileCacheService(),
            BrowserChatComposerAttachmentExecutionLock(),
        ).clearActor(session.userId)
    }

    private suspend fun notifyServerLogout() {
        val credentials = storedSessionOrNull()?.let { WebPushCredentials(it.accessToken, it.webSessionToken) } ?: return
        val apiKey = configuration.supabasePublishableKey.requireConfigured("supabase_publishable_key_missing")
        webPostJson(
            endpoint = configuration.webPushEndpoint(),
            apiKey = apiKey,
            body = buildJsonObject { put("action", "logout") }.toString(),
            accessToken = credentials.accessToken,
            webSessionToken = credentials.webSessionToken,
        )
    }

    private suspend fun notifySupabaseLogout(global: Boolean) {
        val accessToken = storedSessionOrNull()?.accessToken ?: return
        val apiKey = configuration.supabasePublishableKey.requireConfigured("supabase_publishable_key_missing")
        webPostJson(
            endpoint = if (global) configuration.globalLogoutEndpoint() else configuration.supabaseLogoutEndpoint(),
            apiKey = apiKey,
            body = "{}",
            accessToken = accessToken,
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

    private suspend fun refreshSession(current: WebLocalSession): WebLocalSession? {
        val apiKey = configuration.supabasePublishableKey.requireConfigured("supabase_publishable_key_missing")
        val response = webPostJson(
            endpoint = configuration.supabaseRefreshTokenEndpoint(),
            apiKey = apiKey,
            body = buildJsonObject { put("refresh_token", current.refreshToken) }.toString(),
            classifyRefreshFailure = true,
        )
        val refreshed = response.toWebRefreshedSession(current)
        return sessionMutationMutex.withLock {
            val latest = storedSessionOrNull() ?: return@withLock null
            if (!latest.sameCredentialsAs(current)) {
                return@withLock latest.also { activeSession = it }
            }
            refreshed.persist(preferences)
            activeSession = refreshed
            refreshed
        }
    }

    private suspend fun acceptAuthenticationPayload(payload: String): AuthSession {
        val rawSession = payload.toWebAuthSession()
        val session = rawSession.copy(
            isOfficial = rawSession.isOfficial || fetchAuthenticatedProfileIsOfficial(rawSession.bearerToken, rawSession.userId),
        )
        val webSessionToken = payload.webSessionToken()
        val displayName = payload.webProfileDisplayName()
        val accepted = WebLocalSession(
            accessToken = session.accessToken ?: session.token,
            refreshToken = session.refreshToken.orEmpty(),
            webSessionToken = webSessionToken,
            userId = session.userId,
            expiresAt = session.expiresAt ?: currentEpochSeconds(),
            displayName = displayName,
            isOfficial = session.isOfficial,
        )
        sessionMutationMutex.withLock {
            session.persist(preferences, webSessionToken, displayName)
            activeSession = accepted
        }
        return session
    }

    private suspend fun retireSessionIfCurrent(expected: WebLocalSession) {
        sessionMutationMutex.withLock {
            if (storedSessionOrNull()?.sameCredentialsAs(expected) != true) return@withLock
            WebAuthStorage.clear(preferences)
            if (activeSession?.sameCredentialsAs(expected) == true) activeSession = null
        }
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

private class WebRefreshSessionRejected : IllegalStateException("web_auth_refresh_session_rejected")

internal fun WebLocalSession.sameCredentialsAs(other: WebLocalSession): Boolean =
    accessToken == other.accessToken && refreshToken == other.refreshToken &&
        webSessionToken == other.webSessionToken && userId == other.userId

internal fun webGoogleOAuthSessionStillCurrent(
    retained: WebLocalSession?,
    latest: WebLocalSession?,
): Boolean = if (retained == null) latest == null else latest?.sameCredentialsAs(retained) == true

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

private fun WebLocalSession.requiresRefresh(nowEpochSeconds: Long = currentEpochSeconds()): Boolean =
    expiresAt <= nowEpochSeconds + WebSessionRefreshLeewaySeconds

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

private fun WebRuntimeConfiguration.supabaseLogoutEndpoint(): String {
    val baseUrl = supabaseUrl?.trim()?.trimEnd('/').orEmpty()
    check(baseUrl.isNotBlank()) { "supabase_url_missing" }
    return "$baseUrl/auth/v1/logout?scope=local"
}

private fun WebRuntimeConfiguration.globalLogoutEndpoint(): String =
    supabaseUrl.requireConfigured("supabase_url_missing").trimEnd('/') + "/functions/v1/quata-auth-global-logout"

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
private const val WebBrowserUnsubscribeTimeoutMillis = 5_000L
private suspend fun webPostJson(
    endpoint: String,
    apiKey: String,
    body: String,
    accessToken: String? = null,
    webSessionToken: String? = null,
    classifyRefreshFailure: Boolean = false,
): String = suspendCancellableCoroutine { continuation ->
    val cancel = browserPostJson(
        endpoint = endpoint,
        apiKey = apiKey,
        body = body,
        accessToken = accessToken,
        webSessionToken = webSessionToken,
        classifyRefreshFailure = classifyRefreshFailure,
        onSuccess = { value -> if (continuation.isActive) continuation.resume(value) },
        onFailure = { if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException(it))) },
        onTerminalRefreshFailure = { if (continuation.isActive) continuation.resumeWith(Result.failure(WebRefreshSessionRejected())) },
    )
    continuation.invokeOnCancellation { cancel() }
}

private suspend fun webGetJson(
    endpoint: String,
    apiKey: String,
    accessToken: String,
): String = suspendCancellableCoroutine { continuation ->
    val cancel = browserGetJson(
        endpoint = endpoint,
        apiKey = apiKey,
        accessToken = accessToken,
        onSuccess = { value -> if (continuation.isActive) continuation.resume(value) },
        onFailure = { if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException(it))) },
    )
    continuation.invokeOnCancellation { cancel() }
}

private fun webSecureRandomBytes(size: Int): ByteArray {
    val hex = webSecureRandomHex(size)
    check(hex.length == size * 2) { "google_oauth_random_source_invalid" }
    return ByteArray(size) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}

private fun webGoogleOAuthRedirectUri(): String = webOAuthRedirectUri()

private suspend fun awaitWebGoogleOAuthCallback(
    popupToken: String,
    authorizationUrl: String,
    redirectUri: String,
): String = suspendCancellableCoroutine { continuation ->
        val cancel = browserGoogleOAuth(
            popupToken = popupToken,
            authorizationUrl = authorizationUrl,
            redirectUri = redirectUri,
            onSuccess = { if (continuation.isActive) continuation.resume(it) },
            onFailure = { if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException(it))) },
        )
        continuation.invokeOnCancellation { cancel() }
    }

private fun webSecureRandomHex(size: Int): String = js(
    """
    (() => {
      if (!globalThis.crypto?.getRandomValues) throw new Error('google_oauth_secure_random_unavailable');
      const bytes = new Uint8Array(size);
      globalThis.crypto.getRandomValues(bytes);
      return Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
    })()
    """,
)

private fun webOAuthRedirectUri(): String = js(
    """
    (() => globalThis.location.origin + globalThis.location.pathname)()
    """,
)

private fun reserveWebGoogleOAuthPopup(): String? = js(
    """
    (() => {
      const token = globalThis.crypto?.randomUUID?.() || `quata-${'$'}{Date.now()}-${'$'}{Math.random()}`;
      let popup = null;
      try { popup = globalThis.open('about:blank', `quata-google-oauth-${'$'}{token}`, 'popup,width=520,height=720'); }
      catch (_) { return null; }
      if (!popup) return null;
      const popups = globalThis.__quataGoogleOAuthPopups || (globalThis.__quataGoogleOAuthPopups = new Map());
      popups.set(token, popup);
      try {
        popup.sessionStorage.setItem('quata_google_oauth_channel', token);
        popup.document.title = 'Qüata';
        popup.document.body.textContent = 'Abriendo Google…';
      } catch (_) {}
      return token;
    })()
    """,
)

private fun closeWebGoogleOAuthPopup(popupToken: String): Unit = js(
    """
    (() => {
      const popups = globalThis.__quataGoogleOAuthPopups;
      const popup = popups?.get(popupToken);
      popups?.delete(popupToken);
      try { if (popup && !popup.closed) popup.close(); } catch (_) {}
    })()
    """,
)

private fun browserGoogleOAuth(
    popupToken: String,
    authorizationUrl: String,
    redirectUri: String,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit,
): () -> Unit = js(
    """
    (() => {
      const popups = globalThis.__quataGoogleOAuthPopups;
      const popup = popups?.get(popupToken);
      if (!popup || popup.closed) {
        popups?.delete(popupToken);
        onFailure('google_oauth_popup_unavailable');
        return () => {};
      }
      let settled = false;
      let timer = null;
      let detachedByCoop = false;
      const channel = typeof globalThis.BroadcastChannel === 'function'
        ? new globalThis.BroadcastChannel(`quata-google-oauth-${'$'}{popupToken}`)
        : null;
      const cleanup = (closePopup) => {
        if (timer != null) globalThis.clearInterval(timer);
        timer = null;
        try { channel?.close(); } catch (_) {}
        popups?.delete(popupToken);
        if (closePopup) { try { if (!popup.closed) popup.close(); } catch (_) {} }
      };
      const finishFailure = (reason) => {
        if (settled) return;
        settled = true;
        cleanup(true);
        onFailure(reason);
      };
      try { popup.location.replace(authorizationUrl); }
      catch (_) { finishFailure('google_oauth_navigation_failed'); return () => {}; }
      const startedAt = Date.now();
      if (channel) channel.onmessage = (event) => {
        if (settled || event?.data?.type !== 'quata:google-oauth-callback') return;
        const href = String(event.data.href || '');
        if (!href.startsWith(redirectUri + '?') && !href.startsWith(redirectUri + '#')) return;
        settled = true;
        cleanup(true);
        onSuccess(href);
      };
      timer = globalThis.setInterval(() => {
        if (Date.now() - startedAt > 180000) {
          finishFailure('google_oauth_timeout');
          return;
        }
        let canInspectLocation = true;
        try {
          const href = String(popup.location.href);
          if (href.startsWith(redirectUri + '?') || href.startsWith(redirectUri + '#')) {
            if (settled) return;
            settled = true;
            cleanup(true);
            onSuccess(href);
          }
        } catch (_) {
          canInspectLocation = false;
          // Cross-origin access is expected until Supabase returns to this origin.
        }
        let isClosed = null;
        try { isClosed = popup.closed; } catch (_) {}
        if (!canInspectLocation && isClosed === true) detachedByCoop = true;
        if (isClosed === true && canInspectLocation && !detachedByCoop) {
          finishFailure('google_oauth_popup_closed');
        }
      }, 150);
      return () => {
        if (settled) return;
        settled = true;
        cleanup(true);
      };
    })()
    """,
)

private fun browserPostJson(
    endpoint: String,
    apiKey: String,
    body: String,
    accessToken: String?,
    webSessionToken: String?,
    classifyRefreshFailure: Boolean,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit,
    onTerminalRefreshFailure: () -> Unit,
): () -> Unit = js(
    """
    (() => {
    const headers = { 'Content-Type': 'application/json', apikey: apiKey };
    if (accessToken != null && accessToken.length > 0) headers.Authorization = `Bearer ${'$'}{accessToken}`;
    if (webSessionToken != null && webSessionToken.length > 0) headers['x-quata-web-session'] = webSessionToken;
    const controller = typeof globalThis.AbortController === 'function' ? new globalThis.AbortController() : null;
    let settled = false;
    const timeoutId = globalThis.setTimeout(() => {
      try { controller?.abort(); } catch (_) {}
    }, 15000);
    globalThis.fetch(endpoint, { method: 'POST', headers, body, signal: controller?.signal })
      .then(async (response) => {
        const text = await response.text();
        if (settled) return;
        settled = true;
        if (response.ok) onSuccess(text);
        else {
          let parsed = null;
          try { parsed = JSON.parse(text); } catch (_) {}
          if (classifyRefreshFailure && (response.status === 400 || response.status === 401) &&
              (parsed?.error_code === 'refresh_token_not_found' || parsed?.error_code === 'session_not_found')) {
            onTerminalRefreshFailure();
          } else {
            const errorCode = parsed?.error;
            onFailure(errorCode ? `web_auth_${'$'}{errorCode}` : `web_auth_http_${'$'}{response.status}`);
          }
        }
      })
      .catch((error) => {
        if (settled) return;
        settled = true;
        onFailure(error?.message || error?.name || 'web_auth_network_error');
      })
      .finally(() => globalThis.clearTimeout(timeoutId));
    return () => {
      if (settled) return;
      settled = true;
      globalThis.clearTimeout(timeoutId);
      try { controller?.abort(); } catch (_) {}
    };
    })()
    """,
)

private fun browserGetJson(
    endpoint: String,
    apiKey: String,
    accessToken: String,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit,
): () -> Unit = js(
    """
    (() => {
    const headers = { apikey: apiKey, Authorization: `Bearer ${'$'}{accessToken}` };
    const controller = typeof globalThis.AbortController === 'function' ? new globalThis.AbortController() : null;
    let settled = false;
    const timeoutId = globalThis.setTimeout(() => {
      try { controller?.abort(); } catch (_) {}
    }, 15000);
    globalThis.fetch(endpoint, { method: 'GET', headers, signal: controller?.signal })
      .then(async (response) => {
        const text = await response.text();
        if (settled) return;
        settled = true;
        if (response.ok) onSuccess(text);
        else {
          let errorCode = null;
          try { errorCode = JSON.parse(text)?.code || JSON.parse(text)?.message; } catch (_) {}
          onFailure(errorCode ? `web_auth_profile_${'$'}{errorCode}` : `web_auth_profile_http_${'$'}{response.status}`);
        }
      })
      .catch((error) => {
        if (settled) return;
        settled = true;
        onFailure(error?.message || 'web_auth_profile_network_error');
      })
      .finally(() => globalThis.clearTimeout(timeoutId));
    return () => {
      if (settled) return;
      settled = true;
      globalThis.clearTimeout(timeoutId);
      try { controller?.abort(); } catch (_) {}
    };
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
