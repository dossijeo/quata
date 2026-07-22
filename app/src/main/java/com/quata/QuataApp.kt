package com.quata

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.quata.core.di.AppContainer
import com.quata.core.media.QuataMediaCache
import com.quata.core.model.AuthSession
import com.quata.feature.chat.data.ChatMessageStateWorkScheduler
import com.quata.feature.chat.data.ChatOutboxWorkScheduler
import com.quata.core.session.AuthState
import com.quata.feature.externalshare.ShareConversationShortcuts
import com.quata.feature.externalshare.ShareTargetAvailability
import com.google.android.play.core.splitcompat.SplitCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.io.File

class QuataApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startedActivities = 0
    private var resumedActivities = 0
    @Volatile
    var isAppForeground: Boolean = false
        private set
    private var supabaseSessionRefreshJob: Job? = null

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        SplitCompat.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ShareTargetAvailability.setEnabled(this, container.sessionManager.currentSession() != null)
        ChatMessageStateWorkScheduler.ensurePeriodic(this)
        ChatOutboxWorkScheduler.ensurePeriodic(this)
        observeSupabaseAuthState()
        observeShareConversationShortcuts()
        refreshSupabaseSessionIfNeeded()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities += 1
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) {
                    isAppForeground = false
                    container.chatRepository.setAppForeground(false)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) {
                resumedActivities += 1
                isAppForeground = true
                container.chatRepository.setAppForeground(true)
                refreshSupabaseSessionIfNeeded()
                container.pushTokenManager.syncCurrentToken()
            }

            override fun onActivityPaused(activity: Activity) {
                resumedActivities = (resumedActivities - 1).coerceAtLeast(0)
                if (resumedActivities == 0) {
                    isAppForeground = false
                    container.chatRepository.setAppForeground(false)
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun refreshSupabaseSessionIfNeeded() {
        appScope.launch {
            refreshSupabaseSession()
            scheduleNextSupabaseSessionRefresh()
        }
    }

    private suspend fun refreshSupabaseSession(): AuthSession? =
        runCatching { container.supabaseCommunityApi.ensureFreshSession() }
            .onFailure { Log.w(TAG, "Could not refresh Supabase session", it) }
            .getOrNull()

    private fun observeSupabaseAuthState() {
        appScope.launch {
            container.sessionManager.authState.collect {
                scheduleNextSupabaseSessionRefresh()
            }
        }
    }

    private fun observeShareConversationShortcuts() {
        appScope.launch {
            container.sessionManager.authState.collectLatest { authState ->
                ShareTargetAvailability.setEnabled(this@QuataApp, authState is AuthState.LoggedIn)
                if (authState is AuthState.LoggedIn) {
                    container.chatRepository.observeConversations().collect { conversations ->
                        ShareConversationShortcuts.publish(
                            context = this@QuataApp,
                            conversations = conversations,
                            currentUserId = authState.userId
                        )
                    }
                } else {
                    ShareConversationShortcuts.clear(this@QuataApp)
                }
            }
        }
    }

    private fun scheduleNextSupabaseSessionRefresh(allowImmediate: Boolean = true) {
        supabaseSessionRefreshJob?.cancel()
        supabaseSessionRefreshJob = null
        val session = container.sessionManager.currentSession()
            ?.takeIf { it.isSupabaseAuthenticated() }
            ?: return
        val expiresAt = session.expiresAt
        val delayMillis = if (expiresAt == null) {
            DEFAULT_SUPABASE_REFRESH_DELAY_MILLIS
        } else {
            val nowEpochSeconds = System.currentTimeMillis() / 1000L
            val refreshAtSeconds = expiresAt - SUPABASE_REFRESH_LEEWAY_SECONDS
            val rawDelayMillis = (refreshAtSeconds - nowEpochSeconds).coerceAtLeast(0L) * 1000L
            if (rawDelayMillis == 0L && !allowImmediate) {
                SUPABASE_REFRESH_RETRY_MILLIS
            } else {
                rawDelayMillis
            }
        }
        supabaseSessionRefreshJob = appScope.launch {
            delay(delayMillis)
            refreshSupabaseSession()
            supabaseSessionRefreshJob = null
            scheduleNextSupabaseSessionRefresh(allowImmediate = false)
        }
    }

    override fun newImageLoader(): ImageLoader {
        val imageCacheDir = File(filesDir, IMAGE_CACHE_DIR)
        migrateLegacyImageCache(imageCacheDir)
        imageCacheDir.mkdirs()
        QuataMediaCache.pruneStaleFiles(imageCacheDir)
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCacheDir)
                    .maxSizeBytes(MAX_IMAGE_CACHE_BYTES)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }

    private fun migrateLegacyImageCache(targetDir: File) {
        val legacyDir = File(cacheDir, IMAGE_CACHE_DIR)
        if (!legacyDir.exists() || legacyDir.listFiles().isNullOrEmpty()) return
        if (targetDir.exists() && !targetDir.listFiles().isNullOrEmpty()) return

        targetDir.parentFile?.mkdirs()
        if (!targetDir.exists() && runCatching { legacyDir.renameTo(targetDir) }.getOrDefault(false)) {
            return
        }
        runCatching {
            legacyDir.copyRecursively(targetDir, overwrite = false)
        }
    }

    private companion object {
        const val TAG = "QuataApp"
        const val IMAGE_CACHE_DIR = "quata_image_cache"
        const val MAX_IMAGE_CACHE_BYTES = 128L * 1024L * 1024L
        const val SUPABASE_REFRESH_LEEWAY_SECONDS = 300L
        const val DEFAULT_SUPABASE_REFRESH_DELAY_MILLIS = 30L * 60L * 1000L
        const val SUPABASE_REFRESH_RETRY_MILLIS = 60_000L
    }
}
