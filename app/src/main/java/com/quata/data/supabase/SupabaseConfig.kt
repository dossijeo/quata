package com.quata.data.supabase

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
    val storageUrl: String get() = projectUrl.trimEnd('/') + "/storage/v1"
    val realtimeUrl: String get() = projectUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"
}
