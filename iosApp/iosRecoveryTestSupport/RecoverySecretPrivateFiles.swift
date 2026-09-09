import Darwin
import Foundation

enum RecoverySecretStepError: Error { case invalidInput, privateFileUnavailable, operationUnverified }

struct RecoverySecretStepInput: Decodable {
    let runId: String
    let stepId: String
    let stage: String
    let profileId: String
    let authUserId: String
    let ticketId: String?
    let countryCode: String?
    let phone: String?
    let password: String?
    let question: String?
    let answer: String?
    let displayName: String?
    let questionLabel: String?
}

/// Test-only exchange. The coordinator owns directory creation, journaling and removal.
final class RecoverySecretPrivateFiles {
    let input: RecoverySecretStepInput
    private let directory: Int32

    init(path: String) throws {
        let url = URL(fileURLWithPath: path).standardizedFileURL
        guard url.path == path, url.resolvingSymlinksInPath().path == path else {
            throw RecoverySecretStepError.privateFileUnavailable
        }
        let directory = Darwin.open(path, O_RDONLY | O_DIRECTORY | O_NOFOLLOW)
        guard directory >= 0 else { throw RecoverySecretStepError.privateFileUnavailable }
        do {
            var info = stat()
            guard fstat(directory, &info) == 0, info.st_uid == getuid(), info.st_mode & 0o777 == 0o700 else {
                throw RecoverySecretStepError.privateFileUnavailable
            }
            let fd = openat(directory, "input.json", O_RDONLY | O_NOFOLLOW)
            guard fd >= 0 else { throw RecoverySecretStepError.privateFileUnavailable }
            let file = FileHandle(fileDescriptor: fd, closeOnDealloc: true)
            defer { try? file.close() }
            guard fstat(fd, &info) == 0, info.st_uid == getuid(), info.st_mode & S_IFMT == S_IFREG,
                  info.st_mode & 0o777 == 0o600, info.st_size > 0, info.st_size <= 32_768 else {
                throw RecoverySecretStepError.privateFileUnavailable
            }
            let bytes = try file.read(upToCount: 32_769) ?? Data()
            guard bytes.count <= 32_768 else { throw RecoverySecretStepError.invalidInput }
            let input = try JSONDecoder().decode(RecoverySecretStepInput.self, from: bytes)
            guard [input.runId, input.stepId, input.profileId, input.authUserId].allSatisfy({ UUID(uuidString: $0) != nil }),
                  url.lastPathComponent == "recovery-secret-\(input.stepId)",
                  ["login", "logout", "empty", "identity", "configure", "read", "recover"].contains(input.stage) else {
                throw RecoverySecretStepError.invalidInput
            }
            self.input = input
            self.directory = directory
        } catch {
            Darwin.close(directory)
            throw RecoverySecretStepError.invalidInput
        }
    }

    deinit { Darwin.close(directory) }

    func claimOperation() throws {
        let fd = openat(directory, "started", O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, mode_t(0o600))
        guard fd >= 0 else { throw RecoverySecretStepError.operationUnverified }
        defer { Darwin.close(fd) }
        guard fsync(fd) == 0, fsync(directory) == 0 else { throw RecoverySecretStepError.operationUnverified }
    }

    func writeReceipt(_ result: [String: Any]) throws {
        let receipt: [String: Any] = ["runId": input.runId, "stepId": input.stepId,
            "stage": input.stage, "profileId": input.profileId, "authUserId": input.authUserId, "result": result]
        let bytes: Data
        do { bytes = try JSONSerialization.data(withJSONObject: receipt) }
        catch { throw RecoverySecretStepError.operationUnverified }
        guard bytes.count <= 32_768 else { throw RecoverySecretStepError.operationUnverified }
        let fd = openat(directory, "receipt.json", O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, mode_t(0o600))
        guard fd >= 0 else { throw RecoverySecretStepError.privateFileUnavailable }
        let file = FileHandle(fileDescriptor: fd, closeOnDealloc: true)
        defer { try? file.close() }
        do {
            try file.write(contentsOf: bytes)
            try file.synchronize()
            guard fsync(directory) == 0 else { throw RecoverySecretStepError.privateFileUnavailable }
        } catch { throw RecoverySecretStepError.privateFileUnavailable }
    }
}
