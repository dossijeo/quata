package com.quata.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.FileOutputStream
import java.io.File
import java.security.MessageDigest

@OptIn(UnstableApi::class)
object QuataMediaCache {
    private const val VIDEO_CACHE_DIR = "quata_video_cache"
    private const val VIDEO_THUMBNAIL_CACHE_DIR = "quata_video_thumbnail_cache"
    private const val MAX_VIDEO_CACHE_BYTES = 256L * 1024L * 1024L
    private const val MAX_VIDEO_THUMBNAIL_CACHE_BYTES = 64L * 1024L * 1024L
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
        runCatching { videoThumbnailFile(appContext, cleanUrl).delete() }
    }

    fun cachedVideoThumbnail(context: Context, videoUri: String): Bitmap? {
        val file = videoThumbnailFile(context.applicationContext, videoUri)
        if (!file.exists() || file.length() <= 0L) return null
        file.setLastModified(System.currentTimeMillis())
        return runCatching {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                inSampleSize = 1
            }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > VIDEO_THUMBNAIL_MAX_DIMENSION) {
                sampleSize *= 2
            }
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            )
        }.getOrNull()
    }

    private const val VIDEO_THUMBNAIL_MAX_DIMENSION = 2048

    fun cacheVideoThumbnail(context: Context, videoUri: String, bitmap: Bitmap): Bitmap {
        val appContext = context.applicationContext
        val file = videoThumbnailFile(appContext, videoUri)
        runCatching {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 84, output)
            }
            pruneThumbnailCache(appContext)
        }
        return bitmap
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

    private fun videoThumbnailFile(context: Context, videoUri: String): File {
        val directory = File(context.filesDir, VIDEO_THUMBNAIL_CACHE_DIR).apply { mkdirs() }
        pruneStaleFiles(directory)
        return File(directory, "${sha256("v2:${videoUri.trim()}")}.jpg")
    }

    private fun pruneThumbnailCache(context: Context) {
        val directory = File(context.filesDir, VIDEO_THUMBNAIL_CACHE_DIR)
        pruneStaleFiles(directory)
        val files = directory.listFiles()?.filter { it.isFile }.orEmpty()
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= MAX_VIDEO_THUMBNAIL_CACHE_BYTES) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (totalBytes <= MAX_VIDEO_THUMBNAIL_CACHE_BYTES) return
            totalBytes -= file.length()
            runCatching { file.delete() }
        }
    }

    private fun sha256(value: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val digits = "0123456789abcdef".toCharArray()
        return buildString(hash.size * 2) {
            hash.forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                append(digits[unsigned ushr 4])
                append(digits[unsigned and 0x0f])
            }
        }
    }
}
