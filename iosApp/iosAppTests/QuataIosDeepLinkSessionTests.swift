import Darwin
import Foundation
import XCTest
import QuataShared

/// Imports an already journaled fixture session. Never performs a login or HTTP call.
/// The coordinator verifies the bearer/receipt before import and revokes it afterward.
final class QuataIosDeepLinkSessionTests: XCTestCase {
    func testReadOwnedNativeSession() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_DEEP_LINK_OWNED_READ"] == "1",
              let path = environment["QUATA_IOS_DEEP_LINK_SESSION_DIRECTORY"] else {
            throw XCTSkip("Requires the leased native-login coordinator.")
        }
        try requirePassiveDeepLinkHost()
        let files = try DeepLinkOwnedReadFiles(path: path)
        try files.claim()
        let storage = IosKeychainSessionStorage(service: "com.quata.auth-session", account: "current-user")
        let session = storage.getSession()
        guard storage.lastStatus == nil, let session else { throw DeepLinkSessionError.unverified }
        let value = try ownedDeepLinkSession(session, profileId: files.profileId, authUserId: files.authUserId)
        // Read-only adapter; verify the snapshot again before returning it privately.
        let unchanged = storage.getSession()
        guard storage.lastStatus == nil, unchanged?.isEqual(session) == true else { throw DeepLinkSessionError.unverified }
        try files.respond(value)
    }

    func testOwnedNativeReadRejectsForeignOwnerAndMixedTokens() throws {
        let profileId = UUID().uuidString, authUserId = UUID().uuidString, sessionId = UUID().uuidString
        let claims = try JSONSerialization.data(withJSONObject: ["sub": authUserId, "session_id": sessionId, "exp": 2_000_000_000] as [String: Any])
        let payload = claims.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        let access = "synthetic.\(payload).synthetic"
        func fixture(token: String? = nil, expiry: Int64 = 2_000_000_000, refresh: String = "synthetic-refresh") -> AuthSession {
            AuthSession(token: token ?? access, userId: profileId, email: "fixture@example.invalid", displayName: "Synthetic",
                authUserId: authUserId, accessToken: access, refreshToken: refresh, expiresAt: KotlinLong(value: expiry), isOfficial: false)
        }
        let value = try ownedDeepLinkSession(fixture(), profileId: profileId, authUserId: authUserId)
        XCTAssertTrue(value["authSessionId"] as? String == sessionId)
        XCTAssertThrowsError(try ownedDeepLinkSession(fixture(), profileId: UUID().uuidString, authUserId: authUserId))
        XCTAssertThrowsError(try ownedDeepLinkSession(fixture(), profileId: profileId, authUserId: UUID().uuidString))
        XCTAssertThrowsError(try ownedDeepLinkSession(fixture(token: "different"), profileId: profileId, authUserId: authUserId))
        XCTAssertThrowsError(try ownedDeepLinkSession(fixture(expiry: 1), profileId: profileId, authUserId: authUserId))
        XCTAssertThrowsError(try ownedDeepLinkSession(fixture(refresh: ""), profileId: profileId, authUserId: authUserId))
    }

    func testOwnedReadFilesRejectMixedCommandsReplayAndPublicPermissions() throws {
        let step = UUID().uuidString
        let root = FileManager.default.temporaryDirectory.resolvingSymlinksInPath().appendingPathComponent("deep-link-session-\(step)")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false, attributes: [.posixPermissions: 0o700])
        defer { try? FileManager.default.removeItem(at: root) }
        var input = ["runId": UUID().uuidString, "stepId": step, "stage": "read-owned", "profileId": UUID().uuidString, "authUserId": UUID().uuidString]
        let file = root.appendingPathComponent("input.json")
        func save() throws {
            try JSONSerialization.data(withJSONObject: input).write(to: file)
            try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: file.path)
        }
        try save()
        let exchange = try DeepLinkOwnedReadFiles(path: root.path)
        try exchange.claim()
        XCTAssertThrowsError(try exchange.claim())
        try exchange.respond(["accessToken": "synthetic-only"])
        XCTAssertThrowsError(try exchange.respond(["accessToken": "synthetic-only"]))
        let attributes = try FileManager.default.attributesOfItem(atPath: root.appendingPathComponent("private-response.json").path)
        XCTAssertEqual((attributes[.posixPermissions] as? NSNumber)?.intValue, 0o600)
        XCTAssertFalse(FileManager.default.fileExists(atPath: root.appendingPathComponent("receipt.json").path))
        input["accessToken"] = "unexpected"; try save()
        XCTAssertThrowsError(try DeepLinkOwnedReadFiles(path: root.path))
        input.removeValue(forKey: "accessToken"); input["stage"] = "install"; try save()
        XCTAssertThrowsError(try DeepLinkOwnedReadFiles(path: root.path))
        input["stage"] = "read-owned"; try save()
        try FileManager.default.setAttributes([.posixPermissions: 0o644], ofItemAtPath: file.path)
        XCTAssertThrowsError(try DeepLinkOwnedReadFiles(path: root.path))
        try FileManager.default.moveItem(at: file, to: root.appendingPathComponent("original.json"))
        try FileManager.default.createSymbolicLink(at: file, withDestinationURL: root.appendingPathComponent("original.json"))
        XCTAssertThrowsError(try DeepLinkOwnedReadFiles(path: root.path))
    }

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
        try validateDeepLinkInstallTime(stage: "install-expired", expiresAt: 999, now: 1_000, originalExpiresAt: 1_901)
        XCTAssertThrowsError(try validateDeepLinkInstallTime(stage: "install-expired", expiresAt: 1_000, now: 1_000, originalExpiresAt: 1_901))
        XCTAssertThrowsError(try validateDeepLinkInstallTime(stage: "install-expired", expiresAt: 999, now: 1_000, originalExpiresAt: 1_900))
        XCTAssertThrowsError(try validateDeepLinkInstallTime(stage: "install-expired", expiresAt: 999, now: 1_000))
        XCTAssertThrowsError(try validateDeepLinkInstallTime(stage: "install", expiresAt: 999, now: 1_000, originalExpiresAt: 1_901))
        try validateDeepLinkInstallTime(stage: "clear-expired", expiresAt: 999, now: 3_000, originalExpiresAt: 1_901)
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
        try validateDeepLinkInstallTime(stage: input.stage, expiresAt: input.expiresAt,
            now: Int64(Date().timeIntervalSince1970), originalExpiresAt: input.originalExpiresAt)
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
        let expired = AuthSession(token: "synthetic-access", userId: "synthetic-profile", email: "fixture@example.invalid",
            displayName: "Synthetic fixture", authUserId: "synthetic-auth", accessToken: "synthetic-access",
            refreshToken: "synthetic-refresh", expiresAt: KotlinLong(value: 1), isOfficial: false)
        try applyDeepLinkSessionStep("install-expired", storage: storage, expected: expired)
        XCTAssertThrowsError(try applyDeepLinkSessionStep("install-expired", storage: storage, expected: session))
        XCTAssertThrowsError(try applyDeepLinkSessionStep("clear-expired", storage: storage, expected: session))
        try applyDeepLinkSessionStep("clear-expired", storage: storage, expected: expired)
        XCTAssertTrue(storage.getSession() == nil && storage.lastStatus == nil)
    }
}

private enum DeepLinkSessionError: Error { case invalidInput, privateFileUnavailable, unverified }

/// Structural ownership only. The coordinator must verify this bearer with Auth
/// and journal the private return before attempting any exact-session clear.
private func ownedDeepLinkSession(_ session: AuthSession, profileId: String, authUserId: String) throws -> [String: Any] {
    guard session.userId == profileId, session.authUserId == authUserId,
          let access = session.accessToken, !access.isEmpty, session.token == access,
          let refresh = session.refreshToken, !refresh.isEmpty,
          let expiry = session.expiresAt?.int64Value, expiry > 0,
          !session.email.isEmpty, !session.displayName.isEmpty else { throw DeepLinkSessionError.unverified }
    let parts = access.split(separator: ".", omittingEmptySubsequences: false)
    guard parts.count == 3 else { throw DeepLinkSessionError.unverified }
    var payload = String(parts[1]).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
    payload += String(repeating: "=", count: (4 - payload.count % 4) % 4)
    guard let data = Data(base64Encoded: payload),
          let claims = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
          claims["sub"] as? String == authUserId,
          let sessionId = claims["session_id"] as? String, UUID(uuidString: sessionId) != nil,
          (claims["exp"] as? NSNumber)?.int64Value == expiry else { throw DeepLinkSessionError.unverified }
    return ["profileId": profileId, "authUserId": authUserId, "authSessionId": sessionId,
            "accessToken": access, "refreshToken": refresh, "expiresAt": expiry,
            "email": session.email, "displayName": session.displayName, "isOfficial": session.isOfficial]
}

/// A separate read command has no incoming bearer and cannot install or clear.
/// The response is private (0600), unlike the public install/clear receipt.
private final class DeepLinkOwnedReadFiles {
    let runId: String, stepId: String, profileId: String, authUserId: String
    private let directory: Int32

    init(path: String) throws {
        let url = URL(fileURLWithPath: path).standardizedFileURL
        guard url.path == path, url.resolvingSymlinksInPath().path == path else { throw DeepLinkSessionError.privateFileUnavailable }
        let directory = Darwin.open(path, O_RDONLY | O_DIRECTORY | O_NOFOLLOW)
        guard directory >= 0 else { throw DeepLinkSessionError.privateFileUnavailable }
        do {
            var info = stat()
            guard fstat(directory, &info) == 0, info.st_uid == getuid(), info.st_mode & 0o777 == 0o700 else { throw DeepLinkSessionError.privateFileUnavailable }
            let fd = openat(directory, "input.json", O_RDONLY | O_NOFOLLOW)
            guard fd >= 0 else { throw DeepLinkSessionError.privateFileUnavailable }
            let file = FileHandle(fileDescriptor: fd, closeOnDealloc: true)
            defer { try? file.close() }
            guard fstat(fd, &info) == 0, info.st_uid == getuid(), info.st_mode & S_IFMT == S_IFREG,
                  info.st_mode & 0o777 == 0o600, info.st_size > 0, info.st_size <= 4096 else { throw DeepLinkSessionError.privateFileUnavailable }
            let bytes = try file.read(upToCount: 4097) ?? Data()
            guard bytes.count <= 4096, let input = try JSONSerialization.jsonObject(with: bytes) as? [String: String],
                  Set(input.keys) == Set(["runId", "stepId", "stage", "profileId", "authUserId"]), input["stage"] == "read-owned",
                  let run = input["runId"], let step = input["stepId"], let profile = input["profileId"], let auth = input["authUserId"],
                  [run, step, profile, auth].allSatisfy({ UUID(uuidString: $0) != nil }),
                  url.lastPathComponent == "deep-link-session-\(step)" else { throw DeepLinkSessionError.invalidInput }
            runId = run; stepId = step; profileId = profile; authUserId = auth; self.directory = directory
        } catch { Darwin.close(directory); throw DeepLinkSessionError.invalidInput }
    }

    deinit { Darwin.close(directory) }

    func claim() throws {
        let fd = openat(directory, "started", O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, mode_t(0o600))
        guard fd >= 0 else { throw DeepLinkSessionError.unverified }
        defer { Darwin.close(fd) }
        guard fsync(fd) == 0, fsync(directory) == 0 else { throw DeepLinkSessionError.unverified }
    }

    func respond(_ session: [String: Any]) throws {
        let value: [String: Any] = ["runId": runId, "stepId": stepId, "stage": "read-owned", "verified": true, "privateSession": session]
        let bytes = try JSONSerialization.data(withJSONObject: value)
        guard bytes.count <= 32768 else { throw DeepLinkSessionError.unverified }
        let fd = openat(directory, "private-response.json", O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, mode_t(0o600))
        guard fd >= 0 else { throw DeepLinkSessionError.privateFileUnavailable }
        let file = FileHandle(fileDescriptor: fd, closeOnDealloc: true)
        defer { try? file.close() }
        do { try file.write(contentsOf: bytes); try file.synchronize(); guard fsync(directory) == 0 else { throw DeepLinkSessionError.unverified } }
        catch { throw DeepLinkSessionError.unverified }
    }
}

private func requirePassiveDeepLinkHost() throws {
    // The coordinator must additionally hold the simulator-wide lock and have
    // terminated earlier app/test processes. Keychain compare/write is not atomic.
    let arguments = ProcessInfo.processInfo.arguments
    guard arguments.filter({ $0 == "-quata-ui-test-fixture" }).count == 1,
          let index = arguments.firstIndex(of: "-quata-ui-test-fixture"), arguments.indices.contains(index + 1),
          arguments[index + 1] == "anonymous" else { throw DeepLinkSessionError.unverified }
}

private func validateDeepLinkInstallTime(stage: String, expiresAt: Int64, now: Int64, originalExpiresAt: Int64? = nil) throws {
    if ["install-expired", "clear-expired"].contains(stage) {
        guard let originalExpiresAt, expiresAt > 0, expiresAt < now, originalExpiresAt > expiresAt else {
            throw DeepLinkSessionError.invalidInput
        }
        if stage == "install-expired" && originalExpiresAt <= now + 900 { throw DeepLinkSessionError.invalidInput }
        return
    }
    guard originalExpiresAt == nil else { throw DeepLinkSessionError.invalidInput }
    if stage == "install" && expiresAt <= now + 900 { throw DeepLinkSessionError.invalidInput }
}

private func applyDeepLinkSessionStep(_ stage: String, storage: IosKeychainSessionStorage, expected: AuthSession) throws {
    let current = storage.getSession()
    guard storage.lastStatus == nil else { throw DeepLinkSessionError.unverified }
    switch stage {
    case "install", "install-expired":
        guard current == nil else { throw DeepLinkSessionError.unverified }
        storage.saveSession(session: expected)
        guard storage.lastStatus == nil else { throw DeepLinkSessionError.unverified }
        let saved = storage.getSession()
        guard storage.lastStatus == nil, saved?.isEqual(expected) == true else { throw DeepLinkSessionError.unverified }
    case "verify":
        guard current?.isEqual(expected) == true else { throw DeepLinkSessionError.unverified }
    case "clear", "clear-expired":
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
    let originalExpiresAt: Int64?
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
                  url.lastPathComponent == "deep-link-session-\(input.stepId)", ["install", "verify", "clear", "install-expired", "clear-expired"].contains(input.stage),
                  !input.accessToken.isEmpty, !input.refreshToken.isEmpty, input.expiresAt > 0 else { throw DeepLinkSessionError.invalidInput }
            let expiredStage = ["install-expired", "clear-expired"].contains(input.stage)
            guard expiredStage == (input.originalExpiresAt != nil),
                  !expiredStage || input.originalExpiresAt! > input.expiresAt else { throw DeepLinkSessionError.invalidInput }
            // Match the supplied receipt identity, not a signature verification.
            // The coordinator must verify the bearer against Auth before this step.
            let parts = input.accessToken.split(separator: ".", omittingEmptySubsequences: false)
            guard parts.count == 3 else { throw DeepLinkSessionError.invalidInput }
            var payload = String(parts[1]).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
            payload += String(repeating: "=", count: (4 - payload.count % 4) % 4)
            guard let decoded = Data(base64Encoded: payload),
                  let claims = try JSONSerialization.jsonObject(with: decoded) as? [String: Any],
                  claims["sub"] as? String == input.authUserId, claims["session_id"] as? String == input.authSessionId,
                  (claims["exp"] as? NSNumber)?.int64Value == (input.originalExpiresAt ?? input.expiresAt) else { throw DeepLinkSessionError.invalidInput }
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
