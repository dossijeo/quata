import Darwin
import Foundation

@main struct CheckRecoveryPrivateFiles {
    static func main() throws {
        let root = FileManager.default.temporaryDirectory.resolvingSymlinksInPath().appendingPathComponent("quata-private-check-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false, attributes: [.posixPermissions: 0o700])
        defer { try? FileManager.default.removeItem(at: root) }
        func fixture() throws -> URL {
            let step = UUID().uuidString
            let dir = root.appendingPathComponent("recovery-secret-\(step)")
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: false, attributes: [.posixPermissions: 0o700])
            let data = try JSONSerialization.data(withJSONObject: ["runId": UUID().uuidString, "stepId": step,
                "profileId": UUID().uuidString, "authUserId": UUID().uuidString, "stage": "empty"])
            guard FileManager.default.createFile(atPath: dir.appendingPathComponent("input.json").path,
                contents: data, attributes: [.posixPermissions: 0o600]) else { fatalError("fixture creation failed") }
            return dir
        }
        func rejected(_ operation: () throws -> Void) throws {
            do { try operation() } catch { return }
            throw RecoverySecretStepError.operationUnverified
        }
        let valid = try fixture()
        let exchange = try RecoverySecretPrivateFiles(path: valid.path)
        try exchange.claimOperation()
        try rejected { try RecoverySecretPrivateFiles(path: valid.path).claimOperation() }
        try exchange.writeReceipt(["sessionEmpty": true])
        let receipt = valid.appendingPathComponent("receipt.json")
        let original = try Data(contentsOf: receipt)
        let attributes = try FileManager.default.attributesOfItem(atPath: receipt.path)
        guard (attributes[.posixPermissions] as? NSNumber)?.intValue == 0o600 else { fatalError("receipt permissions failed") }
        try rejected { try exchange.writeReceipt(["sessionEmpty": false]) }
        guard try Data(contentsOf: receipt) == original else { fatalError("receipt changed") }

        let exposed = try fixture()
        chmod(exposed.appendingPathComponent("input.json").path, 0o644)
        try rejected { _ = try RecoverySecretPrivateFiles(path: exposed.path) }
        let shared = try fixture()
        chmod(shared.path, 0o755)
        try rejected { _ = try RecoverySecretPrivateFiles(path: shared.path) }
        let linked = try fixture()
        try FileManager.default.removeItem(at: linked.appendingPathComponent("input.json"))
        try FileManager.default.createSymbolicLink(at: linked.appendingPathComponent("input.json"),
            withDestinationURL: valid.appendingPathComponent("input.json"))
        try rejected { _ = try RecoverySecretPrivateFiles(path: linked.path) }
        let poisoned = try fixture()
        try FileManager.default.createSymbolicLink(at: poisoned.appendingPathComponent("receipt.json"), withDestinationURL: receipt)
        try rejected { try RecoverySecretPrivateFiles(path: poisoned.path).writeReceipt(["sessionEmpty": false]) }
        guard try Data(contentsOf: receipt) == original else { fatalError("linked receipt changed") }
        print("PASS: private input, exclusive receipt, file/directory permissions and symlink rejection")
    }
}
