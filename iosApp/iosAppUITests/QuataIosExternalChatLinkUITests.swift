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

    func testObserveDeliveredMissingChatAndBack() throws {
        let env = ProcessInfo.processInfo.environment
        guard env["QUATA_IOS_EXTERNAL_CHAT_E2E"] == "1",
              env["QUATA_IOS_EXTERNAL_CHAT_TARGET_MODE"] == "missing-thread" else {
            throw XCTSkip("Requires the owned missing-thread coordinator.")
        }
        let thread = try XCTUnwrap(env["QUATA_IOS_EXTERNAL_CHAT_THREAD"])
        let message = try XCTUnwrap(env["QUATA_IOS_EXTERNAL_CHAT_MESSAGE"])
        let step = try XCTUnwrap(env["QUATA_IOS_EXTERNAL_CHAT_STEP"])
        XCTAssertNotNil(UUID(uuidString: step))
        XCTAssertNotNil(thread.range(of: "^[0-9]+$", options: .regularExpression))
        XCTAssertNotNil(message.range(of: "^[0-9]+$", options: .regularExpression))
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "com.quata.ios")
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let host = app.descendants(matching: .any).matching(identifier: "quata-ios-chat-host").firstMatch
        let failure = app.staticTexts.matching(NSPredicate(format: "label IN %@", [
            "No se pudieron cargar los mensajes.", "Could not load messages.", "Impossible de charger les messages."
        ])).firstMatch
        print("QUATA_DEEP_LINK_CHAT_OBSERVER_READY:\(step)")
        fflush(stdout)
        let deadline = Date().addingTimeInterval(45)
        var confirmedOpen = false
        while Date() < deadline && !failure.waitForExistence(timeout: 0.5) {
            let open = springboard.alerts.buttons.matching(NSPredicate(format: "label IN %@", ["Abrir", "Open"])).firstMatch
            if !confirmedOpen && open.exists && open.isHittable { confirmedOpen = true; open.tap() }
        }
        XCTAssertTrue(failure.exists && failure.isHittable)
        XCTAssertTrue(host.exists)
        let route = "chat:sb:\(thread)"
        XCTAssertTrue([route, "\(route)?message=\(message)"].contains(host.value as? String ?? ""))
        let retry = app.buttons.matching(NSPredicate(format: "label IN %@", ["Reintentar mensajes", "Retry messages", "Réessayer les messages"])).firstMatch
        XCTAssertTrue(retry.exists && retry.isHittable)
        XCTAssertFalse(app.descendants(matching: .any).matching(identifier: "quata-ios-auth-host").firstMatch.exists)
        XCTAssertFalse(app.descendants(matching: .any).matching(identifier: "quata-ios-auth-required-dialog").firstMatch.exists)
        XCTAssertFalse(app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "chat.message.")).firstMatch.exists)
        let unavailable = XCTAttachment(screenshot: app.screenshot())
        unavailable.name = "external-chat-missing-thread"
        unavailable.lifetime = .keepAlways
        add(unavailable)
        let back = app.descendants(matching: .any).matching(identifier: "chat.back").firstMatch
        XCTAssertTrue(back.waitForExistence(timeout: 10) && back.isHittable)
        back.tap()
        let list = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            host.exists && ((host.value as? String) ?? "").isEmpty && !back.exists && !failure.exists
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [list], timeout: 15), .completed)
        let exited = XCTAttachment(screenshot: app.screenshot())
        exited.name = "external-chat-missing-back-list"
        exited.lifetime = .keepAlways
        add(exited)
    }

    func testObserveDeliveredMissingMessageAndBack() throws {
        let env = ProcessInfo.processInfo.environment
        guard env["QUATA_IOS_EXTERNAL_CHAT_E2E"] == "1",
              env["QUATA_IOS_EXTERNAL_CHAT_TARGET_MODE"] == "missing-message" else {
            throw XCTSkip("Requires the owned missing-message coordinator.")
        }
        continueAfterFailure = false
        let thread = try XCTUnwrap(env["QUATA_IOS_EXTERNAL_CHAT_THREAD"])
        let message = try XCTUnwrap(env["QUATA_IOS_EXTERNAL_CHAT_MESSAGE"])
        let visible = try XCTUnwrap(env["QUATA_IOS_EXTERNAL_CHAT_VISIBLE_MESSAGE"])
        let body = try XCTUnwrap(env["QUATA_IOS_EXTERNAL_CHAT_BODY"])
        let step = try XCTUnwrap(env["QUATA_IOS_EXTERNAL_CHAT_STEP"])
        XCTAssertNotNil(UUID(uuidString: step))
        for value in [thread, message, visible] {
            XCTAssertNotNil(value.range(of: "^[1-9][0-9]{0,15}$", options: .regularExpression))
        }
        XCTAssertNotEqual(message, visible)
        XCTAssertTrue(body.hasPrefix("Deep link "))
        let app = XCUIApplication(bundleIdentifier: "com.quata.ios")
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let host = app.descendants(matching: .any).matching(identifier: "quata-ios-chat-host").firstMatch
        let control = app.buttons.matching(identifier: "chat.message.\(visible)").firstMatch
        let selected = app.descendants(matching: .any).matching(NSPredicate(
            format: "identifier BEGINSWITH %@ AND identifier ENDSWITH %@", "chat.message.", ".selected"))
        let absent = app.descendants(matching: .any).matching(identifier: "chat.message.\(message)").firstMatch
        let composer = app.descendants(matching: .any).matching(identifier: "chat.composer.input").firstMatch
        print("QUATA_DEEP_LINK_CHAT_OBSERVER_READY:\(step)")
        fflush(stdout)
        let deadline = Date().addingTimeInterval(45)
        var confirmedOpen = false
        while Date() < deadline && !control.waitForExistence(timeout: 0.5) {
            let open = springboard.alerts.buttons.matching(NSPredicate(format: "label IN %@", ["Abrir", "Open"])).firstMatch
            if !confirmedOpen && open.exists && open.isHittable { confirmedOpen = true; open.tap() }
        }
        XCTAssertTrue(control.exists && control.isHittable)
        XCTAssertTrue(control.staticTexts.matching(NSPredicate(format: "label == %@", body)).firstMatch.exists)
        XCTAssertTrue(composer.exists && host.exists)
        XCTAssertTrue(["chat:sb:\(thread)", "chat:sb:\(thread)?message=\(message)"].contains(host.value as? String ?? ""))
        let observationEnd = Date().addingTimeInterval(5)
        repeat {
            XCTAssertEqual(selected.count, 0)
            XCTAssertFalse(absent.exists)
            XCTAssertTrue(control.exists && composer.exists)
            Thread.sleep(forTimeInterval: 0.1)
        } while Date() < observationEnd
        let capture = XCTAttachment(screenshot: app.screenshot())
        capture.name = "external-chat-missing-message-control"
        capture.lifetime = .keepAlways
        add(capture)
        let back = app.descendants(matching: .any).matching(identifier: "chat.back").firstMatch
        XCTAssertTrue(back.exists && back.isHittable)
        back.tap()
        let exited = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            host.exists && ((host.value as? String) ?? "").isEmpty && !back.exists && !control.exists
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [exited], timeout: 15), .completed)
        let exitEnd = Date().addingTimeInterval(2)
        repeat {
            XCTAssertTrue(host.exists && ((host.value as? String) ?? "").isEmpty)
            XCTAssertFalse(back.exists || control.exists || composer.exists)
            XCTAssertEqual(selected.count, 0)
            Thread.sleep(forTimeInterval: 0.1)
        } while Date() < exitEnd
        let finalCapture = XCTAttachment(screenshot: app.screenshot())
        finalCapture.name = "external-chat-missing-message-back-list"
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
