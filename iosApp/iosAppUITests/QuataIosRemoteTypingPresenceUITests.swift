import XCTest

/// Focal production-host proof for authenticated, ephemeral Chat typing presence.
@available(iOS 16.4, *)
final class QuataIosRemoteTypingPresenceUITests: XCTestCase {
    func testAuthenticatedPeerAndComposerExchangeTypingPresence() throws {
        let environment = ProcessInfo.processInfo.environment
        guard environment["QUATA_IOS_CHAT_TYPING_PRESENCE_UI_E2E"] == "1" else {
            throw XCTSkip("Authenticated Chat typing presence evidence is opt-in.")
        }
        let conversationId = try XCTUnwrap(nonEmpty(environment["QUATA_IOS_CHAT_E2E_CONVERSATION_ID"]))
        let draft = try XCTUnwrap(nonEmpty(environment["QUATA_IOS_CHAT_TYPING_DRAFT"]))
        let coordinatorPath = try XCTUnwrap(nonEmpty(environment["QUATA_IOS_CHAT_TYPING_COORDINATOR_DIRECTORY"]))
        let coordinator = URL(fileURLWithPath: coordinatorPath, isDirectory: true).standardizedFileURL
        XCTAssertTrue(coordinator.lastPathComponent.hasPrefix("quata-ios-chat-typing-"))
        var isDirectory: ObjCBool = false
        XCTAssertTrue(FileManager.default.fileExists(atPath: coordinator.path, isDirectory: &isDirectory) && isDirectory.boolValue)

        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
        app.launch()
        dismissStartupWhatsNewIfPresent(in: app)
        openDeepLink("quata://egquata.com/#chat-\(encodedFragment(conversationId))", in: app)
        let chat = app.descendants(matching: .any).matching(identifier: "quata-ios-chat-host").firstMatch
        XCTAssertTrue(chat.waitForExistence(timeout: 25), app.debugDescription)
        XCTAssertEqual(chat.value as? String, "chat:\(conversationId)")
        let composer = app.descendants(matching: .any).matching(identifier: "chat.composer.input").firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 45), app.debugDescription)

        try phase("ready-expiry", in: coordinator)
        let indicator = app.descendants(matching: .any).matching(identifier: "chat.typing.remote").firstMatch
        XCTAssertTrue(indicator.waitForExistence(timeout: 30), app.debugDescription)
        XCTAssertTrue(indicator.isHittable || !indicator.frame.isEmpty)
        attachScreenshot(app, name: "ios-chat-remote-typing-visible")
        try phase("expiry-visible", in: coordinator)
        XCTAssertTrue(
            waitForPhase("expiry-signals-stopped", in: coordinator, timeout: 10),
            "The coordinator must confirm that no more typing signals will be emitted."
        )
        XCTAssertTrue(waitForAbsence(indicator, timeout: 8), "Remote typing must expire without a stop broadcast.")
        try phase("expiry-complete", in: coordinator)

        try phase("ready-stop", in: coordinator)
        XCTAssertTrue(indicator.waitForExistence(timeout: 15), app.debugDescription)
        try phase("stop-visible", in: coordinator)
        XCTAssertTrue(waitForAbsence(indicator, timeout: 15), "Remote stop must remove the typing indicator.")
        try phase("stop-complete", in: coordinator)

        composer.tap()
        composer.typeText(draft)
        XCTAssertTrue(waitForValue(draft, in: composer, timeout: 10), "The exact synthetic draft must remain in the composer.")
        try phase("local-typed", in: coordinator)
        RunLoop.current.run(until: Date().addingTimeInterval(4.2))
        try phase("local-idle", in: coordinator)
    }

    private func phase(_ value: String, in directory: URL) throws {
        try Data(value.utf8).write(
            to: directory.appendingPathComponent("\(value).phase"),
            options: .withoutOverwriting
        )
    }

    private func waitForAbsence(_ element: XCUIElement, timeout: TimeInterval) -> Bool {
        let predicate = NSPredicate(format: "exists == false")
        return XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: predicate, object: element)], timeout: timeout) == .completed
    }

    private func waitForPhase(_ value: String, in directory: URL, timeout: TimeInterval) -> Bool {
        let marker = directory.appendingPathComponent("\(value).phase").path
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if FileManager.default.fileExists(atPath: marker) { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        }
        return FileManager.default.fileExists(atPath: marker)
    }

    private func waitForValue(_ expected: String, in element: XCUIElement, timeout: TimeInterval) -> Bool {
        let predicate = NSPredicate(format: "value == %@", expected)
        return XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: predicate, object: element)], timeout: timeout) == .completed
    }

    private func openDeepLink(_ value: String, in app: XCUIApplication) {
        guard let url = URL(string: value) else {
            XCTFail("Invalid deep link: \(value)")
            return
        }
        app.open(url)
    }

    private func dismissStartupWhatsNewIfPresent(in app: XCUIApplication) {
        let host = app.descendants(matching: .any).matching(identifier: "quata-ios-whats-new-host").firstMatch
        guard host.waitForExistence(timeout: 4) else { return }
        let deadline = Date().addingTimeInterval(20)
        while host.exists && Date() < deadline {
            let dismiss = ["whats-new-dismiss", "dismiss_whats_new"]
                .map { app.descendants(matching: .any).matching(identifier: $0).firstMatch }
                .first(where: { $0.exists && $0.isHittable })
            let next = ["whats-new-next", "next_whats_new"]
                .map { app.descendants(matching: .any).matching(identifier: $0).firstMatch }
                .first(where: { $0.exists && $0.isHittable })
            guard let control = dismiss ?? next else {
                RunLoop.current.run(until: Date().addingTimeInterval(0.25))
                continue
            }
            control.tap()
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        XCTAssertFalse(host.exists, "Startup What's New must close before Chat evidence.")
    }

    private func encodedFragment(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .urlFragmentAllowed) ?? value
    }

    private func nonEmpty(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else { return nil }
        return trimmed
    }

    private func attachScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
