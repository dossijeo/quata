package com.quata.feature.externalshare

internal const val ExternalShareClaimLeaseMillis = 2 * 60 * 1_000L
private const val ClaimDirectoryPrefix = "claim-"
private const val ClaimOwnerLength = 32

internal enum class ExternalShareQueueLocation {
    Pending,
    Processing,
}

internal data class ExternalShareQueueEntry(
    val id: String,
    val directoryName: String,
    val createdAtEpochMillis: Long,
    val location: ExternalShareQueueLocation,
    val claimedAtEpochMillis: Long? = null,
)

internal fun selectExternalShareQueueEntry(
    entries: List<ExternalShareQueueEntry>,
    requestedId: String?,
    nowEpochMillis: Long,
    activeIds: Set<String>,
): ExternalShareQueueEntry? = entries
    .asSequence()
    .filter { requestedId == null || it.id == requestedId }
    .filter { it.id !in activeIds }
    .filter { entry ->
        entry.location == ExternalShareQueueLocation.Pending ||
            entry.claimedAtEpochMillis == null ||
            nowEpochMillis - entry.claimedAtEpochMillis >= ExternalShareClaimLeaseMillis
    }
    .sortedWith(compareBy<ExternalShareQueueEntry>({ it.createdAtEpochMillis }, { it.id }, { it.directoryName }))
    .firstOrNull()

internal data class ExternalShareClaimDirectory(
    val id: String,
    val claimedAtEpochMillis: Long,
)

internal fun externalShareClaimDirectoryName(
    id: String,
    claimedAtEpochMillis: Long,
    ownerToken: String,
): String {
    require(ownerToken.length == ClaimOwnerLength && ownerToken.all { it.isLetterOrDigit() })
    return "$ClaimDirectoryPrefix$claimedAtEpochMillis-$ownerToken-$id"
}

internal fun parseExternalShareClaimDirectoryName(value: String): ExternalShareClaimDirectory? {
    if (!value.startsWith(ClaimDirectoryPrefix)) return null
    val remainder = value.removePrefix(ClaimDirectoryPrefix)
    val timestampEnd = remainder.indexOf('-')
    if (timestampEnd <= 0) return null
    val claimedAt = remainder.substring(0, timestampEnd).toLongOrNull()?.takeIf { it >= 0 } ?: return null
    val ownerStart = timestampEnd + 1
    val idStart = ownerStart + ClaimOwnerLength + 1
    if (idStart > remainder.length || remainder.getOrNull(idStart - 1) != '-') return null
    val owner = remainder.substring(ownerStart, idStart - 1)
    if (owner.length != ClaimOwnerLength || !owner.all(Char::isLetterOrDigit)) return null
    val id = remainder.substring(idStart)
    if (!isSafeExternalShareId(id)) return null
    return ExternalShareClaimDirectory(id, claimedAt)
}

internal fun isSafeExternalShareId(value: String): Boolean =
    value.isNotEmpty() && value.length <= MaxExternalShareIdChars &&
        value.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }

/** Both paths must already be canonical absolute paths; prefix checks are segment-aware. */
internal fun isCanonicalExternalSharePathWithinClaim(claimPath: String, candidatePath: String): Boolean {
    val root = claimPath.trimEnd('/')
    return root.isNotEmpty() && candidatePath.startsWith("$root/")
}
