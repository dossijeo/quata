package com.quata.core.language

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Android HTTP adapter; protocol, validation and JSON remain in commonMain. */
class QuataTranslatorClient(
    baseUrl: String = FangTranslationService.DefaultBaseUrl,
    translationClient: OkHttpClient = defaultTranslationClient(),
    warmupClient: OkHttpClient = defaultWarmupClient(),
) : TextTranslator {
    private val service = FangTranslationService(baseUrl, OkHttpTranslationTransport(translationClient, warmupClient))

    suspend fun warmup() = service.warmup()
    override suspend fun translate(text: String, sourceLanguage: QuataTranslationLanguage, targetLanguage: QuataTranslationLanguage): QuataTranslationResult =
        service.translate(text, sourceLanguage, targetLanguage)
    suspend fun translate(text: String, sourceLanguage: QuataTranslationLanguage, targetLanguage: QuataTranslationLanguage, maxNewTokens: Int = FangTranslationService.DefaultMaxNewTokens, warmupFirst: Boolean = true) =
        service.translate(text, sourceLanguage, targetLanguage, maxNewTokens, warmupFirst)
    suspend fun translate(text: String, sourceLanguage: QuataDetectedLanguage, targetLanguage: QuataDetectedLanguage, maxNewTokens: Int = FangTranslationService.DefaultMaxNewTokens, warmupFirst: Boolean = true) =
        service.translate(text, sourceLanguage, targetLanguage, maxNewTokens, warmupFirst)
    suspend fun healthCheck(): Boolean = service.healthCheck()

    companion object {
        /** Compatibility aliases for Android callers while the protocol lives in core/commonMain. */
        const val DefaultMaxNewTokens: Int = FangTranslationService.DefaultMaxNewTokens
        const val MaxNewTokensLimit: Int = FangTranslationService.MaxNewTokensLimit

        fun defaultTranslationClient(): OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build()
        fun defaultWarmupClient(): OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).callTimeout(80, TimeUnit.SECONDS).build()
    }
}

object QuataTranslator { val shared: QuataTranslatorClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { QuataTranslatorClient() } }

private class OkHttpTranslationTransport(private val translationClient: OkHttpClient, private val warmupClient: OkHttpClient) : TranslationHttpTransport {
    override suspend fun get(url: String): TranslationHttpResponse = execute(translationClient, Request.Builder().url(url).get().build())
    override suspend fun post(url: String, body: String): TranslationHttpResponse {
        val client = if (url.endsWith("/warmup")) warmupClient else translationClient
        val request = Request.Builder().url(url).post(body.toRequestBody("application/json; charset=utf-8".toMediaType())).header("Content-Type", "application/json").build()
        return execute(client, request)
    }
    private suspend fun execute(client: OkHttpClient, request: Request): TranslationHttpResponse =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, exception: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            TranslationHttpResponse(it.code, it.message, it.body?.string().orEmpty())
                        }
                    }
                    result.fold(
                        onSuccess = { translatedResponse ->
                            if (continuation.isActive) continuation.resume(translatedResponse)
                        },
                        onFailure = { exception ->
                            if (continuation.isActive) continuation.resumeWithException(exception)
                        },
                    )
                }
            })
        }
}
