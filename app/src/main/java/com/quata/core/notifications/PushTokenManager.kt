package com.quata.core.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.quata.core.config.AppConfig
import com.quata.core.session.SessionManager
import com.quata.data.supabase.SupabaseCommunityApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PushTokenManager(
    private val appContext: Context,
    private val supabaseApi: SupabaseCommunityApi,
    private val sessionManager: SessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun syncCurrentToken() {
        if (AppConfig.USE_MOCK_BACKEND) return
        val session = sessionManager.currentSession() ?: return
        scope.launch {
            val token = runCatching { FirebaseMessaging.getInstance().token.await() }
                .onFailure { Log.w(TAG, "Could not get FCM token", it) }
                .getOrNull()
                ?: preferences.getString(KEY_PENDING_TOKEN, null)
                ?: return@launch
            registerToken(session.userId, token)
        }
    }

    fun onNewToken(token: String) {
        if (AppConfig.USE_MOCK_BACKEND) return
        preferences.edit().putString(KEY_PENDING_TOKEN, token).apply()
        syncCurrentToken()
    }

    private suspend fun registerToken(profileId: String, token: String) {
        runCatching {
            supabaseApi.registerPushToken(profileId = profileId, token = token)
            preferences.edit().putString(KEY_REGISTERED_TOKEN, token).remove(KEY_PENDING_TOKEN).apply()
        }.onFailure {
            Log.w(TAG, "Could not register FCM token", it)
        }
    }

    private companion object {
        const val TAG = "QuataPushToken"
        const val PREFS_NAME = "quata_push_tokens"
        const val KEY_PENDING_TOKEN = "pending_token"
        const val KEY_REGISTERED_TOKEN = "registered_token"
    }
}
