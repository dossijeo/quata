package com.quata.bettermessages

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class BetterMessagesClient(
    baseUrl: String,
    private val cookieStore: PersistentCookieStore = InMemoryCookieStore(),
    okHttpClient: OkHttpClient? = null,
    private val json: Json = BetterMessagesJson.default
) {
    val normalizedBaseUrl: String = baseUrl.trimEnd('/')

    private val cookieJar = BetterMessagesCookieJar(cookieStore)

    val httpClient: OkHttpClient = okHttpClient ?: OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    val bridge: BetterMessagesBridgeApi = BetterMessagesBridgeApi(
        baseUrl = normalizedBaseUrl,
        client = httpClient,
        json = json
    )

    val rest: BetterMessagesRestApi = BetterMessagesRestApi(
        baseUrl = normalizedBaseUrl,
        client = httpClient,
        json = json
    )

    suspend fun prepareSession(profileId: String): BmSyncSessionData {
        bridge.setProfileContext(profileId)
        return bridge.syncSession(profileId)
    }

    suspend fun lookupWordPressUserId(profileId: String): Int? {
        val lookupClient = OkHttpClient.Builder()
            .cookieJar(BetterMessagesCookieJar(InMemoryCookieStore()))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .build()
        val lookupBridge = BetterMessagesBridgeApi(
            baseUrl = normalizedBaseUrl,
            client = lookupClient,
            json = json
        )
        return lookupBridge.syncSession(profileId).userId
    }

    suspend fun refreshRestNonce(profileId: String): String? {
        val inboxUrl = bridge.getInboxUrl(profileId).url.takeIf { it.isNotBlank() } ?: return null
        val request = Request.Builder()
            .url(inboxUrl.absoluteUrl())
            .get()
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()
        val html = httpClient.executeSuspend(request).use { it.readBodyOrThrow() }
        val nonce = extractRestNonce(html)
        rest.setRestNonce(nonce)
        return nonce
    }

    fun clearCookies() {
        cookieStore.clear()
        rest.setRestNonce(null)
    }

    private fun String.absoluteUrl(): String {
        if (startsWith("http://") || startsWith("https://")) return this
        return normalizedBaseUrl + "/" + trimStart('/')
    }

    private fun extractRestNonce(html: String): String? {
        return NONCE_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
    }

    private companion object {
        val NONCE_PATTERNS = listOf(
            Regex(""""nonce"\s*:\s*"([^"]+)"""", setOf(RegexOption.DOT_MATCHES_ALL)),
            Regex("""'nonce'\s*:\s*'([^']+)'""", setOf(RegexOption.DOT_MATCHES_ALL)),
            Regex("""wpApiSettings\s*=\s*\{.*?nonce["']?\s*:\s*["']([^"']+)""", setOf(RegexOption.DOT_MATCHES_ALL))
        )
    }
}
