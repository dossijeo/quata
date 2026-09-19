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
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()
        assertVisible("navigation.primary.profile", in: app, context: "restored authenticated shell", timeout: 25)
        return app
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
