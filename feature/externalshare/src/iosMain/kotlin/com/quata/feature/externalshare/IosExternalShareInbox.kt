package com.quata.feature.externalshare

import com.quata.core.session.IosRenewableAuthSession
import com.quata.feature.chat.domain.ChatRepository
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSURL

const val QuataExternalShareAppGroup = "group.com.quata.ios.share"

/** A claimed App Group payload. Its files remain valid until [cleanup] is called. */
class IosExternalShareClaim internal constructor(
    val payload: ExternalSharePayload,
    private val inbox: IosExternalShareInbox,
) {
    fun cleanup() = inbox.discard(payload.id)
}

/**
 * Claims extension manifests by atomically moving their directory from pending to processing.
 * The share extension never sees the authenticated session; the containing app is the only
 * process allowed to turn a claimed payload into Chat operations.
 */
class IosExternalShareInbox(
    private val appGroupIdentifier: String = QuataExternalShareAppGroup,
    private val fileManager: NSFileManager = NSFileManager.defaultManager,
) {
    fun claim(requestedId: String? = null): IosExternalShareClaim? {
        val root = fileManager.containerURLForSecurityApplicationGroupIdentifier(appGroupIdentifier)
            ?: return null
        val rootPath = root.path ?: return null
        val pendingPath = "$rootPath/ExternalShares/pending"
        val processingPath = "$rootPath/ExternalShares/processing"
        fileManager.createDirectoryAtPath(pendingPath, true, null, null)
        fileManager.createDirectoryAtPath(processingPath, true, null, null)

        val id = requestedId?.takeIf(::isSafeShareId)
            ?: (fileManager.contentsOfDirectoryAtPath(pendingPath, null) as? List<*>)
                .orEmpty()
                .mapNotNull { it as? String }
                .filter(::isSafeShareId)
                .sorted()
                .firstOrNull()
            ?: return null
        val pendingClaimPath = "$pendingPath/$id"
        val processingClaimPath = "$processingPath/$id"
        if (fileManager.fileExistsAtPath(pendingClaimPath)) {
            if (fileManager.fileExistsAtPath(processingClaimPath)) return null
            if (!fileManager.moveItemAtPath(pendingClaimPath, processingClaimPath, null)) return null
        } else if (!fileManager.fileExistsAtPath(processingClaimPath)) {
            return null
        }

        val persisted = readManifest(processingClaimPath, id)
        val result = persisted?.let { manifest ->
            persistedExternalSharePayload(manifest) { relativePath ->
                NSURL.fileURLWithPath("$processingClaimPath/$relativePath").absoluteString
            }
        } ?: PersistedExternalShareResult.Invalid
        return when (result) {
            is PersistedExternalShareResult.Accepted -> IosExternalShareClaim(result.payload, this)
            PersistedExternalShareResult.Empty,
            PersistedExternalShareResult.Invalid,
            PersistedExternalShareResult.TooManyFiles,
            PersistedExternalShareResult.Unsupported -> {
                discard(id)
                null
            }
        }
    }

    internal fun discard(id: String) {
        if (!isSafeShareId(id)) return
        val rootPath = fileManager.containerURLForSecurityApplicationGroupIdentifier(appGroupIdentifier)?.path ?: return
        val processingClaimPath = "$rootPath/ExternalShares/processing/$id"
        if (fileManager.fileExistsAtPath(processingClaimPath)) {
            fileManager.removeItemAtPath(processingClaimPath, null)
        }
    }

    private fun readManifest(claimPath: String, expectedId: String): PersistedExternalShare? {
        val data = NSData.dataWithContentsOfFile("$claimPath/manifest.json") ?: return null
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
        )
    }
}

/** Authenticated composition boundary that reuses the already-installed real Chat repository. */
class IosExternalShareRuntimeBootstrap(
    private val authSession: IosRenewableAuthSession,
    private val chatRepository: ChatRepository,
    private val inbox: IosExternalShareInbox = IosExternalShareInbox(),
) {
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

private fun isSafeShareId(value: String): Boolean =
    value.isNotEmpty() && value.all { it.isLetterOrDigit() || it == '-' || it == '_' }
