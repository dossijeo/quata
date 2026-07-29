import XCTest

final class QuataIosHostUITests: XCTestCase {
    func testAnonymousFixtureLaunchesWithoutCreatingASessionOrComposeSurface() {
        let app = fixtureApp("anonymous")
        app.launch()

        let anonymousSurface = QuataIosHostUITestSupport.fixtureRoot(
            in: app,
            identifier: "quata-ios-test-anonymous-host",
        )
        XCTAssertEqual(
            anonymousSurface.label, "Quata iOS anonymous fixture",
        )
        XCTAssertFalse(app.descendants(matching: .any).matching(identifier: "quata-ios-compose-root").firstMatch.exists)

    }

    func testNormalLaunchExposesTheUnconfiguredComposeMigrationSemantics() {
        let app = XCUIApplication()
        app.launch()

        _ = QuataIosHostUITestSupport.composeRoot(in: app)
        assertUnconfiguredMigrationSemantics(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "compose-migration-unconfigured")
    }

    func testColdRelaunchRestoresOneComposeMigrationSurface() {
        let app = XCUIApplication()
        app.launch()
        _ = QuataIosHostUITestSupport.composeRoot(in: app, context: "initial launch")

        app.terminate()
        app.launch()

        _ = QuataIosHostUITestSupport.composeRoot(in: app, context: "cold relaunch")
        assertUnconfiguredMigrationSemantics(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "compose-migration-cold-relaunch")
    }

    func testNativeAuthInputsExposeOneFocusableAccessibilityElementEach() {
        let app = XCUIApplication()
        app.launchArguments = ["-quata-ui-test-fixture", "auth"]
        app.launch()

        let phone = app.textFields["auth.phone.input"]
        let password = app.secureTextFields["auth.password.input"]
        XCTAssertTrue(phone.waitForExistence(timeout: 15))
        XCTAssertTrue(password.waitForExistence(timeout: 15))
        XCTAssertEqual(app.textFields.matching(identifier: "auth.phone.input").count, 1)
        XCTAssertEqual(app.secureTextFields.matching(identifier: "auth.password.input").count, 1)
        XCTAssertFalse(app.descendants(matching: .any).matching(identifier: "auth.phone").firstMatch.exists)
        XCTAssertFalse(app.descendants(matching: .any).matching(identifier: "auth.password").firstMatch.exists)

        // These are intentionally non-production fixture values. The assertion proves that the
        // native focus bridge updates the visible shared Compose state without an auth request.
        phone.tap()
        phone.typeText("5550101")
        XCTAssertTrue(phone.hasFocus)
        XCTAssertEqual(phone.value as? String, "5550101")
        password.tap()
        password.typeText("FixtureOnly1")
        XCTAssertTrue(password.hasFocus)

        let prefix = app.buttons["+240"]
        XCTAssertTrue(prefix.waitForExistence(timeout: 10))
        XCTAssertTrue(prefix.isHittable, "The native phone overlay must not cover the prefix selector.")
        let submit = app.buttons["auth.submit"]
        XCTAssertTrue(submit.waitForExistence(timeout: 10))
        XCTAssertTrue(submit.isHittable)
        submit.tap()
        QuataIosHostUITestSupport.attachRenderedSurface(named: "native-auth-input-focus")
    }

    func testAuthenticatedFixtureColdStartsChatDeepLinkThroughTheSharedRouter() {
        let app = fixtureApp(
            "authenticated",
            deepLink: "https://egquata.com/#chat-conversation-7?message=message-4",
        )
        app.launch()

        let chatSurface = QuataIosHostUITestSupport.fixtureRoot(
            in: app,
            identifier: "quata-ios-chat-host",
        )
        XCTAssertEqual(chatSurface.label, "Quata iOS Chat")
        XCTAssertFalse(app.descendants(matching: .any).matching(identifier: "quata-ios-compose-root").firstMatch.exists)
    }

    func testAuthenticatedFixtureColdStartsFeedAndOfficialDeepLinksThroughTheSharedRouter() {
        let feedApp = fixtureApp(
            "authenticated",
            deepLink: "https://egquata.com/#post-feed-9",
        )
        feedApp.launch()

        XCTAssertEqual(
            QuataIosHostUITestSupport.fixtureRoot(
                in: feedApp,
                identifier: "quata-ios-feed-host",
            ).label,
            "Quata iOS Feed",
        )
        XCTAssertFalse(feedApp.descendants(matching: .any).matching(identifier: "quata-ios-compose-root").firstMatch.exists)

        feedApp.terminate()

        let officialApp = fixtureApp(
            "authenticated",
            deepLink: "https://egquata.com/#official-public-7",
        )
        officialApp.launch()

        XCTAssertEqual(
            QuataIosHostUITestSupport.fixtureRoot(
                in: officialApp,
                identifier: "quata-ios-official-host",
            ).label,
            "Quata iOS Official",
        )
        XCTAssertFalse(officialApp.descendants(matching: .any).matching(identifier: "quata-ios-compose-root").firstMatch.exists)
    }

    private func fixtureApp(_ fixture: String, deepLink: String? = nil) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-quata-ui-test-fixture", fixture]
        if let deepLink { app.launchArguments += ["-quata-ui-test-deep-link", deepLink] }
        return app
    }

    private func assertUnconfiguredMigrationSemantics(in app: XCUIApplication) {
        let message = app.staticTexts[
            "Quata para iOS necesita una configuración pública válida para iniciar."
        ]
        XCTAssertTrue(
            message.waitForExistence(timeout: 10),
            "The real unconfigured Compose migration text must be exposed through accessibility.",
        )

        let acknowledge = app.buttons["Entendido"]
        XCTAssertTrue(
            acknowledge.waitForExistence(timeout: 10),
            "The real unconfigured Compose action must be exposed through accessibility.",
        )
        XCTAssertTrue(
            acknowledge.isHittable,
            "The visible Compose migration action must be hittable on the normal launcher surface.",
        )
        acknowledge.tap()

        let updatedMessage = app.staticTexts[
            "La configuración pública sigue sin estar disponible."
        ]
        XCTAssertTrue(
            updatedMessage.waitForExistence(timeout: 10),
            "Tapping the real Compose action must update the visible status semantics.",
        )
        let retry = app.buttons["Comprobar de nuevo"]
        XCTAssertTrue(
            retry.waitForExistence(timeout: 10),
            "Tapping the real Compose action must update its accessible label.",
        )
        XCTAssertTrue(
            retry.isHittable,
            "The updated real Compose action must remain hittable.",
        )
    }
}
