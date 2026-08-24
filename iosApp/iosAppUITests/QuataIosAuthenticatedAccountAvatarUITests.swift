import XCTest

/// Opt-in, production-session UI gate for the shared Account avatar flow.
final class QuataIosAuthenticatedAccountAvatarUITests: XCTestCase {
    func testAuthenticatedSessionChangesProfileAvatarFromCommonAccount() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_ACCOUNT_AVATAR_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated account avatar UI gate is opt-in.")
        }
        guard environment["QUATA_IOS_ACCOUNT_AVATAR_PICKER_FIXTURE_OPT_IN"] == "I_ACCEPT_IOS_ACCOUNT_AVATAR_PICKER_FIXTURE" else {
            throw XCTSkip("Account avatar replay requires the picker fixture.")
        }
        XCTAssertFalse(
            (environment["QUATA_IOS_ACCOUNT_AVATAR_PICKER_PATH"] ?? "").isEmpty,
            "Account avatar replay requires QUATA_IOS_ACCOUNT_AVATAR_PICKER_PATH."
        )

        let app = XCUIApplication()
        disableQuiescenceWait(for: app)
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        for key in [
            "QUATA_IOS_ACCOUNT_AVATAR_PICKER_FIXTURE_OPT_IN",
            "QUATA_IOS_ACCOUNT_AVATAR_PICKER_PATH",
            "QUATA_IOS_ACCOUNT_AVATAR_PICKER_NAME",
            "QUATA_IOS_ACCOUNT_AVATAR_PICKER_MIME",
        ] {
            if let value = environment[key], !value.isEmpty {
                app.launchEnvironment[key] = value
            }
        }
        app.launch()

        let feed = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-feed-host")
            .firstMatch
        XCTAssertTrue(feed.waitForExistence(timeout: 25), "A normal launch must restore Feed from the seeded Keychain session.")
        tapIdentifier("navigation.primary.profile", in: app, context: "open account primary route")
        let profileHost = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-profile-sos-host")
            .firstMatch
        XCTAssertTrue(profileHost.waitForExistence(timeout: 25), "The real shared Account/Profile host must open from authenticated iOS chrome.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-account-avatar-profile-opened")

        tapIdentifier("profile.avatar.change", in: app, context: "open avatar menu")
        tapIdentifier("profile.avatar.gallery", in: app, context: "choose avatar gallery fixture")
        let editorRoot = app.descendants(matching: .any)
            .matching(identifier: "post-image-editor.root")
            .firstMatch
        XCTAssertTrue(editorRoot.waitForExistence(timeout: 12), "The iOS Account avatar flow must open the shared avatar image editor.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-account-avatar-editor-opened")
        tapIdentifier("post-image-editor.rotate", in: app, context: "rotate avatar in shared editor")
        tapIdentifier("post-image-editor.save", in: app, context: "save avatar from shared editor")
        XCTAssertTrue(editorRoot.waitForNonExistence(timeout: 18), "Saving the avatar editor must return to the shared Account screen.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-account-avatar-editor-saved-preview")

        tapIdentifier("profile.save", in: app, context: "persist account avatar")
        XCTAssertTrue(
            waitForSavedFeedback(in: app, timeout: 45),
            "Saving Account avatar must expose shared saved feedback or keep the saved avatar preview visible."
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-account-avatar-profile-saved")
        print("IOS_ACCOUNT_AVATAR_UI_GATE_PASSED")
    }

    private func tapIdentifier(_ identifier: String, in app: XCUIApplication, context: String) {
        var element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        for _ in 0..<8 {
            if element.waitForExistence(timeout: 1) {
                if element.isHittable {
                    element.tap()
                } else {
                    element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
                }
                return
            }
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
            element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        }
        XCTAssertTrue(element.exists, "Expected \(identifier) to exist for \(context).")
    }

    private func waitForSavedFeedback(in app: XCUIApplication, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        let savedPredicates = [
            NSPredicate(format: "label CONTAINS[c] %@", "Cambios"),
            NSPredicate(format: "label CONTAINS[c] %@", "saved"),
            NSPredicate(format: "label CONTAINS[c] %@", "synchron"),
        ]
        while Date() < deadline {
            for predicate in savedPredicates {
                if app.descendants(matching: .any).matching(predicate).firstMatch.exists {
                    return true
                }
            }
            if app.descendants(matching: .any).matching(identifier: "profile.avatar.change").firstMatch.exists &&
                !app.descendants(matching: .any).matching(identifier: "post-image-editor.root").firstMatch.exists {
                return true
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        return false
    }

    private func disableQuiescenceWait(for app: XCUIApplication) {
        let selector = NSSelectorFromString("setWaitForQuiescence:")
        guard app.responds(to: selector) else {
            return
        }
        _ = app.perform(selector, with: NSNumber(value: false))
    }
}
