package com.quata.bettermessages

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

internal suspend fun OkHttpClient.executeSuspend(request: Request): Response {
    return withContext(Dispatchers.IO) {
        newCall(request).execute()
    }
}

internal fun Response.readBodyOrThrow(): String {
    val bodyText = body?.string().orEmpty()
    if (!isSuccessful) {
        throw BetterMessagesHttpException(code, bodyText.ifBlank { message })
    }
    return bodyText
}

internal fun Request.Builder.defaultAjaxHeaders(): Request.Builder {
    return header("X-Requested-With", "XMLHttpRequest")
        .header("Accept", "application/json")
}

internal fun Request.Builder.defaultRestHeaders(
    restNonce: String? = null,
    origin: String? = null,
    referer: String? = null
): Request.Builder {
    return defaultBetterMessagesRestHeaders(
        restNonce = restNonce,
        origin = origin,
        referer = referer
    )
        .header("Content-Type", "application/json")
}

internal fun Request.Builder.defaultBetterMessagesRestHeaders(
    restNonce: String? = null,
    origin: String? = null,
    referer: String? = null
): Request.Builder {
    header("Accept", "application/json, text/plain, */*")
    header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
    header(
        "User-Agent",
        "Mozilla/5.0 (Linux; Android 13; QUATA) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
    )
    header("Cache-Control", "no-cache")
    header("Pragma", "no-cache")
    header("Expires", "0")
    if (!origin.isNullOrBlank()) header("Origin", origin)
    if (!referer.isNullOrBlank()) header("Referer", referer)
    restNonceHeader(restNonce)
    return this
}

internal fun Request.Builder.restNonceHeader(restNonce: String?): Request.Builder {
    if (!restNonce.isNullOrBlank()) {
        header("X-WP-Nonce", restNonce)
    }
    return this
}

internal fun formBody(vararg pairs: Pair<String, String?>): FormBody {
    val b = FormBody.Builder()
    pairs.forEach { (key, value) -> b.add(key, value.orEmpty()) }
    return b.build()
}

internal fun String.ensureNoTrailingSlash(): String = trimEnd('/')

internal fun String.withNoCache(): String {
    val separator = if (contains("?")) "&" else "?"
    return this + separator + "nocache=" + System.currentTimeMillis()
}

class BetterMessagesHttpException(
    val statusCode: Int,
    val responseBody: String
) : IOException("Better Messages HTTP $statusCode: ${responseBody.take(500)}")
