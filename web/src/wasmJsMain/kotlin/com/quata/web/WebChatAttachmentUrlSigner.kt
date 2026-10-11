@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.feature.chat.data.ChatAttachmentStorageAccessPolicy
import com.quata.feature.chat.data.ChatAttachmentStorageObject
import com.quata.feature.chat.data.ChatAttachmentUrlSigner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Exchanges a stable private object identity for a five-minute browser URL. */
class WebChatAttachmentUrlSigner(
    private val configuration: WebRuntimeConfiguration,
    private val authRepository: WebAuthRepository,
) : ChatAttachmentUrlSigner {
    override suspend fun sign(storageObject: ChatAttachmentStorageObject): String {
        val canonical = ChatAttachmentStorageAccessPolicy.storageObjectOrNull(
            storageObject.bucket,
            storageObject.path,
        ) ?: error("web_chat_attachment_storage_identity_invalid")
        val base = configuration.supabaseUrl?.trim()?.trimEnd('/')?.takeIf(String::isNotEmpty)
            ?: error("supabase_url_missing")
        val apiKey = configuration.supabasePublishableKey?.trim()?.takeIf(String::isNotEmpty)
            ?: error("supabase_publishable_key_missing")
        val session = authRepository.sessionForAuthenticatedRequest()
            ?: error("web_session_missing")
        val response = browserSignChatAttachment(
            endpoint = "$base/storage/v1/object/sign/${canonical.bucket}/${canonical.path}",
            apiKey = apiKey,
            accessToken = session.accessToken,
            expiresIn = ChatAttachmentStorageAccessPolicy.SignedUrlLifetimeSeconds,
        ).getOrElse { throw it }
        val returned = Json.parseToJsonElement(response).jsonObject["signedURL"]
            ?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: error("web_chat_attachment_signed_url_missing")
        val absolute = when {
            returned.startsWith("https://") -> returned
            returned.startsWith("/storage/v1/") -> "$base$returned"
            returned.startsWith("/object/") -> "$base/storage/v1$returned"
            else -> error("web_chat_attachment_signed_url_invalid")
        }
        return ChatAttachmentStorageAccessPolicy.signedUrlOrNull(base, canonical, absolute)
            ?: error("web_chat_attachment_signed_url_invalid")
    }
}

private suspend fun browserSignChatAttachment(
    endpoint: String,
    apiKey: String,
    accessToken: String,
    expiresIn: Int,
): Result<String> = suspendCoroutine { continuation ->
    browserSignChatAttachmentRequest(
        endpoint = endpoint,
        apiKey = apiKey,
        accessToken = accessToken,
        expiresIn = expiresIn,
        onSuccess = { continuation.resume(Result.success(it)) },
        onFailure = { reason, status ->
            continuation.resume(Result.failure(IllegalStateException("${reason ?: "web_chat_attachment_sign_failed"}:${status ?: 0}")))
        },
    )
}

@JsFun(
    """(endpoint, apiKey, accessToken, expiresIn, onSuccess, onFailure) => {
      if (typeof globalThis.fetch !== 'function') {
        onFailure('web_chat_attachment_sign_fetch_unsupported', null);
        return;
      }
      globalThis.fetch(endpoint, {
        method: 'POST',
        credentials: 'omit',
        cache: 'no-store',
        redirect: 'error',
        headers: {
          apikey: apiKey,
          Authorization: 'Bearer ' + accessToken,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ expiresIn }),
      }).then(async (response) => {
        const body = await response.text();
        if (!response.ok) {
          onFailure('web_chat_attachment_sign_http', response.status);
          return;
        }
        onSuccess(body);
      }).catch(() => onFailure('web_chat_attachment_sign_network', null));
    }""",
)
private external fun browserSignChatAttachmentRequest(
    endpoint: String,
    apiKey: String,
    accessToken: String,
    expiresIn: Int,
    onSuccess: (String) -> Unit,
    onFailure: (String?, Int?) -> Unit,
)
