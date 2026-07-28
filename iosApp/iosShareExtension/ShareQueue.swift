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
    private static let publishLockRecoveryAge: TimeInterval = 120

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
        case duplicateShare, invalidShareID, unreadableFile, queueBusy

        var errorDescription: String? {
            switch self {
            case .emptyPayload: return "Share has no supported content."
            case .tooManyFiles: return "A maximum of five files can be shared at once."
            case .tooManyPendingShares: return "Open Quata to process pending shared items first."
            case .fileTooLarge: return "One shared file is too large."
            case .payloadTooLarge: return "The shared files are too large together."
            case .duplicateShare: return "This share has already been queued."
            case .invalidShareID: return "The share identifier is invalid."
            case .unreadableFile: return "A shared item is not a regular file."
            case .queueBusy: return "Another share is being saved. Please try again."
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
        guard isSafeID(payload.id) else { throw Error.invalidShareID }
        guard payload.attachments.count <= maximumFiles else { throw Error.tooManyFiles }
        let queueRoot = root.appendingPathComponent("ExternalShares", isDirectory: true)
        let pending = root.appendingPathComponent("ExternalShares/pending", isDirectory: true)
        let staging = root.appendingPathComponent("ExternalShares/staging-\(payload.id)", isDirectory: true)
        let destination = pending.appendingPathComponent(payload.id, isDirectory: true)
        try fileManager.createDirectory(at: queueRoot, withIntermediateDirectories: true)
        try fileManager.createDirectory(at: staging, withIntermediateDirectories: false)
        do {
            var totalBytes: Int64 = 0
            var manifestAttachments: [Manifest.Attachment] = []
            for (index, attachment) in payload.attachments.enumerated() {
                let values = try attachment.sourceURL.resourceValues(forKeys: [.fileSizeKey, .isRegularFileKey, .isSymbolicLinkKey])
                guard values.isRegularFile == true, values.isSymbolicLink != true else { throw Error.unreadableFile }
                let size = values.fileSize.map(Int64.init) ?? 0
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
            try withPublicationLock(queueRoot: queueRoot, fileManager: fileManager) {
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
                // The lock makes count-and-rename a single cross-process critical section.
                try fileManager.moveItem(at: staging, to: destination)
            }
        } catch {
            try? fileManager.removeItem(at: staging)
            throw error
        }
    }

    private static func isSafeID(_ id: String) -> Bool {
        guard !id.isEmpty, id.utf8.count <= 120 else { return false }
        return id.utf8.allSatisfy { byte in
            (48...57).contains(byte) || (65...90).contains(byte) ||
                (97...122).contains(byte) || byte == 45 || byte == 95
        }
    }

    private static func withPublicationLock<T>(
        queueRoot: URL,
        fileManager: FileManager,
        body: () throws -> T
    ) throws -> T {
        let lock = queueRoot.appendingPathComponent(".publish-lock", isDirectory: true)
        do {
            try fileManager.createDirectory(at: lock, withIntermediateDirectories: false)
        } catch {
            let modified = try? lock.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate
            if let modified, Date().timeIntervalSince(modified) > publishLockRecoveryAge {
                // Quarantine by rename before deleting. This never removes a lock another
                // process acquired after our stale-age observation.
                let recovered = queueRoot.appendingPathComponent(".publish-lock-recovered-\(UUID().uuidString)")
                guard (try? fileManager.moveItem(at: lock, to: recovered)) != nil else { throw Error.queueBusy }
                try? fileManager.removeItem(at: recovered)
                do {
                    try fileManager.createDirectory(at: lock, withIntermediateDirectories: false)
                } catch {
                    throw Error.queueBusy
                }
            } else {
                throw Error.queueBusy
            }
        }
        defer { try? fileManager.removeItem(at: lock) }
        return try body()
    }
}
