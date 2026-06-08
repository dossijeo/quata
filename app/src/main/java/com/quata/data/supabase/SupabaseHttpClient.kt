package com.quata.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
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
    val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
) {
    private val appJson = "application/json".toMediaType()
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightGetKeys = ConcurrentHashMap.newKeySet<String>()

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
        val body = execute("GET", restUrl(table, query), null, cacheTable = table, cacheMode = cacheMode)
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
            fun refreshNetwork() = launch {
                val result = runCatching { refreshCachedGet(key, url, table) }
                if (result.isFailure && !hasValue) close(result.exceptionOrNull())
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
                    if (body != lastBody) {
                        lastBody = body
                        hasValue = true
                        send(decodeList(serializer, body))
                    }
                }
            }
            val refreshJob = refreshNetwork()
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

    suspend fun delete(table: String, filters: Map<String, String>) {
        execute("DELETE", restUrl(table, filters), null, prefer = "return=minimal")
        invalidateTableAfterMutation(table)
    }

    internal suspend inline fun <reified Req, reified Res> rpc(functionName: String, body: Req): Res {
        val payload = json.encodeToString(body)
        val response = execute("POST", "${config.rpcUrl}/$functionName", payload)
        return json.decodeFromString(response)
    }

    internal suspend inline fun <reified Req> rpcUnit(functionName: String, body: Req) {
        val payload = json.encodeToString(body)
        execute("POST", "${config.rpcUrl}/$functionName", payload)
    }

    suspend fun invalidateTables(vararg tableNames: String) {
        cacheStore?.invalidateTables(*tableNames)
    }

    suspend fun uploadObject(path: String, bytes: ByteArray, contentType: String? = null, upsert: Boolean = true): StorageUploadResult {
        val cleanPath = path.trimStart('/')
        val url = "${config.storageUrl}/object/${config.storageBucket}/$cleanPath"
        val mediaType = (contentType ?: "application/octet-stream").toMediaType()
        val request = baseRequest(url)
            .addHeader("Content-Type", contentType ?: "application/octet-stream")
            .addHeader("x-upsert", upsert.toString())
            .post(bytes.toRequestBody(mediaType))
            .build()
        val responseBody = executeRequest(request)
        val raw = runCatching { json.parseToJsonElement(responseBody) }.getOrNull() ?: JsonPrimitive(responseBody)
        val key = (raw as? JsonObject)?.get("Key")?.toString()?.trim('"')
        return StorageUploadResult(key = key, publicUrl = publicObjectUrl(cleanPath), raw = raw)
    }

    fun publicObjectUrl(path: String): String = "${config.storageUrl}/object/public/${config.storageBucket}/${path.trimStart('/')}"

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
        cacheMode: SupabaseCacheMode = SupabaseCacheMode.CACHE_FIRST
    ): String {
        if (method.equals("GET", ignoreCase = true)) {
            return executeCachedGet(url, cacheTable, cacheMode)
        }
        val builder = baseRequest(url)
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
        cacheMode: SupabaseCacheMode
    ): String {
        val store = cacheStore
        if (store == null || cacheMode == SupabaseCacheMode.NETWORK_ONLY) {
            return executeGetRequest(url)
        }
        val key = cacheKey("GET", url)
        val cached = store.read(key)
        if (cached != null) {
            refreshScope.launch {
                runCatching { refreshCachedGet(key, url, tableName) }
            }
            return cached.responseJson
        }
        return refreshCachedGet(key, url, tableName)
    }

    private suspend fun refreshCachedGet(key: String, url: String, tableName: String?): String {
        val store = cacheStore ?: return executeGetRequest(url)
        if (!inFlightGetKeys.add(key)) {
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

    private suspend fun executeRequest(request: Request): String = withContext(Dispatchers.IO) {
        okHttp.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
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

    private fun baseRequest(url: String): Request.Builder = Request.Builder()
        .url(url)
        .addHeader("apikey", config.anonKey)
        .addHeader("Authorization", "Bearer ${config.anonKey}")
        .addHeader("Accept", "application/json")
        .addHeader("Content-Profile", config.schema)
        .addHeader("Accept-Profile", config.schema)

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
