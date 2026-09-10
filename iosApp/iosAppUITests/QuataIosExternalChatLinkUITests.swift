import XCTest
import Darwin

/// Observes a coordinator-delivered URL. No launch, activation, login or URL delivery.
final class QuataIosExternalChatLinkUITests: XCTestCase {
    /// Real anonymous product flow; the coordinator delivers the URL after READY.
    /// No login submission, session injection or coordinate-based interaction.
    func testAnonymousExternalChatOpensLoginAndCancelsToFeed() throws {
        let env = ProcessInfo.processInfo.environment
        guard env["QUATA_IOS_EXTERNAL_CHAT_ANONYMOUS"] == "1" else {
            throw XCTSkip("Requires the leased anonymous external-link coordinator.")
        }
        let step = try XCTUnwrap(env["QUATA_IOS_EXTERNAL_CHAT_STEP"])
        XCTAssertNotNil(UUID(uuidString: step))
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "com.quata.ios")
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let prompt = app.descendants(matching: .any).matching(identifier: "quata-ios-auth-required-dialog").firstMatch
        let chat = app.descendants(matching: .any).matching(identifier: "quata-ios-chat-host").firstMatch
        let auth = app.descendants(matching: .any).matching(identifier: "quata-ios-auth-host").firstMatch
        print("QUATA_DEEP_LINK_CHAT_OBSERVER_READY:\(step)")
        fflush(stdout)
        let deadline = Date().addingTimeInterval(45)
        var confirmedOpen = false
        while Date() < deadline && !prompt.waitForExistence(timeout: 0.5) {
            let open = springboard.alerts.buttons.matching(NSPredicate(format: "label IN %@", ["Abrir", "Open"])).firstMatch
            if !confirmedOpen && open.exists && open.isHittable { confirmedOpen = true; open.tap() }
        }
        XCTAssertTrue(prompt.exists)
        XCTAssertFalse(chat.exists)
        XCTAssertFalse(auth.exists)
        let barrier = XCTAttachment(screenshot: app.screenshot())
        barrier.name = "external-chat-anonymous-barrier"
        barrier.lifetime = .keepAlways
        add(barrier)
        let login = app.buttons.matching(NSPredicate(format: "label IN %@", ["Ya tengo cuenta", "I have an account"])).firstMatch
        XCTAssertTrue(login.waitForExistence(timeout: 10) && login.isHittable)
        login.tap()
        XCTAssertTrue(auth.waitForExistence(timeout: 15))
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "auth.forgot-password").firstMatch.waitForExistence(timeout: 10),
                      "The real Login form must be mounted before capture.")
        XCTAssertFalse(chat.exists)
        let loginCapture = XCTAttachment(screenshot: app.screenshot())
        loginCapture.name = "external-chat-anonymous-login"
        loginCapture.lifetime = .keepAlways
        add(loginCapture)
        let close = app.buttons.matching(identifier: "quata-ios-auth-close").firstMatch
        XCTAssertTrue(close.waitForExistence(timeout: 10) && close.isHittable)
        close.tap()
        let feed = app.descendants(matching: .any).matching(identifier: "quata-ios-feed-host").firstMatch
        let returned = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            feed.exists && !auth.exists && !prompt.exists && !chat.exists
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [returned], timeout: 15), .completed)
        let finalCapture = XCTAttachment(screenshot: app.screenshot())
        finalCapture.name = "external-chat-anonymous-cancel-feed"
        finalCapture.lifetime = .keepAlways
        add(finalCapture)
    }

    func testObserveDeliveredChatMessageAndBack() throws {
        let env = ProcessInfo.processInfo.environment
        guard env["QUATA_IOS_EXTERNAL_CHAT_E2E"] == "1" else {
            throw XCTSkip("Requires the owned deep-link coordinator.")
        }
        let thread = try XCTUnwrap(env["QUATA_IOS_EXTERNAL_CHAT_THREAD"])
        let message = try XCTUnwrap(env["QUATA_IOS_EXTERNAL_CHAT_MESSAGE"])
        let body = try XCTUnwrap(env["QUATA_IOS_EXTERNAL_CHAT_BODY"])
        let step = try XCTUnwrap(env["QUATA_IOS_EXTERNAL_CHAT_STEP"])
        XCTAssertNotNil(UUID(uuidString: step))
        XCTAssertNotNil(thread.range(of: "^[0-9]+$", options: .regularExpression))
        XCTAssertNotNil(message.range(of: "^[0-9]+$", options: .regularExpression))
        XCTAssertTrue(body.hasPrefix("Deep link "))
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "com.quata.ios")
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let host = app.descendants(matching: .any).matching(identifier: "quata-ios-chat-host").firstMatch
        // iOS appends merged child labels (sender, timestamp, body) to the
        // Compose contentDescription. Keep the exact ID and verify the body
        // child separately instead of assuming the entire AX label is equal.
        let selected = app.buttons.matching(NSPredicate(format: "identifier == %@ AND label BEGINSWITH %@",
            "chat.message.\(message).selected", "Deep link fixture: \(body), ")).firstMatch
        print("QUATA_DEEP_LINK_CHAT_OBSERVER_READY:\(step)")
        fflush(stdout)
        let deadline = Date().addingTimeInterval(45)
        var confirmedOpen = false
        while Date() < deadline && !selected.waitForExistence(timeout: 0.5) {
            let open = springboard.alerts.buttons.matching(NSPredicate(format: "label IN %@", ["Abrir", "Open"])).firstMatch
            if !confirmedOpen && open.exists && open.isHittable { confirmedOpen = true; open.tap() }
        }
        XCTAssertTrue(selected.exists, "Exact target message must be selected.")
        XCTAssertTrue(selected.staticTexts.matching(NSPredicate(format: "label == %@", body)).firstMatch.exists,
                      "Selected bubble must contain the exact fixture body.")
        XCTAssertTrue(host.exists)
        // Consuming the focus intent removes ?message from the native route.
        let route = "chat:sb:\(thread)"
        XCTAssertTrue([route, "\(route)?message=\(message)"].contains(host.value as? String ?? ""))
        XCTAssertFalse(app.descendants(matching: .any).matching(identifier: "quata-ios-auth-host").firstMatch.exists)
        XCTAssertTrue(selected.exists && selected.isHittable, "Selected target bubble must remain exposed before capture.")
        let focused = XCTAttachment(screenshot: app.screenshot())
        focused.name = "external-chat-exact-message"
        focused.lifetime = .keepAlways
        add(focused)
        let back = app.descendants(matching: .any).matching(identifier: "chat.back").firstMatch
        XCTAssertTrue(back.waitForExistence(timeout: 10))
        back.tap()
        let list = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            host.exists && ((host.value as? String) ?? "").isEmpty && !back.exists && !selected.exists
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [list], timeout: 15), .completed)
        let exited = XCTAttachment(screenshot: app.screenshot())
        exited.name = "external-chat-back-list"
        exited.lifetime = .keepAlways
        add(exited)
    }
}
