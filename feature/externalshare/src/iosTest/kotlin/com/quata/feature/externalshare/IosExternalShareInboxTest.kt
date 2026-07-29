@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.quata.feature.externalshare

import com.quata.core.data.toFoundationData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * Native filesystem coverage for the App Group queue protocol. These tests use a private
 * temporary directory; they do not assert that a signed share extension is installed.
 */
class IosExternalShareInboxTest {
    private val fileManager = NSFileManager.defaultManager

    @Test
    fun `claims pending manifests by creation time and cleans each claim idempotently`() = withTemporaryAppGroup { root ->
        writePending(root, id = "later", createdAt = 2_000, text = "second")
        writePending(root, id = "first", createdAt = 1_000, text = "first")
        val inbox = inbox(root, now = { 10_000 })

        val first = assertNotNull(inbox.claim())
        assertEquals("first", first.payload.id)
        first.cleanup()
        first.cleanup()
        assertEquals(emptyList(), directoryNames("$root/ExternalShares/processing"))

        val second = assertNotNull(inbox.claim())
        assertEquals("later", second.payload.id)
        second.cleanup()
        assertEquals(emptyList(), directoryNames("$root/ExternalShares/pending"))
        assertEquals(emptyList(), directoryNames("$root/ExternalShares/processing"))
    }

    @Test
    fun `fresh lease cannot be stolen but a later inbox recovers an expired lease`() = withTemporaryAppGroup { root ->
        writePending(root, id = "recoverable", createdAt = 100, text = "recover me")
        val firstNow = 10_000L
        val firstInbox = inbox(root, now = { firstNow }, owner = "a".repeat(32))
        val firstClaim = assertNotNull(firstInbox.claim("recoverable"))
        assertNull(inbox(root, now = { firstNow + ExternalShareClaimLeaseMillis - 1 }).claim("recoverable"))

        val recovered = assertNotNull(
            inbox(root, now = { firstNow + ExternalShareClaimLeaseMillis }, owner = "b".repeat(32)).claim("recoverable"),
        )
        assertEquals("recoverable", recovered.payload.id)
        // Old-generation cleanup cannot remove the lease acquired by the recovering process.
        firstClaim.cleanup()
        assertEquals(1, directoryNames("$root/ExternalShares/processing").size)
        recovered.cleanup()
        assertEquals(emptyList(), directoryNames("$root/ExternalShares/processing"))
    }

    @Test
    fun `rejects unsafe requested IDs and traversal manifests without escaping the app group`() = withTemporaryAppGroup { root ->
        writePending(root, id = "safe", createdAt = 100, text = "valid")
        writePending(
            root,
            id = "traversal",
            createdAt = 10,
            text = "",
            attachmentsJson = """[{"relativePath":"../outside.txt","name":"outside.txt","mimeType":"text/plain"}]""",
        )
        val outsidePath = "$root/outside.txt"
        writeText(outsidePath, "must survive")
        val inbox = inbox(root, now = { 10_000 })

        assertNull(inbox.claim("../safe"))
        assertNull(inbox.claim("traversal"))
        assertEquals(
            "must survive".encodeToByteArray().size.toULong(),
            fileManager.contentsAtPath(outsidePath)?.length,
        )
        assertFalse(fileManager.fileExistsAtPath("$root/ExternalShares/pending/traversal"))

        val valid = assertNotNull(inbox.claim("safe"))
        assertEquals("safe", valid.payload.id)
        valid.cleanup()
    }

    private fun inbox(
        root: String,
        now: () -> Long,
        owner: String = "c".repeat(32),
    ) = IosExternalShareInbox.forTemporaryAppGroupTest(
        rootUrl = NSURL.fileURLWithPath(root),
        nowEpochMillis = now,
        ownerToken = owner,
    )

    private fun writePending(
        root: String,
        id: String,
        createdAt: Long,
        text: String,
        attachmentsJson: String = "[]",
    ) {
        val directory = "$root/ExternalShares/pending/$id"
        require(fileManager.createDirectoryAtPath(directory, true, null, null))
        writeText(
            "$directory/manifest.json",
            """{"id":"$id","text":"$text","attachments":$attachmentsJson,"createdAtEpochMillis":$createdAt}""",
        )
    }

    private fun writeText(path: String, value: String) {
        require(fileManager.createFileAtPath(path, value.encodeToByteArray().toFoundationData(), null))
    }

    private fun directoryNames(path: String): List<String> =
        (fileManager.contentsOfDirectoryAtPath(path, null) as? List<*>).orEmpty().mapNotNull { it as? String }

    private inline fun withTemporaryAppGroup(block: (String) -> Unit) {
        val root = NSTemporaryDirectory().trimEnd('/') + "/quata-external-share-test-${NSUUID.UUID().UUIDString}"
        require(fileManager.createDirectoryAtPath(root, true, null, null))
        try {
            block(root)
        } finally {
            fileManager.removeItemAtPath(root, null)
        }
    }
}
