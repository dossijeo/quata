package com.quata.core.platform

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Android effect coordinator shared by Feed and Official media viewers. */
class AndroidMediaFileExportService(
    context: Context,
    private val shareService: ShareService,
    client: OkHttpClient = defaultAndroidMediaExportClient(),
    private val materializer: MediaFileMaterializer = AndroidMediaFileMaterializer(context, client),
    private val shareLeaseRetentionMillis: Long = ShareLeaseRetentionMillis,
) : MediaFileExportService {
    private val appContext = context.applicationContext

    override suspend fun export(
        descriptor: MediaFileExportDescriptor,
        action: MediaFileExportAction,
    ): PlatformResult<Unit> {
        val lease = when (val materialized = materializer.materialize(descriptor)) {
            is PlatformResult.Success -> materialized.value
            is PlatformResult.Failure -> return materialized
            PlatformResult.Cancelled -> return PlatformResult.Cancelled
            PlatformResult.Unsupported -> return PlatformResult.Unsupported
        }
        val result = try {
            when (action) {
                MediaFileExportAction.Download -> saveToDownloads(lease.file)
                MediaFileExportAction.Share -> shareService.share(SharePayload(files = listOf(lease.file)))
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            lease.release()
            throw cancelled
        }
        if (action == MediaFileExportAction.Share && result is PlatformResult.Success) {
            // A selected receiver can open the granted URI just after the chooser callback.
            // Release the one private copy after a bounded lease even if no later export runs.
            AndroidMediaExportLeaseScope.launch {
                delay(shareLeaseRetentionMillis)
                lease.release()
            }
        } else {
            lease.release()
        }
        return result
    }

    private suspend fun saveToDownloads(file: PlatformFile): PlatformResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val local = localMediaExportPath(file) ?: error("android_media_export_local_file_missing")
            val name = file.displayName.safeMediaExportName()
            val mime = file.mimeType ?: "application/octet-stream"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val destination = appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("android_media_export_download_insert_failed")
                try {
                    appContext.contentResolver.openOutputStream(destination)?.use { output ->
                        local.inputStream().use { it.copyTo(output) }
                    } ?: error("android_media_export_download_stream_failed")
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    appContext.contentResolver.update(destination, values, null, null)
                } catch (failure: Throwable) {
                    appContext.contentResolver.delete(destination, null, null)
                    throw failure
                }
            } else {
                val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!directory.exists() && !directory.mkdirs()) error("android_media_export_download_directory_failed")
                local.inputStream().use { input -> FileOutputStream(File(directory, name)).use(input::copyTo) }
            }
            PlatformResult.Success(Unit)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            PlatformResult.Failure(failure.message ?: "android_media_export_download_failed")
        }
    }
}

/** Cancellable, bounded Android materializer with an explicit private-file lease. */
class AndroidMediaFileMaterializer(
    context: Context,
    private val client: OkHttpClient = defaultAndroidMediaExportClient(),
) : MediaFileMaterializer {
    private val exportDirectory = File(context.applicationContext.cacheDir, ExportDirectory)

    override suspend fun materialize(
        descriptor: MediaFileExportDescriptor,
    ): PlatformResult<MaterializedMediaFileLease> {
        val source = descriptor.reference.toHttpUrlOrNull()
            ?: return PlatformResult.Failure("android_media_export_url_invalid")
        val loopback = source.host in setOf("localhost", "127.0.0.1", "::1", "10.0.2.2")
        if (source.username.isNotEmpty() || source.password.isNotEmpty() ||
            (source.scheme != "https" && !(source.scheme == "http" && loopback))
        ) return PlatformResult.Failure("android_media_export_url_invalid")

        return try {
            withContext(Dispatchers.IO) {
                pruneExpiredLeases()
                if (!exportDirectory.exists() && !exportDirectory.mkdirs()) {
                    error("android_media_export_cache_create_failed")
                }
            }
            val request = Request.Builder()
                .url(source)
                .header("Accept", descriptor.mimeType)
                .header("Cache-Control", "no-store")
                .build()
            PlatformResult.Success(downloadToLease(request, descriptor))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            PlatformResult.Failure(failure.message ?: "android_media_export_failed")
        }
    }

    private suspend fun downloadToLease(
        request: Request,
        descriptor: MediaFileExportDescriptor,
    ): MaterializedMediaFileLease = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, failure: IOException) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }

            override fun onResponse(call: Call, response: Response) {
                var target: File? = null
                var finalFile: File? = null
                try {
                    response.use {
                        if (!it.isSuccessful || it.isRedirect) error("android_media_export_http_${it.code}")
                        val body = it.body ?: error("android_media_export_body_missing")
                        val responseMime = body.contentType()?.toString()?.substringBefore(';')?.trim()?.lowercase()
                        val expectedMime = descriptor.mimeType.substringBefore(';').trim().lowercase()
                        if (responseMime != expectedMime) error("android_media_export_mime_invalid")
                        val declaredLength = body.contentLength()
                        if (declaredLength == 0L || declaredLength > MaxExportBytes) error("android_media_export_size_invalid")
                        target = File.createTempFile("media-", ".part", exportDirectory)
                        body.byteStream().use { input ->
                            FileOutputStream(requireNotNull(target)).use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var total = 0L
                                while (true) {
                                    if (call.isCanceled()) throw kotlinx.coroutines.CancellationException()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    total += read
                                    if (total > MaxExportBytes) error("android_media_export_size_invalid")
                                    output.write(buffer, 0, read)
                                }
                                if (total <= 0L) error("android_media_export_size_invalid")
                            }
                        }
                    }
                    finalFile = File(
                        exportDirectory,
                        "${requireNotNull(target).nameWithoutExtension}-${descriptor.displayName.safeMediaExportName()}",
                    )
                    if (!requireNotNull(target).renameTo(finalFile)) error("android_media_export_cache_move_failed")
                    val materialized = PlatformFile(
                        reference = Uri.fromFile(finalFile).toString(),
                        displayName = descriptor.displayName.safeMediaExportName(),
                        mimeType = descriptor.mimeType,
                        sizeBytes = finalFile.length(),
                    )
                    if (continuation.isActive) {
                        val ownedFile = finalFile
                        continuation.resume(
                            MaterializedMediaFileLease(materialized) {
                                ownedFile.takeIf { it.parentFile == exportDirectory }?.delete()
                            },
                        )
                    } else {
                        finalFile.delete()
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    target?.delete()
                    finalFile?.delete()
                    if (continuation.isActive) continuation.cancel(cancelled)
                } catch (failure: Throwable) {
                    target?.delete()
                    finalFile?.delete()
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }
            }
        })
    }

    private fun pruneExpiredLeases() {
        val cutoff = System.currentTimeMillis() - ShareLeaseRetentionMillis
        exportDirectory.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach(File::delete)
    }
}

private fun defaultAndroidMediaExportClient(): OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .build()

private fun localMediaExportPath(file: PlatformFile): File? = Uri.parse(file.reference).path?.let(::File)

private fun String?.safeMediaExportName(): String = this.orEmpty().trim()
    .substringAfterLast('/').substringAfterLast('\\')
    .map { if (it.isLetterOrDigit() || it in "._ -") it else '_' }
    .joinToString("").take(128).ifBlank { "quata-media" }

private val AndroidMediaExportLeaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private const val ExportDirectory = "quata_media_exports"
private const val MaxExportBytes = 50L * 1024L * 1024L
private const val ShareLeaseRetentionMillis = 60L * 60L * 1000L
