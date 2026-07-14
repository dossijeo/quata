package com.quata.core.text

import android.content.Context
import com.quata.R

fun Context.localizedChatPreview(raw: String): String =
    localizedSosPreview(raw) ?: when (raw.trim()) {
        ATTACHMENT_PREVIEW_PHOTO -> getString(R.string.conversation_preview_photo)
        ATTACHMENT_PREVIEW_VIDEO -> getString(R.string.conversation_preview_video)
        ATTACHMENT_PREVIEW_DOCUMENT -> getString(R.string.conversation_preview_document)
        ATTACHMENT_PREVIEW_VOICE_NOTE,
        LEGACY_VOICE_NOTE_PREVIEW -> getString(R.string.conversation_preview_voice_note)
        ATTACHMENT_PREVIEW_FILE,
        LEGACY_ATTACHMENT_PREVIEW -> getString(R.string.conversation_preview_file)
        else -> raw
    }

private const val ATTACHMENT_PREVIEW_PHOTO = "[QUATA_ATTACHMENT:photo]"
private const val ATTACHMENT_PREVIEW_VIDEO = "[QUATA_ATTACHMENT:video]"
private const val ATTACHMENT_PREVIEW_DOCUMENT = "[QUATA_ATTACHMENT:document]"
private const val ATTACHMENT_PREVIEW_VOICE_NOTE = "[QUATA_ATTACHMENT:voice_note]"
private const val ATTACHMENT_PREVIEW_FILE = "[QUATA_ATTACHMENT:file]"
private const val LEGACY_VOICE_NOTE_PREVIEW = "[QUATA_NOTIFICATION:chat_voice_note]"
private const val LEGACY_ATTACHMENT_PREVIEW = "[QUATA_NOTIFICATION:chat_attachment]"
