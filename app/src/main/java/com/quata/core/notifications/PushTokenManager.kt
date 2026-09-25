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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class PushTokenManager(
    private val appContext: Context,
    private val supabaseApi: SupabaseCommunityApi,
    private val sessionManager: SessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutationMutex = Mutex()
    @Volatile private var loggingOutProfileId: String? = null

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

    suspend fun unregisterCurrentToken() {
        if (AppConfig.USE_MOCK_BACKEND) return
        val session = sessionManager.currentSession()
        mutationMutex.withLock {
            unregisterTokenLocked(session?.userId, session?.bearerToken).getOrThrow()
        }
    }

    /** Completes the authenticated server removal before callers discard [bearerToken]. */
    suspend fun unregisterTokenForProfileBeforeLogout(profileId: String?, bearerToken: String?): Result<Unit> {
        if (AppConfig.USE_MOCK_BACKEND) return Result.success(Unit)
        return mutationMutex.withLock {
            loggingOutProfileId = profileId
            unregisterTokenLocked(profileId, bearerToken).also { result ->
                if (result.isFailure) loggingOutProfileId = null
            }
        }
    }

    private suspend fun unregisterTokenLocked(profileId: String?, bearerToken: String?): Result<Unit> {
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }
            .getOrNull()
            ?: preferences.getString(KEY_REGISTERED_TOKEN, null)
            ?: preferences.getString(KEY_PENDING_TOKEN, null)
        if (!profileId.isNullOrBlank() && !token.isNullOrBlank()) {
            val remote = runCatching { supabaseApi.unregisterPushToken(profileId, token, bearerToken) }
                .onFailure { Log.w(TAG, "Could not unregister FCM token", it) }
            if (remote.isFailure) return remote.map { Unit }
        }
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
            .onFailure { Log.w(TAG, "Could not delete local FCM token", it) }
        preferences.edit().clear().apply()
        return Result.success(Unit)
    }

    /** Re-establishes the prior registration when a later logout effect could not be committed. */
    suspend fun restoreTokenForProfile(profileId: String) {
        if (AppConfig.USE_MOCK_BACKEND) return
        mutationMutex.withLock {
            try {
                val token = runCatching { FirebaseMessaging.getInstance().token.await() }
                    .getOrNull()
                    ?: preferences.getString(KEY_REGISTERED_TOKEN, null)
                    ?: preferences.getString(KEY_PENDING_TOKEN, null)
                    ?: return@withLock
                registerTokenLocked(profileId, token)
            } finally {
                if (loggingOutProfileId == profileId) loggingOutProfileId = null
            }
        }
    }

    fun logoutCompleted(profileId: String) {
        if (loggingOutProfileId == profileId) loggingOutProfileId = null
    }

    private suspend fun registerToken(profileId: String, token: String) {
        mutationMutex.withLock {
            if (loggingOutProfileId != null || sessionManager.currentSession()?.userId != profileId) return@withLock
            registerTokenLocked(profileId, token)
        }
    }

    private suspend fun registerTokenLocked(profileId: String, token: String) {
        runCatching {
            supabaseApi.registerPushToken(profileId = profileId, token = token)
            preferences.edit().putString(KEY_REGISTERED_TOKEN, token).remove(KEY_PENDING_TOKEN).apply()
        }.onFailure {
            preferences.edit().putString(KEY_PENDING_TOKEN, token).apply()
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
