import XCTest

/// Opt-in production-session gate for native UGC terms persistence.
final class QuataIosUgcTermsRemoteUITests: XCTestCase {
    func testAuthenticatedUserAcceptsTermsThroughProductGate() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_UGC_TERMS_REMOTE_E2E"] == "1" else {
            throw XCTSkip("The iOS UGC terms remote gate is opt-in.")
        }
        guard let profileId = ProcessInfo.processInfo.environment["QUATA_IOS_UGC_TERMS_PROFILE_ID"], !profileId.isEmpty else {
            throw XCTSkip("The owned profile id is required to reset only its local UGC acceptance.")
        }

        let app = XCUIApplication()
        app.launchArguments += [
            "-AppleLanguages", "(es)", "-AppleLocale", "es_ES",
            "-quata-ui-test-reset-ugc-terms-profile", profileId,
        ]
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
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-ugc-terms-remote-accepted")
        print("IOS_UGC_TERMS_REMOTE_UI_GATE_PASSED")
    }

    func testAuthenticatedUserLogsOutThroughProductGate() throws {
        guard ProcessInfo.processInfo.environment["QUATA_IOS_UGC_TERMS_LOGOUT_E2E"] == "1" else {
            throw XCTSkip("The iOS UGC terms logout gate is opt-in.")
        }
        guard let profileId = ProcessInfo.processInfo.environment["QUATA_IOS_UGC_TERMS_PROFILE_ID"], !profileId.isEmpty else {
            throw XCTSkip("The owned profile id is required to prepare the UGC terms gate.")
        }

        let app = XCUIApplication()
        app.launchArguments += [
            "-AppleLanguages", "(es)", "-AppleLocale", "es_ES",
            "-quata-ui-test-reset-primary-route",
            "-quata-ui-test-reset-ugc-terms-profile", profileId,
        ]
        app.launch()

        let prompt = app.descendants(matching: .any)
            .matching(identifier: "quata-ios-ugc-terms-dialog")
            .firstMatch
        XCTAssertTrue(prompt.waitForExistence(timeout: 30), "The authenticated product host must present the real UGC terms prompt.")
        let logout = app.descendants(matching: .any)
            .matching(identifier: "quata-ugc-terms-logout")
            .firstMatch
        XCTAssertTrue(logout.waitForExistence(timeout: 15), "The real UGC terms prompt must expose its logout action.")
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-ugc-terms-logout-required")
        if logout.isHittable {
            logout.tap()
        } else {
            logout.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }

        let publicTransition = app.descendants(matching: .any)
            .matching(NSPredicate(
                format: "identifier == %@ OR label == %@",
                "feed.root",
                "Quata iOS is preparing the public Feed"
            ))
            .firstMatch
        XCTAssertTrue(
            publicTransition.waitForExistence(timeout: 60),
            "UGC terms logout must return to the public Feed or its public loading state."
        )
        XCTAssertTrue(prompt.waitForNonExistence(timeout: 15), "UGC terms logout must dismiss the blocking prompt.")
        XCTAssertTrue(
            app.descendants(matching: .any).matching(identifier: "profile.logout").firstMatch.waitForNonExistence(timeout: 15),
            "The private Profile logout control must be absent after UGC terms logout."
        )
        QuataIosHostUITestSupport.attachRenderedSurface(named: "ios-ugc-terms-logout-public-feed")

        app.terminate()
        let relaunched = XCUIApplication()
        relaunched.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES", "-quata-ui-test-reset-primary-route"]
        relaunched.launch()
        XCTAssertTrue(
            relaunched.descendants(matching: .any).matching(identifier: "feed.root").firstMatch.waitForExistence(timeout: 30),
            "The public Feed must remain visible after a natural relaunch."
        )
        XCTAssertTrue(
            relaunched.descendants(matching: .any).matching(identifier: "profile.logout").firstMatch.waitForNonExistence(timeout: 12),
            "The owned session must remain absent after relaunch."
        )
        print("IOS_UGC_TERMS_LOGOUT_UI_GATE_PASSED")
    }
}
