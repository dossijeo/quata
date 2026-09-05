package com.quata.documentreader.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.URLUtil
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.quata.core.platform.DocumentPreviewKind
import com.quata.documentreader.DocumentReaderChrome
import com.quata.documentreader.QuataDocumentReader
import com.quata.documentreader.QuataDocumentReaderTheme
import com.quata.documentreader.R
import com.quata.documentreader.xs.constant.MainConstant
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

class All_Document_Reader_Activity : AppCompatActivity() {
    private var fileName: String? = null
    private var mimeType: String? = null
    private var prepareGeneration = 0
    private var activeSourceUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        QuataDocumentReaderTheme.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_reader_loading)
        DocumentReaderChrome.apply(this, findViewById(R.id.documentReaderLoadingRoot))

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val generation = ++prepareGeneration
        fileName = intent.getStringExtra(QuataDocumentReader.EXTRA_FILE_NAME)
        mimeType = intent.getStringExtra(QuataDocumentReader.EXTRA_MIME_TYPE)
            ?: intent.type

        val directPath = intent.getStringExtra("path")
            ?.takeIf { it.isNotBlank() && File(it).exists() }
        if (directPath != null) {
            openLocalFile(directPath, generation)
            return
        }

        val source = sourceUriFrom(intent)
        if (source == null) {
            showOpenError()
            return
        }
        activeSourceUri = source

        updateLoadingText(R.string.quata_document_reader_preparing)

        thread(name = "QuataDocumentReaderPrepare") {
            val localPath = runCatching { resolveUriToLocalPath(source) }.getOrNull()
            runOnUiThread {
                if (generation != prepareGeneration || isFinishing || isDestroyed) {
                    localPath?.let(::deleteOwnedTempPath)
                    return@runOnUiThread
                }
                if (localPath.isNullOrBlank()) {
                    showOpenErrorOrChooser(source)
                } else {
                    openLocalFile(localPath, generation)
                }
            }
        }
    }

    private fun sourceUriFrom(intent: Intent): Uri? {
        if (intent.action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { return it }
        }
        return intent.data
    }

    private fun resolveUriToLocalPath(uri: Uri): String? {
        val scheme = uri.scheme?.lowercase(Locale.US)
        return when (scheme) {
            null, "" -> uri.toString()
            "file" -> uri.path
            "content" -> copyContentUri(uri)
            else -> null
        }
    }

    private fun copyContentUri(uri: Uri): String? {
        val resolvedName = fileName
            ?: displayNameFor(uri)
            ?: URLUtil.guessFileName(uri.toString(), null, mimeType)
        fileName = resolvedName
        val target = targetFileFor(resolvedName, mimeType)
        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    copyBounded(input, output)
                }
            } ?: return null
        }.getOrElse {
            runCatching { target.delete() }
            return null
        }
        QuataDocumentReader.pruneOwnedTempFiles(this)
        return target.path
    }

    private fun deleteOwnedTempPath(path: String) {
        val tempDir = File(cacheDir, "quata_document_reader").canonicalFile
        val target = runCatching { File(path).canonicalFile }.getOrNull() ?: return
        if (target.parentFile == tempDir && target.exists()) {
            runCatching { target.delete() }
        }
    }

    private fun targetFileFor(name: String?, mimeType: String?): File {
        val tempDir = File(cacheDir, "quata_document_reader").apply {
            mkdirs()
        }
        QuataDocumentReader.pruneOwnedTempFiles(this)
        val safeName = sanitizeFileName(name)
        val extension = safeName.substringAfterLast('.', missingDelimiterValue = "")
        val baseName = safeName.substringBeforeLast('.', missingDelimiterValue = safeName).ifBlank { "document" }
        val finalName = if (extension.isNotBlank()) {
            "${UUID.randomUUID()}-$baseName.$extension"
        } else {
            val inferred = QuataDocumentReader.extensionForMimeType(mimeType)
            if (inferred == null) "${UUID.randomUUID()}-$baseName" else "${UUID.randomUUID()}-$baseName.$inferred"
        }
        return File(tempDir, finalName)
    }

    private fun openLocalFile(path: String, generation: Int) {
        if (generation != prepareGeneration || isFinishing || isDestroyed) return
        val resolvedName = fileName?.takeIf { it.isNotBlank() } ?: File(path).name
        val fallbackUri = safeFallbackUri(path)
        val descriptor = QuataDocumentReader.previewDescriptor(Uri.fromFile(File(path)), resolvedName, mimeType)
        val targetActivity = when {
            descriptor.kind == DocumentPreviewKind.Pdf -> PDF_Reader_Activity::class.java
            descriptor.kind == DocumentPreviewKind.RichText -> ViewRtf_Activity::class.java
            descriptor.extension == "csv" -> CSVViewer_Activity::class.java
            descriptor.isTextLike -> QuataTextDocumentActivity::class.java
            descriptor.isPreviewable -> ViewFiles_Activity::class.java
            else -> null
        }

        if (targetActivity == null) {
            showOpenErrorOrChooser(activeSourceUri ?: path.toUri())
            return
        }

        val viewerIntent = Intent(this, targetActivity).apply {
            putExtra("name", resolvedName)
            putExtra("fromConverterApp", true)
            putExtra("fileType", MainConstant.getFileType(path).toString())
            putExtra("fromAppActivity", true)
            putExtra("path", path)
            putExtra(QuataDocumentReader.EXTRA_FILE_NAME, resolvedName)
            putExtra(QuataDocumentReader.EXTRA_MIME_TYPE, mimeType)
            putExtra(QuataDocumentReader.EXTRA_FALLBACK_URI, fallbackUri.toString())
            if (QuataDocumentReader.isOwnedTempFile(this@All_Document_Reader_Activity, path)) {
                putExtra(QuataDocumentReader.EXTRA_OWNED_TEMP_PATH, path)
            }
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (fallbackUri.scheme.equals("content", ignoreCase = true)) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        QuataDocumentReaderTheme.copyThemeExtra(intent, viewerIntent)
        startActivity(viewerIntent)
        finish()
    }

    private fun safeFallbackUri(path: String): Uri {
        val source = activeSourceUri
        val sourceScheme = source?.scheme?.lowercase(Locale.US)
        if (source != null && (sourceScheme == "content" || sourceScheme == "file")) {
            return source
        }
        return Uri.fromFile(File(path))
    }

    private fun displayNameFor(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    private fun updateLoadingText(stringRes: Int) {
        findViewById<TextView>(R.id.documentReaderLoadingText)?.setText(stringRes)
    }

    private fun showOpenError() {
        Toast.makeText(this, R.string.cannot_open_document, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showOpenErrorOrChooser(source: Uri?) {
        if (source != null && openWithSystemChooser(source)) return
        showOpenError()
    }

    private fun openWithSystemChooser(source: Uri): Boolean {
        val scheme = source.scheme?.lowercase(Locale.US)
        if (scheme != "content" && scheme != "file") return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(source, mimeType ?: "*/*")
            if (scheme == "content") {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return runCatching {
            startActivity(Intent.createChooser(intent, fileName ?: "document"))
            finish()
            true
        }.getOrDefault(false)
    }

    private fun copyBounded(input: java.io.InputStream, output: FileOutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read.toLong()
            if (total > MaxDocumentReaderBytes) {
                error("document_reader_size_invalid")
            }
            output.write(buffer, 0, read)
        }
        if (total <= 0L) {
            error("document_reader_empty")
        }
    }

    private fun sanitizeFileName(value: String?): String {
        val fallback = "document${QuataDocumentReader.extensionForMimeType(mimeType)?.let { ".$it" }.orEmpty()}"
        return value
            ?.replace(Regex("""[\\/:*?"<>|]"""), "_")
            ?.trim()
            ?.take(160)
            ?.ifBlank { fallback }
            ?: fallback
    }

    companion object {
        private const val MaxDocumentReaderBytes = 50L * 1024L * 1024L
    }
}
