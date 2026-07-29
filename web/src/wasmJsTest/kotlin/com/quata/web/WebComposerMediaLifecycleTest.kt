package com.quata.web

import com.quata.core.platform.PlatformFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebComposerMediaLifecycleTest {
    @Test
    fun replacementDefersOldLocalBlobReleaseUntilTheReplacementCommit() {
        val released = mutableListOf<String>()
        val lifecycle = WebComposerMediaLifecycle()
        val first = owned("blob:https://quata.test/first", released)
        val second = owned("blob:https://quata.test/second", released)

        assertTrue(lifecycle.replace(isVideo = false, replacement = first))
        lifecycle.releaseReplaced()
        assertTrue(released.isEmpty())

        assertTrue(lifecycle.replace(isVideo = false, replacement = second))
        assertTrue(released.isEmpty(), "the old preview still owns its source before Compose commits")
        lifecycle.releaseReplaced()

        assertEquals(listOf(first.file.reference), released)
        assertEquals(second, lifecycle.selected(isVideo = false))
    }

    @Test
    fun clearReleasesTheSelectedLocalBlobExactlyOnce() {
        val released = mutableListOf<String>()
        val lifecycle = WebComposerMediaLifecycle()
        val selected = owned("blob:https://quata.test/clear", released)

        lifecycle.replace(isVideo = false, replacement = selected)
        lifecycle.releaseReplaced()
        assertTrue(lifecycle.replace(isVideo = false, replacement = null))
        lifecycle.releaseReplaced()
        lifecycle.dispose()

        assertEquals(listOf(selected.file.reference), released)
        assertEquals(null, lifecycle.selected(isVideo = false))
    }

    @Test
    fun cancellationDoesNotClearOrReleaseTheCurrentSelectionAndCloseDoes() {
        val released = mutableListOf<String>()
        val lifecycle = WebComposerMediaLifecycle()
        val selected = owned("blob:https://quata.test/cancel", released)

        lifecycle.replace(isVideo = true, replacement = selected)
        lifecycle.releaseReplaced()
        assertFalse(lifecycle.replace(isVideo = true, replacement = selected), "a cancelled picker sends no replacement")
        assertEquals(selected, lifecycle.selected(isVideo = true))
        assertTrue(released.isEmpty())

        lifecycle.dispose()
        lifecycle.dispose()
        assertEquals(listOf(selected.file.reference), released)
    }

    @Test
    fun remoteUrlHasNoRevocationCapability() {
        val lifecycle = WebComposerMediaLifecycle()
        val remote = WebComposerMediaSelection.remote(PlatformFile("https://cdn.quata.test/image.jpg"))

        lifecycle.replace(isVideo = false, replacement = remote)
        lifecycle.releaseReplaced()
        lifecycle.replace(isVideo = false, replacement = null)
        lifecycle.releaseReplaced()
        lifecycle.dispose()

        // The default selection has no owner callback: it models a remote URL and cannot call
        // URL.revokeObjectURL merely because Composer is clearing it.
        assertEquals(null, lifecycle.selected(isVideo = false))
    }

    @Test
    fun lateSelectionAfterDisposeIsReleasedButCannotResurrectComposer() {
        val released = mutableListOf<String>()
        val lifecycle = WebComposerMediaLifecycle()
        val visible = owned("blob:https://quata.test/visible", released)
        val late = owned("blob:https://quata.test/late", released)

        lifecycle.replace(isVideo = false, replacement = visible)
        lifecycle.releaseReplaced()
        lifecycle.dispose()

        assertFalse(lifecycle.replace(isVideo = false, replacement = late))
        lifecycle.releaseReplaced()
        lifecycle.dispose()

        assertEquals(listOf(visible.file.reference, late.file.reference), released)
        assertEquals(null, lifecycle.selected(isVideo = false))
    }

    @Test
    fun distinctOwnedSelectionsWithTheSameUrlReplaceByIdentityWithoutLeakingEitherToken() {
        val released = mutableListOf<String>()
        val lifecycle = WebComposerMediaLifecycle()
        val first = owned("blob:https://quata.test/same", released, token = "first")
        val second = owned("blob:https://quata.test/same", released, token = "second")

        lifecycle.replace(isVideo = false, replacement = first)
        lifecycle.releaseReplaced()
        assertTrue(lifecycle.replace(isVideo = false, replacement = second))
        lifecycle.releaseReplaced()
        lifecycle.dispose()

        assertEquals(listOf("first", "second"), released)
    }

    private fun owned(
        reference: String,
        released: MutableList<String>,
        token: String = reference,
    ): WebComposerMediaSelection {
        val file = PlatformFile(reference)
        return WebComposerMediaSelection.ownedLocal(file) { released += token }
    }
}
