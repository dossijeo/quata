package com.quata.feature.chat.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.PrefixClearableFileCacheService
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App-private durable bytes for a selected Chat composer attachment before Send. */
internal class AndroidChatComposerFileCacheService(context: Context) : PrefixClearableFileCacheService {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "chat_composer_drafts")

    override suspend fun store(cacheKey: String, file: PlatformFile): PlatformResult<PlatformFile> = withContext(Dispatchers.IO) {
        if (!cacheKey.isSafeCacheKey()) return@withContext PlatformResult.Failure("file_cache_key_invalid")
        val target = File(root.apply { mkdirs() }, "$cacheKey.bin")
        val source = Uri.parse(file.reference)
        val input = when (source.scheme?.lowercase()) {
            ContentResolver.SCHEME_FILE -> source.path?.let(::File)?.takeIf(File::isFile)?.let(::FileInputStream)
            else -> appContext.contentResolver.openInputStream(source)
        } ?: return@withContext PlatformResult.Failure("file_cache_source_missing")
        runCatching {
            input.use { sourceStream ->
                target.outputStream().use { output -> sourceStream.copyBoundedTo(output) }
            }
            PlatformResult.Success(
                file.copy(reference = Uri.fromFile(target).toString(), sizeBytes = target.length()),
            )
        }.getOrElse { error ->
            target.delete()
            PlatformResult.Failure(error.message ?: "file_cache_write_failed")
        }
    }

    override suspend fun get(cacheKey: String): PlatformResult<PlatformFile> = withContext(Dispatchers.IO) {
        if (!cacheKey.isSafeCacheKey()) return@withContext PlatformResult.Failure("file_cache_key_invalid")
        val file = File(root, "$cacheKey.bin")
        if (file.isFile) PlatformResult.Success(PlatformFile(Uri.fromFile(file).toString(), sizeBytes = file.length()))
        else PlatformResult.Failure("file_cache_miss")
    }

    override suspend fun remove(cacheKey: String): PlatformResult<Unit> = withContext(Dispatchers.IO) {
        if (!cacheKey.isSafeCacheKey()) return@withContext PlatformResult.Failure("file_cache_key_invalid")
        val file = File(root, "$cacheKey.bin")
        if (!file.exists() || file.delete()) PlatformResult.Success(Unit)
        else PlatformResult.Failure("file_cache_remove_failed")
    }

    override suspend fun removeByPrefix(prefix: String): PlatformResult<Unit> = withContext(Dispatchers.IO) {
        if (!prefix.isSafeCachePrefix()) return@withContext PlatformResult.Failure("file_cache_prefix_invalid")
        val removed = root.listFiles()
            .orEmpty()
            .filter { it.name.startsWith(prefix) }
            .all(File::delete)
        if (removed) PlatformResult.Success(Unit) else PlatformResult.Failure("file_cache_remove_prefix_failed")
    }

    private fun java.io.InputStream.copyBoundedTo(output: java.io.OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) return
            total += read
            check(total <= MaxAttachmentBytes) { "chat_composer_attachment_too_large" }
            output.write(buffer, 0, read)
        }
    }

    private companion object {
        const val MaxAttachmentBytes = 50L * 1024L * 1024L
    }
}

private fun String.isSafeCacheKey(): Boolean = matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,119}"))
private fun String.isSafeCachePrefix(): Boolean =
    isNotEmpty() && length <= 120 && all { it.isLetterOrDigit() || it in "._:-" }
