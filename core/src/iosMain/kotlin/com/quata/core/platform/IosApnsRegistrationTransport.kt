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
    override suspend fun register(registration: ApnsRegistration): Boolean = request(
        "quata_register_apns_token", registration, includeEnvironment = true,
    )

    override suspend fun unregister(registration: ApnsRegistration): Boolean = request(
        "quata_unregister_push_token", registration, includeEnvironment = false,
    )

    private suspend fun request(rpc: String, registration: ApnsRegistration, includeEnvironment: Boolean): Boolean {
        val base = NSURL(string = configuration.supabaseUrl.trim().trimEnd('/')) ?: return false
        if (base.scheme != "https" || base.host.isNullOrBlank() || base.user != null || base.password != null ||
            base.query != null || base.fragment != null || base.path.orEmpty().trim('/').isNotEmpty()) return false
        val bearer = registration.session.bearerToken.takeIf { it.isNotBlank() } ?: return false
        val key = configuration.supabasePublishableKey.takeIf { it.isNotBlank() } ?: return false
        val url = NSURL(string = "${base.absoluteString}/rest/v1/rpc/$rpc") ?: return false
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
            val delegate = ApnsRpcDelegate(continuation)
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
private class ApnsRpcDelegate(private val continuation: CancellableContinuation<Boolean>) :
    NSObject(), NSURLSessionDataDelegateProtocol {
    private var bytes = ByteArray(0)
    private var rejected = false

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (!continuation.isActive || rejected) return
        if (didReceiveData.length > 16_384uL || bytes.size.toULong() + didReceiveData.length > 16_384uL) {
            rejected = true
            dataTask.cancel()
            return
        }
        val chunk = didReceiveData.bytes?.readBytes(didReceiveData.length.toInt()) ?: ByteArray(0)
        bytes += chunk
    }

    override fun URLSession(
        session: NSURLSession, task: NSURLSessionTask, willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest, completionHandler: (NSURLRequest?) -> Unit,
    ) {
        rejected = true
        completionHandler(null)
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        val status = (task.response as? NSHTTPURLResponse)?.statusCode?.toInt()
        val confirmed = !rejected && didCompleteWithError == null && status in 200..299 && runCatching {
            val result = Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
            (result?.get("result") as? JsonPrimitive)?.booleanOrNull == true
        }.getOrDefault(false)
        bytes = ByteArray(0)
        continuation.resume(confirmed)
    }
}
