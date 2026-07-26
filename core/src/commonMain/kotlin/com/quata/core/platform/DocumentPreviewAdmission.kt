package com.quata.core.platform

/**
 * Platform capabilities for a document surface. The sets describe an interaction, never a
 * renderer: a host can map [DocumentPreviewAdmission.Open] to Quick Look, a browser tab, or an
 * injected viewer, and [DocumentPreviewAdmission.Download] to its native download route.
 */
data class DocumentPreviewCapabilities(
    val openKinds: Set<DocumentPreviewKind> = emptySet(),
    val downloadKinds: Set<DocumentPreviewKind> = emptySet(),
)

/** Result of admitting a document to a platform document surface. */
sealed interface DocumentPreviewAdmission {
    val descriptor: DocumentPreviewDescriptor

    data class Open(
        override val descriptor: DocumentPreviewDescriptor,
    ) : DocumentPreviewAdmission

    data class Download(
        override val descriptor: DocumentPreviewDescriptor,
    ) : DocumentPreviewAdmission

    data class Unavailable(
        override val descriptor: DocumentPreviewDescriptor,
        val reason: DocumentPreviewAdmissionReason,
    ) : DocumentPreviewAdmission
}

enum class DocumentPreviewAdmissionReason {
    UnsupportedFormat,
    PlatformUnsupported,
}

/**
 * Common format admission for document hosts.
 *
 * URI trust, sandbox locality and renderer lifecycle stay at the platform boundary. This means
 * the same PDF/RTF/Office classification drives iOS and Web actions without making common code
 * open an URL or import a viewer implementation.
 */
object DocumentPreviewAdmissions {
    fun admit(
        file: PlatformFile,
        capabilities: DocumentPreviewCapabilities,
    ): DocumentPreviewAdmission {
        val descriptor = DocumentSupport.describe(file.reference, file.displayName, file.mimeType)
        if (descriptor.kind !in PreviewableDocumentKinds) {
            return DocumentPreviewAdmission.Unavailable(
                descriptor = descriptor,
                reason = DocumentPreviewAdmissionReason.UnsupportedFormat,
            )
        }
        return when (descriptor.kind) {
            in capabilities.openKinds -> DocumentPreviewAdmission.Open(descriptor)
            in capabilities.downloadKinds -> DocumentPreviewAdmission.Download(descriptor)
            else -> DocumentPreviewAdmission.Unavailable(
                descriptor = descriptor,
                reason = DocumentPreviewAdmissionReason.PlatformUnsupported,
            )
        }
    }

    val PreviewableDocumentKinds: Set<DocumentPreviewKind> = setOf(
        DocumentPreviewKind.Pdf,
        DocumentPreviewKind.RichText,
        DocumentPreviewKind.Office,
    )

    val QuickLook: DocumentPreviewCapabilities = DocumentPreviewCapabilities(
        openKinds = PreviewableDocumentKinds,
    )

    val BrowserFallback: DocumentPreviewCapabilities = DocumentPreviewCapabilities(
        openKinds = setOf(DocumentPreviewKind.Pdf),
        downloadKinds = setOf(DocumentPreviewKind.RichText, DocumentPreviewKind.Office),
    )
}
