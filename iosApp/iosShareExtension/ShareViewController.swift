import Social
import UniformTypeIdentifiers

private enum ShareExtensionConfiguration {
    static let appGroup = "group.com.quata.ios.share"
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
                let createdAtEpochMillis = Int64(Date().timeIntervalSince1970 * 1_000)
                try await persistShare(id: shareID, createdAtEpochMillis: createdAtEpochMillis)
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

    private func persistShare(id: String, createdAtEpochMillis: Int64) async throws {
        guard let root = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: ShareExtensionConfiguration.appGroup
        ) else { throw ShareExtensionError.appGroupUnavailable }
        let providers = extensionContext?.inputItems
            .compactMap { ($0 as? NSExtensionItem)?.attachments }
            .flatMap { $0 }
            ?? []
        var textParts: [String] = [contentText.trimmingCharacters(in: .whitespacesAndNewlines)]
            .filter { !$0.isEmpty }
        var attachments: [ShareQueue.Attachment] = []

        for provider in providers {
            if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
                if let text = try await provider.loadString(for: UTType.plainText), !text.isEmpty { textParts.append(text) }
                continue
            }
            if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                if let url = try await provider.loadURL() { textParts.append(url.absoluteString) }
                continue
            }
            guard let type = provider.registeredContentTypes.first(where: Self.isSupported) else { continue }
            let source = try await provider.loadFile(for: type)
            let originalName = source.lastPathComponent.isEmpty ? "attachment" : source.lastPathComponent
            attachments.append(.init(sourceURL: source, name: originalName, mimeType: type.preferredMIMEType))
        }
        try ShareQueue.persist(
            .init(id: id, createdAtEpochMillis: createdAtEpochMillis, text: textParts.joined(separator: "\n"), attachments: attachments),
            root: root
        )
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
    case appGroupUnavailable, unreadableItem
}
