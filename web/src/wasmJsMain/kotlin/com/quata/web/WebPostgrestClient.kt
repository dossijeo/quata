@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

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

enum class WebPostgrestAuthMode {
    /** Requires an active Web session and never sends a request without its bearer token. */
    SessionRequired,
    /** Allows a read guarded only by the configured publishable key and omits Authorization. */
    Public,
}

/** Pure request decision used before any browser fetch is constructed. */
internal fun webPostgrestReadAccessToken(
    authMode: WebPostgrestAuthMode,
    accessToken: String?,
): Result<String?> = when (authMode) {
    WebPostgrestAuthMode.Public -> Result.success(null)
    WebPostgrestAuthMode.SessionRequired -> webPostgrestSessionAccessToken(accessToken)
}

/**
 * Resolves a read credential lazily. In particular, public reads must not restore or refresh the
 * browser session: doing either would turn the anonymous feed into an authenticated request.
 */
internal suspend fun resolveWebPostgrestReadAccessToken(
    authMode: WebPostgrestAuthMode,
    sessionAccessToken: suspend () -> String?,
): Result<String?> = when (authMode) {
    WebPostgrestAuthMode.Public -> Result.success(null)
    WebPostgrestAuthMode.SessionRequired ->
        webPostgrestReadAccessToken(WebPostgrestAuthMode.SessionRequired, sessionAccessToken())
}

internal fun webPostgrestSessionAccessToken(accessToken: String?): Result<String> = accessToken
    ?.takeIf(String::isNotBlank)
    ?.let(Result.Companion::success)
    ?: Result.failure(IllegalStateException("web_session_missing"))

/**
 * PostgREST transport for WASM repositories. Public GETs can run without a session; mutations
 * still require one. Endpoint models stay at gateways.
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
        authMode: WebPostgrestAuthMode = WebPostgrestAuthMode.SessionRequired,
    ): WebPostgrestResult {
        val baseUrl = configuration.supabaseUrl?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
            ?: return WebPostgrestResult.Failure(WebPostgrestFailureKind.Configuration, "supabase_url_missing")
        val apiKey = configuration.supabasePublishableKey?.takeIf { it.isNotBlank() }
            ?: return WebPostgrestResult.Failure(WebPostgrestFailureKind.Configuration, "supabase_publishable_key_missing")
        if (!table.matches(PostgrestTableName)) {
            return feedReadFailure(table, WebPostgrestFailureKind.Configuration, "postgrest_table_invalid")
        }
        val accessToken = resolveWebPostgrestReadAccessToken(
            authMode = authMode,
            sessionAccessToken = { authRepository.currentWebPushCredentials()?.accessToken },
        ).getOrElse {
            return feedReadFailure(table, WebPostgrestFailureKind.Session, "web_session_missing")
        }
        val parameters = buildMap {
            putAll(query)
            limit?.let { put("limit", it.coerceAtLeast(1).toString()) }
            offset?.let { put("offset", it.coerceAtLeast(0).toString()) }
        }
        recordWebFeedReadState(table, "request_started")
        val result = browserPostgrestGet(
            url = "$baseUrl/rest/v1/$table${parameters.toQueryString()}",
            apiKey = apiKey,
            accessToken = accessToken,
        )
        recordWebFeedReadResult(table, result)
        return result
    }

    suspend fun patch(
        table: String,
        query: Map<String, String>,
        body: String,
    ): WebPostgrestResult = mutate("PATCH", table, query, body)

    suspend fun post(
        table: String,
        body: String,
    ): WebPostgrestResult = mutate("POST", table, emptyMap(), body)

    suspend fun delete(
        table: String,
        query: Map<String, String>,
    ): WebPostgrestResult = mutate("DELETE", table, query, null)

    /** Authenticated Supabase RPC call; function names are kept as strict identifiers. */
    suspend fun rpc(function: String, body: String): WebPostgrestResult {
        if (!function.matches(PostgrestTableName)) {
            return WebPostgrestResult.Failure(WebPostgrestFailureKind.Configuration, "postgrest_rpc_invalid")
        }
        val baseUrl = configuration.supabaseUrl?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: return WebPostgrestResult.Failure(WebPostgrestFailureKind.Configuration, "supabase_url_missing")
        val apiKey = configuration.supabasePublishableKey?.takeIf { it.isNotBlank() }
            ?: return WebPostgrestResult.Failure(WebPostgrestFailureKind.Configuration, "supabase_publishable_key_missing")
        val token = webPostgrestSessionAccessToken(authRepository.currentWebPushCredentials()?.accessToken)
            .getOrElse { return WebPostgrestResult.Failure(WebPostgrestFailureKind.Session, "web_session_missing") }
        return browserPostgrestMutation("POST", "$baseUrl/rest/v1/rpc/$function", apiKey, token, body)
    }

    private suspend fun mutate(
        method: String,
        table: String,
        query: Map<String, String>,
        body: String?,
    ): WebPostgrestResult {
        val baseUrl = configuration.supabaseUrl?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
            ?: return WebPostgrestResult.Failure(WebPostgrestFailureKind.Configuration, "supabase_url_missing")
        val apiKey = configuration.supabasePublishableKey?.takeIf { it.isNotBlank() }
            ?: return WebPostgrestResult.Failure(WebPostgrestFailureKind.Configuration, "supabase_publishable_key_missing")
        val accessToken = webPostgrestSessionAccessToken(
            authRepository.currentWebPushCredentials()?.accessToken,
        ).getOrElse {
            return WebPostgrestResult.Failure(WebPostgrestFailureKind.Session, "web_session_missing")
        }
        if (!table.matches(PostgrestTableName)) {
            return WebPostgrestResult.Failure(WebPostgrestFailureKind.Configuration, "postgrest_table_invalid")
        }
        return browserPostgrestMutation(
            method = method,
            url = "$baseUrl/rest/v1/$table${query.toQueryString()}",
            apiKey = apiKey,
            accessToken = accessToken,
            body = body,
        )
    }
}

private val PostgrestTableName = Regex("[A-Za-z_][A-Za-z0-9_]*")

private fun feedReadFailure(
    table: String,
    kind: WebPostgrestFailureKind,
    reason: String,
): WebPostgrestResult.Failure = WebPostgrestResult.Failure(kind, reason).also { failure ->
    recordWebFeedReadResult(table, failure)
}

/** Web-only, secret-free diagnostics for the public feed smoke. */
internal fun recordWebFeedReadState(table: String, state: String, errorCode: String? = null) {
    if (table != "community_posts") return
    writeWebFeedReadState(state, errorCode)
}

// `js` used from an expression-bodied Kotlin function must itself be one JavaScript expression.
// Keep both diagnostics in a comma expression so the Wasm production backend never receives two
// statements where it is expecting one expression.
private fun writeWebFeedReadState(state: String, errorCode: String?): Unit = js("(globalThis.localStorage?.setItem('web.feed.remote_read_state', state), globalThis.localStorage?.setItem('web.feed.remote_read_error', errorCode || ''))")

internal fun recordWebFeedCollectorStarted(): Unit = js("globalThis.localStorage?.setItem('web.feed.collector_started', 'true')")

private fun recordWebFeedReadResult(table: String, result: WebPostgrestResult) {
    when (result) {
        is WebPostgrestResult.Success -> recordWebFeedReadState(table, "request_succeeded")
        is WebPostgrestResult.Failure -> recordWebFeedReadState(
            table = table,
            state = "request_failed",
            errorCode = result.kind.name.lowercase(),
        )
    }
}

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
    accessToken: String?,
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
    accessToken: String?,
    onSuccess: (String, Int, String?) -> Unit,
    onFailure: (String?, Int?) -> Unit,
): Unit = js(
    """
    (() => {
    if (typeof globalThis.fetch !== 'function') {
      onFailure('postgrest_fetch_unsupported', null);
      return;
    }
    const headers = {
      apikey: apiKey,
      Accept: 'application/json',
    };
    if (accessToken) headers.Authorization = `Bearer ${'$'}{accessToken}`;
    globalThis.fetch(url, {
      method: 'GET',
      headers,
    }).then(async (response) => {
      const body = await response.text();
      if (response.ok) onSuccess(body, response.status, response.headers.get('content-range'));
      else onFailure(`postgrest_http_${'$'}{response.status}`, response.status);
    }).catch((error) => onFailure(error?.message ?? error?.name ?? 'postgrest_network_error', null));
    })()
    """,
)

private suspend fun browserPostgrestMutation(
    method: String,
    url: String,
    apiKey: String,
    accessToken: String,
    body: String?,
): WebPostgrestResult = suspendCoroutine { continuation ->
    browserPostgrestMutationRequest(
        method, url, apiKey, accessToken, body,
        onSuccess = { responseBody, status, contentRange ->
            continuation.resume(WebPostgrestResult.Success(responseBody, status, contentRange))
        },
        onFailure = { reason, status ->
            continuation.resume(WebPostgrestResult.Failure(
                kind = when (status) {
                    401 -> WebPostgrestFailureKind.Unauthorized
                    403 -> WebPostgrestFailureKind.RlsDenied
                    null -> WebPostgrestFailureKind.Network
                    else -> WebPostgrestFailureKind.Http
                },
                reason = reason ?: "postgrest_mutation_failed",
                statusCode = status,
            ))
        },
    )
}

private fun browserPostgrestMutationRequest(
    method: String,
    url: String,
    apiKey: String,
    accessToken: String,
    body: String?,
    onSuccess: (String, Int, String?) -> Unit,
    onFailure: (String?, Int?) -> Unit,
): Unit = js(
    """
    (() => {
    if (typeof globalThis.fetch !== 'function') {
      onFailure('postgrest_fetch_unsupported', null);
      return;
    }
    const headers = {
      apikey: apiKey,
      Authorization: `Bearer ${'$'}{accessToken}`,
      Accept: 'application/json',
      Prefer: 'return=representation',
    };
    if (body != null) headers['Content-Type'] = 'application/json';
    globalThis.fetch(url, { method, headers, body: body || undefined }).then(async (response) => {
      const responseBody = await response.text();
      if (response.ok) onSuccess(responseBody, response.status, response.headers.get('content-range'));
      else onFailure(`postgrest_http_${'$'}{response.status}`, response.status);
    }).catch((error) => onFailure(error?.message ?? error?.name ?? 'postgrest_network_error', null));
    })()
    """,
)
