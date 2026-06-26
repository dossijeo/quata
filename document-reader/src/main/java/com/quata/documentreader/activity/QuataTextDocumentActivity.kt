package com.quata.documentreader.activity

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.quata.documentreader.DocumentReaderChrome
import com.quata.documentreader.QuataDocumentReaderTheme
import com.quata.documentreader.R
import java.io.File

class QuataTextDocumentActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        QuataDocumentReaderTheme.apply(this)
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra("path").orEmpty()
        val title = intent.getStringExtra("name") ?: File(path).name
        val text = runCatching { File(path).readText() }.getOrDefault("")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@QuataTextDocumentActivity, R.color.bg))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val chrome = topBar(title)
        root.addView(chrome.container)
        root.addView(textContentView(text))
        setContentView(root)
        DocumentReaderChrome.configureHeader(
            this,
            root,
            chrome.title,
            chrome.back,
            chrome.download,
            title,
            path
        )
    }

    private fun topBar(title: String): HeaderViews {
        val textColor = ContextCompat.getColor(this, R.color.title_info_color)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@QuataTextDocumentActivity, R.color.bg))
            setPadding(8.dp(), 9.dp(), 8.dp(), 9.dp())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                62.dp()
            )
        }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.back_arrow)
            background = null
            scaleType = ImageView.ScaleType.CENTER
            setColorFilter(ContextCompat.getColor(this@QuataTextDocumentActivity, R.color.navigation_text_color_selected))
            layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp())
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 8.dp()
                rightMargin = 8.dp()
            }
        }
        val download = ImageButton(this).apply {
            background = null
            scaleType = ImageView.ScaleType.CENTER
            setColorFilter(ContextCompat.getColor(this@QuataTextDocumentActivity, R.color.navigation_text_color_selected))
            layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp())
        }
        container.apply {
            addView(back)
            addView(titleView)
            addView(download)
        }
        return HeaderViews(container, titleView, back, download)
    }

    private data class HeaderViews(
        val container: LinearLayout,
        val title: TextView,
        val back: ImageButton,
        val download: ImageButton
    )

    private fun textContentView(text: String): ScrollView =
        ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
            val contentColor = ContextCompat.getColor(this@QuataTextDocumentActivity, R.color.title_info_color)
            val column = LinearLayout(this@QuataTextDocumentActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dp(), 14.dp(), 16.dp(), 24.dp())
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            chunkText(text.ifBlank { getString(R.string.quata_document_reader_empty) }).forEach { chunk ->
                column.addView(TextView(this@QuataTextDocumentActivity).apply {
                    this.text = chunk
                    textSize = 14f
                    typeface = Typeface.MONOSPACE
                    includeFontPadding = false
                    setTextColor(contentColor)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
            }
            addView(column)
        }

    private fun chunkText(text: String): List<String> {
        if (text.length <= TEXT_CHUNK_SIZE) return listOf(text)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + TEXT_CHUNK_SIZE).coerceAtMost(text.length)
            if (end < text.length) {
                val newline = text.lastIndexOf('\n', end - 1).takeIf { it > start }
                if (newline != null && newline - start > TEXT_CHUNK_SIZE / 2) {
                    end = newline + 1
                }
            }
            chunks += text.substring(start, end)
            start = end
        }
        return chunks
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private companion object {
        const val TEXT_CHUNK_SIZE = 4_000
    }
}
