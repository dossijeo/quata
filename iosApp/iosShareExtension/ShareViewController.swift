import Social
import UniformTypeIdentifiers

private enum ShareExtensionConfiguration {
    static let appGroup = "group.com.quata.ios.share"
    static let maximumFiles = 5
    static let maximumPendingShares = 10
    static let maximumFileBytes: Int64 = 25 * 1024 * 1024
    static let maximumTotalBytes: Int64 = 100 * 1024 * 1024
}

private struct ShareManifest: Codable {
    struct Attachment: Codable {
        let relativePath: String
        let name: String
        let mimeType: String?
    }

    let id: String
    let text: String
    let attachments: [Attachment]
}

/// The extension only copies explicitly shared content into the App Group. It has no Quata
/// session, Supabase configuration or publication code; the containing app performs the
/// authenticated handoff and destination selection.
final class ShareViewController: SLComposeServiceViewController {
    private static let wordProcessingTypes = [
        UTType("com.microsoft.word.doc"),
        UTType("org.openxmlformats.wordprocessingml.document"),
        UTType("org.oasis-open.opendocument.text")
    ].compactMap { $0 }

    override func isContentValid() -> Bool { true }

    override func didSelectPost() {
        Task { @MainActor in
            do {
                let shareID = "share-\(UUID().uuidString.lowercased())"
                try await persistShare(id: shareID)
                // Share extensions cannot launch their containing app through a supported public
                // API. Publish to the App Group and finish; Quata claims the oldest pending item
                // on its next authenticated foreground transition.
                extensionContext?.completeRequest(returningItems: nil)
            } catch {
                extensionContext?.cancelRequest(withError: error)
            }
        }
    }

    override func configurationItems() -> [Any]! { [] }

    private func persistShare(id: String) async throws {
        guard let root = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: ShareExtensionConfiguration.appGroup
        ) else { throw ShareExtensionError.appGroupUnavailable }
        let pending = root.appendingPathComponent("ExternalShares/pending", isDirectory: true)
        let staging = root.appendingPathComponent("ExternalShares/staging-\(id)", isDirectory: true)
        let destination = pending.appendingPathComponent(id, isDirectory: true)
        try FileManager.default.createDirectory(at: pending, withIntermediateDirectories: true)
        let pendingCount = try FileManager.default.contentsOfDirectory(
            at: pending,
            includingPropertiesForKeys: nil
        ).count
        guard pendingCount < ShareExtensionConfiguration.maximumPendingShares else {
            throw ShareExtensionError.tooManyPendingShares
        }
        try FileManager.default.createDirectory(at: staging, withIntermediateDirectories: true)
        do {
            let providers = extensionContext?.inputItems
                .compactMap { ($0 as? NSExtensionItem)?.attachments }
                .flatMap { $0 }
                ?? []
            var textParts: [String] = [contentText.trimmingCharacters(in: .whitespacesAndNewlines)]
                .filter { !$0.isEmpty }
            var attachments: [ShareManifest.Attachment] = []
            var totalBytes: Int64 = 0

            for provider in providers {
                if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier), textParts.isEmpty {
                    if let text = try await provider.loadString(for: UTType.plainText) { textParts.append(text) }
                    continue
                }
                if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier), textParts.isEmpty {
                    if let url = try await provider.loadURL() { textParts.append(url.absoluteString) }
                    continue
                }
                guard let type = provider.registeredContentTypes.first(where: Self.isSupported) else { continue }
                guard attachments.count < ShareExtensionConfiguration.maximumFiles else {
                    throw ShareExtensionError.tooManyFiles
                }
                let source = try await provider.loadFile(for: type)
                let size = try source.resourceValues(forKeys: [.fileSizeKey]).fileSize.map(Int64.init) ?? 0
                guard size >= 0, size <= ShareExtensionConfiguration.maximumFileBytes else {
                    throw ShareExtensionError.fileTooLarge
                }
                totalBytes += size
                guard totalBytes <= ShareExtensionConfiguration.maximumTotalBytes else {
                    throw ShareExtensionError.payloadTooLarge
                }
                let originalName = source.lastPathComponent.isEmpty ? "attachment" : source.lastPathComponent
                let storedName = "asset-\(attachments.count)-\(UUID().uuidString.lowercased())"
                let storedURL = staging.appendingPathComponent(storedName, isDirectory: false)
                try FileManager.default.copyItem(at: source, to: storedURL)
                attachments.append(.init(relativePath: storedName, name: originalName, mimeType: type.preferredMIMEType))
            }

            let text = textParts.joined(separator: "\n").prefix(20_000)
            guard !text.isEmpty || !attachments.isEmpty else { throw ShareExtensionError.emptyPayload }
            let manifest = ShareManifest(id: id, text: String(text), attachments: attachments)
            let data = try JSONEncoder().encode(manifest)
            try data.write(to: staging.appendingPathComponent("manifest.json"), options: [.atomic])
            // Renaming a directory inside the same App Group volume is the publish boundary: the
            // app can never observe a partial manifest or half-copied attachment set.
            try FileManager.default.moveItem(at: staging, to: destination)
        } catch {
            try? FileManager.default.removeItem(at: staging)
            throw error
        }
    }

    private static func isSupported(_ type: UTType) -> Bool {
        type.conforms(to: .image) || type.conforms(to: .audio) || type.conforms(to: .movie) ||
            type.conforms(to: .pdf) || type.conforms(to: .plainText) || type.conforms(to: .rtf) ||
            type.conforms(to: .html) || type.conforms(to: .xml) || type.conforms(to: .json) ||
            type.conforms(to: .commaSeparatedText) || type.conforms(to: .spreadsheet) ||
            type.conforms(to: .presentation) ||
            wordProcessingTypes.contains(where: { type.conforms(to: $0) })
    }
}

private extension NSItemProvider {
    func loadFile(for type: UTType) async throws -> URL {
        try await withCheckedThrowingContinuation { continuation in
            loadFileRepresentation(forTypeIdentifier: type.identifier) { url, error in
                if let error { continuation.resume(throwing: error) }
                else if let url { continuation.resume(returning: url) }
                else { continuation.resume(throwing: ShareExtensionError.unreadableItem) }
            }
        }
    }

    func loadString(for type: UTType) async throws -> String? {
        try await withCheckedThrowingContinuation { continuation in
            loadItem(forTypeIdentifier: type.identifier) { item, error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume(returning: item as? String) }
            }
        }
    }

    func loadURL() async throws -> URL? {
        try await withCheckedThrowingContinuation { continuation in
            loadItem(forTypeIdentifier: UTType.url.identifier) { item, error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume(returning: item as? URL) }
            }
        }
    }
}

private enum ShareExtensionError: LocalizedError {
    case appGroupUnavailable, emptyPayload, tooManyFiles, tooManyPendingShares
    case fileTooLarge, payloadTooLarge, unreadableItem
}
