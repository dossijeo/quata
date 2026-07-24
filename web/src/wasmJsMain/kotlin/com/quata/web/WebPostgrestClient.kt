package com.quata.web

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Result of an authenticated browser PostgREST read without coupling callers to a table schema. */
sealed interface WebPostgrestResult {
    data class Success(
        val body: String,
        val statusCode: Int,
        val contentRange: String? = null,
    ) : WebPostgrestResult

    data class Failure(
        val kind: WebPostgrestFailureKind,
        val reason: String,
        val statusCode: Int? = null,
    ) : WebPostgrestResult
}

enum class WebPostgrestFailureKind {
    Configuration,
    Session,
    Unauthorized,
    RlsDenied,
    Http,
    Network,
}

/**
 * Read-only authenticated PostgREST transport for WASM repositories. It intentionally owns no
 * endpoint model, cache, realtime connection or mutation method.
 */
class WebPostgrestClient(
    private val configuration: WebRuntimeConfiguration,
    private val authRepository: WebAuthRepository,
) {
    suspend fun get(
        table: String,
        query: Map<String, String> = emptyMap(),
        limit: Int? = null,
        offset: Int? = null,
    ): WebPostgrestResult {
        val baseUrl = configuration.supabaseUrl?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
            ?: return WebPostgrestResult.Failure(WebPostgrestFailureKind.Configuration, "supabase_url_missing")
        val apiKey = configuration.supabasePublishableKey?.takeIf { it.isNotBlank() }
            ?: return WebPostgrestResult.Failure(WebPostgrestFailureKind.Configuration, "supabase_publishable_key_missing")
        val accessToken = authRepository.currentWebPushCredentials()?.accessToken
            ?: return WebPostgrestResult.Failure(WebPostgrestFailureKind.Session, "web_session_missing")
        if (!table.matches(PostgrestTableName)) {
            return WebPostgrestResult.Failure(WebPostgrestFailureKind.Configuration, "postgrest_table_invalid")
        }
        val parameters = buildMap {
            putAll(query)
            limit?.let { put("limit", it.coerceAtLeast(1).toString()) }
            offset?.let { put("offset", it.coerceAtLeast(0).toString()) }
        }
        return browserPostgrestGet(
            url = "$baseUrl/rest/v1/$table${parameters.toQueryString()}",
            apiKey = apiKey,
            accessToken = accessToken,
        )
    }
}

private val PostgrestTableName = Regex("[A-Za-z_][A-Za-z0-9_]*")

private fun Map<String, String>.toQueryString(): String {
    if (isEmpty()) return ""
    return entries.joinToString(prefix = "?", separator = "&") { (key, value) ->
        "${browserEncodeQueryPart(key)}=${browserEncodeQueryPart(value)}"
    }
}

private fun browserEncodeQueryPart(value: String): String = js("encodeURIComponent(value)")

private suspend fun browserPostgrestGet(
    url: String,
    apiKey: String,
    accessToken: String,
): WebPostgrestResult = suspendCoroutine { continuation ->
    browserPostgrestGetRequest(
        url = url,
        apiKey = apiKey,
        accessToken = accessToken,
        onSuccess = { body, status, contentRange ->
            continuation.resume(WebPostgrestResult.Success(body, status, contentRange))
        },
        onFailure = { reason, status ->
            continuation.resume(
                WebPostgrestResult.Failure(
                    kind = when (status) {
                        401 -> WebPostgrestFailureKind.Unauthorized
                        403 -> WebPostgrestFailureKind.RlsDenied
                        null -> WebPostgrestFailureKind.Network
                        else -> WebPostgrestFailureKind.Http
                    },
                    reason = reason ?: "postgrest_request_failed",
                    statusCode = status,
                ),
            )
        },
    )
}

private fun browserPostgrestGetRequest(
    url: String,
    apiKey: String,
    accessToken: String,
    onSuccess: (String, Int, String?) -> Unit,
    onFailure: (String?, Int?) -> Unit,
): Unit = js(
    """
    if (typeof globalThis.fetch !== 'function') {
      onFailure('postgrest_fetch_unsupported', null);
      return;
    }
    globalThis.fetch(url, {
      method: 'GET',
      headers: {
        apikey: apiKey,
        Authorization: `Bearer ${'$'}{accessToken}`,
        Accept: 'application/json',
      },
    }).then(async (response) => {
      const body = await response.text();
      if (response.ok) onSuccess(body, response.status, response.headers.get('content-range'));
      else onFailure(`postgrest_http_${'$'}{response.status}`, response.status);
    }).catch((error) => onFailure(error?.message ?? error?.name ?? 'postgrest_network_error', null));
    """,
)
