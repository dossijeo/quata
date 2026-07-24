package com.quata.web

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Authenticated PostgREST RPC transport for browser repositories.
 * Callers own the function-specific JSON contract; this class deliberately exposes no endpoint
 * names or domain mapping.
 */
class WebPostgrestRpcClient(
    private val configuration: WebRuntimeConfiguration,
    private val authRepository: WebAuthRepository,
) {
    suspend fun post(functionName: String, body: String): WebPostgrestResult {
        val baseUrl = configuration.supabaseUrl?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
            ?: return WebPostgrestResult.Failure(WebPostgrestFailureKind.Configuration, "supabase_url_missing")
        val apiKey = configuration.supabasePublishableKey?.takeIf { it.isNotBlank() }
            ?: return WebPostgrestResult.Failure(WebPostgrestFailureKind.Configuration, "supabase_publishable_key_missing")
        val accessToken = authRepository.currentWebPushCredentials()?.accessToken
            ?: return WebPostgrestResult.Failure(WebPostgrestFailureKind.Session, "web_session_missing")
        if (!functionName.matches(PostgrestRpcFunctionName)) {
            return WebPostgrestResult.Failure(WebPostgrestFailureKind.Configuration, "postgrest_rpc_function_invalid")
        }
        if (body.isBlank()) {
            return WebPostgrestResult.Failure(WebPostgrestFailureKind.Configuration, "postgrest_rpc_body_missing")
        }
        return browserPostgrestRpcPost(
            url = "$baseUrl/rest/v1/rpc/$functionName",
            apiKey = apiKey,
            accessToken = accessToken,
            body = body,
        )
    }
}

private val PostgrestRpcFunctionName = Regex("[A-Za-z_][A-Za-z0-9_]*")

private suspend fun browserPostgrestRpcPost(
    url: String,
    apiKey: String,
    accessToken: String,
    body: String,
): WebPostgrestResult = suspendCoroutine { continuation ->
    browserPostgrestRpcPostRequest(
        url = url,
        apiKey = apiKey,
        accessToken = accessToken,
        body = body,
        onSuccess = { responseBody, status, contentRange ->
            continuation.resume(WebPostgrestResult.Success(responseBody, status, contentRange))
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
                    reason = reason ?: "postgrest_rpc_request_failed",
                    statusCode = status,
                ),
            )
        },
    )
}

private fun browserPostgrestRpcPostRequest(
    url: String,
    apiKey: String,
    accessToken: String,
    body: String,
    onSuccess: (String, Int, String?) -> Unit,
    onFailure: (String?, Int?) -> Unit,
): Unit = js(
    """
    if (typeof globalThis.fetch !== 'function') {
      onFailure('postgrest_fetch_unsupported', null);
      return;
    }
    globalThis.fetch(url, {
      method: 'POST',
      headers: {
        apikey: apiKey,
        Authorization: `Bearer ${'$'}{accessToken}`,
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body,
    }).then(async (response) => {
      const responseBody = await response.text();
      if (response.ok) onSuccess(responseBody, response.status, response.headers.get('content-range'));
      else onFailure(`postgrest_rpc_http_${'$'}{response.status}`, response.status);
    }).catch((error) => onFailure(error?.message ?? error?.name ?? 'postgrest_rpc_network_error', null));
    """,
)
