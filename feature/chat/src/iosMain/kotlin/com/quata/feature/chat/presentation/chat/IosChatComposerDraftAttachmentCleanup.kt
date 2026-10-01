package com.quata.feature.chat.presentation.chat

import com.quata.core.platform.IosFileCacheService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.Foundation.NSUserDefaults

/** Records cleanup custody before Swift retires the generation and removes its draft metadata. */
fun prepareIosChatComposerDraftAttachmentRetirement(actorId: String, generation: Long) {
    val defaults = NSUserDefaults.standardUserDefaults
    val cursorKey = ChatComposerDraftStore.retiredCleanupCursorKey(actorId)
    if (defaults.stringForKey(cursorKey)?.toLongOrNull() != null) return
    // A stale absence observation may race common journal migration. Zero can repeat
    // idempotent cleanup, but it can never advance past a generation still in custody.
    defaults.setObject("0", forKey = cursorKey)
}

/** Starts the already-journaled cleanup without crossing an attachment effect still in flight. */
fun clearIosChatComposerDraftAttachments(actorId: String, generation: Long) {
    CoroutineScope(Dispatchers.Default).launch {
        InProcessChatComposerAttachmentExecutionLock.withLock(actorId, generation) {
            IosFileCacheService().removeByPrefix(
                chatComposerDraftAttachmentGenerationPrefix(actorId, generation),
            )
        }
    }
}
