import XCTest

/// Opt-in, production-session UI gate for the shared Account details flow.
final class QuataIosAuthenticatedAccountDetailsUITests: XCTestCase {
    func testAuthenticatedSessionChangesAccountDetailsFromCommonProfile() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_ACCOUNT_DETAILS_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated account details UI gate is opt-in.")
        }
        let expectedName = try required(environment, "QUATA_IOS_ACCOUNT_DETAILS_DISPLAY_NAME")
        let expectedNeighborhood = try required(environment, "QUATA_IOS_ACCOUNT_DETAILS_NEIGHBORHOOD")
        let expectedPhone = try required(environment, "QUATA_IOS_ACCOUNT_DETAILS_PHONE")

        let app = launchAuthenticatedApp()
        openProfileDetails(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-account-details-form-opened")

        clearAndTypeText(expectedName, into: "profile.details.name", in: app)
        XCTAssertTrue(waitForFieldValue("profile.details.name", in: app, equals: expectedName))
        clearAndTypeText(expectedNeighborhood, into: "profile.details.neighborhood", in: app)
        XCTAssertTrue(waitForFieldValue("profile.details.neighborhood", in: app, equals: expectedNeighborhood))
        clearAndTypeText(expectedPhone, into: "profile.details.phone", in: app)
        XCTAssertTrue(waitForFieldValue("profile.details.phone", in: app, equalsDigits: expectedPhone))
        dismissKeyboard(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-account-details-form-edited")

        tapIdentifier("profile.details.save", in: app, context: "save account details")
        XCTAssertTrue(
            waitForSavedFeedback(in: app, timeout: 45),
            "Saving Account details must expose shared saved feedback after server persistence."
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-account-details-saved")

        app.terminate()
        let relaunched = launchAuthenticatedApp()
        openProfileDetails(in: relaunched)
        let reloadedName = fieldValue("profile.details.name", in: relaunched)
        let reloadedNeighborhood = fieldValue("profile.details.neighborhood", in: relaunched)
        let reloadedPhone = fieldValue("profile.details.phone", in: relaunched)
        XCTAssertTrue(
            reloadedName == expectedName,
            "Relaunched iOS Account details must show the persisted display name. Actual: \(reloadedName)"
        )
        XCTAssertTrue(
            reloadedNeighborhood == expectedNeighborhood,
            "Relaunched iOS Account details must show the persisted neighborhood. Actual: \(reloadedNeighborhood)"
        )
        XCTAssertTrue(
            onlyDigits(reloadedPhone) == onlyDigits(expectedPhone),
            "Relaunched iOS Account details must show the persisted phone. Actual: \(reloadedPhone)"
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-account-details-reloaded")
        print("IOS_ACCOUNT_DETAILS_UI_GATE_PASSED")
    }

    private func launchAuthenticatedApp() -> XCUIApplication {
        let app = XCUIApplication()
        disableQuiescenceWait(for: app)
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()
        let feed = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-feed-host")
            .firstMatch
        XCTAssertTrue(feed.waitForExistence(timeout: 25), "A normal launch must restore Feed from the seeded Keychain session.")
        return app
    }

    private func openProfileDetails(in app: XCUIApplication) {
        tapIdentifier("navigation.primary.profile", in: app, context: "open account primary route")
        let profileHost = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-profile-sos-host")
            .firstMatch
        XCTAssertTrue(profileHost.waitForExistence(timeout: 25), "The real shared Account/Profile host must open from authenticated iOS chrome.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-account-details-profile-opened")
        tapIdentifier("profile.details.open", in: app, context: "open account details")
        XCTAssertTrue(
            app.descendants(matching: .any).matching(identifier: "profile.details.root").firstMatch.waitForExistence(timeout: 12),
            "The shared Account details surface must open from iOS."
        )
    }

    private func tapIdentifier(_ identifier: String, in app: XCUIApplication, context: String) {
        var element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        for _ in 0..<8 {
            if element.waitForExistence(timeout: 1) {
                if element.isHittable {
                    element.tap()
                    return
                }
            }
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
            element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        }
        if element.exists {
            element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
            return
        }
        XCTAssertTrue(element.exists, "Expected \(identifier) to exist for \(context).")
    }

    private func clearAndTypeText(_ value: String, into identifier: String, in app: XCUIApplication) {
        let field = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10), "Expected \(identifier) to exist before text entry.")
        if !field.isHittable {
            field.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        } else {
            field.tap()
        }
        RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        clearText(identifier: identifier, app: app)
        typeIntoFocusedElement(value, fallback: field, in: app)
    }

    private func fieldValue(_ identifier: String, in app: XCUIApplication) -> String {
        let field = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10), "Expected \(identifier) after reload.")
        let rawValue = field.value as? String
        return rawValue ?? field.label
    }

    private func waitForFieldValue(_ identifier: String, in app: XCUIApplication, equals expected: String) -> Bool {
        waitForFieldValue(identifier, in: app) { $0 == expected }
    }

    private func waitForFieldValue(_ identifier: String, in app: XCUIApplication, equalsDigits expected: String) -> Bool {
        let digits = onlyDigits(expected)
        return waitForFieldValue(identifier, in: app) { onlyDigits($0) == digits }
    }

    private func waitForFieldValue(_ identifier: String, in app: XCUIApplication, matches predicate: (String) -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(6)
        repeat {
            if predicate(fieldValue(identifier, in: app)) {
                return true
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        } while Date() < deadline
        return false
    }

    private func waitForSavedFeedback(in app: XCUIApplication, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        let semanticSuccess = app.descendants(matching: .any)
            .matching(identifier: "profile.feedback.success")
            .firstMatch
        let savedPredicates = [
            NSPredicate(format: "label CONTAINS[c] %@", "Cambios"),
            NSPredicate(format: "label CONTAINS[c] %@", "saved"),
            NSPredicate(format: "label CONTAINS[c] %@", "synchron"),
        ]
        while Date() < deadline {
            if semanticSuccess.exists {
                return true
            }
            for predicate in savedPredicates {
                if app.descendants(matching: .any).matching(predicate).firstMatch.exists {
                    return true
                }
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        return false
    }

    private func firstExistingMenuItem(in app: XCUIApplication, labels: [String], timeout: TimeInterval) -> XCUIElement? {
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            for label in labels {
                let item = app.menuItems[label].firstMatch
                if item.exists {
                    return item
                }
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        } while Date() < deadline
        return nil
    }

    private func clearText(identifier: String, app: XCUIApplication) {
        if fieldValue(identifier, in: app).isEmpty {
            return
        }
        let clear = app.descendants(matching: .any)
            .matching(identifier: "\(identifier).clear")
            .firstMatch
        XCTAssertTrue(clear.waitForExistence(timeout: 4), "Expected clear action for \(identifier).")
        clear.tap()
        XCTAssertTrue(waitForFieldValue(identifier, in: app, equals: ""), "Expected \(identifier) to be empty before replacement. Actual: \(fieldValue(identifier, in: app))")
    }

    private func typeIntoFocusedElement(_ value: String, fallback: XCUIElement, in app: XCUIApplication) {
        let focused = app.descendants(matching: .any)
            .matching(NSPredicate(format: "hasKeyboardFocus == 1"))
            .firstMatch
        if focused.waitForExistence(timeout: app.keyboards.count > 0 ? 0.5 : 2) {
            focused.typeText(value)
        } else {
            fallback.tap()
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
            fallback.typeText(value)
        }
    }

    private func dismissKeyboard(in app: XCUIApplication) {
        guard app.keyboards.count > 0 else { return }
        for label in ["Done", "Return", "Intro", "Aceptar"] {
            let key = app.keyboards.buttons[label].firstMatch
            if key.exists {
                key.tap()
                RunLoop.current.run(until: Date().addingTimeInterval(0.2))
                if app.keyboards.count == 0 { return }
            }
        }
        app.tap()
    }

    private func disableQuiescenceWait(for app: XCUIApplication) {
        let selector = NSSelectorFromString("setWaitForQuiescence:")
        guard app.responds(to: selector) else {
            return
        }
        _ = app.perform(selector, with: NSNumber(value: false))
    }

    private func required(_ environment: [String: String], _ key: String) throws -> String {
        guard let value = environment[key], !value.isEmpty else {
            throw XCTSkip("Missing \(key) for Account details replay.")
        }
        return value
    }

    private func onlyDigits(_ value: String) -> String {
        value.filter(\.isNumber)
    }
}
