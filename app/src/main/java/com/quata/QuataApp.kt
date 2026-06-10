package com.quata

import android.app.Activity
import android.app.Application
import android.os.Bundle
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.quata.core.di.AppContainer
import com.quata.core.media.QuataMediaCache
import com.quata.core.notifications.BetterMessagesBackgroundPollScheduler
import com.quata.feature.chat.domain.ChatPollingMode
import java.io.File

class QuataApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set
    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        BetterMessagesBackgroundPollScheduler(this).schedule()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities += 1
                container.chatRepository.setAppForeground(true)
                container.chatRepository.setPollingMode(ChatPollingMode.MEDIUM)
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) {
                    container.chatRepository.setAppForeground(false)
                    val mode = if (container.sessionManager.isLoggedIn()) {
                        ChatPollingMode.RELAXED
                    } else {
                        ChatPollingMode.MINIMAL
                    }
                    container.chatRepository.setPollingMode(mode)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
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
        const val IMAGE_CACHE_DIR = "quata_image_cache"
        const val MAX_IMAGE_CACHE_BYTES = 128L * 1024L * 1024L
    }
}
