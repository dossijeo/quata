import XCTest

/// Focal, non-destructive postflight for the shared authenticated Account root.
final class QuataIosAuthenticatedAccountPostflightUITests: XCTestCase {
    func testAuthenticatedAccountRootNavigatesAndCancelsLifecycleActions() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_ACCOUNT_POSTFLIGHT_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Account postflight is opt-in.")
        }

        let app = launchAuthenticatedApp()
        tapIdentifier("navigation.primary.profile", in: app, context: "open Account")
        assertVisible("profile.details.open", in: app, context: "Account details entry")
        assertVisible("profile.management.open", in: app, context: "Account management entry")
        assertVisible("profile.logout", in: app, context: "Account logout entry")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-account-postflight-overview")

        tapIdentifier("profile.details.open", in: app, context: "open Account details")
        assertVisible("profile.details.root", in: app, context: "Account details surface")
        tapIdentifier("profile.details.back", in: app, context: "return from Account details")

        tapIdentifier("profile.management.open", in: app, context: "open Account management")
        assertVisible("profile.management.root", in: app, context: "Account management surface")
        openAndCancel("profile.management.deactivate", in: app)
        openAndCancel("profile.management.delete", in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-account-postflight-management-cancelled")
        tapIdentifier("profile.management.back", in: app, context: "return from Account management")
        assertVisible("profile.logout", in: app, context: "authenticated Account session after cancellation")

        app.terminate()
        let relaunched = launchAuthenticatedApp()
        tapIdentifier("navigation.primary.profile", in: relaunched, context: "reopen Account after relaunch")
        assertVisible("profile.logout", in: relaunched, context: "authenticated Account session after relaunch")
        print("IOS_ACCOUNT_POSTFLIGHT_UI_GATE_PASSED")
    }

    func testAuthenticatedLogoutReturnsToPublicFeedAndClearsRestoredSession() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_AUTH_LOGOUT_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated logout postflight is opt-in.")
        }
        continueAfterFailure = false

        let app = launchAuthenticatedApp()
        tapIdentifier("navigation.primary.profile", in: app, context: "open Account before logout")
        tapIdentifier("profile.logout", in: app, context: "activate the product logout control")
        assertVisible("feed.root", in: app, context: "public Feed after logout", timeout: 25)
        assertPrivateProfileAbsent(in: app, context: "after logout")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-auth-logout-public-feed")

        app.terminate()
        let relaunched = XCUIApplication()
        disableQuiescenceWait(for: relaunched)
        relaunched.launchArguments += [
            "-AppleLanguages", "(es)", "-AppleLocale", "es_ES",
            "-quata-ui-test-reset-primary-route",
        ]
        relaunched.launch()
        assertVisible("feed.root", in: relaunched, context: "public Feed after logout relaunch", timeout: 25)
        assertPrivateProfileAbsent(in: relaunched, context: "after logout relaunch")
        tapIdentifier("navigation.primary.profile", in: relaunched, context: "request Account while anonymous")
        assertVisible("quata-ios-auth-required-dialog", in: relaunched, context: "authentication gate after logout")
        print("IOS_AUTH_LOGOUT_UI_GATE_PASSED")
    }

    func testAuthenticatedAccountLifecycleExecutesFromProductUI() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_ACCOUNT_LIFECYCLE_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated account lifecycle trial is opt-in.")
        }
        guard let action = environment["QUATA_IOS_ACCOUNT_LIFECYCLE_ACTION"],
              ["deactivate", "delete"].contains(action) else {
            XCTFail("A bounded account lifecycle action is required.")
            return
        }
        let password = try lifecyclePassword(from: environment)
        continueAfterFailure = false

        let app = launchAuthenticatedApp()
        tapIdentifier("navigation.primary.profile", in: app, context: "open Account before lifecycle operation")
        tapIdentifier("profile.management.open", in: app, context: "open Account management")
        tapIdentifier("profile.management.\(action)", in: app, context: "open shared lifecycle confirmation")
        assertVisible("profile.management.confirmation", in: app, context: "shared lifecycle confirmation")
        tapIdentifier("profile.management.confirm", in: app, context: "accept shared lifecycle confirmation")

        assertVisible("account.lifecycle.prompt", in: app, context: "native lifecycle credential prompt")
        let passwordField = app.secureTextFields.matching(identifier: "account.lifecycle.password").firstMatch
        XCTAssertTrue(passwordField.waitForExistence(timeout: 10), "Expected the lifecycle password field.")
        passwordField.tap()
        passwordField.typeText(password)
        if action == "delete" {
            let confirmation = app.textFields.matching(identifier: "account.lifecycle.delete-confirmation").firstMatch
            XCTAssertTrue(confirmation.waitForExistence(timeout: 10), "Expected the deletion confirmation field.")
            confirmation.tap()
            confirmation.typeText("ELIMINAR")
        }
        tapFirstButton(labels: ["Continuar", "Continue"], in: app, context: "activate lifecycle operation")

        assertVisible("feed.root", in: app, context: "public Feed after lifecycle operation", timeout: 35)
        assertPrivateProfileAbsent(in: app, context: "after \(action)")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-account-lifecycle-\(action)-public-feed")

        app.terminate()
        let relaunched = XCUIApplication()
        disableQuiescenceWait(for: relaunched)
        relaunched.launchArguments += [
            "-AppleLanguages", "(es)", "-AppleLocale", "es_ES",
            "-quata-ui-test-reset-primary-route",
        ]
        relaunched.launch()
        assertVisible("feed.root", in: relaunched, context: "public Feed after lifecycle relaunch", timeout: 25)
        assertPrivateProfileAbsent(in: relaunched, context: "after lifecycle relaunch")
        print("IOS_ACCOUNT_LIFECYCLE_UI_GATE_PASSED:\(action)")
    }

    private func assertPrivateProfileAbsent(in app: XCUIApplication, context: String) {
        for identifier in ["quata-ios-profile-sos-host", "profile.logout"] {
            XCTAssertTrue(
                app.descendants(matching: .any).matching(identifier: identifier).firstMatch
                    .waitForNonExistence(timeout: 12),
                "The private Profile element \(identifier) must remain absent \(context)."
            )
        }
    }

    private func openAndCancel(_ action: String, in app: XCUIApplication) {
        tapIdentifier(action, in: app, context: "open lifecycle confirmation")
        assertVisible("profile.management.confirmation", in: app, context: "lifecycle confirmation")
        assertVisible("profile.management.confirm", in: app, context: "destructive confirmation action")
        tapIdentifier("profile.management.cancel", in: app, context: "cancel lifecycle confirmation")
        XCTAssertTrue(
            app.descendants(matching: .any).matching(identifier: "profile.management.confirmation").firstMatch.waitForNonExistence(timeout: 10),
            "Cancelling must dismiss the lifecycle confirmation."
        )
        assertVisible("profile.management.root", in: app, context: "Account management after cancellation")
    }

    private func launchAuthenticatedApp() -> XCUIApplication {
        let app = XCUIApplication()
        disableQuiescenceWait(for: app)
        app.launchArguments += [
            "-AppleLanguages", "(es)", "-AppleLocale", "es_ES",
            "-quata-ui-test-reset-primary-route",
        ]
        app.launch()
        assertVisible("navigation.primary.profile", in: app, context: "restored authenticated shell", timeout: 25)
        return app
    }

    private func lifecyclePassword(from environment: [String: String]) throws -> String {
        guard let path = environment["QUATA_IOS_AUTH_E2E_FILE"],
              let data = FileManager.default.contents(atPath: path),
              let payload = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let password = payload["password"] as? String,
              !password.isEmpty else {
            XCTFail("Lifecycle credentials were not provided to the UI test target.")
            throw NSError(domain: "QuataIosUITests", code: 1)
        }
        return password
    }

    private func tapFirstButton(labels: [String], in app: XCUIApplication, context: String) {
        for label in labels {
            let button = app.buttons[label].firstMatch
            if button.waitForExistence(timeout: 2), button.isHittable {
                button.tap()
                return
            }
        }
        XCTFail("Expected a localized button for \(context).")
    }

    private func assertVisible(
        _ identifier: String,
        in app: XCUIApplication,
        context: String,
        timeout: TimeInterval = 12
    ) {
        var element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            if element.exists { return }
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
            element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        } while Date() < deadline
        XCTAssertTrue(element.exists, "Expected \(identifier) for \(context).")
    }

    private func tapIdentifier(_ identifier: String, in app: XCUIApplication, context: String) {
        var element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        for _ in 0..<10 {
            if element.exists, element.isHittable {
                element.tap()
                return
            }
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
            element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        }
        XCTAssertTrue(element.exists, "Expected \(identifier) for \(context).")
        element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
    }

    private func disableQuiescenceWait(for app: XCUIApplication) {
        let selector = NSSelectorFromString("setWaitForQuiescence:")
        if app.responds(to: selector) {
            _ = app.perform(selector, with: NSNumber(value: false))
        }
    }
}
