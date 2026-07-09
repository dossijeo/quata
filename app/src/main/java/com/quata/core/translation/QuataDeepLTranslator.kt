package com.quata.core.translation

import com.quata.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class QuataDeepLLanguage(val sourceCode: String, val targetCode: String) {
    Spanish("ES", "ES"),
    English("EN", "EN-US"),
    French("FR", "FR")
}

class QuataDeepLException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : IOException(message, cause)

class QuataDeepLTranslator(
    private val apiKey: String = BuildConfig.DEEPL_API_KEY,
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun translateText(
        text: String,
        sourceLanguage: QuataDeepLLanguage,
        targetLanguage: QuataDeepLLanguage
    ): String = withContext(Dispatchers.IO) {
        val normalized = text.trim()
        if (normalized.isBlank()) return@withContext ""
        if (sourceLanguage == targetLanguage) return@withContext normalized
        if (apiKey.isBlank()) {
            throw QuataDeepLException("DeepL API key is not configured")
        }

        val body = FormBody.Builder()
            .add("text", normalized)
            .add("source_lang", sourceLanguage.sourceCode)
            .add("target_lang", targetLanguage.targetCode)
            .build()
        val request = Request.Builder()
            .url(DeepLFreeTranslateUrl)
            .addHeader("Authorization", "DeepL-Auth-Key $apiKey")
            .post(body)
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw QuataDeepLException(
                        message = extractDeepLError(responseBody) ?: response.message.ifBlank { "DeepL translation failed" },
                        statusCode = response.code
                    )
                }
                json.decodeFromString(DeepLTranslateResponse.serializer(), responseBody)
                    .translations
                    .firstOrNull()
                    ?.text
                    ?.trim()
                    .orEmpty()
            }
        }.getOrElse { error ->
            if (error is QuataDeepLException) throw error
            throw QuataDeepLException("DeepL translation failed", cause = error)
        }
    }

    private fun extractDeepLError(responseBody: String): String? =
        runCatching { json.decodeFromString(DeepLErrorResponse.serializer(), responseBody).message }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    companion object {
        private const val DeepLFreeTranslateUrl = "https://api-free.deepl.com/v2/translate"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}

object QuataOfficialDeepLTranslator {
    val shared: QuataDeepLTranslator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        QuataDeepLTranslator()
    }
}

@Serializable
private data class DeepLTranslateResponse(
    val translations: List<DeepLTranslation>
)

@Serializable
private data class DeepLTranslation(
    @SerialName("detected_source_language") val detectedSourceLanguage: String? = null,
    val text: String
)

@Serializable
private data class DeepLErrorResponse(
    val message: String? = null
)
