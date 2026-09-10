import XCTest

/// Observes a URL already delivered by simctl; never launches or opens the app itself.
/// Handles an optional system confirmation and verifies detail containment only.
/// Does not prove exact target, delivery causality, or warm delivery.
final class QuataIosExternalDeepLinksUITests: XCTestCase {
    func testObservePendingExternalPublicLink() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_EXTERNAL_LINKS_E2E"] == "1" else {
            throw XCTSkip("Requires the dedicated anonymous external-link simulator.")
        }
        let kind = try XCTUnwrap(environment["QUATA_IOS_EXTERNAL_LINK_KIND"])
        guard ["feed", "official"].contains(kind) else {
            XCTFail("Unsupported public link kind")
            return
        }
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "com.quata.ios")
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let open = springboard.alerts.buttons.matching(
            NSPredicate(format: "label IN %@", ["Abrir", "Open"])
        ).firstMatch
        if open.waitForExistence(timeout: 5) { open.tap() }
        let detail = app.descendants(matching: .any)
            .matching(identifier: "\(kind).detail.chrome").firstMatch
        XCTAssertTrue(detail.waitForExistence(timeout: 60), "Externally delivered URL must reach shared detail chrome.")
        if let expectedText = environment["QUATA_IOS_EXTERNAL_LINK_EXPECTED_TEXT"], !expectedText.isEmpty {
            let content = app.descendants(matching: .any).matching(
                NSPredicate(format: "label CONTAINS %@", expectedText)
            ).firstMatch
            XCTAssertTrue(content.waitForExistence(timeout: 30), "Expected public content or terminal state must be exposed before capture.")
        }
        XCTAssertFalse(app.descendants(matching: .any)
            .matching(identifier: "quata-ios-auth-host").firstMatch.exists)
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = "external-public-\(kind)-detail-observed"
        capture.lifetime = .keepAlways
        add(capture)
        if environment["QUATA_IOS_EXTERNAL_LINK_CHECK_BACK"] == "1" {
            let back = app.descendants(matching: .any)
                .matching(identifier: "\(kind).detail.back").firstMatch
            XCTAssertTrue(back.waitForExistence(timeout: 10))
            back.tap()
            let removed = XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "exists == false"), object: detail
            )
            XCTAssertEqual(XCTWaiter.wait(for: [removed], timeout: 10), .completed)
            XCTAssertTrue(app.descendants(matching: .any)
                .matching(identifier: "quata-ios-\(kind)-host").firstMatch.exists)
            let backCapture = XCTAttachment(screenshot: app.screenshot())
            backCapture.name = "external-public-\(kind)-back-observed"
            backCapture.lifetime = .keepAlways
            add(backCapture)
        }
    }
}
