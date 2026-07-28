import XCTest
@testable import QuataShareQueue

final class ShareQueueTests: XCTestCase {
    private var root: URL!

    override func setUpWithError() throws {
        root = FileManager.default.temporaryDirectory
            .appendingPathComponent("ShareQueueTests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: root)
        root = nil
    }

    func testPublishesTextURLAndFilesOnlyAfterCompleteManifestIsWritten() throws {
        let source = try writeSource(named: "photo.jpg", contents: Data([1, 2, 3]))
        try ShareQueue.persist(
            .init(
                id: "share-contract",
                createdAtEpochMillis: 42,
                text: "Hello\nhttps://example.invalid/article",
                attachments: [.init(sourceURL: source, name: "photo.jpg", mimeType: "image/jpeg")]
            ),
            root: root
        )

        let pending = root.appendingPathComponent("ExternalShares/pending/share-contract")
        XCTAssertTrue(FileManager.default.fileExists(atPath: pending.appendingPathComponent("manifest.json").path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: root.appendingPathComponent("ExternalShares/staging-share-contract").path))
        let manifest = try JSONDecoder().decode(
            ShareQueue.Manifest.self,
            from: Data(contentsOf: pending.appendingPathComponent("manifest.json"))
        )
        XCTAssertEqual(manifest.id, "share-contract")
        XCTAssertEqual(manifest.createdAtEpochMillis, 42)
        XCTAssertEqual(manifest.text, "Hello\nhttps://example.invalid/article")
        XCTAssertEqual(manifest.attachments.count, 1)
        XCTAssertEqual(manifest.attachments[0].name, "photo.jpg")
        XCTAssertTrue(FileManager.default.fileExists(atPath: pending.appendingPathComponent(manifest.attachments[0].relativePath).path))
    }

    func testRejectsMoreThanFiveFilesAndTenPendingItems() throws {
        let source = try writeSource(named: "file.txt", contents: Data("x".utf8))
        XCTAssertThrowsError(
            try ShareQueue.persistForTesting(
                .init(id: "share-six", createdAtEpochMillis: 1, text: "", attachments: Array(repeating: .init(sourceURL: source, name: "file.txt", mimeType: "text/plain"), count: 6)),
                root: root
            )
        ) { XCTAssertEqual($0 as? ShareQueue.Error, .tooManyFiles) }

        let pending = root.appendingPathComponent("ExternalShares/pending", isDirectory: true)
        try FileManager.default.createDirectory(at: pending, withIntermediateDirectories: true)
        for index in 0..<ShareQueue.maximumPendingShares {
            try FileManager.default.createDirectory(at: pending.appendingPathComponent("share-\(index)"), withIntermediateDirectories: false)
        }
        XCTAssertThrowsError(
            try ShareQueue.persist(.init(id: "share-eleven", createdAtEpochMillis: 2, text: "text", attachments: []), root: root)
        ) { XCTAssertEqual($0 as? ShareQueue.Error, .tooManyPendingShares) }
    }

    func testRejectsUnsafeIdentifiersBeforeAnyQueuePathIsComposed() throws {
        for id in ["../escape", "share/name", "share\\name", "share-ñ", "", String(repeating: "a", count: 121)] {
            XCTAssertThrowsError(
                try ShareQueue.persist(.init(id: id, createdAtEpochMillis: 1, text: "text", attachments: []), root: root)
            ) { XCTAssertEqual($0 as? ShareQueue.Error, .invalidShareID) }
        }
        XCTAssertFalse(FileManager.default.fileExists(atPath: root.appendingPathComponent("ExternalShares").path))
    }

    func testRejectsSymlinkSourceWithoutPublishingIt() throws {
        let source = try writeSource(named: "real.txt", contents: Data("content".utf8))
        let symlink = root.appendingPathComponent("linked.txt")
        try FileManager.default.createSymbolicLink(atPath: symlink.path, withDestinationPath: source.path)

        XCTAssertThrowsError(
            try ShareQueue.persistForTesting(
                .init(id: "share-symlink", createdAtEpochMillis: 1, text: "", attachments: [.init(sourceURL: symlink, name: "linked.txt", mimeType: "text/plain")]),
                root: root
            )
        ) { XCTAssertEqual($0 as? ShareQueue.Error, .unreadableFile) }
        XCTAssertFalse(FileManager.default.fileExists(atPath: root.appendingPathComponent("ExternalShares/pending/share-symlink").path))
    }

    func testRejectsSymlinkedExternalSharesDirectory() throws {
        let outside = root.appendingPathComponent("outside", isDirectory: true)
        try FileManager.default.createDirectory(at: outside, withIntermediateDirectories: true)
        try FileManager.default.createSymbolicLink(
            atPath: root.appendingPathComponent("ExternalShares").path,
            withDestinationPath: outside.path
        )
        XCTAssertThrowsError(
            try ShareQueue.persist(.init(id: "share-directory-link", createdAtEpochMillis: 1, text: "text", attachments: []), root: root)
        ) { XCTAssertEqual($0 as? ShareQueue.Error, .unreadableFile) }
        XCTAssertFalse(FileManager.default.fileExists(atPath: outside.appendingPathComponent("pending/share-directory-link").path))
    }

    func testConcurrentPublishersNeverCreateMoreThanTenPendingDirectories() throws {
        let group = DispatchGroup()
        let resultLock = NSLock()
        var results: [Swift.Error] = []
        for index in 0..<16 {
            group.enter()
            DispatchQueue.global().async {
                defer { group.leave() }
                do {
                    try ShareQueue.persist(.init(id: "share-race-\(index)", createdAtEpochMillis: Int64(index), text: "text", attachments: []), root: self.root)
                } catch {
                    resultLock.lock()
                    results.append(error)
                    resultLock.unlock()
                }
            }
        }
        XCTAssertEqual(group.wait(timeout: .now() + 10), .success)
        let pending = root.appendingPathComponent("ExternalShares/pending", isDirectory: true)
        let count = (try? FileManager.default.contentsOfDirectory(at: pending, includingPropertiesForKeys: nil).count) ?? 0
        XCTAssertLessThanOrEqual(count, ShareQueue.maximumPendingShares)
        XCTAssertFalse(results.isEmpty)
    }

    func testLockTimeoutFailsClosedWithoutPublishing() throws {
        XCTAssertThrowsError(
            try ShareQueue.persistForTesting(
                .init(id: "share-locked", createdAtEpochMillis: 1, text: "text", attachments: []),
                root: root,
                locking: TimeoutLock()
            )
        ) { XCTAssertEqual($0 as? ShareQueue.Error, .queueBusy) }
        XCTAssertFalse(FileManager.default.fileExists(atPath: root.appendingPathComponent("ExternalShares/pending/share-locked").path))
    }

    func testCopyFailureRollsBackStagingWithoutPublishingAPartialPayload() throws {
        let source = try writeSource(named: "will-fail.txt", contents: Data("source".utf8))
        XCTAssertThrowsError(
            try ShareQueue.persistForTesting(
                .init(id: "share-failure", createdAtEpochMillis: 3, text: "text", attachments: [.init(sourceURL: source, name: "will-fail.txt", mimeType: "text/plain")]),
                root: root,
                copyFile: { _, _ in throw CocoaError(.fileWriteUnknown) }
            )
        )
        XCTAssertFalse(FileManager.default.fileExists(atPath: root.appendingPathComponent("ExternalShares/staging-share-failure").path))
        XCTAssertFalse(FileManager.default.fileExists(atPath: root.appendingPathComponent("ExternalShares/pending/share-failure").path))
    }

    func testDuplicatePublishIsRejectedWithoutOverwritingTheOriginalManifest() throws {
        try ShareQueue.persist(.init(id: "share-once", createdAtEpochMillis: 4, text: "first", attachments: []), root: root)
        XCTAssertThrowsError(
            try ShareQueue.persist(.init(id: "share-once", createdAtEpochMillis: 5, text: "second", attachments: []), root: root)
        ) { XCTAssertEqual($0 as? ShareQueue.Error, .duplicateShare) }
        let manifestURL = root.appendingPathComponent("ExternalShares/pending/share-once/manifest.json")
        let manifest = try JSONDecoder().decode(ShareQueue.Manifest.self, from: Data(contentsOf: manifestURL))
        XCTAssertEqual(manifest.text, "first")
    }

    private func writeSource(named name: String, contents: Data) throws -> URL {
        let url = root.appendingPathComponent(name)
        try contents.write(to: url)
        return url
    }
}

private struct TimeoutLock: ShareQueueLocking {
    func withLock<T>(at url: URL, timeout: TimeInterval, body: () throws -> T) throws -> T {
        throw ShareQueue.Error.queueBusy
    }
}
