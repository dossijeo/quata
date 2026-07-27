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

    func testLaunchesUIKitCompositionRootWithComposeSurface() {
        let app = XCUIApplication()
        app.launch()

        let migrationSurface = QuataIosHostUITestSupport.composeRoot(in: app)
        XCTAssertEqual(
            migrationSurface.label,
            "Quata iOS requires an authenticated Feed session",
        )
        let before = migrationSurface.screenshot().pngRepresentation
        QuataIosHostUITestSupport.attachRenderedSurface(named: "compose-migration-before-action")
        migrationSurface.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.56)).tap()
        RunLoop.current.run(until: Date().addingTimeInterval(1))
        let after = migrationSurface.screenshot().pngRepresentation
        QuataIosHostUITestSupport.attachRenderedSurface(named: "compose-migration-after-action")
        XCTAssertNotEqual(before, after, "The stable Compose root rendering must change after its real action.")
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
}
