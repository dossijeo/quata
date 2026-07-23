package com.quata.core.moderation

import android.content.Context
import androidx.core.content.FileProvider
import com.quata.core.localization.QuataLanguageManager
import com.quata.documentreader.QuataDocumentReader
import java.io.File

/** Opens the bundled, language-specific legal documents with the in-app document reader. */
object LegalDocuments {
    private const val AssetDirectory = "legal"
    private const val MimeTypeDocx =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    fun open(context: Context, document: LegalDocument, isDarkMode: Boolean) {
        runCatching {
            val assetName = document.assetName(QuataLanguageManager.currentLanguage)
            val localFile = File(context.cacheDir, "legal_documents/$assetName")
            localFile.parentFile?.mkdirs()
            context.assets.open("$AssetDirectory/$assetName").use { input ->
                localFile.outputStream().use { output -> input.copyTo(output) }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                localFile
            )
            QuataDocumentReader.open(
                context = context,
                uri = uri,
                fileName = localFile.name,
                mimeType = MimeTypeDocx,
                isDarkMode = isDarkMode
            )
        }
    }
}
