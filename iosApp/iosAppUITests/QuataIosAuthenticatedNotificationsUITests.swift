import XCTest

/// Opt-in, production-host UI gate. The companion runner seeds the normal app Keychain first;
/// this test deliberately launches the app with no fixture arguments, mock route or reinstall.
final class QuataIosAuthenticatedNotificationsUITests: XCTestCase {
    func testAuthenticatedSessionOpensRealNotificationsFromFeed() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_AUTH_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Notifications UI gate is opt-in.")
        }

        let app = XCUIApplication()
        app.launch()

        let feed = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-feed-host")
            .firstMatch
        XCTAssertTrue(feed.waitForExistence(timeout: 20), "A normal launch must restore Feed from the seeded Keychain session.")

        let alerts = app.buttons["Avisos"]
        XCTAssertTrue(alerts.waitForExistence(timeout: 15), "The shared authenticated chrome must expose Avisos.")
        XCTAssertTrue(alerts.isHittable, "Avisos must be tappable from the normal Feed shell.")
        alerts.tap()

        let root = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-notifications-host")
            .firstMatch
        XCTAssertTrue(root.waitForExistence(timeout: 20), "Avisos must mount the real Notifications host.")
        XCTAssertTrue(app.staticTexts["Avisos"].waitForExistence(timeout: 10), "The common Notifications title must be visible.")
        XCTAssertTrue(app.buttons["Volver"].waitForExistence(timeout: 10), "Notifications must expose its real back action.")
        XCTAssertTrue(
            app.descendants(matching: .any).matching(identifier: "quata-ios-authenticated-top-chrome").firstMatch.exists,
            "The shared header must remain visible on Notifications.",
        )
        XCTAssertTrue(
            app.descendants(matching: .any).matching(identifier: "quata-ios-authenticated-primary-navigation").firstMatch.exists,
            "The shared primary navigation must remain visible on Notifications.",
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "authenticated-notifications-real-host")
    }
}
