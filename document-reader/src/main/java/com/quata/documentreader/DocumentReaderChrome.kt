package com.quata.documentreader

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

object DocumentReaderChrome {
    @JvmStatic
    fun apply(activity: Activity, root: View?) {
        val isDarkMode = isDarkMode(activity)
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkMode
            isAppearanceLightNavigationBars = !isDarkMode
        }

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
        @Suppress("DEPRECATION")
        backButton?.setOnClickListener { activity.onBackPressed() }
        downloadButton?.apply {
            setImageResource(R.drawable.ic_download_24)
            contentDescription = activity.getString(R.string.quata_document_reader_download)
            setOnClickListener { download(activity, filePath, fileName) }
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
    fun color(activity: Activity, @ColorRes colorRes: Int): Int =
        ContextCompat.getColor(activity, colorRes)

    private fun isDarkMode(activity: Activity): Boolean =
        activity.intent.getBooleanExtra(QuataDocumentReader.EXTRA_IS_DARK_MODE, false)

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
