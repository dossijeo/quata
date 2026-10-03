package com.quata.feature.chat.presentation.chat

import android.content.Context
import com.quata.feature.chat.domain.ChatRepository
import java.io.File

private const val DocumentRetryEvidencePreferences = "quata_chat_evidence"
private const val DocumentRetryEvidenceOptInKey = "documentRetryLocal.optIn"
private const val DocumentRetryEvidenceOptIn = "I_ACCEPT_ANDROID_DOCUMENT_RETRY_LOCAL_FIXTURE"
private const val DocumentRetryEvidenceAsset = "legal/privacy_es.docx"

/** Local, explicit alternative to remote fixture seeding when Supabase Auth is unavailable. */
internal fun androidDocumentRetryEvidenceRepositoryOrNull(context: Context): ChatRepository? {
    val optedIn = context.getSharedPreferences(DocumentRetryEvidencePreferences, Context.MODE_PRIVATE)
        .getString(DocumentRetryEvidenceOptInKey, null) == DocumentRetryEvidenceOptIn
    if (!optedIn) return null
    val fixture = androidDocumentRetryEvidenceFile(context)
    val prepared = runCatching {
        fixture.parentFile?.mkdirs()
        context.assets.open(DocumentRetryEvidenceAsset).use { input ->
            fixture.outputStream().use(input::copyTo)
        }
        fixture.isFile && fixture.length() > 0L
    }.getOrDefault(false)
    return if (prepared) DocumentRetryEvidenceChatRepository(fixture.absolutePath) else null
}

internal fun androidDocumentRetryEvidenceFile(context: Context): File =
    File(File(context.cacheDir, "document-retry-fixture"), DocumentRetryEvidenceDocumentName)
