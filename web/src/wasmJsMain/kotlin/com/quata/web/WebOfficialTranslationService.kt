@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.presentation.OfficialDraftTranslator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Authenticated browser client for the server-side DeepL proxy. */
class WebOfficialTranslationService(
    private val configuration: WebRuntimeConfiguration,
    private val auth: WebAuthRepository,
) : OfficialDraftTranslator {
    override suspend fun translate(draft: OfficialPostDraft, target: OfficialPostLanguage): Result<OfficialPostDraft> = runCatching {
        val session = auth.sessionForAuthenticatedRequest() ?: error("web_official_translation_session_missing")
        val endpoint = configuration.supabaseUrl?.trimEnd('/')?.plus("/functions/v1/quata-official-translate")
            ?: error("web_official_translation_endpoint_missing")
        suspend fun translateField(value: String, html: Boolean): String {
            if (value.isBlank()) return ""
            val payload = buildJsonObject {
                put("source", draft.language.remoteValue.uppercase())
                put("target", target.remoteValue.uppercase())
                put("text", value)
                if (html) put("tagHandling", "html")
            }.toString()
            val response = webOfficialTranslateRequest(endpoint, session.accessToken, payload)
            return Json.parseToJsonElement(response).jsonObject["text"]?.jsonPrimitive?.contentOrNull
                ?: error("web_official_translation_response_invalid")
        }
        draft.copy(
            title = translateField(draft.title, false),
            summary = translateField(draft.summary, false),
            contentHtml = translateField(draft.contentHtml, true),
            language = target,
        )
    }
}

private suspend fun webOfficialTranslateRequest(url: String, token: String, body: String): String = suspendCoroutine { continuation ->
    browserOfficialTranslate(url, token, body,
        onSuccess = { continuation.resume(it) },
        onFailure = { continuation.resumeWith(Result.failure(IllegalStateException(it))) },
    )
}

@JsFun("""(url, token, body, onSuccess, onFailure) => {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 25000);
  fetch(url, { method:'POST', signal:controller.signal, headers:{Authorization:`Bearer ${'$'}{token}`,'Content-Type':'application/json'}, body })
    .then(async response => { const text = await response.text(); if (!response.ok) throw new Error(`official_translation_${'$'}{response.status}:${'$'}{text.slice(0,160)}`); onSuccess(text); })
    .catch(error => onFailure(error?.name === 'AbortError' ? 'official_translation_timeout' : (error?.message || 'official_translation_failed')))
    .finally(() => clearTimeout(timeout));
}""")
private external fun browserOfficialTranslate(url: String, token: String, body: String, onSuccess: (String) -> Unit, onFailure: (String) -> Unit)
