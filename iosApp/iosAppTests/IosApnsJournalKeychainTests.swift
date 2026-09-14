import XCTest
import Security
import QuataShared

final class IosApnsJournalKeychainTests: XCTestCase {
    private func removeTestJournal(_ backend: String) {
        let status = SecItemDelete([
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: "com.quata.apns-cleanup:\(backend)",
            kSecAttrAccount: "registrations-v1",
        ] as CFDictionary)
        XCTAssertTrue(status == errSecSuccess || status == errSecItemNotFound)
    }

    func testPendingRegistrationSurvivesNewJournalInstanceAndUsesDeviceOnlyStorage() {
        let backend = "https://apns-journal-\(UUID().uuidString.lowercased()).invalid"
        defer { removeTestJournal(backend) }
        let first = IosApnsRegistrationJournal(backendUrl: backend)
        XCTAssertTrue(first.read().isEmpty)
        let record = ApnsPendingRegistration(
            profileId: "test-profile", authUserId: "test-auth", token: "abcdef", environment: "sandbox")
        XCTAssertTrue(first.write(records: [record]))

        let restored = IosApnsRegistrationJournal(backendUrl: backend + "/")
        let records = restored.read()
        XCTAssertEqual(records.count, 1)
        XCTAssertEqual(records.first?.profileId, "test-profile")
        XCTAssertEqual(records.first?.authUserId, "test-auth")
        XCTAssertEqual(records.first?.token, "abcdef")
        XCTAssertEqual(records.first?.environment, "sandbox")
        var attributes: CFTypeRef?
        XCTAssertEqual(SecItemCopyMatching([
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: "com.quata.apns-cleanup:\(backend)",
            kSecAttrAccount: "registrations-v1",
            kSecReturnAttributes: true,
        ] as CFDictionary, &attributes), errSecSuccess)
        let values = attributes as? [String: Any]
        XCTAssertEqual(values?[kSecAttrAccessible as String] as? String,
                       kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly as String)
        XCTAssertTrue(restored.write(records: []))
        XCTAssertTrue(IosApnsRegistrationJournal(backendUrl: backend).read().isEmpty)
    }

    func testDifferentBackendsCannotReadOrOverwriteEachOthersCleanup() {
        let firstBackend = "https://apns-journal-\(UUID().uuidString.lowercased()).invalid"
        let secondBackend = "https://apns-journal-\(UUID().uuidString.lowercased()).invalid"
        defer { removeTestJournal(firstBackend); removeTestJournal(secondBackend) }
        let first = IosApnsRegistrationJournal(backendUrl: firstBackend)
        let second = IosApnsRegistrationJournal(backendUrl: secondBackend)
        XCTAssertTrue(first.write(records: [ApnsPendingRegistration(
            profileId: "test-profile", authUserId: nil, token: "1234", environment: "production")]))
        XCTAssertTrue(second.read().isEmpty)
        XCTAssertTrue(second.write(records: []))
        XCTAssertEqual(first.read().first?.token, "1234")
    }
}
