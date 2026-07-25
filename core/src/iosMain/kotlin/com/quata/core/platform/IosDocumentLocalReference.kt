package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL

/**
 * Normalizes the only references that may be passed to the native document adapters.
 *
 * Quick Look and Quick Look Thumbnailing must never receive a remote or provider URL from a
 * shared [PlatformFile]. Download/import is an explicit host responsibility; this boundary only
 * admits a local file URL or an absolute sandbox path.
 */
@OptIn(ExperimentalForeignApi::class)
fun iosDocumentLocalReferenceOrNull(reference: String): String? =
    iosDocumentLocalUrlOrNull(reference)?.absoluteString

@OptIn(ExperimentalForeignApi::class)
internal fun iosDocumentLocalUrlOrNull(reference: String): NSURL? {
    val value = reference.trim()
    val url = when {
        value.startsWith("file://") -> NSURL(string = value)
        value.startsWith("/") -> NSURL.fileURLWithPath(value)
        else -> null
    }
    return url?.takeIf { candidate ->
        // A file URL with a host denotes a network location (except the standard localhost
        // spelling). Quick Look must only receive a sandbox-local document; importing or
        // downloading provider/remote references belongs to a separate host adapter.
        val host = candidate.host
        candidate.isFileURL() &&
            (host.isNullOrBlank() || host.equals("localhost", ignoreCase = true)) &&
            !candidate.path.isNullOrBlank()
    }
}
