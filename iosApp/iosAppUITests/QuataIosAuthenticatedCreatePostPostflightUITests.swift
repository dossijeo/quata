import XCTest

/// Focal, non-destructive postflight for the authenticated shared Create Post root.
final class QuataIosAuthenticatedCreatePostPostflightUITests: XCTestCase {
    func testAuthenticatedCreatePostRootOpensAndReturnsWithoutPublishing() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_CREATE_POST_POSTFLIGHT_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Create Post postflight is opt-in.")
        }

        let app = launchAuthenticatedApp()
        assertVisible("quata-ios-feed-host", in: app, context: "authenticated Feed")
        tapPrefix("feed.action.publish.", in: app, context: "open Create Post from Feed")
        assertVisible("quata-ios-composer-host", in: app, context: "Create Post host")
        assertVisible("create-post-common-root", in: app, context: "common Create Post root")
        assertVisible("navigation.primary.composer", in: app, context: "composer shell destination")
        assertVisible("composer-type-text", in: app, context: "text post type")
        assertVisible("composer-type-image", in: app, context: "image post type")
        assertVisible("composer-type-video", in: app, context: "video post type")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-create-post-postflight-opened")

        tapIdentifier("navigation.primary.feed", in: app, context: "return to Feed without publishing")
        assertVisible("quata-ios-feed-host", in: app, context: "Feed after Create Post return")
        XCTAssertTrue(
            app.descendants(matching: .any).matching(identifier: "create-post-common-root").firstMatch.waitForNonExistence(timeout: 12),
            "Returning to Feed must dismiss the common Create Post root."
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-create-post-postflight-returned")

        app.terminate()
        let relaunched = launchAuthenticatedApp()
        assertVisible("quata-ios-feed-host", in: relaunched, context: "authenticated Feed after relaunch")
        tapIdentifier("navigation.primary.profile", in: relaunched, context: "open authenticated Profile after relaunch")
        assertVisible("quata-ios-profile-sos-host", in: relaunched, context: "restored authenticated Profile after relaunch")
        print("IOS_CREATE_POST_POSTFLIGHT_UI_GATE_PASSED")
    }

    private func launchAuthenticatedApp() -> XCUIApplication {
        let app = XCUIApplication()
        disableQuiescenceWait(for: app)
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()
        assertVisible("navigation.primary.profile", in: app, context: "restored authenticated shell", timeout: 25)
        dismissStartupWhatsNewIfPresent(in: app)
        return app
    }

    private func dismissStartupWhatsNewIfPresent(in app: XCUIApplication) {
        let host = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-whats-new-host")
            .firstMatch
        guard host.waitForExistence(timeout: 3) else { return }

        let deadline = Date().addingTimeInterval(20)
        while host.exists && Date() < deadline {
            let dismiss = ["whats-new-dismiss", "dismiss_whats_new"]
                .map { app.descendants(matching: .any).matching(identifier: $0).firstMatch }
                .first(where: { $0.exists && $0.isHittable })
            let next = ["whats-new-next", "next_whats_new"]
                .map { app.descendants(matching: .any).matching(identifier: $0).firstMatch }
                .first(where: { $0.exists && $0.isHittable })
            guard let control = dismiss ?? next else {
                RunLoop.current.run(until: Date().addingTimeInterval(0.25))
                continue
            }
            control.tap()
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        XCTAssertFalse(host.exists, "Startup What's New must close before exercising Create Post.")
    }

    private func assertVisible(
        _ identifier: String,
        in app: XCUIApplication,
        context: String,
        timeout: TimeInterval = 15
    ) {
        let element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: timeout), "Expected \(identifier) for \(context).")
    }

    private func tapIdentifier(_ identifier: String, in app: XCUIApplication, context: String) {
        let element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 12), "Expected \(identifier) for \(context).")
        if element.isHittable {
            element.tap()
        } else {
            element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
    }

    private func tapPrefix(_ prefix: String, in app: XCUIApplication, context: String) {
        let element = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH %@", prefix))
            .firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 25), "Expected \(prefix) for \(context).")
        if element.isHittable {
            element.tap()
        } else {
            element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
    }

    private func disableQuiescenceWait(for app: XCUIApplication) {
        let selector = NSSelectorFromString("setWaitForQuiescence:")
        if app.responds(to: selector) {
            _ = app.perform(selector, with: NSNumber(value: false))
        }
    }
}
