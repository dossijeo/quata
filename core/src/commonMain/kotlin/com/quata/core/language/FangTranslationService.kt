package com.quata.core.language

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class TranslationHttpResponse(val statusCode: Int, val message: String, val body: String)

interface TranslationHttpTransport {
    suspend fun get(url: String): TranslationHttpResponse
    suspend fun post(url: String, body: String): TranslationHttpResponse
}

class QuataTranslationException(message: String, val statusCode: Int? = null, cause: Throwable? = null) : Exception(message, cause)

class FangTranslationService(
    baseUrl: String = DefaultBaseUrl,
    private val transport: TranslationHttpTransport,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true },
) : TextTranslator {
    private val rootUrl = baseUrl.trimEnd('/')
    private val warmupMutex = Mutex()
    private var warmedUp = false

    suspend fun warmup() = warmupMutex.withLock {
        if (warmedUp) return@withLock
        post("/warmup", "", "No se pudo calentar el traductor")
        warmedUp = true
    }

    suspend fun translate(text: String, sourceLanguage: QuataDetectedLanguage, targetLanguage: QuataDetectedLanguage, maxNewTokens: Int = DefaultMaxNewTokens, warmupFirst: Boolean = true): QuataTranslationResult {
        val source = QuataTranslationLanguage.fromDetectedLanguage(sourceLanguage) ?: throw IllegalArgumentException("Idioma origen no soportado: ${sourceLanguage.code}")
        val target = QuataTranslationLanguage.fromDetectedLanguage(targetLanguage) ?: throw IllegalArgumentException("Idioma destino no soportado: ${targetLanguage.code}")
        return translate(text, source, target, maxNewTokens, warmupFirst)
    }

    suspend fun translate(text: String, sourceLanguage: QuataTranslationLanguage, targetLanguage: QuataTranslationLanguage, maxNewTokens: Int = DefaultMaxNewTokens, warmupFirst: Boolean = true): QuataTranslationResult {
        require(text.isNotBlank()) { "El texto a traducir no puede estar vacio" }
        require(maxNewTokens in 1..MaxNewTokensLimit) { "maxNewTokens debe estar entre 1 y $MaxNewTokensLimit" }
        if (warmupFirst) warmup()
        val request = FangTranslationRequest(text, sourceLanguage.apiCode, targetLanguage.apiCode, maxNewTokens)
        val body = post("/translate", json.encodeToString(FangTranslationRequest.serializer(), request), "No se pudo traducir el texto")
        return json.decodeFromString(FangTranslationResponse.serializer(), body).toDomain()
    }

    override suspend fun translate(text: String, sourceLanguage: QuataTranslationLanguage, targetLanguage: QuataTranslationLanguage): QuataTranslationResult =
        translate(text, sourceLanguage, targetLanguage, DefaultMaxNewTokens, warmupFirst = true)

    suspend fun healthCheck(): Boolean = runCatching { transport.get(rootUrl).statusCode in 200..299 }.getOrDefault(false)

    private suspend fun post(path: String, body: String, fallback: String): String {
        val response = try { transport.post(rootUrl + path, body) } catch (error: Throwable) { throw QuataTranslationException(fallback, cause = error) }
        if (response.statusCode !in 200..299) {
            val detail = runCatching { json.decodeFromString(FangTranslationError.serializer(), response.body).detail }.getOrNull()
            throw QuataTranslationException(detail?.takeIf { it.isNotBlank() } ?: response.message.ifBlank { fallback }, response.statusCode)
        }
        return response.body
    }

    companion object { const val DefaultBaseUrl = "https://dossijeo-nllb-fang-quata.hf.space"; const val DefaultMaxNewTokens = 64; const val MaxNewTokensLimit = 256 }
}

@Serializable private data class FangTranslationRequest(val text: String, @SerialName("src_lang") val sourceLanguage: String, @SerialName("tgt_lang") val targetLanguage: String, @SerialName("max_new_tokens") val maxNewTokens: Int)
@Serializable private data class FangTranslationResponse(val translation: String, @SerialName("pivot_used") val pivotUsed: Boolean = false, @SerialName("pivot_lang") val pivotLanguage: String? = null, @SerialName("pivot_text") val pivotText: String? = null, @SerialName("pivot_engine") val pivotEngine: String? = null, val route: List<String> = emptyList()) { fun toDomain() = QuataTranslationResult(translation, pivotUsed, route.mapNotNull(QuataTranslationLanguage::fromApiCode), pivotLanguage?.let(QuataTranslationLanguage::fromApiCode), pivotText, pivotEngine) }
@Serializable private data class FangTranslationError(val detail: String? = null)
