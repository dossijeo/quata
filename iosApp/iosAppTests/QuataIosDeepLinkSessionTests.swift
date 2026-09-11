import Darwin
import Foundation
import XCTest
import QuataShared

/// Imports an already journaled fixture session. Never performs a login or HTTP call.
/// The coordinator verifies the bearer/receipt before import and revokes it afterward.
final class QuataIosDeepLinkSessionTests: XCTestCase {
    func testPrivateExchangeRejectsReplayAndMixedReceiptWithoutKeychainWrites() throws {
        let stepId = UUID().uuidString
        let root = FileManager.default.temporaryDirectory.resolvingSymlinksInPath()
            .appendingPathComponent("deep-link-session-\(stepId)", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false, attributes: [.posixPermissions: 0o700])
        defer { try? FileManager.default.removeItem(at: root) }
        let authUserId = UUID().uuidString, authSessionId = UUID().uuidString
        let claims = try JSONSerialization.data(withJSONObject: ["sub": authUserId, "session_id": authSessionId, "exp": 2_000_000_000] as [String: Any])
        let payload = claims.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        var input: [String: Any] = ["runId": UUID().uuidString, "stepId": stepId, "stage": "verify",
            "profileId": UUID().uuidString, "authUserId": authUserId, "authSessionId": authSessionId,
            "accessToken": "synthetic.\(payload).synthetic", "refreshToken": "synthetic-refresh",
            "expiresAt": 2_000_000_000, "email": "fixture@example.invalid", "displayName": "Synthetic", "isOfficial": false]
        let inputPath = root.appendingPathComponent("input.json")
        try JSONSerialization.data(withJSONObject: input).write(to: inputPath)
        try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: inputPath.path)
        let files = try DeepLinkSessionFiles(path: root.path)
        try files.claim()
        XCTAssertThrowsError(try files.claim())
        try files.receipt()
        XCTAssertThrowsError(try files.receipt())
        let receipt = try String(contentsOf: root.appendingPathComponent("receipt.json"), encoding: .utf8)
        XCTAssertFalse(receipt.contains("synthetic-refresh") || receipt.contains(payload))
        input["authSessionId"] = UUID().uuidString
        try JSONSerialization.data(withJSONObject: input).write(to: inputPath)
        XCTAssertThrowsError(try DeepLinkSessionFiles(path: root.path))
        input["authSessionId"] = authSessionId
        try JSONSerialization.data(withJSONObject: input).write(to: inputPath)
        try FileManager.default.setAttributes([.posixPermissions: 0o644], ofItemAtPath: inputPath.path)
        XCTAssertThrowsError(try DeepLinkSessionFiles(path: root.path))
        try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: inputPath.path)
        let original = root.appendingPathComponent("original.json")
        try FileManager.default.moveItem(at: inputPath, to: original)
        try FileManager.default.createSymbolicLink(at: inputPath, withDestinationURL: original)
        XCTAssertThrowsError(try DeepLinkSessionFiles(path: root.path))
        XCTAssertThrowsError(try validateDeepLinkInstallTime(stage: "install", expiresAt: 1_900, now: 1_000))
        try validateDeepLinkInstallTime(stage: "install", expiresAt: 1_901, now: 1_000)
        try validateDeepLinkInstallTime(stage: "clear", expiresAt: 1, now: 1_000)
    }

    func testDedicatedDeepLinkHostHasNoStoredSession() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_DEEP_LINK_ANONYMOUS_PROBE"] == "1" else {
            throw XCTSkip("Requires the leased anonymous deep-link coordinator.")
        }
        try requirePassiveDeepLinkHost()
        let storage = IosKeychainSessionStorage(service: "com.quata.auth-session", account: "current-user")
        let absent = storage.getSession() == nil
        XCTAssertTrue(absent && storage.lastStatus == nil, "Anonymous trial requires verified empty storage.")
    }

    func testOwnedDeepLinkSessionStep() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_DEEP_LINK_SESSION_E2E"] == "1",
              let path = environment["QUATA_IOS_DEEP_LINK_SESSION_DIRECTORY"] else {
            throw XCTSkip("Requires the dedicated deep-link session coordinator.")
        }
        let files = try DeepLinkSessionFiles(path: path)
        let input = files.input
        try requirePassiveDeepLinkHost()
        try validateDeepLinkInstallTime(stage: input.stage, expiresAt: input.expiresAt, now: Int64(Date().timeIntervalSince1970))
        let storage = IosKeychainSessionStorage(service: "com.quata.auth-session", account: "current-user")
        try files.claim()
        try applyDeepLinkSessionStep(input.stage, storage: storage, expected: input.session)
        try files.receipt()
    }

    func testIsolatedSessionImportRefusesReplacementAndClearsOnlyExactSession() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_DEEP_LINK_KEYCHAIN_PROBE"] == "1" else {
            throw XCTSkip("Requires the dedicated signed simulator; never writes during unsigned CI.")
        }
        try requirePassiveDeepLinkHost()
        let storage = IosKeychainSessionStorage(service: "com.quata.tests.deep-links.\(UUID().uuidString)", account: "fixture")
        defer { storage.clear() }
        let session = AuthSession(token: "synthetic-access", userId: "synthetic-profile", email: "fixture@example.invalid",
            displayName: "Synthetic fixture", authUserId: "synthetic-auth", accessToken: "synthetic-access",
            refreshToken: "synthetic-refresh", expiresAt: KotlinLong(value: 2_000_000_000), isOfficial: false)
        let other = AuthSession(token: "other-access", userId: "synthetic-profile", email: "fixture@example.invalid",
            displayName: "Synthetic fixture", authUserId: "synthetic-auth", accessToken: "other-access",
            refreshToken: "other-refresh", expiresAt: KotlinLong(value: 2_000_000_000), isOfficial: false)
        try applyDeepLinkSessionStep("install", storage: storage, expected: session)
        XCTAssertThrowsError(try applyDeepLinkSessionStep("install", storage: storage, expected: other))
        XCTAssertThrowsError(try applyDeepLinkSessionStep("clear", storage: storage, expected: other))
        try applyDeepLinkSessionStep("verify", storage: storage, expected: session)
        try applyDeepLinkSessionStep("clear", storage: storage, expected: session)
        XCTAssertTrue(storage.getSession() == nil && storage.lastStatus == nil)
    }
}

private enum DeepLinkSessionError: Error { case invalidInput, privateFileUnavailable, unverified }

private func requirePassiveDeepLinkHost() throws {
    // The coordinator must additionally hold the simulator-wide lock and have
    // terminated earlier app/test processes. Keychain compare/write is not atomic.
    let arguments = ProcessInfo.processInfo.arguments
    guard arguments.filter({ $0 == "-quata-ui-test-fixture" }).count == 1,
          let index = arguments.firstIndex(of: "-quata-ui-test-fixture"), arguments.indices.contains(index + 1),
          arguments[index + 1] == "anonymous" else { throw DeepLinkSessionError.unverified }
}

private func validateDeepLinkInstallTime(stage: String, expiresAt: Int64, now: Int64) throws {
    if stage == "install" && expiresAt <= now + 900 { throw DeepLinkSessionError.invalidInput }
}

private func applyDeepLinkSessionStep(_ stage: String, storage: IosKeychainSessionStorage, expected: AuthSession) throws {
    let current = storage.getSession()
    guard storage.lastStatus == nil else { throw DeepLinkSessionError.unverified }
    switch stage {
    case "install":
        guard current == nil else { throw DeepLinkSessionError.unverified }
        storage.saveSession(session: expected)
        guard storage.lastStatus == nil else { throw DeepLinkSessionError.unverified }
        let saved = storage.getSession()
        guard storage.lastStatus == nil, saved?.isEqual(expected) == true else { throw DeepLinkSessionError.unverified }
    case "verify":
        guard current?.isEqual(expected) == true else { throw DeepLinkSessionError.unverified }
    case "clear":
        guard current?.isEqual(expected) == true else { throw DeepLinkSessionError.unverified }
        storage.clear()
        guard storage.lastStatus == nil else { throw DeepLinkSessionError.unverified }
        let remaining = storage.getSession()
        guard storage.lastStatus == nil, remaining == nil else { throw DeepLinkSessionError.unverified }
    default: throw DeepLinkSessionError.invalidInput
    }
}

private struct DeepLinkSessionInput: Decodable {
    let runId: String
    let stepId: String
    let stage: String
    let profileId: String
    let authUserId: String
    let authSessionId: String
    let accessToken: String
    let refreshToken: String
    let expiresAt: Int64
    let email: String
    let displayName: String
    let isOfficial: Bool

    var session: AuthSession {
        AuthSession(token: accessToken, userId: profileId, email: email, displayName: displayName,
            authUserId: authUserId, accessToken: accessToken, refreshToken: refreshToken,
            expiresAt: KotlinLong(value: expiresAt), isOfficial: isOfficial)
    }
}

/// Private, single-use exchange. No credentials are returned in the receipt or errors.
private final class DeepLinkSessionFiles {
    let input: DeepLinkSessionInput
    private let directory: Int32

    init(path: String) throws {
        let url = URL(fileURLWithPath: path).standardizedFileURL
        guard url.path == path, url.resolvingSymlinksInPath().path == path else { throw DeepLinkSessionError.privateFileUnavailable }
        let directory = Darwin.open(path, O_RDONLY | O_DIRECTORY | O_NOFOLLOW)
        guard directory >= 0 else { throw DeepLinkSessionError.privateFileUnavailable }
        do {
            var info = stat()
            guard fstat(directory, &info) == 0, info.st_uid == getuid(), info.st_mode & 0o777 == 0o700 else {
                throw DeepLinkSessionError.privateFileUnavailable
            }
            let fd = openat(directory, "input.json", O_RDONLY | O_NOFOLLOW)
            guard fd >= 0 else { throw DeepLinkSessionError.privateFileUnavailable }
            let file = FileHandle(fileDescriptor: fd, closeOnDealloc: true)
            defer { try? file.close() }
            guard fstat(fd, &info) == 0, info.st_uid == getuid(), info.st_mode & S_IFMT == S_IFREG,
                  info.st_mode & 0o777 == 0o600, info.st_size > 0, info.st_size <= 32_768 else {
                throw DeepLinkSessionError.privateFileUnavailable
            }
            let bytes = try file.read(upToCount: 32_769) ?? Data()
            guard bytes.count <= 32_768 else { throw DeepLinkSessionError.invalidInput }
            let input = try JSONDecoder().decode(DeepLinkSessionInput.self, from: bytes)
            guard [input.runId, input.stepId, input.profileId, input.authUserId, input.authSessionId].allSatisfy({ UUID(uuidString: $0) != nil }),
                  url.lastPathComponent == "deep-link-session-\(input.stepId)", ["install", "verify", "clear"].contains(input.stage),
                  !input.accessToken.isEmpty, !input.refreshToken.isEmpty, input.expiresAt > 0 else { throw DeepLinkSessionError.invalidInput }
            // Match the supplied receipt identity, not a signature verification.
            // The coordinator must verify the bearer against Auth before this step.
            let parts = input.accessToken.split(separator: ".", omittingEmptySubsequences: false)
            guard parts.count == 3 else { throw DeepLinkSessionError.invalidInput }
            var payload = String(parts[1]).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
            payload += String(repeating: "=", count: (4 - payload.count % 4) % 4)
            guard let decoded = Data(base64Encoded: payload),
                  let claims = try JSONSerialization.jsonObject(with: decoded) as? [String: Any],
                  claims["sub"] as? String == input.authUserId, claims["session_id"] as? String == input.authSessionId,
                  (claims["exp"] as? NSNumber)?.int64Value == input.expiresAt else { throw DeepLinkSessionError.invalidInput }
            self.input = input
            self.directory = directory
        } catch { Darwin.close(directory); throw DeepLinkSessionError.invalidInput }
    }

    deinit { Darwin.close(directory) }

    func claim() throws {
        let fd = openat(directory, "started", O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, mode_t(0o600))
        guard fd >= 0 else { throw DeepLinkSessionError.unverified }
        defer { Darwin.close(fd) }
        guard fsync(fd) == 0, fsync(directory) == 0 else { throw DeepLinkSessionError.unverified }
    }

    func receipt() throws {
        let bytes = try JSONSerialization.data(withJSONObject: ["runId": input.runId, "stepId": input.stepId,
            "stage": input.stage, "verified": true] as [String: Any])
        let fd = openat(directory, "receipt.json", O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, mode_t(0o600))
        guard fd >= 0 else { throw DeepLinkSessionError.privateFileUnavailable }
        let file = FileHandle(fileDescriptor: fd, closeOnDealloc: true)
        defer { try? file.close() }
        do { try file.write(contentsOf: bytes); try file.synchronize(); guard fsync(directory) == 0 else { throw DeepLinkSessionError.unverified } }
        catch { throw DeepLinkSessionError.unverified }
    }
}
