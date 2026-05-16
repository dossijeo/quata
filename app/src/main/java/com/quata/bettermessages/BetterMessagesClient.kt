package com.quata.bettermessages

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class BetterMessagesClient(
    baseUrl: String,
    private val cookieStore: PersistentCookieStore = InMemoryCookieStore(),
    okHttpClient: OkHttpClient? = null,
    json: Json = BetterMessagesJson.default
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

    fun clearCookies() {
        cookieStore.clear()
    }
}
