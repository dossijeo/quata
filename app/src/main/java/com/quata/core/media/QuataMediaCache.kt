package com.quata.core.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File

@OptIn(UnstableApi::class)
object QuataMediaCache {
    private const val VIDEO_CACHE_DIR = "quata_video_cache"
    private const val MAX_VIDEO_CACHE_BYTES = 256L * 1024L * 1024L
    private const val MAX_CACHE_FILE_AGE_MILLIS = 14L * 24L * 60L * 60L * 1000L
    private const val VIDEO_HTTP_TIMEOUT_MILLIS = 20_000

    @Volatile
    private var videoCache: SimpleCache? = null

    private val videoCacheLock = Any()

    fun videoMediaSourceFactory(context: Context): DefaultMediaSourceFactory {
        val appContext = context.applicationContext
        val upstreamFactory = DefaultDataSource.Factory(
            appContext,
            DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(VIDEO_HTTP_TIMEOUT_MILLIS)
                .setReadTimeoutMs(VIDEO_HTTP_TIMEOUT_MILLIS)
        )
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(videoCache(appContext))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return DefaultMediaSourceFactory(cacheDataSourceFactory)
    }

    fun removeVideo(context: Context, videoUrl: String?) {
        val cleanUrl = videoUrl?.trim()?.takeIf { it.isNotBlank() } ?: return
        val appContext = context.applicationContext
        val cacheKey = CacheKeyFactory.DEFAULT.buildCacheKey(DataSpec(Uri.parse(cleanUrl)))
        runCatching {
            videoCache(appContext).removeResource(cacheKey)
        }
    }

    fun pruneStaleFiles(directory: File, maxAgeMillis: Long = MAX_CACHE_FILE_AGE_MILLIS) {
        val now = System.currentTimeMillis()
        if (!directory.exists()) return
        directory.walkBottomUp()
            .filter { file -> file.isFile && now - file.lastModified() > maxAgeMillis }
            .forEach { file -> runCatching { file.delete() } }
    }

    private fun videoCache(context: Context): SimpleCache =
        videoCache ?: synchronized(videoCacheLock) {
            videoCache ?: buildVideoCache(context).also { videoCache = it }
        }

    private fun buildVideoCache(context: Context): SimpleCache {
        val directory = File(context.cacheDir, VIDEO_CACHE_DIR).apply { mkdirs() }
        pruneStaleFiles(directory)
        return SimpleCache(
            directory,
            LeastRecentlyUsedCacheEvictor(MAX_VIDEO_CACHE_BYTES),
            StandaloneDatabaseProvider(context)
        )
    }
}
