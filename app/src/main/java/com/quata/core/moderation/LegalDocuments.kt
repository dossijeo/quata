package com.quata.core.moderation

import android.content.Context
import com.quata.core.localization.QuataLanguageManager
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import java.io.File

/** Materializes bundled, language-specific legal documents for the shared document boundary. */
object LegalDocuments {
    private const val AssetDirectory = "legal"
    private const val MimeTypeDocx =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    fun platformFile(context: Context, document: LegalDocument): PlatformResult<PlatformFile> =
        runCatching {
            val assetName = document.assetName(QuataLanguageManager.currentLanguage)
            val localFile = File(context.cacheDir, "legal_documents/$assetName")
            localFile.parentFile?.mkdirs()
            context.assets.open("$AssetDirectory/$assetName").use { input ->
                localFile.outputStream().use { output -> input.copyTo(output) }
            }
            PlatformResult.Success(
                PlatformFile(
                    reference = localFile.toURI().toString(),
                    displayName = localFile.name,
                    mimeType = MimeTypeDocx,
                    sizeBytes = localFile.length().takeIf { it > 0L },
                )
            )
        }.getOrElse { PlatformResult.Failure(it.message) }
}
