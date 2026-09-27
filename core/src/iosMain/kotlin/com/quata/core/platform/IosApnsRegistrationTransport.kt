package com.quata.core.platform

import com.quata.core.data.toFoundationData
import com.quata.core.session.IosSupabaseAuthRuntimeConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.Foundation.*
import platform.darwin.NSObject
import kotlin.coroutines.resume

/** Native authenticated RPC boundary. It neither stores credentials nor changes the session owner. */
@OptIn(ExperimentalForeignApi::class)
class IosApnsRegistrationTransport(
    private val configuration: IosSupabaseAuthRuntimeConfiguration,
) : ApnsRegistrationTransport {
    override suspend fun register(registration: ApnsRegistration): Boolean = registerResult(registration).confirmed

    override suspend fun unregister(registration: ApnsRegistration): Boolean = unregisterResult(registration).confirmed

    internal suspend fun registerResult(registration: ApnsRegistration): IosApnsTransportResult = request(
        "quata_register_apns_token", registration, includeEnvironment = true,
    )

    internal suspend fun unregisterResult(registration: ApnsRegistration): IosApnsTransportResult = request(
        "quata_unregister_push_token", registration, includeEnvironment = false,
    )

    private suspend fun request(
        rpc: String,
        registration: ApnsRegistration,
        includeEnvironment: Boolean,
    ): IosApnsTransportResult {
        val base = NSURL(string = configuration.supabaseUrl.trim().trimEnd('/'))
            ?: return IosApnsTransportResult.InvalidConfiguration
        if (base.scheme != "https" || base.host.isNullOrBlank() || base.user != null || base.password != null ||
            base.query != null || base.fragment != null || base.path.orEmpty().trim('/').isNotEmpty()
        ) return IosApnsTransportResult.InvalidConfiguration
        val bearer = registration.session.bearerToken.takeIf { it.isNotBlank() }
            ?: return IosApnsTransportResult.InvalidAuthentication
        val key = configuration.supabasePublishableKey.takeIf { it.isNotBlank() }
            ?: return IosApnsTransportResult.InvalidAuthentication
        val url = NSURL(string = "${base.absoluteString}/rest/v1/rpc/$rpc")
            ?: return IosApnsTransportResult.InvalidConfiguration
        val expectedUrl = url.absoluteString ?: return IosApnsTransportResult.InvalidConfiguration
        val payload = buildJsonObject {
            put("p_profile_id", registration.session.userId)
            put("p_token", registration.token)
            if (includeEnvironment) put("p_environment", registration.environment.wireValue)
        }
        val request = NSMutableURLRequest.requestWithURL(url).apply {
            setHTTPMethod("POST")
            setHTTPBody(payload.toString().encodeToByteArray().toFoundationData())
            setValue("Bearer $bearer", "Authorization")
            setValue(key, "apikey")
            setValue("application/json", "Content-Type")
            setValue("application/json", "Accept")
            setTimeoutInterval(10.0)
        }
        return suspendCancellableCoroutine { continuation ->
            val delegate = ApnsRpcDelegate(continuation, expectedUrl)
            val settings = NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
                timeoutIntervalForRequest = 10.0
                timeoutIntervalForResource = 15.0
            }
            val session = NSURLSession.sessionWithConfiguration(settings, delegate, null)
            val task = session.dataTaskWithRequest(request)
            continuation.invokeOnCancellation { task.cancel(); session.invalidateAndCancel() }
            task.resume()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class ApnsRpcDelegate(
    private val continuation: CancellableContinuation<IosApnsTransportResult>,
    private val expectedUrl: String,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private var bytes = ByteArray(0)
    private var terminalFailure: IosApnsTransportResult? = null

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (!continuation.isActive || terminalFailure != null) return
        if (didReceiveData.length > 16_384uL || bytes.size.toULong() + didReceiveData.length > 16_384uL) {
            terminalFailure = IosApnsTransportResult.ResponseTooLarge
            dataTask.cancel()
            return
        }
        val chunk = didReceiveData.bytes?.readBytes(didReceiveData.length.toInt()) ?: ByteArray(0)
        bytes += chunk
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit,
    ) {
        terminalFailure = IosApnsTransportResult.RedirectRejected
        completionHandler(null)
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        val response = task.response as? NSHTTPURLResponse
        val status = response?.statusCode?.toInt()
        val result = terminalFailure ?: when {
            didCompleteWithError != null -> IosApnsTransportResult.TransportFailure(didCompleteWithError.code)
            response?.URL?.absoluteString != expectedUrl -> IosApnsTransportResult.RedirectRejected
            status !in 200..299 -> IosApnsTransportResult.HttpRejected(status)
            runCatching {
                val body = Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
                (body?.get("result") as? JsonPrimitive)?.booleanOrNull == true
            }.getOrDefault(false) -> IosApnsTransportResult.Confirmed
            else -> IosApnsTransportResult.InvalidResponse
        }
        bytes = ByteArray(0)
        continuation.resume(result)
    }
}

internal sealed interface IosApnsTransportResult {
    val confirmed: Boolean get() = this === Confirmed
    val evidenceCode: String

    data object Confirmed : IosApnsTransportResult { override val evidenceCode = "confirmed" }
    data object InvalidConfiguration : IosApnsTransportResult { override val evidenceCode = "invalid_configuration" }
    data object InvalidAuthentication : IosApnsTransportResult { override val evidenceCode = "invalid_authentication" }
    data object RedirectRejected : IosApnsTransportResult { override val evidenceCode = "redirect_rejected" }
    data object ResponseTooLarge : IosApnsTransportResult { override val evidenceCode = "response_too_large" }
    data class TransportFailure(val code: Long) : IosApnsTransportResult {
        override val evidenceCode = "transport_$code"
    }
    data class HttpRejected(val status: Int?) : IosApnsTransportResult {
        override val evidenceCode = "http_${status ?: "unknown"}"
    }
    data object InvalidResponse : IosApnsTransportResult { override val evidenceCode = "invalid_response" }
}
