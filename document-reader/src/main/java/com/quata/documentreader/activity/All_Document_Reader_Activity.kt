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
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
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

        updateLoadingText(
            if (source.scheme.equals("http", ignoreCase = true) || source.scheme.equals("https", ignoreCase = true)) {
                R.string.quata_document_reader_downloading
            } else {
                R.string.quata_document_reader_preparing
            }
        )

        thread(name = "QuataDocumentReaderPrepare") {
            val localPath = runCatching { resolveUriToLocalPath(source) }.getOrNull()
            runOnUiThread {
                if (generation != prepareGeneration || isFinishing || isDestroyed) return@runOnUiThread
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
            "https" -> downloadUri(uri)
            else -> uri.path
        }
    }

    private fun copyContentUri(uri: Uri): String? {
        val resolvedName = fileName
            ?: displayNameFor(uri)
            ?: URLUtil.guessFileName(uri.toString(), null, mimeType)
        fileName = resolvedName
        val target = targetFileFor(resolvedName, mimeType)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output ->
                copyBounded(input, output)
            }
        } ?: return null
        return target.path
    }

    private fun downloadUri(uri: Uri): String? {
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "QuataDocumentReader")
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) return null
            val declaredLength = connection.contentLengthLong
            if (declaredLength == 0L || declaredLength > MaxDocumentReaderBytes) return null
            val contentType = connection.contentType?.takeIf { it.isNotBlank() }
            if (mimeType.isNullOrBlank()) mimeType = contentType
            val resolvedName = fileName
                ?: URLUtil.guessFileName(uri.toString(), connection.getHeaderField("Content-Disposition"), mimeType)
            fileName = resolvedName
            val target = targetFileFor(resolvedName, mimeType)
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    copyBounded(input, output)
                }
            }
            target.path
        } finally {
            connection.disconnect()
        }
    }

    private fun targetFileFor(name: String?, mimeType: String?): File {
        val tempDir = File(cacheDir, "quata_document_reader").apply {
            mkdirs()
        }
        val safeName = sanitizeFileName(name)
        val extension = safeName.substringAfterLast('.', missingDelimiterValue = "")
        val finalName = if (extension.isNotBlank()) {
            safeName
        } else {
            val inferred = QuataDocumentReader.extensionForMimeType(mimeType)
            if (inferred == null) safeName else "$safeName.$inferred"
        }
        return File(tempDir, finalName)
    }

    private fun openLocalFile(path: String, generation: Int) {
        if (generation != prepareGeneration || isFinishing || isDestroyed) return
        val resolvedName = fileName?.takeIf { it.isNotBlank() } ?: File(path).name
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
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        QuataDocumentReaderTheme.copyThemeExtra(intent, viewerIntent)
        startActivity(viewerIntent)
        finish()
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
