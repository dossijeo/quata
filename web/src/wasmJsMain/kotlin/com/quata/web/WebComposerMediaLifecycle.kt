package com.quata.web

import com.quata.core.platform.PlatformFile

/**
 * A media reference together with the capability that created it.
 *
 * A remote URL deliberately has the default no-op releaser.  Browser-created Blob URLs instead
 * carry the picker/camera capability that issued them, so Composer can never revoke an arbitrary
 * `blob:` reference merely because its text looks local.
 */
class WebComposerMediaSelection private constructor(
    val file: PlatformFile,
    private val releaseOwnedReference: (() -> Unit)?,
) {
    private var released = false

    internal fun releaseOnce() {
        if (!released) {
            released = true
            releaseOwnedReference?.invoke()
        }
    }

    companion object {
        /** A server/CDN reference has no browser-object ownership capability. */
        fun remote(file: PlatformFile): WebComposerMediaSelection = WebComposerMediaSelection(file, null)

        /** Only the browser route may pair a picker/camera result with its issuing capability. */
        internal fun ownedLocal(
            file: PlatformFile,
            releaseOwnedReference: () -> Unit,
        ): WebComposerMediaSelection = WebComposerMediaSelection(file, releaseOwnedReference)
    }
}

/**
 * Keeps transient browser media alive while it is selected, including when the user temporarily
 * switches Composer type. Replaced references are released only after Compose has committed the
 * replacement; [dispose] handles route close and cancellation before that commit can occur.
 */
internal class WebComposerMediaLifecycle {
    private var image: WebComposerMediaSelection? = null
    private var video: WebComposerMediaSelection? = null
    private val pendingRelease = mutableListOf<WebComposerMediaSelection>()
    private var disposed = false

    fun selected(isVideo: Boolean): WebComposerMediaSelection? = if (isVideo) video else image

    /** Returns true when the visible Composer state changed and needs a post-commit release pass. */
    fun replace(isVideo: Boolean, replacement: WebComposerMediaSelection?): Boolean {
        if (disposed) {
            // A late picker/camera callback must not resurrect a closed Composer. It still owns a
            // browser resource, however, so consume its capability exactly once.
            replacement?.releaseOnce()
            return false
        }
        val previous = selected(isVideo)
        if (previous === replacement) return false
        if (isVideo) video = replacement else image = replacement
        previous?.let(pendingRelease::add)
        return true
    }

    /** Invoke from a post-commit Compose effect, after the preview has received its new source. */
    fun releaseReplaced() {
        val pending = pendingRelease.toList()
        pendingRelease.clear()
        pending.forEach(WebComposerMediaSelection::releaseOnce)
    }

    /** Idempotent route-close/cancellation cleanup. */
    fun dispose() {
        if (disposed) return
        disposed = true
        releaseReplaced()
        image?.releaseOnce()
        video?.releaseOnce()
        image = null
        video = null
    }
}
