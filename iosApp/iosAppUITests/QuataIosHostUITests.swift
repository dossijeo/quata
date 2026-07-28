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

        let migrationSurface = QuataIosHostUITestSupport.composeRoot(in: app)
        XCTAssertEqual(
            migrationSurface.label,
            "Quata iOS requires an authenticated Feed session",
        )
        assertUnconfiguredMigrationSemantics(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "compose-migration-unconfigured")
    }

    func testColdRelaunchRestoresOneComposeMigrationSurface() {
        let app = XCUIApplication()
        app.launch()
        _ = QuataIosHostUITestSupport.composeRoot(in: app, context: "initial launch")

        app.terminate()
        app.launch()

        let migrationSurface = QuataIosHostUITestSupport.composeRoot(in: app, context: "cold relaunch")
        XCTAssertEqual(migrationSurface.label, "Quata iOS requires an authenticated Feed session")
        assertUnconfiguredMigrationSemantics(in: app)
        QuataIosHostUITestSupport.attachRenderedSurface(named: "compose-migration-cold-relaunch")
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
    }
}
