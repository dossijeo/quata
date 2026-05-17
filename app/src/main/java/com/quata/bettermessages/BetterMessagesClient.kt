package com.quata.bettermessages

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class BetterMessagesClient(
    baseUrl: String,
    private val cookieStore: PersistentCookieStore = InMemoryCookieStore(),
    okHttpClient: OkHttpClient? = null,
    private val json: Json = BetterMessagesJson.default
) {
    val normalizedBaseUrl: String = baseUrl.trimEnd('/')

    private val cookieJar = BetterMessagesCookieJar(cookieStore)
    private val webBootMillis = System.currentTimeMillis()

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
        webReferer = betterMessagesWebUrl().stripFragment(),
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
        val inboxUrl = bridge.getInboxUrl(profileId).url.takeIf { it.isNotBlank() }
        val webUrl = betterMessagesWebUrl(inboxUrl)
        rest.setWebReferer(webUrl.stripFragment())
        val request = Request.Builder()
            .url(webUrl)
            .get()
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", BROWSER_ACCEPT_LANGUAGE)
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Referer", "$normalizedBaseUrl/")
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

    private fun betterMessagesWebUrl(inboxUrl: String? = null): String {
        val baseMessagesUrl = inboxUrl
            ?.absoluteUrl()
            ?.substringBefore('#')
            ?.substringBefore('?')
            ?.takeIf { it.isNotBlank() }
            ?: "$normalizedBaseUrl/mensajes/"
        val separator = if (baseMessagesUrl.endsWith("/")) "" else "/"
        val returnTo = URLEncoder.encode("$normalizedBaseUrl/", Charsets.UTF_8.name())
        return buildString {
            append(baseMessagesUrl)
            append(separator)
            append("?quqos_return_to=")
            append(returnTo)
            append("&quqos_bm_boot=")
            append(webBootMillis)
            append("&quqos_bm_view=private&quqos_bm_scope=private#/?&scrollToContainer")
        }
    }

    private fun String.stripFragment(): String = substringBefore('#')

    private fun extractRestNonce(html: String): String? {
        return NONCE_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
    }

    private companion object {
        const val BROWSER_ACCEPT_LANGUAGE = "es-ES,es;q=0.9,en;q=0.8"
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; QUATA) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"

        val NONCE_PATTERNS = listOf(
            Regex("""wpApiSettings\s*=\s*\{.*?["']nonce["']?\s*:\s*["']([^"']+)""", setOf(RegexOption.DOT_MATCHES_ALL)),
            Regex(""""root"\s*:\s*"[^"]*wp-json[^"]*".{0,500}?"nonce"\s*:\s*"([^"]+)"""", setOf(RegexOption.DOT_MATCHES_ALL)),
            Regex("""'root'\s*:\s*'[^']*wp-json[^']*'.{0,500}?'nonce'\s*:\s*'([^']+)'""", setOf(RegexOption.DOT_MATCHES_ALL)),
            Regex(""""restNonce"\s*:\s*"([^"]+)"""", setOf(RegexOption.DOT_MATCHES_ALL)),
            Regex("""'restNonce'\s*:\s*'([^']+)'""", setOf(RegexOption.DOT_MATCHES_ALL)),
            Regex(""""nonce"\s*:\s*"([^"]+)"""", setOf(RegexOption.DOT_MATCHES_ALL)),
            Regex("""'nonce'\s*:\s*'([^']+)'""", setOf(RegexOption.DOT_MATCHES_ALL))
        )
    }
}
