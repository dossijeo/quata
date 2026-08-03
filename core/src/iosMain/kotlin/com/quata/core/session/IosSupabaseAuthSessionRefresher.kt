package com.quata.core.session

import com.quata.core.model.AuthSession
import com.quata.core.model.currentEpochSeconds
import com.quata.core.data.toFoundationData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Client-safe Supabase deployment settings for the iOS auth boundary. */
data class IosSupabaseAuthRuntimeConfiguration(
    val supabaseUrl: String,
    val supabasePublishableKey: String,
)

/**
 * Native URLSession implementation of the standard Supabase refresh-token exchange.
 * Interactive login remains a feature-level flow: this class only renews an existing Keychain
 * session and never manufactures an identity.
 */
@OptIn(ExperimentalForeignApi::class)
class IosSupabaseAuthSessionRefresher(
    private val configuration: IosSupabaseAuthRuntimeConfiguration,
) : IosAuthSessionRefresher {
    override suspend fun refresh(session: AuthSession): AuthSession? = runCatching {
        val refreshToken = session.refreshToken?.takeIf(String::isNotBlank)
            ?: error("ios_auth_refresh_token_missing")
        val baseUrl = configuration.supabaseUrl.trim().trimEnd('/').takeIf(String::isNotEmpty)
            ?: error("ios_auth_supabase_url_missing")
        val publishableKey = configuration.supabasePublishableKey.trim().takeIf(String::isNotEmpty)
            ?: error("ios_auth_supabase_publishable_key_missing")
        val endpoint = NSURL(string = "$baseUrl/auth/v1/token?grant_type=refresh_token")
            ?: error("ios_auth_refresh_url_invalid")
        // Kotlin/Native's Foundation binding exposes the mutable request through this factory
        // (rather than the Objective-C initializer), with positional header arguments.
        val request = NSMutableURLRequest.requestWithURL(endpoint).apply {
            setHTTPMethod("POST")
            setHTTPBody("{\"refresh_token\":${refreshToken.toIosJsonString()}}".toIosData())
            setValue(publishableKey, "apikey")
            setValue("Bearer $publishableKey", "Authorization")
            setValue("application/json", "Accept")
            setValue("application/json", "Content-Type")
        }
        request.iosData().toRefreshedAuthSession(session)
    }.getOrNull()
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun NSURLRequest.iosData(): NSData = suspendCancellableCoroutine { continuation ->
    val delegate = IosAuthDataTaskDelegate(continuation)
    val session = NSURLSession.sessionWithConfiguration(
        NSURLSessionConfiguration.ephemeralSessionConfiguration(), delegate, null,
    )
    val task = session.dataTaskWithRequest(this)
    continuation.invokeOnCancellation {
        task.cancel()
        session.invalidateAndCancel()
    }
    task.resume()
}

@OptIn(ExperimentalForeignApi::class)
private class IosAuthDataTaskDelegate(
    private val continuation: CancellableContinuation<NSData>,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = mutableListOf<ByteArray>()

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (continuation.isActive) chunks += didReceiveData.toIosByteArray()
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        if (didCompleteWithError != null) {
            continuation.resumeWithException(IllegalStateException(didCompleteWithError.localizedDescription))
            return
        }
        val status = (task.response as? NSHTTPURLResponse)?.statusCode?.toInt()
        if (status == null || status !in 200..299) {
            continuation.resumeWithException(IllegalStateException("ios_auth_refresh_http_${status ?: "unknown"}"))
            return
        }
        continuation.resume(chunks.toIosDataOrNull() ?: NSData())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toIosByteArray(): ByteArray =
    if (length == 0uL) ByteArray(0) else bytes?.readBytes(length.toInt()) ?: ByteArray(0)

@OptIn(ExperimentalForeignApi::class)
private fun List<ByteArray>.toIosDataOrNull(): NSData? {
    if (all(ByteArray::isEmpty)) return null
    return toFoundationData()
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toIosData(): NSData = encodeToByteArray().toFoundationData()

internal fun String.toIosJsonString(): String = buildString(length + 2) {
    append('"')
    this@toIosJsonString.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) {
                append("\\u")
                append(character.code.toString(16).padStart(4, '0'))
            } else append(character)
        }
    }
    append('"')
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toRefreshedAuthSession(current: AuthSession): AuthSession {
    val root = NSJSONSerialization.JSONObjectWithData(this, options = 0u, error = null) as? Map<*, *>
        ?: error("ios_auth_refresh_response_invalid")
    val accessToken = root["access_token"]?.toString()?.takeIf(String::isNotBlank)
        ?: error("ios_auth_refresh_access_token_missing")
    val refreshToken = root["refresh_token"]?.toString()?.takeIf(String::isNotBlank)
        ?: error("ios_auth_refresh_refresh_token_missing")
    val expiresAt = root["expires_at"].toIosLongOrNull()
        ?: root["expires_in"].toIosLongOrNull()?.let { currentEpochSeconds() + it }
    return current.copy(token = accessToken, accessToken = accessToken, refreshToken = refreshToken, expiresAt = expiresAt)
}

private fun Any?.toIosLongOrNull(): Long? = when (this) {
    is Number -> toLong()
    else -> toString().toLongOrNull()
}
