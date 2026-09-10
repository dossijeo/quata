import XCTest
import Darwin

/// Observes a coordinator-delivered URL. No launch, activation, login or URL delivery.
final class QuataIosExternalChatLinkUITests: XCTestCase {
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
        let selected = app.buttons.matching(NSPredicate(format: "identifier == %@ AND label == %@",
            "chat.message.\(message).selected", "Deep link fixture: \(body)")).firstMatch
        print("QUATA_DEEP_LINK_CHAT_OBSERVER_READY:\(step)")
        fflush(stdout)
        let deadline = Date().addingTimeInterval(45)
        var confirmedOpen = false
        while Date() < deadline && !selected.waitForExistence(timeout: 0.5) {
            let open = springboard.alerts.buttons.matching(NSPredicate(format: "label IN %@", ["Abrir", "Open"])).firstMatch
            if !confirmedOpen && open.exists && open.isHittable { confirmedOpen = true; open.tap() }
        }
        XCTAssertTrue(selected.exists, "Exact target message must be selected.")
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
