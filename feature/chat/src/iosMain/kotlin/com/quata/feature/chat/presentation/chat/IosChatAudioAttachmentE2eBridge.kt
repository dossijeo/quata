package com.quata.feature.chat.presentation.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.Foundation.NSProcessInfo

@Composable
internal fun IosChatAudioAttachmentE2eBridge(actions: ChatAudioAttachmentActions) {
    DisposableEffect(
        actions.file.reference,
        actions.file.displayName,
        actions.file.mimeType,
        actions.playback,
        actions.toggle,
        actions.seekToFraction,
    ) {
        val dispose = IosChatAudioAttachmentE2eRegistry.install(
            reference = actions.file.reference,
            name = actions.file.displayName.orEmpty(),
            mimeType = actions.file.mimeType.orEmpty(),
            isPlaying = actions.playback.isPlaying,
            positionMillis = actions.playback.positionMillis,
            durationMillis = actions.playback.durationMillis,
            toggle = actions.toggle,
            seekToFraction = actions.seekToFraction,
        )
        onDispose(dispose)
    }
}

fun iosChatAudioAttachmentE2eHandleUrl(url: String): Boolean =
    IosChatAudioAttachmentE2eRegistry.handleUrl(url)

private object IosChatAudioAttachmentE2eRegistry {
    private data class Entry(
        val key: String,
        val reference: String,
        val name: String,
        val mimeType: String,
        val isPlaying: Boolean,
        val positionMillis: Long,
        val durationMillis: Long,
        val toggle: () -> Unit,
        val seekToFraction: (Float) -> Unit,
    )

    private data class PendingAction(
        val action: String,
        val needle: String?,
        val fraction: Float,
    )

    private val entries = linkedMapOf<String, Entry>()
    private val pendingActions = mutableListOf<PendingAction>()

    fun install(
        reference: String,
        name: String,
        mimeType: String,
        isPlaying: Boolean,
        positionMillis: Long,
        durationMillis: Long,
        toggle: () -> Unit,
        seekToFraction: (Float) -> Unit,
    ): () -> Unit {
        if (!isOptedIn()) return {}
        val key = "$reference\n$name\n$mimeType"
        entries[key] = Entry(
            key = key,
            reference = reference,
            name = name,
            mimeType = mimeType,
            isPlaying = isPlaying,
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            toggle = toggle,
            seekToFraction = seekToFraction,
        )
        applyPendingActions(entries.getValue(key))
        return { entries.remove(key) }
    }

    fun handleUrl(url: String): Boolean {
        if (!isOptedIn()) return false
        val query = url.substringAfter("#chat-audio-e2e?", missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?: return false
        val params = query.split('&')
            .mapNotNull { token ->
                val key = token.substringBefore('=', missingDelimiterValue = "").urlDecode()
                val value = token.substringAfter('=', missingDelimiterValue = "").urlDecode()
                key.takeIf { it.isNotBlank() }?.let { it to value }
            }
            .toMap()
        val action = params["action"]?.lowercase().orEmpty()
        val fraction = params["fraction"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
        val target = find(params["needle"])
        if (target == null) {
            if (action == "toggle" || action == "seek") {
                pendingActions += PendingAction(action, params["needle"], fraction)
            }
            return true
        }
        when (action) {
            "toggle" -> target.toggle()
            "seek" -> target.seekToFraction(fraction)
            else -> return true
        }
        return true
    }

    private fun find(needle: String?): Entry? {
        val query = needle.orEmpty().trim().lowercase()
        if (entries.isEmpty()) return null
        if (query.isBlank()) return entries.values.last()
        return entries.values.firstOrNull { entry ->
            entry.name.lowercase().contains(query) ||
                entry.reference.lowercase().contains(query)
        }
    }

    private fun applyPendingActions(entry: Entry) {
        if (pendingActions.isEmpty()) return
        val iterator = pendingActions.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (find(pending.needle)?.key != entry.key) continue
            when (pending.action) {
                "toggle" -> entry.toggle()
                "seek" -> entry.seekToFraction(pending.fraction)
            }
            iterator.remove()
        }
    }

    private fun isOptedIn(): Boolean {
        val environment = NSProcessInfo.processInfo.environment
        return environment["QUATA_IOS_CHAT_AUDIO_ATTACHMENT_E2E"]?.toString() == "1"
    }
}

private fun String.urlDecode(): String =
    replace("+", " ").percentDecode()

private fun String.percentDecode(): String {
    val bytes = mutableListOf<Byte>()
    val output = StringBuilder()
    var index = 0
    while (index < length) {
        val char = this[index]
        if (char == '%' && index + 2 < length) {
            val byte = substring(index + 1, index + 3).toIntOrNull(16)
            if (byte != null) {
                bytes += byte.toByte()
                index += 3
                continue
            }
        }
        if (bytes.isNotEmpty()) {
            output.append(bytes.toByteArray().decodeToString())
            bytes.clear()
        }
        output.append(char)
        index += 1
    }
    if (bytes.isNotEmpty()) {
        output.append(bytes.toByteArray().decodeToString())
    }
    return output.toString()
}
