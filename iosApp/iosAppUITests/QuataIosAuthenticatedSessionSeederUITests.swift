import Foundation
import XCTest

/// Opt-in, one-shot session seeder for manual authenticated iOS visual gates.
///
/// It deliberately leaves the successful Keychain session in place and does not exercise any
/// feature screen beyond the normal Feed -> Conversations authentication entry point.
final class QuataIosAuthenticatedSessionSeederUITests: XCTestCase {
    func testSeedAuthenticatedSessionForVisualGates() throws {
        guard let configurationFile = ProcessInfo.processInfo.environment["QUATA_IOS_AUTH_E2E_FILE"],
              !configurationFile.isEmpty else {
            throw XCTSkip("QUATA_IOS_AUTH_E2E_FILE is not configured; authenticated seeding is opt-in.")
        }
        let credentials = try AuthSeederCredentials.load(from: configurationFile)

        let app = XCUIApplication()
        app.launch()

        let conversations = app.buttons["Conversaciones"]
        XCTAssertTrue(
            conversations.waitForExistence(timeout: 15),
            "Normal public Feed must expose Conversations as the authentication entry point.",
        )
        conversations.tap()

        let phoneSemantic = semantic("auth.phone", in: app)
        let passwordSemantic = semantic("auth.password", in: app)
        let submit = semantic("auth.submit", in: app)
        XCTAssertTrue(phoneSemantic.waitForExistence(timeout: 15), "Shared auth.phone semantics must be available.")
        XCTAssertTrue(passwordSemantic.waitForExistence(timeout: 15), "Shared auth.password semantics must be available.")
        XCTAssertTrue(submit.waitForExistence(timeout: 15), "Shared auth.submit semantics must be available.")

        let phoneField = try editableDescendant(of: phoneSemantic, in: app, secure: false)
        enter(credentials.localPhone, into: phoneField, semantic: phoneSemantic, secure: false)

        let passwordField = try editableDescendant(of: passwordSemantic, in: app, secure: true)
        enter(credentials.password, into: passwordField, semantic: passwordSemantic, secure: true)
        submit.tap()

        let chromeLabels = ["Qüata", "Chats", "Oficial", "Feed", "Cuenta"]
        let firstChromeItem = app.staticTexts[chromeLabels[0]]
        let hostLabel = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Quata para iOS"))
            .firstMatch
        XCTAssertTrue(
            firstChromeItem.waitForExistence(timeout: 20) || hostLabel.waitForExistence(timeout: 20),
            "A successful sign-in must install the authenticated host or shared primary navigation chrome.",
        )
        if firstChromeItem.exists {
            chromeLabels.forEach { label in
                XCTAssertTrue(app.staticTexts[label].exists, "Authenticated primary navigation is missing \(label).")
            }
        }
        // Intentionally do not terminate: the Keychain session is the reusable visual-gate seed.
    }

    private func semantic(_ identifier: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    private func editableDescendant(of semantic: XCUIElement, in app: XCUIApplication, secure: Bool) throws -> XCUIElement {
        let preferred = secure ? semantic.secureTextFields : semantic.textFields
        if preferred.firstMatch.exists { return preferred.firstMatch }
        let fallback = secure ? semantic.textFields : semantic.secureTextFields
        if fallback.firstMatch.exists { return fallback.firstMatch }
        // Compose semantics can be flattened by the XCTest accessibility bridge. Preserve the
        // semantic-first lookup above, then use the sole matching editable control in the app.
        let flattenedPreferred = secure ? app.secureTextFields : app.textFields
        if flattenedPreferred.count == 1 { return flattenedPreferred.firstMatch }
        let flattenedFallback = secure ? app.textFields : app.secureTextFields
        if flattenedFallback.count == 1 { return flattenedFallback.firstMatch }
        return semantic
    }

    private func enter(_ value: String, into field: XCUIElement, semantic: XCUIElement, secure: Bool) {
        if field == semantic {
            // Compose can expose only the semantic container. Its right side is the local phone
            // field; the password field occupies the full container. Tapping there focuses the
            // platform editor before typeText is sent to the semantics bridge.
            let x = secure ? 0.5 : 0.75
            semantic.coordinate(withNormalizedOffset: CGVector(dx: x, dy: 0.5)).tap()
            semantic.typeText(value)
        } else {
            field.tap()
            field.typeText(value)
        }
    }
}

private struct AuthSeederCredentials: Decodable {
    let phone: String
    let password: String
    let countryCode: String?

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
        countryCode = try container.decodeIfPresent(String.self, forKey: .countryCode)
        let digits = phone.hasPrefix("+") ? String(phone.dropFirst()) : ""
        guard digits.count >= 8, digits.count <= 15, digits.allSatisfy(\.isNumber), !password.isEmpty else {
            throw AuthSeederConfigurationError.invalidCredentials
        }
        let configuredCountry = countryCode?.trimmingCharacters(in: CharacterSet(charactersIn: "+ "))
        guard configuredCountry == nil || (configuredCountry!.allSatisfy(\.isNumber) && digits.hasPrefix(configuredCountry!)) else {
            throw AuthSeederConfigurationError.invalidCountryCode
        }
        let effectiveCountry = configuredCountry ?? (digits.hasPrefix("240") ? "240" : nil)
        guard effectiveCountry == "240", digits.count > effectiveCountry!.count else {
            throw AuthSeederConfigurationError.unsupportedCountryCode
        }
        localPhone = String(digits.dropFirst(effectiveCountry!.count))
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

    var errorDescription: String? {
        switch self {
        case .invalidCredentials: return "The auth seeder file has an invalid credential shape."
        case .invalidCountryCode: return "The auth seeder country code does not match the E.164 phone."
        case .unsupportedCountryCode: return "The current iOS seeder requires Equatorial Guinea country code 240."
        }
    }
}
