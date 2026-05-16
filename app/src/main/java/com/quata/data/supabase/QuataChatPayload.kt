package com.quata.data.supabase

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val CHAT_PREFIX = "__QUQOS_CHAT__"

@Serializable
data class QuataChatAttachmentPayload(
    val type: String,
    val text: String? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null
)

object QuataChatPayloadCodec {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun encode(payload: QuataChatAttachmentPayload): String = CHAT_PREFIX + json.encodeToString(payload)

    fun decodeOrNull(body: String?): QuataChatAttachmentPayload? {
        val value = body?.trim().orEmpty()
        if (!value.startsWith(CHAT_PREFIX)) return null
        return runCatching { json.decodeFromString<QuataChatAttachmentPayload>(value.removePrefix(CHAT_PREFIX)) }.getOrNull()
    }

    fun plainText(body: String?): String = decodeOrNull(body)?.text ?: body.orEmpty()
}
