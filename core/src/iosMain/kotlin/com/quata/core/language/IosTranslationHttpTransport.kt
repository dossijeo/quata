package com.quata.core.language

import com.quata.core.data.toFoundationData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.create
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Ephemeral URLSession boundary for the public Fang translation endpoint. */
@OptIn(ExperimentalForeignApi::class)
class IosTranslationHttpTransport : TranslationHttpTransport {
    override suspend fun get(url: String): TranslationHttpResponse = request("GET", url, null)

    override suspend fun post(url: String, body: String): TranslationHttpResponse = request("POST", url, body)

    private suspend fun request(method: String, value: String, body: String?): TranslationHttpResponse =
        suspendCancellableCoroutine { continuation ->
            val url = NSURL(string = value) ?: run {
                continuation.resumeWithException(IllegalArgumentException("ios_translation_url_invalid"))
                return@suspendCancellableCoroutine
            }
            val request = NSMutableURLRequest.requestWithURL(url).apply {
                setHTTPMethod(method)
                setValue("application/json", "Accept")
                body?.let {
                    setValue("application/json", "Content-Type")
                    setHTTPBody(it.encodeToByteArray().toFoundationData())
                }
            }
            val delegate = IosTranslationDelegate(
                success = { response -> if (continuation.isActive) continuation.resume(response) },
                failure = { error -> if (continuation.isActive) continuation.resumeWithException(error) },
            )
            val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
                timeoutIntervalForRequest = 90.0
                timeoutIntervalForResource = 120.0
            }
            val session = NSURLSession.sessionWithConfiguration(configuration, delegate, null)
            val task = session.dataTaskWithRequest(request)
            continuation.invokeOnCancellation {
                task.cancel()
                session.invalidateAndCancel()
            }
            task.resume()
        }
}

@OptIn(ExperimentalForeignApi::class)
private class IosTranslationDelegate(
    private val success: (TranslationHttpResponse) -> Unit,
    private val failure: (Throwable) -> Unit,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = mutableListOf<ByteArray>()

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (didReceiveData.length > 0uL) {
            chunks += didReceiveData.bytes?.readBytes(didReceiveData.length.toInt()).orEmpty()
        }
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (didCompleteWithError != null) {
            failure(IllegalStateException(didCompleteWithError.localizedDescription))
            return
        }
        val response = task.response as? NSHTTPURLResponse
        val body = chunks.fold(ByteArray(0)) { accumulated, chunk -> accumulated + chunk }.decodeToString()
        success(
            TranslationHttpResponse(
                statusCode = response?.statusCode?.toInt() ?: 0,
                message = NSHTTPURLResponse.localizedStringForStatusCode(response?.statusCode?.toLong() ?: 0L),
                body = body,
            ),
        )
    }
}
