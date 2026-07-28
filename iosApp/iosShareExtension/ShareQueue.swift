import Foundation
import UniformTypeIdentifiers

/// File-system boundary shared by the Share Extension and its hermetic tests. The caller owns
/// extracting `NSItemProvider` values; this type never receives an authenticated session or a
/// destination/conversation identifier.
enum ShareQueue {
    static let maximumFiles = 5
    static let maximumPendingShares = 10
    static let maximumFileBytes: Int64 = 25 * 1024 * 1024
    static let maximumTotalBytes: Int64 = 100 * 1024 * 1024

    struct Attachment {
        let sourceURL: URL
        let name: String
        let mimeType: String?
    }

    struct Payload {
        let id: String
        let createdAtEpochMillis: Int64
        let text: String
        let attachments: [Attachment]
    }

    struct Manifest: Codable, Equatable {
        struct Attachment: Codable, Equatable {
            let relativePath: String
            let name: String
            let mimeType: String?
        }

        let id: String
        let createdAtEpochMillis: Int64
        let text: String
        let attachments: [Attachment]
    }

    enum Error: LocalizedError, Equatable {
        case emptyPayload, tooManyFiles, tooManyPendingShares, fileTooLarge, payloadTooLarge
        case duplicateShare

        var errorDescription: String? {
            switch self {
            case .emptyPayload: return "Share has no supported content."
            case .tooManyFiles: return "A maximum of five files can be shared at once."
            case .tooManyPendingShares: return "Open Quata to process pending shared items first."
            case .fileTooLarge: return "One shared file is too large."
            case .payloadTooLarge: return "The shared files are too large together."
            case .duplicateShare: return "This share has already been queued."
            }
        }
    }

    /// Persists a complete payload in staging and publishes it with one rename. If any copy,
    /// encoding, or publication step fails, staging is removed and no partial pending item stays
    /// visible to the containing app.
    static func persist(
        _ payload: Payload,
        root: URL,
        fileManager: FileManager = .default,
        copyFile: (URL, URL) throws -> Void = { try FileManager.default.copyItem(at: $0, to: $1) }
    ) throws {
        guard payload.attachments.count <= maximumFiles else { throw Error.tooManyFiles }
        let pending = root.appendingPathComponent("ExternalShares/pending", isDirectory: true)
        let staging = root.appendingPathComponent("ExternalShares/staging-\(payload.id)", isDirectory: true)
        let destination = pending.appendingPathComponent(payload.id, isDirectory: true)
        try fileManager.createDirectory(at: pending, withIntermediateDirectories: true)
        let pendingCount = try fileManager.contentsOfDirectory(
            at: pending,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: [.skipsHiddenFiles]
        ).filter { url in
            (try? url.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true
        }.count
        guard pendingCount < maximumPendingShares else { throw Error.tooManyPendingShares }
        guard !fileManager.fileExists(atPath: destination.path) else { throw Error.duplicateShare }

        try fileManager.createDirectory(at: staging, withIntermediateDirectories: false)
        do {
            var totalBytes: Int64 = 0
            var manifestAttachments: [Manifest.Attachment] = []
            for (index, attachment) in payload.attachments.enumerated() {
                let size = try attachment.sourceURL.resourceValues(forKeys: [.fileSizeKey]).fileSize.map(Int64.init) ?? 0
                guard size >= 0, size <= maximumFileBytes else { throw Error.fileTooLarge }
                totalBytes += size
                guard totalBytes <= maximumTotalBytes else { throw Error.payloadTooLarge }

                let storedName = "asset-\(index)-\(UUID().uuidString.lowercased())"
                try copyFile(attachment.sourceURL, staging.appendingPathComponent(storedName))
                manifestAttachments.append(.init(relativePath: storedName, name: attachment.name, mimeType: attachment.mimeType))
            }
            let text = String(payload.text.prefix(20_000))
            guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !manifestAttachments.isEmpty else {
                throw Error.emptyPayload
            }
            let manifest = Manifest(
                id: payload.id,
                createdAtEpochMillis: payload.createdAtEpochMillis,
                text: text,
                attachments: manifestAttachments
            )
            try JSONEncoder().encode(manifest).write(
                to: staging.appendingPathComponent("manifest.json"),
                options: .atomic
            )
            try fileManager.moveItem(at: staging, to: destination)
        } catch {
            try? fileManager.removeItem(at: staging)
            throw error
        }
    }
}
