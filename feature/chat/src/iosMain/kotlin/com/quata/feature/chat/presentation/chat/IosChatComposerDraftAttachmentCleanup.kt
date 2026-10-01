package com.quata.feature.chat.presentation.chat

import com.quata.core.platform.IosFileCacheService
import com.quata.core.platform.PlatformResult

/** Synchronous bridge used by the Swift logout owner before it tears down the authenticated host. */
fun clearIosChatComposerDraftAttachments(actorId: String, generation: Long): Boolean =
    when (
        IosFileCacheService().removeByPrefixNow(
            chatComposerDraftAttachmentGenerationPrefix(actorId, generation),
        )
    ) {
        is PlatformResult.Success -> true
        is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported -> false
    }
