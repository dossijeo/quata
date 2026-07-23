package com.quata.core.media

/** Platform-owned media references are represented as stable strings in shared code. */
data class MediaSource(val value: String, val mimeType: String? = null)

interface FilePicker {
    suspend fun pick(mimeTypes: List<String>): MediaSource?
}

interface ShareController {
    suspend fun share(text: String, media: MediaSource? = null)
}
