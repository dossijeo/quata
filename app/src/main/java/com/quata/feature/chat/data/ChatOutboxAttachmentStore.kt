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
        input.use { sourceStream -> target.outputStream().use(sourceStream::copyTo) }
        Uri.fromFile(target).toString()
    }

    fun remove(stagedUri: String?) {
        val uri = stagedUri?.let(Uri::parse) ?: return
        if (uri.scheme == ContentResolver.SCHEME_FILE && uri.path?.contains("chat_outbox_attachments") == true) {
            runCatching { File(uri.path.orEmpty()).delete() }
        }
    }
}
