package com.quata.documentreader

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.quata.documentreader.adapters_All.Adapter_Print_PdfDocument
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

object DocumentReaderChrome {
    @JvmStatic
    fun apply(activity: Activity, root: View?) {
        val isDarkMode = isDarkMode(activity)
        activity.applyDocumentReaderEdgeToEdge(isDarkMode)

        root ?: return
        val originalLeft = root.paddingLeft
        val originalTop = root.paddingTop
        val originalRight = root.paddingRight
        val originalBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                originalLeft + systemBars.left,
                originalTop + systemBars.top,
                originalRight + systemBars.right,
                originalBottom + systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    @JvmStatic
    fun configureHeader(
        activity: Activity,
        root: View?,
        titleView: TextView?,
        backButton: View?,
        downloadButton: ImageView?,
        fileName: String?,
        filePath: String?
    ) {
        apply(activity, root)
        titleView?.text = fileName.orEmpty()
        backButton?.setOnClickListener {
            (activity as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
                ?: activity.finish()
        }
        downloadButton?.apply {
            setImageResource(R.drawable.ic_download_24)
            contentDescription = activity.getString(R.string.quata_document_reader_download)
            setOnClickListener { download(activity, filePath, fileName) }
        }
    }

    @JvmStatic
    fun configureHeader(
        activity: Activity,
        root: View?,
        titleView: TextView?,
        backButton: View?,
        printButton: ImageView?,
        downloadButton: ImageView?,
        fileName: String?,
        filePath: String?
    ) {
        configureHeader(activity, root, titleView, backButton, downloadButton, fileName, filePath)
        printButton?.apply {
            setImageResource(R.drawable.ic_baseline_print)
            contentDescription = activity.getString(R.string.quata_document_reader_print)
            setOnClickListener { printFile(activity, filePath, fileName) }
        }
    }

    @JvmStatic
    fun download(activity: Activity, filePath: String?, fileName: String?) {
        val source = filePath?.let(::File)
        if (source == null || !source.exists() || !source.isFile) {
            Toast.makeText(activity, R.string.quata_document_reader_download_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val safeName = sanitizeFileName(fileName?.takeIf { it.isNotBlank() } ?: source.name)
        val result = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(activity, source, safeName)
            } else {
                saveLegacy(source, safeName)
            }
        }

        Toast.makeText(
            activity,
            if (result.isSuccess) R.string.quata_document_reader_download_saved else R.string.quata_document_reader_download_failed,
            Toast.LENGTH_SHORT
        ).show()
    }

    @JvmStatic
    fun printFile(activity: Activity, filePath: String?, fileName: String?) {
        val source = filePath?.let(::File)
        if (source == null || !source.exists() || !source.isFile) {
            Toast.makeText(activity, R.string.quata_document_reader_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        if (source.extension.equals("pdf", ignoreCase = true)) {
            val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
            printManager.print(
                fileName?.takeIf { it.isNotBlank() } ?: activity.getString(R.string.quata_document_reader_print_job),
                Adapter_Print_PdfDocument(activity, source.absolutePath),
                PrintAttributes.Builder().build()
            )
            return
        }

        val uri = runCatching {
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", source)
        }.getOrElse { Uri.fromFile(source) }
        val printIntent = Intent("android.intent.action.PRINT").apply {
            setDataAndType(uri, mimeFor(source.name))
            putExtra(Intent.EXTRA_TITLE, fileName?.takeIf { it.isNotBlank() } ?: source.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeFor(source.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            activity.startActivity(printIntent)
        }.recoverCatching {
            activity.startActivity(Intent.createChooser(viewIntent, activity.getString(R.string.quata_document_reader_print)))
        }.onFailure {
            Toast.makeText(activity, R.string.quata_document_reader_unsupported, Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun printPlainText(activity: Activity, title: String?, text: String?) {
        printHtml(
            activity = activity,
            title = title,
            html = "<html><body><pre style=\"white-space:pre-wrap;font-family:monospace;font-size:12px;\">" +
                escapeHtml(text.orEmpty()) +
                "</pre></body></html>"
        )
    }

    @JvmStatic
    fun printCsvRows(activity: Activity, title: String?, rows: List<List<String>>) {
        val tableRows = rows.joinToString(separator = "") { row ->
            "<tr>" + row.joinToString(separator = "") { cell ->
                "<td style=\"border:1px solid #ddd;padding:6px;\">" + escapeHtml(cell) + "</td>"
            } + "</tr>"
        }
        printHtml(
            activity = activity,
            title = title,
            html = "<html><body><table style=\"border-collapse:collapse;font-family:sans-serif;font-size:11px;\">" +
                tableRows +
                "</table></body></html>"
        )
    }

    @JvmStatic
    fun color(activity: Activity, @ColorRes colorRes: Int): Int =
        ContextCompat.getColor(activity, colorRes)

    private fun printHtml(activity: Activity, title: String?, html: String) {
        val webView = WebView(activity)
        webView.settings.apply {
            javaScriptEnabled = false
            allowContentAccess = false
            allowFileAccess = false
            blockNetworkLoads = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printTitle = title?.takeIf { it.isNotBlank() }
                    ?: activity.getString(R.string.quata_document_reader_print_job)
                printManager.print(
                    printTitle,
                    view.createPrintDocumentAdapter(printTitle),
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                        .build()
                )
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun isDarkMode(activity: Activity): Boolean =
        activity.intent.getBooleanExtra(QuataDocumentReader.EXTRA_IS_DARK_MODE, false)

    private fun Activity.applyDocumentReaderEdgeToEdge(isDarkMode: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkMode
            isAppearanceLightNavigationBars = !isDarkMode
        }
    }

    private fun saveWithMediaStore(context: Context, source: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeFor(displayName))
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        try {
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(source).use { input -> input.copyTo(output) }
            } ?: error("Could not open download output stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (throwable: Throwable) {
            resolver.delete(uri, null, null)
            throw throwable
        }
    }

    private fun saveLegacy(source: File, displayName: String): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.exists()) downloads.mkdirs()
        val target = uniqueFile(downloads, displayName)
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun uniqueFile(directory: File, displayName: String): File {
        val base = displayName.substringBeforeLast('.', missingDelimiterValue = displayName)
        val ext = displayName.substringAfterLast('.', missingDelimiterValue = "")
        var candidate = File(directory, displayName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, if (ext.isBlank()) "$base ($index)" else "$base ($index).$ext")
            index++
        }
        return candidate
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "document" }

    private fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun mimeFor(name: String): String =
        when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "pdf" -> "application/pdf"
            "rtf" -> "application/rtf"
            "csv" -> "text/csv"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
}
