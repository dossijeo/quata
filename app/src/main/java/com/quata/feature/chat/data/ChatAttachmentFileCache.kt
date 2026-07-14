package com.quata.feature.chat.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.quata.core.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

internal class ChatAttachmentFileCache(
    private val appContext: Context,
    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {
    suspend fun prefetchAndResolve(profileId: String, messages: List<Message>): List<Message> =
        withContext(Dispatchers.IO) {
            prune(profileId)
            val candidates = messages.takeLast(MAX_PREFETCH_ATTACHMENTS).map { it.id }.toSet()
            val semaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)
            coroutineScope {
                messages.map { message ->
                    async {
                        val cachedFile = if (message.id in candidates) {
                            semaphore.withPermit { ensureCached(profileId, message) }
                        } else {
                            cachedFile(profileId, message)
                        }
                        if (cachedFile != null) message.withLocalAttachment(cachedFile) else message
                    }
                }.awaitAll()
            }
        }

    suspend fun resolveCached(profileId: String, messages: List<Message>): List<Message> =
        withContext(Dispatchers.IO) {
            messages.map { message ->
                val cachedFile = cachedFile(profileId, message)
                if (cachedFile != null) message.withLocalAttachment(cachedFile) else message
            }
        }

    private fun ensureCached(profileId: String, message: Message): File? {
        val remoteUrl = message.remoteAttachmentUrl() ?: return null
        val target = attachmentFile(profileId, message, remoteUrl)
        if (target.exists() && target.length() > 0L) return target
        val temp = File(target.parentFile, "${target.name}.tmp")
        return runCatching {
            target.parentFile?.mkdirs()
            val request = Request.Builder().url(remoteUrl).get().build()
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Attachment download failed: ${response.code}")
                val body = response.body ?: error("Attachment download body is empty")
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output -> input.copyTo(output) }
                }
            }
            if (temp.length() <= 0L) error("Attachment download produced an empty file")
            if (target.exists()) target.delete()
            temp.renameTo(target)
            target
        }.onFailure {
            temp.delete()
        }.getOrNull()
    }

    private fun cachedFile(profileId: String, message: Message): File? {
        val remoteUrl = message.remoteAttachmentUrl() ?: return null
        val file = attachmentFile(profileId, message, remoteUrl)
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    private fun attachmentFile(profileId: String, message: Message, remoteUrl: String): File {
        val directory = File(File(appContext.filesDir, CACHE_DIRECTORY), "attachments/${profileId.safePathSegment()}")
        val extension = extensionFor(message, remoteUrl)
        val suffix = sha256(remoteUrl).take(16)
        val id = message.id.safePathSegment().ifBlank { suffix }
        val name = if (extension.isBlank()) "$id-$suffix" else "$id-$suffix.$extension"
        return File(directory, name)
    }

    private fun prune(profileId: String) {
        val directory = File(File(appContext.filesDir, CACHE_DIRECTORY), "attachments/${profileId.safePathSegment()}")
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }.orEmpty()
        val cutoff = System.currentTimeMillis() - MAX_CACHE_AGE_MILLIS
        files.filter { it.lastModified() < cutoff }.forEach(File::delete)
        var total = files.filter(File::exists).sumOf(File::length)
        if (total <= MAX_CACHE_BYTES) return
        files.filter(File::exists).sortedBy(File::lastModified).forEach { file ->
            if (total <= MAX_CACHE_BYTES) return
            total -= file.length()
            file.delete()
        }
    }

    private fun extensionFor(message: Message, remoteUrl: String): String {
        val fromName = message.attachmentName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
        if (fromName != null) return fromName.safeExtension()
        val fromMime = message.attachmentMimeType
            ?.substringBefore(";")
            ?.lowercase(Locale.US)
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?.takeIf { it.isNotBlank() }
        if (fromMime != null) return fromMime.safeExtension()
        return remoteUrl
            .substringBefore('?')
            .substringAfterLast('/', missingDelimiterValue = "")
            .substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.safeExtension()
            .orEmpty()
    }

    private fun Message.remoteAttachmentUrl(): String? {
        val uri = attachmentUri?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val scheme = runCatching { Uri.parse(uri).scheme?.lowercase(Locale.US) }.getOrNull()
        return uri.takeIf { scheme == "http" || scheme == "https" }
    }

    private fun Message.withLocalAttachment(file: File): Message =
        copy(attachmentUri = Uri.fromFile(file).toString())

    private fun String.safePathSegment(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun String.safeExtension(): String =
        lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "").take(12)

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

    private companion object {
        const val CACHE_DIRECTORY = "supabase_chat_cache_v1"
        const val MAX_PREFETCH_ATTACHMENTS = 12
        const val MAX_CONCURRENT_DOWNLOADS = 3
        const val MAX_CACHE_BYTES = 96L * 1024L * 1024L
        const val MAX_CACHE_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L
    }
}
