import XCTest

/// Opt-in production-session gate for native UGC terms persistence.
final class QuataIosUgcTermsRemoteUITests: XCTestCase {
    func testAuthenticatedUserAcceptsTermsThroughProductGate() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_UGC_TERMS_REMOTE_E2E"] == "1" else {
            throw XCTSkip("The iOS UGC terms remote gate is opt-in.")
        }

        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()

        let host = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-ugc-terms-dialog")
            .firstMatch
        XCTAssertTrue(host.waitForExistence(timeout: 30), "The authenticated product host must present the real UGC terms prompt.")
        let commonDialog = app.descendants(matching: .any)
            .matching(identifier: "quata-ugc-terms-dialog")
            .firstMatch
        XCTAssertTrue(commonDialog.waitForExistence(timeout: 15), "The iOS prompt must contain the common UGC terms gate.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-ugc-terms-remote-required")

        let accept = app.descendants(matching: .any)
            .matching(identifier: "quata-ugc-terms-accept")
            .firstMatch
        XCTAssertTrue(accept.waitForExistence(timeout: 10), "The common UGC terms accept action must be visible.")
        if accept.isHittable {
            accept.tap()
        } else {
            accept.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }

        let dismissed = NSPredicate(format: "exists == false")
        XCTAssertEqual(
            XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: dismissed, object: host)], timeout: 30),
            .completed,
            "Accepting through the product UI must dismiss the UGC terms prompt."
        )
        XCTAssertTrue(
            app.descendants(matching: .any).matching(identifier: "quata-ios-feed-host").firstMatch.waitForExistence(timeout: 20),
            "The authenticated product feed must remain after acceptance."
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-ugc-terms-remote-accepted")
        print("IOS_UGC_TERMS_REMOTE_UI_GATE_PASSED")
    }
}
