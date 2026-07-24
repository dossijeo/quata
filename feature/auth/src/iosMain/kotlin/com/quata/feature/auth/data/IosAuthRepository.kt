package com.quata.feature.auth.data

import com.quata.core.model.AuthSession
import com.quata.core.model.currentEpochSeconds
import com.quata.core.session.IosAuthSessionRefresher
import com.quata.core.session.IosRenewableAuthSession
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.domain.PasswordRecoveryQuestion
import com.quata.feature.auth.domain.RegisterAccountRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import platform.CoreFoundation.CFDataCreate
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject

/**
 * Client-safe configuration supplied by the iOS launcher or its build settings. A service-role
 * key must never cross this boundary: only the public Supabase URL and publishable key are used.
 */
data class IosAuthRuntimeConfiguration(
    val supabaseUrl: String,
    val supabasePublishableKey: String,
)

/** Small injectable URLSession boundary so host tests can exercise auth parsing without a network. */
fun interface IosAuthHttpTransport {
    suspend fun post(endpoint: String, headers: Map<String, String>, body: String): IosAuthHttpResponse
}

data class IosAuthHttpResponse(
    val statusCode: Int,
    val body: String,
)

/** Explicit composition input for the iOS launcher; it does not retain credentials itself. */
class IosAuthRepositoryDependencies(
    val configuration: IosAuthRuntimeConfiguration,
    val session: IosRenewableAuthSession,
    val transport: IosAuthHttpTransport = IosUrlSessionAuthHttpTransport(),
)

fun iosAuthRepository(dependencies: IosAuthRepositoryDependencies): AuthRepository = IosAuthRepository(
    configuration = dependencies.configuration,
    session = dependencies.session,
    transport = dependencies.transport,
)

/**
 * Real iOS implementation of the public Auth bridge protocol. It deliberately uses the same
 * unauthenticated bridge actions as Android (`login`, recovery and reset), not the Web Push-only
 * `web_login` action. Registration has no public backend endpoint yet, so it remains explicit.
 */
class IosAuthRepository(
    private val configuration: IosAuthRuntimeConfiguration,
    private val session: IosRenewableAuthSession,
    private val transport: IosAuthHttpTransport = IosUrlSessionAuthHttpTransport(),
) : AuthRepository {
    override suspend fun login(countryCode: String, phone: String, password: String): Result<AuthSession> = runCatching {
        require(password.isNotBlank()) { "ios_auth_password_required" }
        val payload = postPublic(
            endpoint = configuration.authBridgeEndpoint(),
            body = buildJsonObject {
                put("action", "login")
                put("country_code", countryCode.digitsOrThrow("ios_auth_country_code_required"))
                put("phone", phone.digitsOrThrow("ios_auth_phone_required"))
                put("password", password)
            }.toString(),
        )
        payload.toIosAuthSession().also(session::save)
    }

    override suspend fun register(request: RegisterAccountRequest): Result<AuthSession> =
        Result.failure(UnsupportedOperationException("ios_auth_register_not_implemented"))

    override suspend fun getPasswordRecoveryQuestion(
        countryCode: String,
        phone: String,
    ): Result<PasswordRecoveryQuestion?> = runCatching {
        val response = postPublic(
            endpoint = configuration.authBridgeEndpoint(),
            body = buildJsonObject {
                put("action", "recovery_question")
                put("country_code", countryCode.digitsOrThrow("ios_auth_country_code_required"))
                put("phone", phone.digitsOrThrow("ios_auth_phone_required"))
            }.toString(),
        )
        Json.parseToJsonElement(response).jsonObject["secret_question"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let(::PasswordRecoveryQuestion)
    }

    override suspend fun resetPassword(
        countryCode: String,
        phone: String,
        secretAnswer: String,
        newPassword: String,
    ): Result<Unit> = runCatching {
        require(secretAnswer.isNotBlank()) { "ios_auth_secret_answer_required" }
        require(newPassword.length >= 6) { "ios_auth_new_password_invalid" }
        postPublic(
            endpoint = configuration.authBridgeEndpoint(),
            body = buildJsonObject {
                put("action", "reset_password")
                put("country_code", countryCode.digitsOrThrow("ios_auth_country_code_required"))
                put("phone", phone.digitsOrThrow("ios_auth_phone_required"))
                put("secret_answer", secretAnswer.trim())
                put("new_password", newPassword)
            }.toString(),
        )
        Unit
    }

    override suspend fun deactivateAccount(password: String): Result<Unit> =
        performLifecycle("deactivate", password)

    override suspend fun deleteAccountData(password: String): Result<Unit> =
        performLifecycle("delete", password)

    /** The local Keychain session is always cleared, even when remote Supabase logout is offline. */
    override suspend fun logout() {
        runCatching {
            session.currentSession()?.let { active ->
                post(
                    endpoint = configuration.supabaseLogoutEndpoint(),
                    accessToken = active.bearerToken,
                    body = "{}",
                )
            }
        }
        session.clear()
    }

    private suspend fun performLifecycle(action: String, password: String): Result<Unit> = runCatching {
        require(password.isNotBlank()) { "ios_auth_password_required" }
        val active = session.currentSession() ?: error("ios_auth_session_required")
        val response = post(
            endpoint = configuration.accountLifecycleEndpoint(),
            accessToken = active.bearerToken,
            body = buildJsonObject {
                put("action", action)
                put("password", password)
            }.toString(),
        )
        check(Json.parseToJsonElement(response).jsonObject["ok"]?.jsonPrimitive?.booleanOrNull == true) {
            "ios_auth_lifecycle_failed"
        }
        session.clear()
    }

    private suspend fun postPublic(endpoint: String, body: String): String = post(endpoint, null, body)

    private suspend fun post(endpoint: String, accessToken: String?, body: String): String {
        val response = transport.post(
            endpoint = endpoint,
            headers = buildMap {
                put("Content-Type", "application/json")
                put("Accept", "application/json")
                put("apikey", configuration.publishableKey())
                accessToken?.takeIf(String::isNotBlank)?.let { put("Authorization", "Bearer $it") }
            },
            body = body,
        )
        if (response.statusCode !in 200..299) throw IosAuthHttpException(response.statusCode, response.body.errorCode())
        return response.body
    }
}

/**
 * Creates the refresher consumed by [IosRenewableAuthSession]. Keeping it separate from the
 * repository avoids a construction cycle and lets the Swift launcher choose one Keychain owner.
 */
fun iosSupabaseSessionRefresher(
    configuration: IosAuthRuntimeConfiguration,
    transport: IosAuthHttpTransport = IosUrlSessionAuthHttpTransport(),
): IosAuthSessionRefresher = IosAuthSessionRefresher { current ->
    runCatching {
        val key = configuration.publishableKey()
        val response = transport.post(
            endpoint = configuration.supabaseRefreshEndpoint(),
            headers = mapOf(
                "Content-Type" to "application/json",
                "Accept" to "application/json",
                "apikey" to key,
            ),
            body = buildJsonObject { put("refresh_token", current.refreshToken.orEmpty()) }.toString(),
        )
        response.takeIf { it.statusCode in 200..299 }
            ?.body
            ?.toIosRefreshedSession(current)
    }.getOrNull()
}

/** URLSession transport used by production iOS hosts. */
@OptIn(ExperimentalForeignApi::class)
class IosUrlSessionAuthHttpTransport : IosAuthHttpTransport {
    override suspend fun post(endpoint: String, headers: Map<String, String>, body: String): IosAuthHttpResponse {
        val url = NSURL(string = endpoint) ?: error("ios_auth_url_invalid")
        val request = NSMutableURLRequest.requestWithURL(url).apply {
            HTTPMethod = "POST"
            HTTPBody = body.encodeToByteArray().toIosAuthData()
            headers.forEach { (name, value) -> setValue(value, forHTTPHeaderField = name) }
        }
        return NSURLSessionConfiguration.ephemeralSessionConfiguration().iosAuthData(request)
    }
}

class IosAuthHttpException(statusCode: Int, backendCode: String?) : IllegalStateException(
    "ios_auth_${backendCode?.takeIf(String::isNotBlank) ?: "http_$statusCode"}",
)

@OptIn(ExperimentalForeignApi::class)
private suspend fun NSURLSessionConfiguration.iosAuthData(request: NSMutableURLRequest): IosAuthHttpResponse =
    suspendCancellableCoroutine { continuation ->
        val delegate = IosAuthDataTaskDelegate(continuation)
        val session = NSURLSession.sessionWithConfiguration(this, delegate, null)
        val task = session.dataTaskWithRequest(request)
        continuation.invokeOnCancellation {
            task.cancel()
            session.invalidateAndCancel()
        }
        task.resume()
    }

@OptIn(ExperimentalForeignApi::class)
private class IosAuthDataTaskDelegate(
    private val continuation: CancellableContinuation<IosAuthHttpResponse>,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = mutableListOf<ByteArray>()

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (continuation.isActive) chunks += didReceiveData.toIosAuthBytes()
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        if (didCompleteWithError != null) {
            continuation.resumeWithException(IllegalStateException(didCompleteWithError.localizedDescription))
            return
        }
        val status = (task.response as? NSHTTPURLResponse)?.statusCode?.toInt()
            ?: run {
                continuation.resumeWithException(IllegalStateException("ios_auth_http_unknown"))
                return
            }
        continuation.resume(IosAuthHttpResponse(status, chunks.toIosAuthString()))
    }
}

private fun IosAuthRuntimeConfiguration.baseUrl(): String = supabaseUrl.trim().trimEnd('/')
    .takeIf(String::isNotBlank) ?: error("ios_auth_supabase_url_missing")
private fun IosAuthRuntimeConfiguration.publishableKey(): String = supabasePublishableKey.trim()
    .takeIf(String::isNotBlank) ?: error("ios_auth_supabase_publishable_key_missing")
private fun IosAuthRuntimeConfiguration.authBridgeEndpoint(): String = "${baseUrl()}/functions/v1/quata-auth-bridge"
private fun IosAuthRuntimeConfiguration.accountLifecycleEndpoint(): String = "${baseUrl()}/functions/v1/quata-account-lifecycle"
private fun IosAuthRuntimeConfiguration.supabaseRefreshEndpoint(): String = "${baseUrl()}/auth/v1/token?grant_type=refresh_token"
private fun IosAuthRuntimeConfiguration.supabaseLogoutEndpoint(): String = "${baseUrl()}/auth/v1/logout"

private fun String.digitsOrThrow(error: String): String = filter(Char::isDigit).takeIf(String::isNotBlank) ?: throw IllegalArgumentException(error)

private fun String.errorCode(): String? = runCatching {
    Json.parseToJsonElement(this).jsonObject["error"]?.jsonPrimitive?.contentOrNull
}.getOrNull()

private fun String.toIosAuthSession(): AuthSession {
    val root = Json.parseToJsonElement(this).jsonObject
    val session = root.requiredIosObject("session")
    val profile = root.requiredIosObject("profile")
    val user = root["user"]?.jsonObject
    val accessToken = session.requiredIosString("access_token")
    val refreshToken = session.requiredIosString("refresh_token")
    val expiresAt = session["expires_at"]?.jsonPrimitive?.longOrNull
        ?: session["expires_in"]?.jsonPrimitive?.longOrNull?.let { currentEpochSeconds() + it }
    return AuthSession(
        token = accessToken,
        userId = profile.requiredIosString("id"),
        authUserId = profile.iosString("auth_user_id") ?: user?.iosString("id"),
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = expiresAt,
        email = user?.iosString("email") ?: "${profile.iosString("country_code").orEmpty().filter(Char::isDigit)}${profile.iosString("phone_local").orEmpty().filter(Char::isDigit)}@phone.quata.app",
        displayName = profile.iosString("display_name") ?: profile.iosString("phone_local") ?: "Usuario",
    )
}

private fun String.toIosRefreshedSession(current: AuthSession): AuthSession {
    val root = Json.parseToJsonElement(this).jsonObject
    val accessToken = root.requiredIosString("access_token")
    val refreshToken = root.requiredIosString("refresh_token")
    val expiresAt = root["expires_at"]?.jsonPrimitive?.longOrNull
        ?: root["expires_in"]?.jsonPrimitive?.longOrNull?.let { currentEpochSeconds() + it }
        ?: error("ios_auth_refresh_missing_expiry")
    return current.copy(token = accessToken, accessToken = accessToken, refreshToken = refreshToken, expiresAt = expiresAt)
}

private fun JsonObject.requiredIosObject(name: String): JsonObject = this[name]?.jsonObject
    ?: error("ios_auth_response_missing_$name")
private fun JsonObject.requiredIosString(name: String): String = iosString(name)
    ?: error("ios_auth_response_missing_$name")
private fun JsonObject.iosString(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toIosAuthData(): NSData = usePinned { pinned ->
    CFDataCreate(null, pinned.addressOf(0).reinterpret(), size.toLong())!! as NSData
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toIosAuthBytes(): ByteArray =
    if (length == 0uL) ByteArray(0) else bytes?.readBytes(length.toInt()) ?: ByteArray(0)

private fun List<ByteArray>.toIosAuthString(): String {
    val size = sumOf(ByteArray::size)
    if (size == 0) return ""
    val merged = ByteArray(size)
    var offset = 0
    forEach { bytes ->
        bytes.copyInto(merged, destinationOffset = offset)
        offset += bytes.size
    }
    return merged.decodeToString()
}
