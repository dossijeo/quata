package com.quata.data.supabase

import android.util.Log
import com.quata.core.model.AuthSession
import com.quata.core.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class SupabaseApiException(message: String, val statusCode: Int? = null, val responseBody: String? = null) : Exception(message)

class SupabaseHttpClient(
    private val config: SupabaseConfig,
    private val okHttp: OkHttpClient = OkHttpClient(),
    private val cacheStore: SupabaseResponseCacheStore? = null,
    private val sessionManager: SessionManager? = null,
    val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
) {
    private val appJson = "application/json".toMediaType()
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightGetKeys = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var refreshBlockedUntilMillis: Long = 0L

    internal suspend inline fun <reified T> getList(
        table: String,
        query: Map<String, String?> = emptyMap(),
        cacheMode: SupabaseCacheMode = SupabaseCacheMode.CACHE_FIRST
    ): List<T> = getList(table, serializer(), query, cacheMode)

    internal suspend fun <T> getList(
        table: String,
        serializer: KSerializer<T>,
        query: Map<String, String?> = emptyMap(),
        cacheMode: SupabaseCacheMode = SupabaseCacheMode.CACHE_FIRST
    ): List<T> {
        val body = execute("GET", restUrl(table, query), null, cacheTable = table, cacheQuery = query, cacheMode = cacheMode)
        return decodeList(serializer, body)
    }

    internal inline fun <reified T> observeList(
        table: String,
        query: Map<String, String?> = emptyMap(),
        cacheMode: SupabaseCacheMode = SupabaseCacheMode.CACHE_FIRST
    ): Flow<List<T>> = observeList(table, serializer(), query, cacheMode)

    internal fun <T> observeList(
        table: String,
        serializer: KSerializer<T>,
        query: Map<String, String?> = emptyMap(),
        cacheMode: SupabaseCacheMode = SupabaseCacheMode.CACHE_FIRST
    ): Flow<List<T>> {
        val url = restUrl(table, query)
        val store = cacheStore
        if (store == null || cacheMode == SupabaseCacheMode.NETWORK_ONLY) {
            return channelFlow {
                send(decodeList(serializer, execute("GET", url, null, cacheMode = SupabaseCacheMode.NETWORK_ONLY)))
                close()
            }
        }
        val key = cacheKey("GET", url)
        return channelFlow {
            var lastBody: String? = null
            var hasValue = false
            suspend fun emitBody(body: String) {
                if (body != lastBody) {
                    lastBody = body
                    hasValue = true
                    send(decodeList(serializer, body))
                }
            }
            fun refreshNetwork() = launch {
                val result = runCatching { refreshCachedGet(key, url, table) }
                if (result.isFailure && !hasValue) close(result.exceptionOrNull())
            }

            val initialCache = store.read(key)
            val initialBody = initialCache?.responseJson ?: readReusableCachedGet(table, query)
            if (initialBody != null) {
                emitBody(initialBody)
            }

            val cacheJob = launch {
                store.observe(key).collect { cached ->
                    val body = cached?.responseJson
                    if (body == null) {
                        if (hasValue) {
                            refreshNetwork()
                        }
                        return@collect
                    }
                    emitBody(body)
                }
            }
            val refreshJob = if (initialCache != null && initialCache.isFresh()) {
                launch { }
            } else {
                refreshNetwork()
            }
            awaitClose {
                cacheJob.cancel()
                refreshJob.cancel()
            }
        }
    }

    internal suspend inline fun <reified T> getSingleOrNull(
        table: String,
        query: Map<String, String?> = emptyMap(),
        cacheMode: SupabaseCacheMode = SupabaseCacheMode.NETWORK_ONLY
    ): T? = getList<T>(table, query + ("limit" to "1"), cacheMode).firstOrNull()

    internal suspend inline fun <reified T, reified B> post(table: String, body: B, select: String = "*"): T? {
        val payload = json.encodeToString(body)
        val response = execute("POST", restUrl(table, mapOf("select" to select)), payload, prefer = "return=representation")
        val decoded = json.decodeFromString(ListSerializer(serializer<T>()), response).firstOrNull()
        invalidateTableAfterMutation(table)
        return decoded
    }

    internal suspend inline fun <reified T, reified B> postList(table: String, body: List<B>, select: String = "*"): List<T> {
        if (body.isEmpty()) return emptyList()
        val payload = json.encodeToString(body)
        val response = execute("POST", restUrl(table, mapOf("select" to select)), payload, prefer = "return=representation")
        val decoded = json.decodeFromString(ListSerializer(serializer<T>()), response)
        invalidateTableAfterMutation(table)
        return decoded
    }

    internal suspend inline fun <reified T, reified B> patch(table: String, filters: Map<String, String>, body: B, select: String = "*"): List<T> {
        val payload = json.encodeToString(body)
        val response = execute("PATCH", restUrl(table, filters + ("select" to select)), payload, prefer = "return=representation")
        val decoded = json.decodeFromString(ListSerializer(serializer<T>()), response)
        invalidateTableAfterMutation(table)
        return decoded
    }

    suspend fun delete(
        table: String,
        filters: Map<String, String>,
        returnRepresentation: Boolean = false,
        invalidate: Boolean = true
    ) {
        execute(
            "DELETE",
            restUrl(table, filters),
            null,
            prefer = if (returnRepresentation) "return=representation" else "return=minimal"
        )
        if (invalidate) {
            invalidateTableAfterMutation(table)
        }
    }

    internal suspend inline fun <reified Req, reified Res> rpc(functionName: String, body: Req): Res {
        val payload = json.encodeToString(body)
        val response = execute("POST", "${config.rpcUrl}/$functionName", payload)
        return json.decodeFromString(response)
    }

    internal suspend inline fun <reified Req, reified Res> invokeFunction(functionName: String, body: Req): Res {
        val payload = json.encodeToString(body)
        val response = execute("POST", "${config.functionsUrl}/$functionName", payload, useContentProfile = false)
        return json.decodeFromString(response)
    }

    internal suspend inline fun <reified Req> rpcUnit(functionName: String, body: Req) {
        val payload = json.encodeToString(body)
        execute("POST", "${config.rpcUrl}/$functionName", payload)
    }

    suspend fun invalidateTables(vararg tableNames: String) {
        cacheStore?.invalidateTables(*tableNames)
    }

    suspend fun uploadObject(
        path: String,
        bytes: ByteArray,
        contentType: String? = null,
        upsert: Boolean = true,
        bucket: String = config.storageBucket
    ): StorageUploadResult {
        val cleanPath = path.trimStart('/')
        val url = "${config.storageUrl}/object/$bucket/$cleanPath"
        val mediaType = (contentType ?: "application/octet-stream").toMediaType()
        val request = baseRequest(url)
            .addHeader("Content-Type", contentType ?: "application/octet-stream")
            .addHeader("x-upsert", upsert.toString())
            .post(bytes.toRequestBody(mediaType))
            .build()
        val responseBody = executeRequest(request)
        val raw = runCatching { json.parseToJsonElement(responseBody) }.getOrNull() ?: JsonPrimitive(responseBody)
        val key = (raw as? JsonObject)?.get("Key")?.toString()?.trim('"')
        return StorageUploadResult(key = key, publicUrl = publicObjectUrl(cleanPath, bucket), raw = raw)
    }

    fun publicObjectUrl(path: String, bucket: String = config.storageBucket): String =
        "${config.storageUrl}/object/public/$bucket/${path.trimStart('/')}"

    private fun restUrl(table: String, query: Map<String, String?>): String {
        val qs = query.filterValues { !it.isNullOrBlank() }
            .map { (k, v) -> enc(k) + "=" + enc(v!!) }
            .joinToString("&")
        return config.restUrl + "/" + table + if (qs.isBlank()) "" else "?$qs"
    }

    private suspend fun execute(
        method: String,
        url: String,
        body: String?,
        prefer: String? = null,
        cacheTable: String? = null,
        cacheQuery: Map<String, String?>? = null,
        cacheMode: SupabaseCacheMode = SupabaseCacheMode.CACHE_FIRST,
        useContentProfile: Boolean = true
    ): String {
        if (method.equals("GET", ignoreCase = true)) {
            return executeCachedGet(url, cacheTable, cacheQuery, cacheMode)
        }
        val builder = baseRequest(url, useContentProfile)
        if (prefer != null) builder.addHeader("Prefer", prefer)
        val requestBody = body?.toRequestBody(appJson)
        val request = when (method.uppercase()) {
            "POST" -> builder.post(requestBody ?: ByteArray(0).toRequestBody(appJson)).build()
            "PATCH" -> builder.patch(requestBody ?: ByteArray(0).toRequestBody(appJson)).build()
            "DELETE" -> builder.delete(requestBody).build()
            else -> error("Unsupported method: $method")
        }
        return executeRequest(request)
    }

    private suspend fun executeCachedGet(
        url: String,
        tableName: String?,
        query: Map<String, String?>?,
        cacheMode: SupabaseCacheMode
    ): String {
        val store = cacheStore
        if (store == null || cacheMode == SupabaseCacheMode.NETWORK_ONLY) {
            return executeGetRequest(url)
        }
        val key = cacheKey("GET", url)
        val cachedResponse = store.read(key)
        val cachedBody = cachedResponse?.responseJson ?: readReusableCachedGet(tableName, query)
        if (cachedBody != null) {
            if (cachedResponse?.isFresh() != true) {
                refreshScope.launch {
                    runCatching { refreshCachedGet(key, url, tableName) }
                }
            }
            return cachedBody
        }
        return refreshCachedGet(key, url, tableName)
    }

    private suspend fun readReusableCachedGet(
        tableName: String?,
        query: Map<String, String?>?
    ): String? {
        val store = cacheStore ?: return null
        if (tableName != "community_profiles") return null
        val profileIds = parseIdFilter(query?.get("id")) ?: return null
        if (profileIds.isEmpty()) return null

        val profilesById = LinkedHashMap<String, JsonObject>()
        store.readTable(tableName).forEach cachedEntries@{ cached ->
            val array = runCatching { json.parseToJsonElement(cached.responseJson) as? JsonArray }
                .getOrNull()
                ?: return@cachedEntries
            array.forEach { item ->
                val profile = item as? JsonObject ?: return@forEach
                val id = (profile["id"] as? JsonPrimitive)?.content ?: return@forEach
                profilesById[id] = profile
            }
        }

        val selectedProfiles = profileIds.mapNotNull { id -> profilesById[id] }
        if (selectedProfiles.isEmpty()) return null
        return JsonArray(selectedProfiles).toString()
    }

    private fun parseIdFilter(filter: String?): List<String>? {
        val value = filter?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            value.startsWith("eq.") -> listOf(value.removePrefix("eq.").trim()).filter { it.isNotBlank() }
            value.startsWith("in.(") && value.endsWith(")") -> value
                .removePrefix("in.(")
                .removeSuffix(")")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            else -> null
        }
    }

    private suspend fun refreshCachedGet(key: String, url: String, tableName: String?): String {
        val store = cacheStore ?: return executeGetRequest(url)
        if (!inFlightGetKeys.add(key)) {
            repeat(IN_FLIGHT_CACHE_WAIT_ATTEMPTS) {
                store.read(key)?.let { return it.responseJson }
                delay(IN_FLIGHT_CACHE_WAIT_DELAY_MILLIS)
            }
            store.read(key)?.let { return it.responseJson }
        }
        return try {
            val response = executeGetRequest(url)
            store.write(
                key = key,
                method = "GET",
                url = url,
                tableName = tableName,
                responseJson = response
            )
            response
        } finally {
            inFlightGetKeys.remove(key)
        }
    }

    private suspend fun executeGetRequest(url: String): String =
        executeRequest(baseRequest(url).get().build())

    private suspend fun invalidateTableAfterMutation(table: String) {
        cacheStore?.invalidateTable(table)
    }

    private fun <T> decodeList(serializer: KSerializer<T>, body: String): List<T> =
        json.decodeFromString(ListSerializer(serializer), body)

    private fun cacheKey(method: String, url: String): String = "$method $url"

    private fun CachedSupabaseResponse.isFresh(): Boolean =
        System.currentTimeMillis() - updatedAtMillis < CACHE_FIRST_REFRESH_TTL_MILLIS

    private suspend fun executeRequest(request: Request): String = withContext(Dispatchers.IO) {
        val freshRequest = withAuthHeader(refreshSessionIfNeeded(request))
        okHttp.newCall(freshRequest).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (response.code == 401 && sessionManager?.currentSession()?.refreshToken?.isNotBlank() == true) {
                val refreshed = refreshCurrentSession(force = true)
                if (refreshed != null) {
                    val retryRequest = withAuthHeader(request)
                    okHttp.newCall(retryRequest).execute().use { retryResponse ->
                        val retryBody = retryResponse.body?.string().orEmpty()
                        if (!retryResponse.isSuccessful) {
                            throw SupabaseApiException(
                                message = "Supabase HTTP ${retryResponse.code}: ${retryBody.take(800)}",
                                statusCode = retryResponse.code,
                                responseBody = retryBody
                            )
                        }
                        return@withContext retryBody
                    }
                }
            }
            if (!response.isSuccessful) {
                throw SupabaseApiException(
                    message = "Supabase HTTP ${response.code}: ${responseBody.take(800)}",
                    statusCode = response.code,
                    responseBody = responseBody
                )
            }
            responseBody
        }
    }

    suspend fun ensureFreshSession(force: Boolean = false): AuthSession? =
        refreshCurrentSession(force = force)

    private suspend fun refreshSessionIfNeeded(request: Request): Request {
        refreshCurrentSession(force = false)
        return request
    }

    private suspend fun refreshCurrentSession(force: Boolean): AuthSession? {
        val manager = sessionManager ?: return null
        val current = manager.currentSession() ?: return null
        if (System.currentTimeMillis() < refreshBlockedUntilMillis) return if (force) null else current
        if (!force && !current.shouldRefresh()) return current
        var failed = false
        val refreshed = manager.ensureFreshSession(force = force) { session ->
            val refreshToken = session.refreshToken?.takeIf { it.isNotBlank() } ?: return@ensureFreshSession null
            runCatching { refreshAuthSession(session, refreshToken) }
                .onFailure { error ->
                    failed = true
                    refreshBlockedUntilMillis = System.currentTimeMillis() + REFRESH_FAILURE_COOLDOWN_MILLIS
                    val status = (error as? SupabaseApiException)?.statusCode
                    Log.w(TAG, "Supabase session refresh failed with status=$status")
                    if (status == 400 || status == 401) {
                        manager.clearSession()
                    }
                }
                .getOrNull()
        }
        return if (force && failed) null else refreshed
    }

    private suspend fun refreshAuthSession(current: AuthSession, refreshToken: String): AuthSession {
        val payload = json.encodeToString(SupabaseRefreshTokenRequest(refreshToken))
        val request = Request.Builder()
            .url("${config.authUrl}/token?grant_type=refresh_token")
            .addHeader("apikey", config.anonKey)
            .addHeader("Authorization", "Bearer ${config.anonKey}")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody(appJson))
            .build()
        val body = withContext(Dispatchers.IO) {
            okHttp.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw SupabaseApiException(
                        message = "Supabase refresh HTTP ${response.code}: ${responseBody.take(800)}",
                        statusCode = response.code,
                        responseBody = responseBody
                    )
                }
                responseBody
            }
        }
        val refreshed = json.decodeFromString<SupabaseAuthSession>(body)
        return current.copy(
            token = refreshed.access_token,
            accessToken = refreshed.access_token,
            refreshToken = refreshed.refresh_token,
            expiresAt = refreshed.expires_at ?: refreshed.expires_in?.let { System.currentTimeMillis() / 1000L + it }
        )
    }

    private fun withAuthHeader(request: Request): Request {
        val bearer = sessionManager
            ?.currentSession()
            ?.bearerToken
            ?.takeIf { it.isNotBlank() }
            ?: config.anonKey
        return request.newBuilder()
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer $bearer")
            .build()
    }

    private fun baseRequest(url: String, useContentProfile: Boolean = true): Request.Builder {
        val builder = Request.Builder()
        .url(url)
        .addHeader("apikey", config.anonKey)
        .addHeader("Accept", "application/json")
        if (useContentProfile) {
            builder
                .addHeader("Content-Profile", config.schema)
                .addHeader("Accept-Profile", config.schema)
        }
        return builder
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
            .replace("%2C", ",")
            .replace("%2c", ",")

    private companion object {
        const val TAG = "SupabaseHttpClient"
        const val REFRESH_FAILURE_COOLDOWN_MILLIS = 60_000L
        const val CACHE_FIRST_REFRESH_TTL_MILLIS = 60_000L
        const val IN_FLIGHT_CACHE_WAIT_ATTEMPTS = 30
        const val IN_FLIGHT_CACHE_WAIT_DELAY_MILLIS = 100L
    }
}
