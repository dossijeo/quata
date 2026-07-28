package com.quata.feature.externalshare

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ExternalShareQueuePolicyTest {
    @Test
    fun `oldest manifest timestamp wins regardless of UUID ordering`() {
        val selected = selectExternalShareQueueEntry(
            entries = listOf(
                pending(id = "share-000", createdAt = 2_000),
                pending(id = "share-zzz", createdAt = 1_000),
            ),
            requestedId = null,
            nowEpochMillis = 3_000,
            activeIds = emptySet(),
        )

        assertEquals("share-zzz", selected?.id)
    }

    @Test
    fun `ordering is deterministic when timestamps match`() {
        val entries = listOf(
            pending(id = "share-b", createdAt = 1_000),
            pending(id = "share-a", createdAt = 1_000),
        )

        assertEquals(
            "share-a",
            selectExternalShareQueueEntry(entries, null, 2_000, emptySet())?.id,
        )
    }

    @Test
    fun `fresh processing lease is not reclaimed but stale lease is`() {
        val now = 1_000_000L
        val fresh = processing("share-fresh", createdAt = 100, claimedAt = now - 1_000)
        val stale = processing(
            "share-stale",
            createdAt = 200,
            claimedAt = now - ExternalShareClaimLeaseMillis,
        )

        assertEquals(
            "share-stale",
            selectExternalShareQueueEntry(listOf(fresh, stale), null, now, emptySet())?.id,
        )
        assertNull(
            selectExternalShareQueueEntry(listOf(fresh), "share-fresh", now, emptySet()),
        )
    }

    @Test
    fun `active claim cannot be reclaimed even after TTL`() {
        val stale = processing("share-active", createdAt = 100, claimedAt = 0)

        assertNull(
            selectExternalShareQueueEntry(
                listOf(stale),
                requestedId = null,
                nowEpochMillis = ExternalShareClaimLeaseMillis + 1,
                activeIds = setOf("share-active"),
            ),
        )
    }

    @Test
    fun `same requested share is selected once while its claim is active`() {
        val entry = pending(id = "share-once", createdAt = 100)

        assertEquals(
            "share-once",
            selectExternalShareQueueEntry(listOf(entry), "share-once", 200, emptySet())?.id,
        )
        assertNull(
            selectExternalShareQueueEntry(listOf(entry), "share-once", 200, setOf("share-once")),
        )
    }

    @Test
    fun `share IDs are ASCII and directory or manifest symlink destinations cannot leave App Group root`() {
        assertEquals(false, isSafeExternalShareId("share-ñ"))
        assertEquals(false, isSafeExternalShareId("share/escape"))
        assertEquals(false, isSafeExternalShareId("share\\escape"))
        assertEquals(true, isSafeExternalShareId("share_123-ABC"))
        assertEquals(
            true,
            isCanonicalExternalSharePathWithinClaim("/group/processing/claim-1", "/group/processing/claim-1/asset-0"),
        )
        assertEquals(
            false,
            isCanonicalExternalSharePathWithinClaim("/group/processing/claim-1", "/group/processing/claim-10/asset-0"),
        )
        // A symlink is resolved before this predicate; an escaping destination is rejected.
        assertEquals(
            false,
            isCanonicalExternalSharePathWithinClaim("/group/processing/claim-1", "/private/secret.pdf"),
        )
        assertEquals(
            false,
            isCanonicalExternalSharePathWithinClaim("/group", "/private/manifest.json"),
        )
    }

    @Test
    fun `lease directory round trips and cleanup identity remains generation specific`() {
        val first = externalShareClaimDirectoryName(
            id = "share-123",
            claimedAtEpochMillis = 1_000,
            ownerToken = "a".repeat(32),
        )
        val recovered = externalShareClaimDirectoryName(
            id = "share-123",
            claimedAtEpochMillis = 2_000,
            ownerToken = "b".repeat(32),
        )

        assertEquals(ExternalShareClaimDirectory("share-123", 1_000), parseExternalShareClaimDirectoryName(first))
        assertEquals(ExternalShareClaimDirectory("share-123", 2_000), parseExternalShareClaimDirectoryName(recovered))
        // Cleanup stores the exact directory name, not only the payload ID.
        assertNotEquals(first, recovered)
    }

    private fun pending(id: String, createdAt: Long) = ExternalShareQueueEntry(
        id = id,
        directoryName = id,
        createdAtEpochMillis = createdAt,
        location = ExternalShareQueueLocation.Pending,
    )

    private fun processing(id: String, createdAt: Long, claimedAt: Long) = ExternalShareQueueEntry(
        id = id,
        directoryName = externalShareClaimDirectoryName(id, claimedAt, "c".repeat(32)),
        createdAtEpochMillis = createdAt,
        location = ExternalShareQueueLocation.Processing,
        claimedAtEpochMillis = claimedAt,
    )
}
