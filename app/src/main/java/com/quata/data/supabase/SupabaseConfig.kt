package com.quata.data.supabase

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Do not hardcode secret/service-role keys in the Android app.
 * Use the public anon key only, preferably injected from BuildConfig.
 */
data class SupabaseConfig(
    val projectUrl: String = "https://yrrlankpwmhluexshxnw.supabase.co",
    val anonKey: String,
    val schema: String = "public",
    val storageBucket: String = "community-posts"
) {
    val restUrl: String get() = projectUrl.trimEnd('/') + "/rest/v1"
    val rpcUrl: String get() = projectUrl.trimEnd('/') + "/rest/v1/rpc"
    val authUrl: String get() = projectUrl.trimEnd('/') + "/auth/v1"
    val functionsUrl: String get() = projectUrl.trimEnd('/') + "/functions/v1"
    val storageUrl: String get() = projectUrl.trimEnd('/') + "/storage/v1"
    val realtimeUrl: String get() = realtimeUrl(anonKey)

    fun realtimeUrl(apiKey: String): String {
        val encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name())
        return projectUrl
            .trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "/realtime/v1/websocket?apikey=$encodedKey&vsn=2.0.0"
    }
}
