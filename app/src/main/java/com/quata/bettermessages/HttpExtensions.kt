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

internal fun Request.Builder.defaultRestHeaders(): Request.Builder {
    return header("Accept", "application/json")
        .header("Content-Type", "application/json")
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
