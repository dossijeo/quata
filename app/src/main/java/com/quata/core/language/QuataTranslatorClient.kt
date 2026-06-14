package com.quata.core.language

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class QuataTranslationLanguage(val apiCode: String) {
    Fang("fan_Latn"),
    Spanish("spa_Latn"),
    English("eng_Latn"),
    French("fra_Latn");

    companion object {
        fun fromDetectedLanguage(language: QuataDetectedLanguage): QuataTranslationLanguage? = when (language) {
            QuataDetectedLanguage.Fang -> Fang
            QuataDetectedLanguage.Spanish -> Spanish
            QuataDetectedLanguage.English -> English
            QuataDetectedLanguage.French -> French
            QuataDetectedLanguage.Unknown -> null
        }

        fun fromApiCode(apiCode: String): QuataTranslationLanguage? = entries.firstOrNull {
            it.apiCode.equals(apiCode, ignoreCase = true)
        }
    }
}

data class QuataTranslationResult(
    val translation: String,
    val pivotUsed: Boolean,
    val route: List<QuataTranslationLanguage>,
    val pivotLanguage: QuataTranslationLanguage?,
    val pivotText: String?,
    val pivotEngine: String?
)

class QuataTranslationException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : IOException(message, cause)

class QuataTranslatorClient(
    baseUrl: String = DefaultBaseUrl,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true },
    private val translationClient: OkHttpClient = defaultTranslationClient(),
    private val warmupClient: OkHttpClient = defaultWarmupClient()
) {
    private val rootUrl = baseUrl.trimEnd('/')
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val warmupMutex = Mutex()
    @Volatile
    private var warmedUp = false

    suspend fun warmup() {
        warmupMutex.withLock {
            if (warmedUp) return
            post(
                client = warmupClient,
                path = "/warmup",
                body = "",
                timeoutMessage = "No se pudo calentar el traductor"
            )
            warmedUp = true
        }
    }

    suspend fun translate(
        text: String,
        sourceLanguage: QuataTranslationLanguage,
        targetLanguage: QuataTranslationLanguage,
        maxNewTokens: Int = DefaultMaxNewTokens,
        warmupFirst: Boolean = true
    ): QuataTranslationResult {
        require(text.isNotBlank()) { "El texto a traducir no puede estar vacio" }
        require(maxNewTokens in 1..MaxNewTokensLimit) { "maxNewTokens debe estar entre 1 y $MaxNewTokensLimit" }
        if (warmupFirst) warmup()

        val request = TranslationRequest(
            text = text,
            sourceLanguage = sourceLanguage.apiCode,
            targetLanguage = targetLanguage.apiCode,
            maxNewTokens = maxNewTokens
        )
        val responseBody = post(
            client = translationClient,
            path = "/translate",
            body = json.encodeToString(TranslationRequest.serializer(), request),
            timeoutMessage = "No se pudo traducir el texto"
        )
        return json.decodeFromString(TranslationResponse.serializer(), responseBody).toDomain()
    }

    suspend fun translate(
        text: String,
        sourceLanguage: QuataDetectedLanguage,
        targetLanguage: QuataDetectedLanguage,
        maxNewTokens: Int = DefaultMaxNewTokens,
        warmupFirst: Boolean = true
    ): QuataTranslationResult {
        val source = QuataTranslationLanguage.fromDetectedLanguage(sourceLanguage)
            ?: throw IllegalArgumentException("Idioma origen no soportado: ${sourceLanguage.code}")
        val target = QuataTranslationLanguage.fromDetectedLanguage(targetLanguage)
            ?: throw IllegalArgumentException("Idioma destino no soportado: ${targetLanguage.code}")
        return translate(text, source, target, maxNewTokens, warmupFirst)
    }

    suspend fun healthCheck(): Boolean =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(rootUrl).get().build()
            runCatching {
                translationClient.newCall(request).execute().use { response -> response.isSuccessful }
            }.getOrDefault(false)
        }

    private suspend fun post(
        client: OkHttpClient,
        path: String,
        body: String,
        timeoutMessage: String
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(rootUrl + path)
            .post(body.toRequestBody(mediaType))
            .header("Content-Type", "application/json")
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw QuataTranslationException(
                        message = extractErrorMessage(responseBody) ?: response.message.ifBlank { timeoutMessage },
                        statusCode = response.code
                    )
                }
                responseBody
            }
        }.getOrElse { throwable ->
            if (throwable is QuataTranslationException) throw throwable
            throw QuataTranslationException(timeoutMessage, cause = throwable)
        }
    }

    private fun extractErrorMessage(responseBody: String): String? =
        runCatching {
            json.decodeFromString(ErrorResponse.serializer(), responseBody).detail
        }.getOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        const val DefaultBaseUrl = "https://dossijeo-nllb-fang-quata.hf.space"
        const val DefaultMaxNewTokens = 64
        const val MaxNewTokensLimit = 256

        fun defaultTranslationClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()

        fun defaultWarmupClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(75, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(80, TimeUnit.SECONDS)
            .build()
    }
}

object QuataTranslator {
    val shared: QuataTranslatorClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        QuataTranslatorClient()
    }
}

@Serializable
private data class TranslationRequest(
    val text: String,
    @SerialName("src_lang") val sourceLanguage: String,
    @SerialName("tgt_lang") val targetLanguage: String,
    @SerialName("max_new_tokens") val maxNewTokens: Int = QuataTranslatorClient.DefaultMaxNewTokens
)

@Serializable
private data class TranslationResponse(
    val translation: String,
    @SerialName("pivot_used") val pivotUsed: Boolean = false,
    @SerialName("pivot_lang") val pivotLanguage: String? = null,
    @SerialName("pivot_text") val pivotText: String? = null,
    @SerialName("pivot_engine") val pivotEngine: String? = null,
    val route: List<String> = emptyList()
) {
    fun toDomain(): QuataTranslationResult =
        QuataTranslationResult(
            translation = translation,
            pivotUsed = pivotUsed,
            route = route.mapNotNull(QuataTranslationLanguage::fromApiCode),
            pivotLanguage = pivotLanguage?.let(QuataTranslationLanguage::fromApiCode),
            pivotText = pivotText,
            pivotEngine = pivotEngine
        )
}

@Serializable
private data class ErrorResponse(
    val detail: String? = null
)
