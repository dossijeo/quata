package com.quata.feature.chat.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

internal class ChatOutboxAttachmentStore(private val context: Context) {
    suspend fun stage(clientMessageId: String, sourceUri: String?): String? = withContext(Dispatchers.IO) {
        if (sourceUri.isNullOrBlank()) return@withContext null
        val source = Uri.parse(sourceUri)
        if (source.scheme == ContentResolver.SCHEME_FILE && source.path?.let(::File)?.isFile == true) return@withContext sourceUri
        val directory = File(context.filesDir, "chat_outbox_attachments").apply { mkdirs() }
        val target = File(directory, clientMessageId.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        val input = when (source.scheme?.lowercase()) {
            ContentResolver.SCHEME_FILE -> source.path?.let(::File)?.let(::FileInputStream)
            else -> context.contentResolver.openInputStream(source)
        } ?: error("No se pudo conservar el adjunto para enviarlo")
        runCatching {
            input.use { sourceStream ->
                target.outputStream().use { output ->
                    sourceStream.copyTo(output, limitBytes = MAX_STAGED_ATTACHMENT_BYTES)
                }
            }
        }.onFailure {
            target.delete()
        }.getOrThrow()
        Uri.fromFile(target).toString()
    }

    fun remove(stagedUri: String?) {
        val uri = stagedUri?.let(Uri::parse) ?: return
        if (uri.scheme == ContentResolver.SCHEME_FILE && uri.path?.contains("chat_outbox_attachments") == true) {
            runCatching { File(uri.path.orEmpty()).delete() }
        }
    }

    private fun java.io.InputStream.copyTo(output: java.io.OutputStream, limitBytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) return
            total += read
            if (total > limitBytes) error("chat_outbox_attachment_too_large")
            output.write(buffer, 0, read)
        }
    }

    companion object {
        private const val MAX_STAGED_ATTACHMENT_BYTES = 50L * 1024L * 1024L
    }
}
