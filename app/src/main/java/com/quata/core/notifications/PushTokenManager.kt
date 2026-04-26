package com.quata.core.notifications

import com.quata.core.network.supabase.SupabaseApi
import com.quata.core.network.supabase.SupabasePushTokenRequest

class PushTokenManager(private val supabaseApi: SupabaseApi) {
    suspend fun registerToken(userId: String, token: String) {
        supabaseApi.registerPushToken(SupabasePushTokenRequest(userId = userId, token = token))
    }
}
