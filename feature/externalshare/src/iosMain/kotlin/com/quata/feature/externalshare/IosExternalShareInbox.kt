package com.quata.feature.externalshare

import com.quata.core.session.IosRenewableAuthSession
import com.quata.feature.chat.domain.ChatRepository
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFAbsoluteTimeGetCurrent
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSFileTypeRegular
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSLock
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

const val QuataExternalShareAppGroup = "group.com.quata.ios.share"

/** A claimed App Group payload. Its files remain valid until [cleanup] is called. */
class IosExternalShareClaim internal constructor(
    val payload: ExternalSharePayload,
    private val inbox: IosExternalShareInbox,
    private val processingDirectoryName: String,
) {
    fun cleanup() = inbox.discard(payload.id, processingDirectoryName)
}

/**
 * Claims extension manifests by atomically moving their directory from pending to processing.
 * The share extension never sees the authenticated session; the containing app is the only
 * process allowed to turn a claimed payload into Chat operations.
 */
@OptIn(ExperimentalForeignApi::class)
class IosExternalShareInbox(
    private val appGroupIdentifier: String = QuataExternalShareAppGroup,
    private val fileManager: NSFileManager = NSFileManager.defaultManager,
    private val nowEpochMillis: () -> Long = {
        ((CFAbsoluteTimeGetCurrent() + AppleEpochOffsetSeconds) * 1_000.0).toLong()
    },
    private val ownerToken: String = NSUUID.UUID().UUIDString.replace("-", "").lowercase(),
) {
    private val claimLock = NSLock()
    private val activeClaimIds = mutableSetOf<String>()

    fun claim(requestedId: String? = null): IosExternalShareClaim? = withClaimLock {
        if (requestedId != null && !isSafeExternalShareId(requestedId)) return@withClaimLock null
        val root = fileManager.containerURLForSecurityApplicationGroupIdentifier(appGroupIdentifier)
            ?: return@withClaimLock null
        val rootPath = root.path ?: return@withClaimLock null
        val canonicalRootPath = canonicalPath(rootPath) ?: return@withClaimLock null
        val externalSharesPath = "$canonicalRootPath/ExternalShares"
        val pendingPath = "$externalSharesPath/pending"
        val processingPath = "$externalSharesPath/processing"
        fileManager.createDirectoryAtPath(pendingPath, true, null, null)
        fileManager.createDirectoryAtPath(processingPath, true, null, null)
        val canonicalExternalSharesPath = canonicalDirectoryPath(canonicalRootPath, externalSharesPath)
            ?: return@withClaimLock null
        val canonicalPendingPath = canonicalDirectoryPath(canonicalExternalSharesPath, pendingPath)
            ?: return@withClaimLock null
        val canonicalProcessingPath = canonicalDirectoryPath(canonicalExternalSharesPath, processingPath)
            ?: return@withClaimLock null

        val now = nowEpochMillis()
        val selected = selectExternalShareQueueEntry(
            entries = readQueueEntries(canonicalRootPath, canonicalPendingPath, canonicalProcessingPath),
            requestedId = requestedId,
            nowEpochMillis = now,
            activeIds = activeClaimIds,
        ) ?: return@withClaimLock null
        val id = selected.id
        val claimedDirectoryName = externalShareClaimDirectoryName(id, now, ownerToken)
        val sourcePath = when (selected.location) {
            ExternalShareQueueLocation.Pending -> "$canonicalPendingPath/${selected.directoryName}"
            ExternalShareQueueLocation.Processing -> "$canonicalProcessingPath/${selected.directoryName}"
        }
        val processingClaimPath = "$canonicalProcessingPath/$claimedDirectoryName"
        // The rename both acquires the claim and publishes its lease generation. A competing
        // process can observe the candidate, but only one move from the exact source can win.
        if (!fileManager.moveItemAtPath(sourcePath, processingClaimPath, null)) return@withClaimLock null
        val canonicalProcessingClaimPath = canonicalDirectoryPath(canonicalRootPath, processingClaimPath)
            ?: return@withClaimLock null

        val persisted = readManifest(canonicalRootPath, canonicalProcessingClaimPath, id)
        val result = persisted?.let { manifest ->
            persistedExternalSharePayload(manifest) { relativePath ->
                claimedRegularFileUrl(canonicalRootPath, canonicalProcessingClaimPath, relativePath).orEmpty()
            }
        } ?: PersistedExternalShareResult.Invalid
        when (result) {
            is PersistedExternalShareResult.Accepted -> {
                activeClaimIds += id
                IosExternalShareClaim(result.payload, this, claimedDirectoryName)
            }
            PersistedExternalShareResult.Empty,
            PersistedExternalShareResult.Invalid,
            PersistedExternalShareResult.TooManyFiles,
            PersistedExternalShareResult.Unsupported -> {
                discardLocked(id, claimedDirectoryName)
                null
            }
        }
    }

    internal fun discard(id: String, processingDirectoryName: String) = withClaimLock {
        discardLocked(id, processingDirectoryName)
    }

    private fun discardLocked(id: String, processingDirectoryName: String) {
        if (!isSafeExternalShareId(id)) return
        val parsed = parseExternalShareClaimDirectoryName(processingDirectoryName)
        if (parsed?.id != id) return
        activeClaimIds -= id
        val rootPath = fileManager.containerURLForSecurityApplicationGroupIdentifier(appGroupIdentifier)?.path ?: return
        val canonicalRootPath = canonicalPath(rootPath) ?: return
        val externalSharesPath = canonicalDirectoryPath(canonicalRootPath, "$canonicalRootPath/ExternalShares") ?: return
        val processingPath = canonicalDirectoryPath(externalSharesPath, "$externalSharesPath/processing") ?: return
        val processingClaimPath = canonicalDirectoryPath(canonicalRootPath, "$processingPath/$processingDirectoryName") ?: return
        if (fileManager.fileExistsAtPath(processingClaimPath)) {
            fileManager.removeItemAtPath(processingClaimPath, null)
        }
    }

    private fun readQueueEntries(
        canonicalRootPath: String,
        pendingPath: String,
        processingPath: String,
    ): List<ExternalShareQueueEntry> {
        val pending = directoryNames(pendingPath).mapNotNull { directoryName ->
            if (!isSafeExternalShareId(directoryName)) return@mapNotNull null
            val manifest = readManifest(canonicalRootPath, "$pendingPath/$directoryName", directoryName)
                ?: return@mapNotNull null
            ExternalShareQueueEntry(
                id = directoryName,
                directoryName = directoryName,
                createdAtEpochMillis = manifest.createdAtEpochMillis,
                location = ExternalShareQueueLocation.Pending,
            )
        }
        val processing = directoryNames(processingPath).mapNotNull { directoryName ->
            val parsed = parseExternalShareClaimDirectoryName(directoryName)
            val id = parsed?.id ?: directoryName.takeIf(::isSafeExternalShareId) ?: return@mapNotNull null
            val manifest = readManifest(canonicalRootPath, "$processingPath/$directoryName", id)
                ?: return@mapNotNull null
            ExternalShareQueueEntry(
                id = id,
                directoryName = directoryName,
                createdAtEpochMillis = manifest.createdAtEpochMillis,
                location = ExternalShareQueueLocation.Processing,
                // Legacy processing/<id> entries have no lease. Their creation timestamp is the
                // safest available lower bound; pre-metadata entries use zero and are recoverable.
                claimedAtEpochMillis = parsed?.claimedAtEpochMillis ?: manifest.createdAtEpochMillis,
            )
        }
        return pending + processing
    }

    private fun directoryNames(path: String): List<String> =
        (fileManager.contentsOfDirectoryAtPath(path, null) as? List<*>)
            .orEmpty()
            .mapNotNull { it as? String }

    private inline fun <T> withClaimLock(block: () -> T): T {
        claimLock.lock()
        return try {
            block()
        } finally {
            claimLock.unlock()
        }
    }

    private fun readManifest(canonicalRootPath: String, claimPath: String, expectedId: String): PersistedExternalShare? {
        val canonicalClaimPath = canonicalDirectoryPath(canonicalRootPath, claimPath) ?: return null
        val manifestPath = "$canonicalClaimPath/manifest.json"
        val attributes = fileManager.attributesOfItemAtPath(manifestPath, null) ?: return null
        if (attributes[NSFileType] != NSFileTypeRegular) return null
        val canonicalManifestPath = canonicalPath(manifestPath) ?: return null
        if (!isCanonicalExternalSharePathWithinClaim(canonicalRootPath, canonicalManifestPath)) return null
        val data = fileManager.contentsAtPath(canonicalManifestPath) ?: return null
        val root = NSJSONSerialization.JSONObjectWithData(data, options = 0u, error = null) as? Map<*, *> ?: return null
        val id = root["id"] as? String ?: return null
        if (id != expectedId) return null
        val attachments = (root["attachments"] as? List<*>).orEmpty().map { item ->
            val row = item as? Map<*, *> ?: return null
            PersistedExternalShareAttachment(
                relativePath = row["relativePath"] as? String ?: return null,
                name = row["name"] as? String ?: return null,
                mimeType = row["mimeType"] as? String,
            )
        }
        return PersistedExternalShare(
            id = id,
            text = root["text"] as? String ?: "",
            attachments = attachments,
            // Extensions never accept a conversation ID from another app. This field remains
            // absent so the authenticated user must explicitly choose a destination.
            directConversationId = null,
            createdAtEpochMillis = (root["createdAtEpochMillis"] as? NSNumber)
                ?.longLongValue
                ?.takeIf { it >= 0 }
                ?: 0,
        )
    }

    /** Never expose a URI for a symlink, a non-regular node, or a resolved path outside claim. */
    private fun claimedRegularFileUrl(canonicalRootPath: String, claimPath: String, relativePath: String): String? {
        if (relativePath != relativePath.substringAfterLast('/') || relativePath.contains('\\')) return null
        val originalPath = "$claimPath/$relativePath"
        val attributes = fileManager.attributesOfItemAtPath(originalPath, null) ?: return null
        if (attributes[NSFileType] != NSFileTypeRegular) return null
        val canonicalClaimPath = NSURL.fileURLWithPath(claimPath)?.URLByResolvingSymlinksInPath?.path ?: return null
        val canonicalCandidate = NSURL.fileURLWithPath(originalPath)?.URLByResolvingSymlinksInPath?.path ?: return null
        if (!isCanonicalExternalSharePathWithinClaim(canonicalRootPath, canonicalCandidate)) return null
        if (!isCanonicalExternalSharePathWithinClaim(canonicalClaimPath, canonicalCandidate)) return null
        return NSURL.fileURLWithPath(canonicalCandidate).absoluteString
    }

    private fun canonicalPath(path: String): String? =
        NSURL.fileURLWithPath(path)?.URLByResolvingSymlinksInPath?.path

    private fun canonicalDirectoryPath(canonicalRootPath: String, path: String): String? {
        val attributes = fileManager.attributesOfItemAtPath(path, null) ?: return null
        if (attributes[NSFileType] != NSFileTypeDirectory) return null
        val canonicalPath = canonicalPath(path) ?: return null
        return canonicalPath.takeIf { isCanonicalExternalSharePathWithinClaim(canonicalRootPath, it) }
    }
}

/** Authenticated composition boundary that reuses the already-installed real Chat repository. */
class IosExternalShareRuntimeBootstrap(
    private val authSession: IosRenewableAuthSession,
    private val chatRepository: ChatRepository,
    private val inbox: IosExternalShareInbox = IosExternalShareInbox(),
) {
    /** Synchronous restored-session path used after the authenticated host is installed. */
    fun claimRestoredAuthenticated(requestedId: String?): IosExternalShareClaim? {
        if (authSession.restoredSession() == null) return null
        return inbox.claim(requestedId)
    }

    suspend fun claimAuthenticated(requestedId: String?): IosExternalShareClaim? {
        if (authSession.currentSession() == null) return null
        return inbox.claim(requestedId)
    }

    fun hostDependencies(
        claim: IosExternalShareClaim,
        onDismiss: () -> Unit,
        onOpenConversation: (String) -> Unit,
    ): IosExternalShareHostDependencies = IosExternalShareHostDependencies(
        payload = claim.payload,
        viewModel = ShareToQuataViewModel(chatRepository, claim.payload),
        onDismiss = {
            claim.cleanup()
            onDismiss()
        },
        onOpenConversation = onOpenConversation,
    )
}

fun createIosExternalShareRuntimeBootstrap(
    authSession: IosRenewableAuthSession,
    chatRepository: ChatRepository,
): IosExternalShareRuntimeBootstrap = IosExternalShareRuntimeBootstrap(authSession, chatRepository)

private const val AppleEpochOffsetSeconds = 978_307_200.0
