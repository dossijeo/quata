import Foundation
import XCTest
import QuataShared
@testable import QuataIos

/// Opt-in production-session seed for manual iOS visual gates.
///
/// This is intentionally a single repository login, not a feature test. A successful result is
/// kept in the app host Keychain so a subsequent normal launch can reuse the authenticated state.
final class QuataIosAuthenticatedSessionSeederTests: XCTestCase {
    func testSeedAuthenticatedSessionForVisualGates() throws {
        guard let configurationFile = ProcessInfo.processInfo.environment["QUATA_IOS_AUTH_E2E_FILE"],
              !configurationFile.isEmpty else {
            throw XCTSkip("QUATA_IOS_AUTH_E2E_FILE is not configured; authenticated seeding is opt-in.")
        }
        let credentials = try AuthSeederCredentials.load(from: configurationFile)
        guard let feedConfiguration = IosPublicRuntimeConfiguration.feedConfiguration() else {
            throw XCTSkip("The app host has no valid public runtime configuration.")
        }

        let runtimeBootstrap = IosFeedRuntimeBootstrapKt.createIosFeedRuntimeBootstrap(
            configuration: feedConfiguration,
        )
        let repository = IosAuthRepositoryKt.createIosAuthRepository(
            configuration: IosPublicRuntimeConfiguration.authConfiguration(from: feedConfiguration),
            session: runtimeBootstrap.authSessionForInteractiveLogin(),
        )
        let completed = expectation(description: "one production login completion")
        var completionCount = 0

        repository.login(
            countryCode: credentials.countryCode,
            phone: credentials.localPhone,
            password: credentials.password,
        ) { session, error in
            completionCount += 1
            XCTAssertNil(error, "The production login completion must not return an error.")
            XCTAssertNotNil(session, "The production login completion must return an authenticated session.")
            if let receiptPath = ProcessInfo.processInfo.environment["QUATA_IOS_AUTH_LOGOUT_SESSION_RECEIPT_FILE"],
               !receiptPath.isEmpty {
                XCTAssertNoThrow(try writeLogoutSessionReceipt(session: session, path: receiptPath))
            }
            completed.fulfill()
        }

        wait(for: [completed], timeout: 30)
        XCTAssertEqual(completionCount, 1, "The seeder must issue exactly one login completion.")
        XCTAssertTrue(runtimeBootstrap.hasRestoredSession(), "The production runtime must restore the saved Keychain session.")
    }

    func testClearAuthenticatedSessionAfterVisualGates() throws {
        guard let feedConfiguration = IosPublicRuntimeConfiguration.feedConfiguration() else {
            throw XCTSkip("The app host has no valid public runtime configuration.")
        }
        let runtimeBootstrap = IosFeedRuntimeBootstrapKt.createIosFeedRuntimeBootstrap(
            configuration: feedConfiguration,
        )
        let session = runtimeBootstrap.authSessionForInteractiveLogin()
        let repository = IosAuthRepositoryKt.createIosAuthRepository(
            configuration: IosPublicRuntimeConfiguration.authConfiguration(from: feedConfiguration),
            session: session,
        )
        let completed = expectation(description: "visual gate session cleanup")
        completed.assertForOverFulfill = true
        var completionCount = 0
        repository.logout { error in
            completionCount += 1
            XCTAssertNil(error, "Visual-gate logout must complete even when remote retirement is unavailable.")
            completed.fulfill()
        }
        wait(for: [completed], timeout: 45)
        XCTAssertEqual(completionCount, 1, "Visual-gate cleanup must complete exactly once.")
        XCTAssertNil(session.restoredSession(), "Visual-gate cleanup must remove the Keychain session.")
        XCTAssertFalse(runtimeBootstrap.hasRestoredSession(), "The production runtime must return to anonymous state.")
    }
}

private func writeLogoutSessionReceipt(session: AuthSession?, path: String) throws {
    guard let session,
          UUID(uuidString: session.authUserId) != nil,
          let sessionId = jwtSessionId(session.accessToken),
          UUID(uuidString: sessionId) != nil else {
        throw AuthSeederConfigurationError.invalidSessionReceipt
    }
    let data = try JSONSerialization.data(
        withJSONObject: ["session_id": sessionId, "auth_user_id": session.authUserId],
        options: [.sortedKeys],
    )
    let url = URL(fileURLWithPath: path)
    try data.write(to: url, options: .atomic)
    try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: path)
}

private func jwtSessionId(_ token: String) -> String? {
    let parts = token.split(separator: ".", omittingEmptySubsequences: false)
    guard parts.count == 3 else { return nil }
    var encoded = String(parts[1]).replacingOccurrences(of: "-", with: "+")
        .replacingOccurrences(of: "_", with: "/")
    encoded += String(repeating: "=", count: (4 - encoded.count % 4) % 4)
    guard let data = Data(base64Encoded: encoded),
          let payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
    return payload["session_id"] as? String
}

private struct AuthSeederCredentials: Decodable {
    let phone: String
    let password: String
    let countryCode: String

    enum CodingKeys: String, CodingKey {
        case phone
        case password
        case countryCode = "country_code"
    }

    let localPhone: String

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        phone = try container.decode(String.self, forKey: .phone)
        password = try container.decode(String.self, forKey: .password)
        let configuredCountryInput = try container.decodeIfPresent(String.self, forKey: .countryCode)
        let digits = phone.hasPrefix("+") ? String(phone.dropFirst()) : ""
        guard digits.count >= 8, digits.count <= 15, digits.allSatisfy(\.isNumber), !password.isEmpty else {
            throw AuthSeederConfigurationError.invalidCredentials
        }
        let configuredCountry = configuredCountryInput?.trimmingCharacters(in: CharacterSet(charactersIn: "+ "))
        guard configuredCountry == nil || (configuredCountry!.allSatisfy(\.isNumber) && digits.hasPrefix(configuredCountry!)) else {
            throw AuthSeederConfigurationError.invalidCountryCode
        }
        let effectiveCountry = configuredCountry ?? (digits.hasPrefix("240") ? "240" : nil)
        guard effectiveCountry == "240", digits.count > effectiveCountry!.count else {
            throw AuthSeederConfigurationError.unsupportedCountryCode
        }
        let selectedCountry = effectiveCountry!
        countryCode = selectedCountry
        localPhone = String(digits.dropFirst(selectedCountry.count))
    }

    static func load(from path: String) throws -> AuthSeederCredentials {
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        return try JSONDecoder().decode(AuthSeederCredentials.self, from: data)
    }
}

private enum AuthSeederConfigurationError: LocalizedError {
    case invalidCredentials
    case invalidCountryCode
    case unsupportedCountryCode
    case invalidSessionReceipt

    var errorDescription: String? {
        switch self {
        case .invalidCredentials: return "The auth seeder file has an invalid credential shape."
        case .invalidCountryCode: return "The auth seeder country code does not match the E.164 phone."
        case .unsupportedCountryCode: return "The current iOS seeder requires Equatorial Guinea country code 240."
        case .invalidSessionReceipt: return "The seeded session cannot produce a private logout receipt."
        }
    }
}
