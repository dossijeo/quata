package com.quata.feature.official.presentation

import com.quata.core.data.toFoundationData
import com.quata.core.session.IosRenewableAuthSession
import com.quata.feature.official.data.IosOfficialRuntimeConfiguration
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostLanguage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Renewable authenticated iOS client for the server-side DeepL boundary. */
@OptIn(ExperimentalForeignApi::class)
class IosOfficialTranslationService(
    private val configuration: IosOfficialRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession,
) : OfficialDraftTranslator {
    override suspend fun translate(draft: OfficialPostDraft, target: OfficialPostLanguage): Result<OfficialPostDraft> = runCatching {
        suspend fun field(value: String, html: Boolean): String {
            if (value.isBlank()) return ""
            val body = buildJsonObject {
                put("source", draft.language.remoteValue.uppercase())
                put("target", target.remoteValue.uppercase())
                put("text", value)
                if (html) put("tagHandling", "html")
            }.toString()
            val session = authSession.currentSession()?.takeIf { it.bearerToken.isNotBlank() }
                ?: error("ios_official_translation_session_missing")
            val endpoint = "${configuration.supabaseUrl.trimEnd('/')}/functions/v1/quata-official-translate"
            val request = NSMutableURLRequest.requestWithURL(NSURL(string = endpoint) ?: error("ios_official_translation_url_invalid")).apply {
                setHTTPMethod("POST")
                setValue("Bearer ${session.bearerToken}", "Authorization")
                setValue("application/json", "Content-Type")
                setValue("application/json", "Accept")
                setHTTPBody(body.encodeToByteArray().toFoundationData())
                setTimeoutInterval(25.0)
            }
            val response = request.executeOfficialTranslation().decodeToString()
            return Json.parseToJsonElement(response).jsonObject["text"]?.jsonPrimitive?.contentOrNull
                ?: error("ios_official_translation_response_invalid")
        }
        draft.copy(
            title = field(draft.title, false),
            summary = field(draft.summary, false),
            contentHtml = field(draft.contentHtml, true),
            language = target,
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun NSMutableURLRequest.executeOfficialTranslation(): ByteArray = suspendCancellableCoroutine { continuation ->
    val delegate = IosOfficialTranslationDelegate(continuation)
    val session = NSURLSession.sessionWithConfiguration(NSURLSessionConfiguration.ephemeralSessionConfiguration(), delegate, null)
    val task = session.dataTaskWithRequest(this)
    continuation.invokeOnCancellation { task.cancel(); session.invalidateAndCancel() }
    task.resume()
}

@OptIn(ExperimentalForeignApi::class)
private class IosOfficialTranslationDelegate(private val continuation: CancellableContinuation<ByteArray>) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = mutableListOf<ByteArray>()
    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (continuation.isActive) chunks += didReceiveData.bytes?.readBytes(didReceiveData.length.toInt()) ?: ByteArray(0)
    }
    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        if (didCompleteWithError != null) return continuation.resumeWithException(IllegalStateException(didCompleteWithError.localizedDescription))
        val status = (task.response as? NSHTTPURLResponse)?.statusCode?.toInt()
        val bytes = chunks.fold(ByteArray(0)) { acc, chunk -> acc + chunk }
        if (status == null || status !in 200..299) continuation.resumeWithException(IllegalStateException("ios_official_translation_http_${status ?: "unknown"}:${bytes.decodeToString().take(160)}"))
        else continuation.resume(bytes)
    }
}
